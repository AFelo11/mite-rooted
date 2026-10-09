package net.dsh.createmite.mixin;

import net.dsh.createmite.CMFats;
import net.minecraft.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 必需脂肪 → 移动速度（2026-10-09 用户定稿）。
 *
 *   脂肪 ≥75% ⇒ ×0.9（厚实笨重）／ 中性 ×1.0 ／ 5~25% ×1.1 ／ ≤5% ×1.3（轻快）
 *
 * 【为什么缩放 motion 而不是改 getAIMoveSpeed】
 *   1.6.4 的玩家行走是 EntityPlayer.moveEntityWithHeading 里**写死的 0.1F** ✗，
 *   属性表那一套对玩家不生效 ⇒ 只能在算完之后把水平位移乘一下 ✓
 *   只在"玩家自己在动"时缩放 ✓ 免得把击退/水流/爆炸的位移也一起缩了 ✗
 */
@Mixin(EntityPlayer.class)
public abstract class FatsMoveMixin {

    @Inject(method = "moveEntityWithHeading(FF)V", at = @At("RETURN"))
    private void createmite$fatsSpeed(float strafe, float forward, CallbackInfo ci) {
        try {
            EntityPlayer p = (EntityPlayer) (Object) this;
            if (p.moveForward == 0.0F && p.moveStrafing == 0.0F) return;   // 没在走 ⇒ 不动
            float f = CMFats.speedFactor();
            if (f == 1.0F) return;
            p.motionX *= f;
            p.motionZ *= f;
        } catch (Throwable ignored) { }
    }
}
