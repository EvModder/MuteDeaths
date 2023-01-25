package net.evmodder.MuteDeaths;

import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvPlugin;
import java.util.List;
import java.util.UUID;
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
	private final boolean USE_DISPLAY_NAME = true;

	@Override public void onEvEnable(){
		deathMessageBlocker = new DeathMessagePacketIntercepter(this);
		new CommandMuteDeath(this);
		new CommandMuteKill(this);
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
			if(deathMessageBlocker.muteDeaths(sender.getUniqueId(), target)){
				sender.sendMessage(ChatColor.GOLD+"Muted death messages from "+ChatColor.RESET+findNameForUUID(target));
			}
			else{
				sender.sendMessage(ChatColor.GOLD+"Unmuted death messages from "+ChatColor.RESET+findNameForUUID(target));
			}
			return true;
		}
	}
	public class CommandMuteKill extends MuteCommand{
		public CommandMuteKill(JavaPlugin pl){super(pl);}

		@Override boolean processMuteCommand(Player sender, UUID target){
			if(deathMessageBlocker.muteKills(sender.getUniqueId(), target)){
				sender.sendMessage(ChatColor.GOLD+"Muted kills from "+ChatColor.RESET+findNameForUUID(target));
			}
			else{
				sender.sendMessage(ChatColor.GOLD+"Unmuted kills from "+ChatColor.RESET+findNameForUUID(target));
			}
			return true;
		}
	}
}