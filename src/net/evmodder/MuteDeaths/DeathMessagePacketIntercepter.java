package net.evmodder.MuteDeaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class DeathMessagePacketIntercepter{
	private final Plugin pl;
	private final HashMap<UUID, HashSet<UUID>> blockedKillers, blockedVictims;

	private final RefClass outboundPacketClazz = ReflectionUtils.getRefClass(
			"{nms}.PacketPlayOutChat", "{nm}.network.protocol.game.PacketPlayOutChat", "{nm}.network.protocol.game.ClientboundSystemChatPacket");
	private final RefClass chatBaseCompClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent", "{nm}.network.chat.IChatBaseComponent");
	private final RefClass chatSerializerClazz = ReflectionUtils.getRefClass(
			"{nms}.IChatBaseComponent$ChatSerializer", "{nm}.network.chat.IChatBaseComponent$ChatSerializer");
	private final RefField chatBaseCompField;
	private final RefMethod getChatBaseComp;
	private final RefMethod toJsonMethod = chatSerializerClazz.findMethod(/*isStatic=*/true, String.class, chatBaseCompClazz);
	private final Pattern uuidPattern = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

	public DeathMessagePacketIntercepter(Plugin plugin){
		pl = plugin;
		blockedKillers = new HashMap<>();
		blockedVictims = new HashMap<>();
		
		RefField field = null;
		RefMethod method = null;
		try{field = outboundPacketClazz.findField(chatBaseCompClazz);}
		catch(RuntimeException ex){method = outboundPacketClazz.getMethod("content");}
		finally{
			chatBaseCompField = field;
			getChatBaseComp = method;
		}

		// Injecting packet intercepter when players join/leave
		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onJoin(PlayerJoinEvent evt){
				try{injectPlayer(evt.getPlayer());}
				catch(Exception e){/*NoSuchElementException (wrapped) happens if they disconnect before login is complete*/}
			}
			@EventHandler public void onQuit(PlayerQuitEvent evt){removePlayer(evt.getPlayer());}
		}, pl);
		for(Player p : pl.getServer().getOnlinePlayers()) injectPlayer(p);

		// Listen for DropHeads BeheadMessageEvent
		try{
			Class.forName("net.evmodder.DropHeads.events.BeheadMessageEvent");
			new BeheadMessageIntercepter(pl, blockedKillers, blockedVictims);
		}
		catch(ClassNotFoundException e){}
		catch(IllegalStateException e){pl.getLogger().warning("reload issue?: "); e.printStackTrace();}
	}

	private void removePlayer(Player player){
		final Channel channel = PacketUtils_TODO_MoveToEvLib.getPlayerChannel(player);
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

	private void injectPlayer(Player player){
		PacketUtils_TODO_MoveToEvLib.getPlayerChannel(player).pipeline().addBefore("packet_handler", "mute_deaths", new ChannelDuplexHandler(){
			final UUID uuid = player.getUniqueId();
			@Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
				if(!outboundPacketClazz.isInstance(packet)){ // Not a chat packet
					super.write(context, packet, promise);
					return;
				}
				//pl.getLogger().info("chat packet");
				final Object chatBaseComp = chatBaseCompField == null ? packet : chatBaseCompField.of(packet).get();
				if(chatBaseComp == null){ // Chat packet does not have a comp field/method (pre-1.19)
					super.write(context, packet, promise);
					return;
				}
				//pl.getLogger().info("has base comp");
				final String jsonMsg = (String)(chatBaseCompField == null ? getChatBaseComp.of(packet).call() : toJsonMethod.call(chatBaseComp));
				if(jsonMsg == null){ // Chat comp is not a json object
					super.write(context, packet, promise);
					return;
				}
				//pl.getLogger().info("is json");
				if(!jsonMsg.startsWith("{\"translate\":\"death.")){
					super.write(context, packet, promise);
					return;
				}
//				pl.getLogger().info("detected death msg:\n"+jsonMsg);
				final Matcher matcher = uuidPattern.matcher(jsonMsg);
				if(!matcher.find()){
					pl.getLogger().warning("Unable to find UUID from death message: "+jsonMsg);
					pl.getLogger().warning("This is probably caused by another plugin destructively modifying the selector");
					super.write(context, packet, promise);
					return;
				}
				final UUID victimUUID = UUID.fromString(matcher.group()); // victimUUID is always first
				HashSet<UUID> mutedVictims = blockedVictims.get(uuid);
				if(mutedVictims != null && mutedVictims.contains(victimUUID)) return;
				if(matcher.find()){
					final UUID killerUUID = UUID.fromString(matcher.group()); // TODO: Is killerUUID the only other id? Is it always 2nd?
					HashSet<UUID> mutedKillers = blockedKillers.get(uuid);
					if(mutedKillers != null && mutedKillers.contains(killerUUID)) return;
				}
				super.write(context, packet, promise);
				return;
			}
		});
	}
}
