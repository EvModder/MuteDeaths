package net.evmodder.MuteDeaths;

import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvPlugin;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
*
* @author EvModder/EvDoc (evdoc at altcraft.net)
*/
public class MuteDeaths extends EvPlugin{
	private DeathMessagePacketIntercepter deathMessageBlocker;
	private BeheadMessageIntercepter beheadMessageBlocker;
	private final boolean USE_DISPLAY_NAME = true;
	private boolean deathMuteIncludesBehead;

	@Override public void onEvEnable(){
		deathMessageBlocker = new DeathMessagePacketIntercepter(this);
		new CommandMuteDeath(this);
		new CommandMuteKill(this);
		new CommandMuteBehead(this);

		// Listen for DropHeads BeheadMessageEvent
		try{
			Class.forName("net.evmodder.DropHeads.events.BeheadMessageEvent");
			beheadMessageBlocker = new BeheadMessageIntercepter(this);
			deathMuteIncludesBehead = getServer().getPluginManager().getPlugin("DropHeads").getConfig()
					.getBoolean("behead-announcement-replaces-player-death-event-message", false);
		}
		catch(ClassNotFoundException e){}
		catch(IllegalStateException e){getLogger().warning("reload issue?: "); e.printStackTrace();}
	}
	@Override public void onEvDisable(){
		deathMessageBlocker.unregisterAll();
	}

	public abstract class MuteCommand extends EvCommand{
		public MuteCommand(JavaPlugin pl){super(pl);}
		abstract boolean processMuteCommand(Player sender, UUID target);

		final String findNameForUUID(UUID target){//TODO: selector instead of just string name
			OfflinePlayer targetPlayer = getServer().getOfflinePlayer(target);
			if(targetPlayer != null){
				return (USE_DISPLAY_NAME && targetPlayer.isOnline()) ? targetPlayer.getPlayer().getDisplayName() : targetPlayer.getName();
			}
			Entity entity = getServer().getEntity(target);
			if(entity == null) return target.toString();
			if(entity.getCustomName() != null) return entity.getCustomName();
			return entity.getType().toString();//TODO: send selector
		}

		@Override final public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
			final String arg0 = (args.length > 0 ? String.join("", args) : "").toLowerCase();
			return getServer().getOnlinePlayers().stream().map(p -> p.getName()).filter(name -> name.toLowerCase().startsWith(arg0)).toList();
		}

