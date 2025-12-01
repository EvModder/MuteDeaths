package net.evmodder.MuteDeaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.evmodder.EvLib.bukkit.PacketUtils;
import net.evmodder.EvLib.bukkit.ReflectionUtils;
import net.evmodder.EvLib.bukkit.ReflectionUtils.RefClass;
import net.evmodder.EvLib.bukkit.ReflectionUtils.RefField;
import net.evmodder.EvLib.bukkit.ReflectionUtils.RefMethod;

public class DeathMessagePacketIntercepter{
	private final Plugin pl;
	private final HashMap<UUID, HashSet<UUID>> blockedKillers, blockedVictims;

	private final RefClass outboundChatPacketClazz = ReflectionUtils.getRefClass(
			"{nms}.PacketPlayOutChat", "{nm}.network.protocol.game.PacketPlayOutChat", "{nm}.network.protocol.game.ClientboundSystemChatPacket");
	private final RefClass chatBaseCompClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent", "{nm}.network.chat.IChatBaseComponent");
	private final RefClass chatSerializerClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.IChatBaseComponent$ChatSerializer");
	private final RefField chatBaseCompField;
	private final RefMethod getChatBaseComp;
	private final RefMethod getJsonKyori; private final Object jsonSerializerKyori;
	private final RefMethod toJsonMethod; private final Object registryAccessObj;//class: IRegistryCustom.Dimension
	private final Pattern uuidPattern1 = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
	private final Pattern uuidPattern2 = Pattern.compile("\\[I?;?\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+),\\s*(-?[0-9]+)\\s*\\]");

