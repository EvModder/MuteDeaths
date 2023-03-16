package net.evmodder.MuteDeaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import net.evmodder.DropHeads.events.BeheadMessageEvent;

public class BeheadMessageIntercepter{
	public BeheadMessageIntercepter(final Plugin pl, final HashMap<UUID, HashSet<UUID>> blockedKillers, final HashMap<UUID, HashSet<UUID>> blockedVictims){
		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onBeheadMessage(BeheadMessageEvent evt){
				//pl.getLogger().info("death msg evt");
				if(evt.isCancelled()) return;
				final UUID recipientUUID = evt.getRecipient().getUniqueId();
				final UUID victimUUID = evt.getVictim().getUniqueId();
				HashSet<UUID> mutedVictims = blockedVictims.get(recipientUUID);
				if(mutedVictims != null && mutedVictims.contains(victimUUID)){evt.setCancelled(true); return;}
				else if(evt.getKiller() != null){
					final UUID killerUUID = evt.getKiller().getUniqueId();
					HashSet<UUID> mutedKillers = blockedKillers.get(recipientUUID);
					if(mutedKillers != null && mutedKillers.contains(killerUUID)){evt.setCancelled(true); return;}
				}
			}
		}, pl);
	}
}
