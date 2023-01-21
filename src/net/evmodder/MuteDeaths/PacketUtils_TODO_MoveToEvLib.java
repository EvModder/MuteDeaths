package net.evmodder.MuteDeaths;

import org.bukkit.entity.Player;
import io.netty.channel.Channel;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

// A trashy place to dump stuff that I should probably move to EvLib after ensure cross-version safety
public class PacketUtils_TODO_MoveToEvLib{
	private final static RefClass craftPlayerClazz = ReflectionUtils.getRefClass("{cb}.entity.CraftPlayer");
	private final static RefMethod playerGetHandleMethod = craftPlayerClazz.getMethod("getHandle");
	private final static RefClass entityPlayerClazz = ReflectionUtils.getRefClass("{nms}.EntityPlayer", "{nm}.server.level.EntityPlayer");
	private final static RefClass playerConnectionClazz = ReflectionUtils.getRefClass("{nms}.PlayerConnection", "{nm}.server.network.PlayerConnection");
	private final static RefField playerConnectionField = entityPlayerClazz.findField(playerConnectionClazz);
	private final static RefClass networkManagerClazz = ReflectionUtils.getRefClass("{nms}.NetworkManager", "{nm}.network.NetworkManager");
	private final static RefField networkManagerField = playerConnectionClazz.findField(networkManagerClazz);
	private final static RefField channelField = networkManagerClazz.findField(Channel.class);
	public static Channel getPlayerChannel(Player player){
		final Object playerEntity = playerGetHandleMethod.of(player).call();
		final Object playerConnection = playerConnectionField.of(playerEntity).get();
		final Object networkManager = networkManagerField.of(playerConnection).get();
		return (Channel)channelField.of(networkManager).get();
	}
	private final static RefClass classPacket = ReflectionUtils.getRefClass("{nms}.Packet", "{nm}.network.protocol.Packet");
	private final static RefMethod sendPacketMethod = playerConnectionClazz.findMethod(/*isStatic=*/false, Void.TYPE, classPacket);
	public static void sendPacket(Player player, Object packet){
		Object entityPlayer = playerGetHandleMethod.of(player).call();
		Object playerConnection = playerConnectionField.of(entityPlayer).get();
		Object castPacket = classPacket.getRealClass().cast(packet);
		sendPacketMethod.of(playerConnection).call(castPacket);
	}
}
