package net.dsh.createmite;

import net.minecraft.EntityPlayer;
import net.minecraft.WorldServer;

/** 客户端右键 → 同 JVM 的服务端玩家（★ MITE 的 Block.onBlockActivated 只有客户端会被调 ✗）*/
public final class CMCampfireBridge {

    private CMCampfireBridge() {}

    public static void forward(EntityPlayer clientPlayer, int x, int y, int z) {
        try {
            if (clientPlayer == null || clientPlayer.worldObj == null) return;
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null) return;
            WorldServer sw = server.worldServerForDimension(clientPlayer.worldObj.provider.dimensionId);
            if (sw == null) return;
            java.util.List players = sw.playerEntities;
            if (players == null || players.isEmpty()) return;
            Object o = players.get(0);
            if (!(o instanceof net.minecraft.ServerPlayer)) return;
            net.dsh.createmite.campfire.BlockCampfire.useOnServer(sw, x, y, z, (net.minecraft.ServerPlayer) o);
        } catch (Throwable t) {
            System.out.println("[CreateMITE][CAMPFIRE] 转服务端失败: " + t);
        }
    }
}
