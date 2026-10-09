package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BlockCarrot;
import net.minecraft.BlockCrops;
import net.minecraft.BlockOnion;
import net.minecraft.BlockPotato;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 四季 × 农作物：**当季生长倍率**（2026-10-01 用户口述定稿 ✓）
 *
 * 【为什么挂在 BlockCrops 而不是 BlockGrowingPlant】后者那个 getGrowthRate 是
 *   **abstract** 的 ✗ —— Mixin 注入不了抽象方法 ⇒ 只能在**具体实现类**上挂 ✓
 *   （BlockCrops 这一挂就覆盖了 小麦 / 胡萝卜 / 马铃薯 / 洋葱 ✓）
 *   瓜梗是另一个实现类 ⇒ 见 SeasonsStemMixin ✓
 *
 * 【温度那半边已经处理过了】SeasonsCropMixin 把季节温度偏移从温度系数里减掉了 ✓
 *   ⇒ 这里只负责乘"我们自己那张季节表" ✓，两套效果不会叠 ✓
 *
 * ⚠️ 判种类**必须先判子类** ✗ —— 胡萝卜/洋葱/马铃薯全都 extends BlockCrops ✓
 *   顺序反了就全被当成小麦 ✗
 */
@Mixin(BlockCrops.class)
public abstract class SeasonsCropGrowthMixin {

    @Inject(method = "getGrowthRate", at = @At("RETURN"), cancellable = true)
    private void cm$seasonalGrowth(World world, int x, int y, int z, CallbackInfoReturnable<Float> cir) {
        // ★ 2026-10-01：用户实测确认季节作物正常 ✓ ⇒ **取数日志已拆除** ✗（不再刷屏 ✓）
        int kind = cm$kindOf(this);
        if (kind < 0) return;
        float f = CMSeasons.cropGrowthFactor(kind);
        if (f == 1.0F) return;
        cir.setReturnValue(cir.getReturnValueF() * f);

    }

    private static String cm$nameOf(Object block) {
        if (block instanceof BlockCarrot) return "胡萝卜";
        if (block instanceof BlockOnion) return "洋葱";
        if (block instanceof BlockPotato) return "马铃薯";
        return "小麦";
    }

    /** 这是哪一种作物 ✓（子类优先 ✗ 顺序不能反 ✓） */
    private static int cm$kindOf(Object block) {
        if (block instanceof BlockCarrot) return CMSeasons.CROP_CARROT_ONION;
        if (block instanceof BlockOnion) return CMSeasons.CROP_CARROT_ONION;
        if (block instanceof BlockPotato) return CMSeasons.CROP_POTATO;
        return CMSeasons.CROP_WHEAT;
    }
}
