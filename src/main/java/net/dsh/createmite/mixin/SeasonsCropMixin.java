package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BlockGrowingPlant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 四季 × 作物：**先让作物当四季不存在** ✓（2026-09-30 用户原话：
 *   「至于作物生长：现在先统一都能长，暂时不修改［我还没想好咋调］」✓）
 *
 * 【为什么要专门挂一手】MITE 的作物生长速率**本来就吃温度** ✓
 *   （`BlockGrowingPlant.getTemperatureGrowthRateModifier(float)` ✓），
 *   而我们的季节温度偏移是**全局**加在 `BiomeGenBase.getFloatTemperature()` 上的 ✓
 *   → 不管的话冬天作物就会不长 ✗，与"先统一都能长"冲突 ✗。
 *
 * 【做法】在作物**拿到温度之前**把季节偏移减掉 ✓ —— 作物看到的还是"没有四季"的温度 ✓，
 *   于是生长速率和原版一样 ✓；等用户想好怎么调，把这个 mixin 去掉 / 改成乘系数即可 ✓。
 *
 * ⚠️ 同类的东西还有「芦苇生长最低温」捏在 `MITEConstant.min_temperature_for_reed_growth`(0.2)
 *   里 ✗（编译期内联常量，改不动 ✓）—— 那一处暂时没管 ✓，用户要的话再单独处理 ✓。
 */
@Mixin(BlockGrowingPlant.class)
public abstract class SeasonsCropMixin {

    @ModifyVariable(method = "getTemperatureGrowthRateModifier(F)F", at = @At("HEAD"), argsOnly = true)
    private float cm$cropIgnoresSeasons(float temperature) {
        return temperature - CMSeasons.temperatureOffset();
    }
}
