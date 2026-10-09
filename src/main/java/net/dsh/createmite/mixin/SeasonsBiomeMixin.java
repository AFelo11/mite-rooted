package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ★ 四季的**唯一温度挂点** ✓（2026-09-30）。
 *
 * `BiomeGenBase.getFloatTemperature()` 内部只有一句 `return this.temperature;` ✓（javap 实证），
 * 而**全工程**引用它的只有 7 处：区块生成（雪/冰）、雾色/天空色、雪人、
 * 以及它自己的 `getBiomeGrassColor()/getBiomeFoliageColor()`（草色/叶色就是这么算的 ✓）。
 * → 在这里加一个季节偏移，**视觉 + 气候 + 草色 + 作物生长**全都跟着季节走 ✓
 *   （MITE 的 `BlockGrowingPlant` 本来就是读温度算生长速率的 ✓ 白捡 ✓）。
 *
 * ⚠️ 这个方法**无参** ✗ → 偏移只能取静态值（`CMSeasons` 每 tick 从世界时钟刷新的那个 ✓）。
 * ⚠️ 偏移是全局的 → 下界/地下世界的群系温度也会被一起偏移（幅度很小 ✓，先这样 ✓）。
 */
@Mixin(BiomeGenBase.class)
public abstract class SeasonsBiomeMixin {

    @Inject(method = "getFloatTemperature()F", at = @At("RETURN"), cancellable = true)
    private void cm$seasonTemperature(CallbackInfoReturnable<Float> cir) {
        // ★★ 2026-09-30 用户定稿：改成"由 CMSeasons 统一决定最终温度" ✓
        //   冬天 = **钳到结冰线 0.15 以下** ⇒ 原版机制自己冻湖面 ✓（用户："原版自己冻吧" ✓）
        //   最后 3 天 = 放回正常值 ⇒ 原版化冰机制接管 ✓
        //   其它季节 = 原来的余弦偏移 ✓
        //   ⚠️ **生成期 = 原样返回** ✗（用户："别动我的世界生成规则" ✓）
        cir.setReturnValue(Float.valueOf(CMSeasons.adjustTemperature(cir.getReturnValueF())));
    }

    /**
     * ★★ 2026-09-30 用户要求：**草色/叶色按季节整体换** ✓
     *   用户指定 —— 春 = 草原群系、夏 = 雨林群系、秋 = 热带草原、冬 = 雪地群系 ✓
     *   （具体目标色在 `CMSeasons` 里算 ✓：从颜色表按目标群系的温/湿取值 ✓、换季平滑过渡 ✓）
     *
     * 为什么直接**整个替换**而不是调温度 ✗：只调温度的话草色变化很微弱，
     *   用户实测"看不出实装" ✗；而且他要的就是明确的四季配色 ✓。
     */
    @Inject(method = "getBiomeGrassColor()I", at = @At("RETURN"), cancellable = true)
    private void cm$seasonGrassColor(CallbackInfoReturnable<Integer> cir) {
        if (CMSeasons.enabled()) {
            cir.setReturnValue(Integer.valueOf(CMSeasons.grassColor()));
        }
    }

    /** 同上：树叶 / 藤蔓 ✓ */
    @Inject(method = "getBiomeFoliageColor()I", at = @At("RETURN"), cancellable = true)
    private void cm$seasonFoliageColor(CallbackInfoReturnable<Integer> cir) {
        if (CMSeasons.enabled()) {
            cir.setReturnValue(Integer.valueOf(CMSeasons.foliageColor()));
        }
    }
}