		@Override final public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
			if(sender instanceof Player == false){
				sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players");
				return true;
			}
			if(args.length == 0){
				sender.sendMessage(ChatColor.RED+"Please specific a target");
				return false;
			}
			UUID target = null;
			try{target = UUID.fromString(args[0]);}
			catch(IllegalArgumentException ex){
				@SuppressWarnings("deprecation")
				OfflinePlayer targetPlayer = getServer().getOfflinePlayer(args[0]);
				if(targetPlayer != null) target = targetPlayer.getUniqueId();
			}
			if(target == null){
				sender.sendMessage(ChatColor.RED+"Unknown player/uuid: "+String.join(" ", args));
				return true;
			}
			return processMuteCommand((Player)sender, target);
		}
	}

	public class CommandMuteDeath extends MuteCommand{
		public CommandMuteDeath(JavaPlugin pl){super(pl);}

		@Override boolean processMuteCommand(Player sender, UUID target){
			final boolean muted = deathMuteIncludesBehead && beheadMessageBlocker.muteBeheadsOf(sender.getUniqueId(), target);
			if(deathMessageBlocker.muteDeaths(sender.getUniqueId(), target)){
				if(deathMuteIncludesBehead && !muted) beheadMessageBlocker.muteBeheadsOf(sender.getUniqueId(), target);
				sender.sendMessage(ChatColor.GOLD+"Muted death messages for "+ChatColor.RESET+findNameForUUID(target));
			}
			else{
				if(deathMuteIncludesBehead && muted) beheadMessageBlocker.muteBeheadsOf(sender.getUniqueId(), target);
				sender.sendMessage(ChatColor.GOLD+"Unmuted death messages for "+ChatColor.RESET+findNameForUUID(target));
			}
			return true;
		}
	}
	public class CommandMuteKill extends MuteCommand{
		public CommandMuteKill(JavaPlugin pl){super(pl);}

		@Override boolean processMuteCommand(Player sender, UUID target){
			final boolean muted = deathMuteIncludesBehead && beheadMessageBlocker.muteBeheadsBy(sender.getUniqueId(), target);
			if(deathMessageBlocker.muteKills(sender.getUniqueId(), target)){
				if(deathMuteIncludesBehead && !muted) beheadMessageBlocker.muteBeheadsBy(sender.getUniqueId(), target);
				sender.sendMessage(ChatColor.GOLD+"Muted kills"+(deathMuteIncludesBehead?"/beheads":"")+" by "+ChatColor.RESET+findNameForUUID(target));
			}
			else{
				if(deathMuteIncludesBehead && muted) beheadMessageBlocker.muteBeheadsBy(sender.getUniqueId(), target);
				sender.sendMessage(ChatColor.GOLD+"Unmuted kills"+(deathMuteIncludesBehead?"/beheads":"")+" by "+ChatColor.RESET+findNameForUUID(target));
			}
			return true;
		}
	}
	public class CommandMuteBehead extends EvCommand{
		public CommandMuteBehead(JavaPlugin pl){super(pl);}

		@Override final public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
			if(args.length == 1){
				return Stream.of("of", "by", "LOCAL", "GLOBAL").filter(t -> t.startsWith(args[0])).toList();
			}
			if(args.length == 2 && (args[0].equals("of") || args[0].equals("by"))){
				final String arg0 = (args.length > 0 ? String.join("", args) : "").toLowerCase();
				return getServer().getOnlinePlayers().stream().map(p -> p.getName()).filter(name -> name.toLowerCase().startsWith(arg0)).toList();
			}
			return List.of();
		}

		final String findNameForUUID(UUID target){//TODO: selector instead of just string name
			OfflinePlayer targetPlayer = getServer().getOfflinePlayer(target);
			if(targetPlayer != null){
				return (USE_DISPLAY_NAME && targetPlayer.isOnline()) ? targetPlayer.getPlayer().getDisplayName() : targetPlayer.getName();
			}
			Entity entity = getServer().getEntity(target);
			if(entity == null) return target.toString();
			if(entity.getCustomName() != null) return entity.getCustomName();
			return entity.getType().toString();//TODO: send selector
		}

		@Override final public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
			if(sender instanceof Player == false){
				sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players");
				return true;
			}
			if(args.length == 0){
				sender.sendMessage(ChatColor.RED+"Too few arguments");
				return false;
			}
			if(args[0].equalsIgnoreCase("of") || args[0].equalsIgnoreCase("by")){
				UUID target = null;
				try{target = UUID.fromString(args[1]);}
				catch(IllegalArgumentException ex){
					@SuppressWarnings("deprecation")
					OfflinePlayer targetPlayer = getServer().getOfflinePlayer(args[0]);
					if(targetPlayer != null) target = targetPlayer.getUniqueId();
				}
				if(target == null){
					sender.sendMessage(ChatColor.RED+"Unknown player/uuid: "+String.join(" ", args));
					return true;
				}
				if(args[0].equalsIgnoreCase("of")){
					if(beheadMessageBlocker.muteBeheadsOf(((Player)sender).getUniqueId(), target)){
						sender.sendMessage(ChatColor.GOLD+"Muted beheadings of "+ChatColor.RESET+findNameForUUID(target));
					}
					else{
						sender.sendMessage(ChatColor.GOLD+"Unmuted beheadings of"+ChatColor.RESET+findNameForUUID(target));
					}
					return true;
				}
				else{
					if(beheadMessageBlocker.muteBeheadsBy(((Player)sender).getUniqueId(), target)){
						sender.sendMessage(ChatColor.GOLD+"Muted beheadings by "+ChatColor.RESET+findNameForUUID(target));
					}
					else{
						sender.sendMessage(ChatColor.GOLD+"Unmuted beheadings by "+ChatColor.RESET+findNameForUUID(target));
					}
					return true;
				}
			}
			else{
				if(args[0].equalsIgnoreCase("LOCAL")){
					if(beheadMessageBlocker.muteLocal(((Player)sender).getUniqueId())) sender.sendMessage(ChatColor.GOLD+"Muted local behead messages");
					else sender.sendMessage(ChatColor.GOLD+"Unmuted local behead messages");
				}
				else{
					if(beheadMessageBlocker.muteGlobal(((Player)sender).getUniqueId())) sender.sendMessage(ChatColor.GOLD+"Muted global behead messages");
					else sender.sendMessage(ChatColor.GOLD+"Unmuted global behead messages");
				}
				return false;
			}
		}
	}
}