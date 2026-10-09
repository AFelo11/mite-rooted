package net.dsh.createmite.mixin;

import net.dsh.createmite.CMAmbientFeel;
import net.minecraft.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 体感温度 -> 回血速度（2026-10-02 用户定稿）
 *
 * MITE 的自然回血由 EntityPlayer.shouldHeal() 把关（javap 实证 ✓）。
 * 它是**布尔**，表达不了"30% 速度" ⇒ 用**概率**折算：
 *   原本会回血的那一次，有 (1 - 系数) 的概率**挡掉** ⇒ 统计上就是回血变慢到那个百分比 ✓
 *
 * 系数表（CMAmbientFeel.regenMultiplier ✓）：
 *   <-15 = 15% / -15~-5 = 30% / -5~5 = 65% / 5~25 = 100% / 25~35 = 70% / 35~45 = 25% / >45 = 15%
 */
@Mixin(EntityPlayer.class)
public abstract class FeelRegenMixin {

    @Inject(method = "shouldHeal", at = @At("RETURN"), cancellable = true)
    private void cm$feelRegen(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        float m = CMAmbientFeel.regenMultiplierOf((EntityPlayer) (Object) this);
        if (m >= 1.0F) return;
        if (m <= 0.0F) { cir.setReturnValue(Boolean.FALSE); return; }
        if (((EntityPlayer) (Object) this).getRNG().nextFloat() >= m) {
            CMAmbientFeel.countRegen(false);     // ★ 计入"挡掉" ✓
            cir.setReturnValue(Boolean.FALSE);
        } else {
            CMAmbientFeel.countRegen(true);      // ★ 计入"放行" ✓
        }
    }
}
