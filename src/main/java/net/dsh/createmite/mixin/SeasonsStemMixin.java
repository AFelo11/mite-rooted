package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BlockStem;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 四季 × 瓜梗（西瓜 / 南瓜）：**当季生长倍率**（2026-10-01 用户口述定稿 ✓）
 *
 * 【用户给的数】春/夏 4 天成型 + 3 天一个瓜 ／ 秋 3 天成型 + 2 天一个 ／ 冬 5 天成型 + 3 天一个 ✓
 *
 * 【怎么落的】BlockStem.getGrowthRate 在它的 updateTick 里**同时影响两件事** ✓：
 *   ① 梗自己长大（成型 ✓）  ② 成型后结果子的概率 ✓
 *   ⇒ 乘一个倍率就能同时挪动这两个周期 ✓
 *   ⚠️ 但两件事的"比例"是**代码里写死的** ✗ —— 用户给的三组数
 *      （春 4/3、秋 3/2、冬 5/3）比例并不完全一致（1.33 / 1.5 / 1.67）
 *      ⇒ 一个倍率只能取近似 ✓（取向：以"成型天数"为准 ✓，结果周期跟着走 ✓）
 *      ⇒ 要做到完全精确，得再单独挂钩结果子那一段 ✗（本轮先不做 ✓ 见交接文档 ✓）
 */
@Mixin(BlockStem.class)
public abstract class SeasonsStemMixin {

    @Inject(method = "getGrowthRate", at = @At("RETURN"), cancellable = true)
    private void cm$seasonalStemGrowth(World world, int x, int y, int z, CallbackInfoReturnable<Float> cir) {
        float f = CMSeasons.cropGrowthFactor(CMSeasons.CROP_STEM);
        if (f == 1.0F) return;
        cir.setReturnValue(cir.getReturnValueF() * f);   // ★ 取数日志已拆（用户确认正常 ✓）
    }
}
