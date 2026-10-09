package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 冬季让所有群系"**会下雪**" ✓（2026-09-30 修"雨雪粒子全没了" ✗）
 *
 * 【为什么】MITE/原版决定降水粒子是雨还是雪，看的是**群系自己的两个东西**：
 *   ① 温度 `getFloatTemperature()`（< 0.15 才是雪 ✓）
 *   ② 群系标志 `getEnableSnow()`（**只有雪地群系才是 true** ✗）
 *   ⇒ 我们冬天把温度钳到 0.05 ✓ 之后：**温度像雪地、但标志还是"非雪地"** ✗
 *     ⇒ 雨的分支不要它（温度太低 ✗）、雪的分支也不要它（标志 false ✗）
 *     ⇒ **雨和雪粒子全都不生成** ✗✗ = 用户看到的现象 ✓
 *
 * 【修法】冬季把 `getEnableSnow()` 也变成 true ✓ ⇒ 原版直接走"下雪"分支 ✓
 *   （和"冬天整片世界相当于雪地群系"的设定完全一致 ✓）
 */
@Mixin(BiomeGenBase.class)
public abstract class SeasonsSnowFlagMixin {

    @Inject(method = "getEnableSnow()Z", at = @At("HEAD"), cancellable = true)
    private void cm$winterAlwaysSnow(CallbackInfoReturnable<Boolean> cir) {
        if (!CMSeasons.enabled()) return;
        if (CMSeasons.isWinter()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
