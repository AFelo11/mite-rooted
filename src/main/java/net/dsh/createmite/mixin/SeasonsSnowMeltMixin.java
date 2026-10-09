package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BlockSnow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 雪层（`BlockSnow`）：**冬季不许融化** ✓（2026-09-30 用户："下雪下出来的雪片在冬季不应该融化" ✓）
 *
 * 【为什么之前没拦住】之前只给 `BlockIce` 挂了闸 ✗ —— **雪层是另一个方块** ✓，
 *   它有自己的 `updateTick` / `canMelt` / `melt` ✓（javap 实证 ✓）⇒ 得单独再挂一手 ✓。
 *
 * 做法与冰完全一致 ✓：冬季（**不在**最后三天的融冰窗口里）把
 *   `updateTick` 取消 ✓ + `melt` 返回 false ✓；最后三天放行 ⇒ 原版化雪机制照常 ✓。
 */
@Mixin(BlockSnow.class)
public abstract class SeasonsSnowMeltMixin {

    @Inject(method = "updateTick(Lnet/minecraft/World;IIILjava/util/Random;)V", at = @At("HEAD"), cancellable = true)
    private void cm$noSnowMeltTick(net.minecraft.World world, int x, int y, int z,
                                   java.util.Random random, CallbackInfo ci) {
        if (!CMSeasons.enabled()) return;
        if (CMSeasons.isWinter() && !CMSeasons.isWinterMeltWindow()) {
            ci.cancel();
        }
    }

    @Inject(method = "melt(Lnet/minecraft/World;III)Z", at = @At("HEAD"), cancellable = true)
    private void cm$noSnowMelt(net.minecraft.World world, int x, int y, int z,
                               CallbackInfoReturnable<Boolean> cir) {
        if (!CMSeasons.enabled()) return;
        if (CMSeasons.isWinter() && !CMSeasons.isWinterMeltWindow()) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