	public DeathMessagePacketIntercepter(Plugin plugin){
		pl = plugin;
		blockedKillers = new HashMap<>();
		blockedVictims = new HashMap<>();

		RefField field = null;
		RefMethod method = null, kyoriMethod = null; Object kyoriObj = null;
		try{field = outboundChatPacketClazz.findField(chatBaseCompClazz);}
		catch(RuntimeException e1){
			try{
				method = outboundChatPacketClazz.getMethod("adventure$content");
				kyoriObj = ReflectionUtils.getRefClass("net.kyori.adventure.text.serializer.json.JSONComponentSerializer").getMethod("json").call();
				kyoriMethod = ReflectionUtils.getRefClass("net.kyori.adventure.text.serializer.ComponentSerializer").findMethodByName("serialize");
			}
			catch(RuntimeException e2){method = outboundChatPacketClazz.getMethod("content");}
		}
		finally{
			chatBaseCompField = field;
			getChatBaseComp = method;
			getJsonKyori = kyoriMethod;
			jsonSerializerKyori = kyoriObj;
		}

		RefMethod toJsonMethodTemp; Object registryAccessObjTemp = null;
		try{//1.20.5+
			toJsonMethodTemp = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz,
					ReflectionUtils.getRefClass("{nm}.core.HolderLookup$Provider", "{nm}.core.HolderLookup$a"));
			// If above succeeds:
			try{
				Object nmsServerObj = ReflectionUtils.getRefClass("{cb}.CraftServer").getMethod("getServer").of(Bukkit.getServer()).call();
				RefClass registryAccessClazz = ReflectionUtils.getRefClass("net.minecraft.core.IRegistryCustom$Dimension");
				registryAccessObjTemp = ReflectionUtils.getRefClass("{nm}.server.MinecraftServer")
						.findMethod(/*isStatic=*/false, registryAccessClazz).of(nmsServerObj).call();
			}
			catch(RuntimeException ex){ex.printStackTrace();}
		}
		catch(RuntimeException e){
			toJsonMethodTemp = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz);
		}
		toJsonMethod = toJsonMethodTemp;
		registryAccessObj = registryAccessObjTemp;

		// Injecting packet intercepter when players join/leave
		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onJoin(PlayerJoinEvent evt){
				try{injectPlayer(evt.getPlayer());}
				catch(Exception e){/*NoSuchElementException (wrapped) happens if they disconnect before login is complete*/}
			}
			@EventHandler public void onQuit(PlayerQuitEvent evt){removePlayer(evt.getPlayer());}
		}, pl);
		for(Player p : pl.getServer().getOnlinePlayers()) injectPlayer(p);
	}

	private void removePlayer(Player player){
		final Channel channel = PacketUtils.getPlayerChannel(player);
		channel.eventLoop().submit(()->{
			channel.pipeline().remove(player.getName());
			return null;
		});
	}

	public void unregisterAll(){
		for(Player player : pl.getServer().getOnlinePlayers()) removePlayer(player);
	}

	// Returns true if muted, false if unmuted.
	public boolean muteDeaths(UUID who, UUID mutedVictim){
		HashSet<UUID> muted = blockedVictims.get(who);
		if(muted == null) blockedVictims.put(who, muted=new HashSet<>());
		if(!muted.add(mutedVictim)){
			muted.remove(mutedVictim);
			return false;
		}
		return true;
	}

	// Returns true if muted, false if unmuted.
	public boolean muteKills(UUID who, UUID mutedKiller){
		HashSet<UUID> muted = blockedKillers.get(who);
		if(muted == null) blockedKillers.put(who, muted=new HashSet<>());
		if(!muted.add(mutedKiller)){
			muted.remove(mutedKiller);
			return false;
		}
		return true;
	}

	private UUID parseUUIDFromFourIntStrings(String s1, String s2, String s3, String s4){
		final Integer i1 = Integer.parseInt(s1), i2 = Integer.parseInt(s2), i3 = Integer.parseInt(s3), i4 = Integer.parseInt(s4); 
		return new UUID((long)i1 << 32 | i2 & 0xFFFFFFFFL, (long)i3 << 32 | i4 & 0xFFFFFFFFL);
	}

	private void injectPlayer(Player player){
		PacketUtils.getPlayerChannel(player).pipeline().addBefore("packet_handler", "mute_deaths", new ChannelDuplexHandler(){
			final UUID uuid = player.getUniqueId();
			@Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
				if(!outboundChatPacketClazz.isInstance(packet)){ // Not a chat packet
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("chat packet:\n"+packet+"\n");
				final Object chatBaseComp = chatBaseCompField == null ? packet : chatBaseCompField.of(packet).get();
				if(chatBaseComp == null){ // Chat packet does not have a comp field/method (pre-1.19)
					super.write(context, packet, promise);
					return;
				}
//				if(chatBaseCompField != null) pl.getLogger().info("chat packet base comp:\n"+chatBaseComp+"\n");
				final String jsonMsg = (String)(chatBaseCompField != null ?
						(registryAccessObj != null ? toJsonMethod.call(chatBaseComp, registryAccessObj) : toJsonMethod.call(chatBaseComp)) :
					getJsonKyori == null ? getChatBaseComp.of(packet).call() : getJsonKyori.of(jsonSerializerKyori).call(getChatBaseComp.of(packet).call())
				);
				if(jsonMsg == null){ // Chat comp is not a json object
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("chat packet isn't blocked");
				if(!jsonMsg.startsWith("{\"translate\":\"death.")){
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("detected death msg:\n"+jsonMsg);
				final UUID victimUUID; // uuid of entity that died
				final Matcher matcher1 = uuidPattern1.matcher(jsonMsg), matcher2;
				if(matcher1.find()){
					victimUUID = UUID.fromString(matcher1.group());
					matcher2 = null;
				}
				else{
					matcher2 = uuidPattern2.matcher(jsonMsg);
					if(matcher2.find()) victimUUID = parseUUIDFromFourIntStrings(matcher2.group(1), matcher2.group(2), matcher2.group(3), matcher2.group(4));
					else{
						pl.getLogger().warning("Unable to find UUID from death message: "+jsonMsg);
						pl.getLogger().warning("This is probably caused by another plugin destructively modifying the selector");
						super.write(context, packet, promise);
						return;
					}
				}
				HashSet<UUID> mutedVictims = blockedVictims.get(uuid);
				if(mutedVictims != null && mutedVictims.contains(victimUUID)) return;

				final UUID killerUUID; // TODO: Is killerUUID the only other id? Is it always 2nd?
				if(matcher1.find()) killerUUID = UUID.fromString(matcher1.group());
				else if(matcher2 != null && matcher2.find()){
					killerUUID = parseUUIDFromFourIntStrings(matcher2.group(1), matcher2.group(2), matcher2.group(3), matcher2.group(4));
				}
				else{ // No killer found
					super.write(context, packet, promise);
					return;
				}
				HashSet<UUID> mutedKillers = blockedKillers.get(uuid);
				if(mutedKillers != null && mutedKillers.contains(killerUUID)) return;

				super.write(context, packet, promise);
				return;
			}
		});
	}
}
