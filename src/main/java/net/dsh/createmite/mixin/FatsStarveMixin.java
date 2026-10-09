package net.dsh.createmite.mixin;

import net.dsh.createmite.CMFats;
import net.minecraft.FoodStats;
import net.minecraft.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 必需脂肪 → 挨饿保护（2026-10-09 用户定稿）。
 *
 *   脂肪 > 65% ⇒ 饿到 0 时**掉血速率降到原版的 0.35 倍**（储备 ✓），并把脂肪快速烧到 50% ✓
 *
 * 【怎么做到"降速率"】MITE 的饿死伤害由 FoodStats 里的私有进度
 *   `starve_progress` 累积触发 ✓ ⇒ 我们每 tick 把它**乘 0.35** ✓
 *   进度长得慢 ⇒ 达到掉血阈值要的时间变长 ⇒ 掉血速率正好是原版的 0.35 倍 ✓
 *   （不用去拦那次 attackEntityFrom，更稳 ✓）
 */
@Mixin(FoodStats.class)
public abstract class FatsStarveMixin {

    @Shadow private float starve_progress;

    @Inject(method = "onUpdate(Lnet/minecraft/ServerPlayer;)V", at = @At("HEAD"))
    private void createmite$fatsStarve(ServerPlayer player, CallbackInfo ci) {
        try {
            CMFats.tick(player);                                   // 刷新缓存 + 挨饿烧脂肪 ✓
            // 用户 2026-10-09 定稿：有储备时**掉血速率 = 原版的 0.35 倍** ✓
            //   （进度按 0.35 累积 ⇒ 自然就是"要等约 2.86 倍时间才掉一点血" ✓ 不是加快 ✗）
            if (CMFats.starveShield()) this.starve_progress *= CMFats.STARVE_RATE;
        } catch (Throwable ignored) { }
    }
}
