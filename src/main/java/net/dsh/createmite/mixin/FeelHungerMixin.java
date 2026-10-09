package net.dsh.createmite.mixin;

import net.minecraft.FoodStats;
import net.minecraft.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 体感温度 → 饥饿消耗倍率（2026-10-09 由 BodyTempHungerMixin 改名而来）。
 *
 * 【为什么挂在这里】javap 实证 FoodStats.onUpdate(ServerPlayer) 的开头是：
 *     40: aload_1
 *     41: invokevirtual ServerPlayer.getWetnessAndMalnourishmentHungerMultiplier()F
 *     44: fstore_2                      <-- 第一个 float 局部变量：本 tick 的饥饿系数
 *   ⇒ 改 fstore_2 这一个局部变量，就把本 tick 的饥饿累积量整体缩放了 ✓，
 *     而且**不动 MITE 自己的湿度 / 营养不良系数**（我们是乘在它外面 ✓）。
 *
 * 【为什么不用 addHungerServerSide】那个方法也会被"吃东西 / 躺床"等路径调用，
 *   一刀切会把回填的负值也缩放，语义就错了 ✗。只缩放"每 tick 自然消耗"这一条才是对的 ✓。
 */
@Mixin(FoodStats.class)
public abstract class FeelHungerMixin {

    /** 只报一次，方便核对挂钩到底有没有生效 */
    private static boolean createmite$logged = false;

    @ModifyVariable(method = "onUpdate(Lnet/minecraft/ServerPlayer;)V", at = @At("STORE"), ordinal = 0)
    private float createmite$hungerRate(float mult, ServerPlayer player) {
        float m = net.dsh.createmite.CMAmbientFeel.hungerMultiplier(
                net.dsh.createmite.CMAmbientFeel.tierOf(net.dsh.createmite.CMAmbientFeel.feel(player)));
        if (!createmite$logged) {
            createmite$logged = true;
            System.out.println("[MITE][体感] 饥饿倍率挂钩已生效：MITE 系数=" + mult + " 体感倍率=" + m);
        }
        return mult * m;
    }
}
