package net.dsh.createmite.mixin;

import net.dsh.createmite.furnace.BigFurnaceContainer;
import net.dsh.createmite.furnace.BigFurnaceUi;
import net.dsh.createmite.furnace.FurnaceCoreTileEntity;
import net.dsh.createmite.furnace.client.GuiBigFurnace;
import net.minecraft.Minecraft;
import net.minecraft.NetClientHandler;
import net.minecraft.Packet100OpenWindow;
import net.minecraft.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端：把「大熔炉」那个自定义窗口类型拦下来，自己建容器 + 开界面。
 *
 * 【为什么是这里】原版 {@code NetClientHandler.handleOpenWindow} 只会认它自己那几种
 * inventoryType ✗，我们用的是自由号（{@link BigFurnaceUi#WINDOW_TYPE}），
 * 不拦的话客户端会当成没见过的类型直接忽略 ✗。
 *
 * 【windowId 必须用包里带回来的那个】否则之后所有槽位点击（Packet102）都对不上 ✗。
 * 【坐标从哪来】MITE 给 Packet100OpenWindow 加了 {@code setCoords} ✓（javap 实证），
 * 服务端已经塞了方块实体坐标 ✓ → 这边直接按坐标取客户端那一格的 TE ✓。
 */
@Mixin(NetClientHandler.class)
public abstract class NetClientHandlerMixin {

    @Inject(method = "handleOpenWindow", at = @At("HEAD"), cancellable = true)
    private void createmite$openBigFurnace(Packet100OpenWindow packet, CallbackInfo ci) {
        if (packet.inventoryType != BigFurnaceUi.WINDOW_TYPE) return;
        ci.cancel();                       // 我们自己的类型：原版那条路一律不走 ✓

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        TileEntity te = mc.theWorld.getBlockTileEntity(packet.x, packet.y, packet.z);
        if (!(te instanceof FurnaceCoreTileEntity)) return;

        // ★ 机壳档位用服务端发来的那个 ✓（客户端核心 TE 的 casing 没人同步过 ✗，
        //   照它建会少 8 格 → Packet104 越界崩溃 ✗ 实测踩到）
        BigFurnaceContainer container = new BigFurnaceContainer(mc.thePlayer, (FurnaceCoreTileEntity) te, packet.field_111008_f);
        container.windowId = packet.windowId;
        mc.thePlayer.openContainer = container;
        mc.displayGuiScreen(new GuiBigFurnace(container));
    }
}
