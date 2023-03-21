package net.evmodder.MuteDeaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import net.evmodder.DropHeads.events.BeheadMessageEvent;

public class BeheadMessageIntercepter{
	private final HashMap<UUID, HashSet<UUID>> blockedKillers, blockedVictims;
	private final HashSet<UUID> mutedGlobal, mutedLocal;

	public BeheadMessageIntercepter(final Plugin pl){
		blockedKillers = new HashMap<>();
		blockedVictims = new HashMap<>();
		mutedGlobal = new HashSet<>();
		mutedLocal = new HashSet<>();

		pl.getServer().getPluginManager().registerEvents(new Listener(){
			@EventHandler public void onBeheadMessage(BeheadMessageEvent evt){
				//pl.getLogger().info("death msg evt");
				if(evt.isCancelled()) return;
				final UUID recipientUUID = evt.getRecipient().getUniqueId();
				if(evt.isGlobal() ? mutedGlobal.contains(recipientUUID) : mutedLocal.contains(recipientUUID)){evt.setCancelled(true); return;}
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

	// Returns true if muted, false if unmuted.
	public boolean muteBeheadsOf(UUID who, UUID mutedVictim){
		HashSet<UUID> muted = blockedVictims.get(who);
		if(muted == null) blockedVictims.put(who, muted=new HashSet<>());
		if(!muted.add(mutedVictim)){
			muted.remove(mutedVictim);
			return false;
		}
		return true;
	}

	// Returns true if muted, false if unmuted.
	public boolean muteBeheadsBy(UUID who, UUID mutedKiller){
		HashSet<UUID> muted = blockedKillers.get(who);
		if(muted == null) blockedKillers.put(who, muted=new HashSet<>());
		if(!muted.add(mutedKiller)){
			muted.remove(mutedKiller);
			return false;
		}
		return true;
	}

	// Returns true if muted, false if unmuted.
	public boolean muteLocal(UUID who){
		if(mutedLocal.add(who)) return true;
		else mutedLocal.remove(who);
		return false;
	}

	// Returns true if muted, false if unmuted.
	public boolean muteGlobal(UUID who){
		if(mutedGlobal.add(who)) return true;
		else mutedGlobal.remove(who);
		return false;
	}
}
