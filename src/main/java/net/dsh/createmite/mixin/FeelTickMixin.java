package net.dsh.createmite.mixin;

import net.minecraft.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 体感温度系统的每 tick 驱动（2026-10-09 由 BodyTempPlayerMixin 改名而来）。
 *
 * 挂在 EntityPlayer.onUpdate() 的头上：每 tick 推进一次体感温度
 * （内部只认服务端玩家 ✓）。
 * javap 实证：ServerPlayer.onUpdateEntity() 会 invokespecial EntityPlayer.onUpdate()，
 * 所以挂在父类这里，服务端玩家一定会走到 ✓。
 *
 * ⚠️ 原体温系统的 writeEntityToNBT / readEntityFromNBT 两个注入**已删掉** ✗ ——
 *    体感温度是**瞬时值、不存盘** ✓（用户 2026-10-09：体温系统整体卸载 ✓）
 */
@Mixin(EntityPlayer.class)
public abstract class FeelTickMixin {

    @Inject(method = "onUpdate()V", at = @At("HEAD"))
    private void createmite$feelTick(CallbackInfo ci) {
        net.dsh.createmite.CMAmbientFeel.tick((EntityPlayer) (Object) this);
    }
}
