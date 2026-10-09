package net.dsh.createmite.mixin;

import net.dsh.createmite.CMItems;
import net.minecraft.TileEntityFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 锌的熔炼热值：**粗锌 / 粉碎粗锌 至少要用圆石熔炉（热值 2）**
 *
 * 【为什么必须用 Mixin】MITE 的熔炼热值不是"注册配方时给的"，而是**按输入物品 id 现场查表**：
 *   FurnaceRecipes.getSmeltingResult(stack, heatLevel) 里有一句
 *   if (heatLevel &lt; TileEntityFurnace.getHeatLevelRequired(itemID)) return null;
 *   而 getHeatLevelRequired(int) 是一张**写死的静态表**
 *   （艾德曼矿石 4 ／ 秘银矿石 3 ／ 铜银金铁矿石 2 ／ 其余默认 1）；
 *   addSmelting 只收 (id, 产物)，**根本没有热值参数**。
 *   所以想让我们那两个物品要 2 级，只能在这张表的入口挂钩子 —— 这也是唯一干净的位置。
 *
 * 【各熔炉上限（javap 实证）】黏土/砂岩/硬化黏土 = 1 ／ 圆石 = 2 ／ 黑曜石 = 3 ／ 下界岩 = 4
 * 【热值来自燃料（MITE 参考数据）】木板木头木炭类 = 1 ／ 煤炭 = 2 ／ 岩浆桶 = 3 ／ 烈焰棒 = 4
 *
 * → 效果：黏土/砂岩/硬化黏土熔炉**烧不出锌锭**，圆石熔炉及以上才行（燃料热值也得够 2）。
 */
@Mixin(TileEntityFurnace.class)
public abstract class FurnaceHeatMixin {

    /** 圆石熔炉的热值 = 2 */
    private static final int CM_ZINC_HEAT = 2;

    @Inject(method = "getHeatLevelRequired(I)I", at = @At("HEAD"), cancellable = true)
    private static void cm$zincHeatRequirement(int itemId, CallbackInfoReturnable<Integer> cir) {
        int heat = cm$heatFor(itemId);
        if (heat > 0) {
            cir.setReturnValue(heat);
        }
    }

    /**
     * 本模组各物品的熔炼热值要求 ✓
     *
     * 【和 MITE 自带矿石**一一对应**】用户要求"熔炼要求也要统一"：
     *   铁/金/铜/银矿石在 MITE 里是 2 级 → 它们的**粉碎产物也是 2 级**（圆石熔炉）✓
     *   秘银矿石 3 级 → 粉碎秘银矿石 **3 级（黑曜石熔炉）** ✓
     *   艾德曼矿石 4 级 → 粉碎艾德曼矿石 **4 级（下界岩熔炉）** ✓
     *   锌（我们自己的矿）：粗锌 / 粉碎粗锌 都定 2 级（圆石熔炉）✓
     *
     * @return 需要几级热值；0 = 不干预（交回 MITE 原来的表 ✓）
     */
    private static int cm$heatFor(int itemId) {
        if (CMItems.rawZinc != null && itemId == CMItems.rawZinc.itemID) return 2;
        if (CMItems.crushedRawZinc != null && itemId == CMItems.crushedRawZinc.itemID) return 2;
        if (CMItems.crushedIron != null && itemId == CMItems.crushedIron.itemID) return 2;
        if (CMItems.crushedGold != null && itemId == CMItems.crushedGold.itemID) return 2;
        if (CMItems.crushedCopper != null && itemId == CMItems.crushedCopper.itemID) return 2;
        if (CMItems.crushedSilver != null && itemId == CMItems.crushedSilver.itemID) return 2;
        if (CMItems.crushedMithril != null && itemId == CMItems.crushedMithril.itemID) return 3;
        if (CMItems.crushedAdamantium != null && itemId == CMItems.crushedAdamantium.itemID) return 4;
        return 0;
    }
}
