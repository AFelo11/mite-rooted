package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BlockIce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冬季**禁用原版自带的化冰机制** ✓（2026-09-30 用户指定 ✓）
 *
 * 用户原话：「原版冰是有自带的溶解机制的［我需要你在**冬季的时候禁用此机制**！
 *   冬季不再自主化冰，但是**冬季最后三天除外**（最后三天需要使用原版的化冰机制化冰）］」✓
 *
 * 做法：`BlockIce.updateTick` 在冬季（且**不在**最后三天的融冰窗口里）直接取消 ✓，
 *   于是整个冬季冰都不会自己化 ✓；到了最后三天放行 ✓，原版那套化冰照常跑 ✓
 *   （跟我们的"从湖心往外化"的 pass 并行 ✓，两套一起化 ✓）
 */
@Mixin(BlockIce.class)
public abstract class SeasonsIceMeltMixin {

    @Inject(method = "updateTick(Lnet/minecraft/World;IIILjava/util/Random;)V", at = @At("HEAD"), cancellable = true)
    private void cm$disableVanillaMeltInWinter(net.minecraft.World world, int x, int y, int z,
                                                java.util.Random random, CallbackInfo ci) {
        if (!CMSeasons.enabled()) return;
        // 冬季 且 不在最后三天的融冰窗口 ⇒ 原版化冰禁止 ✓
        if (CMSeasons.isWinter() && !CMSeasons.isWinterMeltWindow()) {
            ci.cancel();
        }
    }

    /**
     * ★★ 更底层的一道闸：**直接拦 `BlockIce.melt(...)`** ✓
     *
     * 【为什么还要这一道】javap 实证 MITE 的化冰链是：
     *   `BlockIce.updateTick` → `Block.ice` 的 `World.isFreezing` 判断 →
     *   不满足就 `BlockSnow.canMelt(world,x,y,z)` → **`BlockIce.melt(world,x,y,z)`** ✓
     *   只拦 `updateTick` 的话，**别的调用者**（MITE 自己的雪/冰 pass 等）照样能把冰化掉 ✗
     *   ⇒ 拦在 `melt` 上，**不管谁调都过不去** ✓（除非在最后三天的融冰窗口里 ✓）
     */
    @Inject(method = "melt(Lnet/minecraft/World;III)Z", at = @At("HEAD"), cancellable = true)
    private void cm$blockMeltInWinter(net.minecraft.World world, int x, int y, int z,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!CMSeasons.enabled()) return;
        if (CMSeasons.isWinter() && !CMSeasons.isWinterMeltWindow()) {
            cir.setReturnValue(Boolean.FALSE);      // 冬季：一律不许化 ✓
        }
    }
}
