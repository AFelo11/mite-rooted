package net.dsh.createmite.mixin;

import net.dsh.createmite.CMRecipes;
import net.minecraft.CraftingManager;
import net.minecraft.IRecipe;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 删掉 MITE 自带的面粉配方（小麦×3 → 面粉），让面粉**只能**从石磨磨出来。
 *
 * 【为什么不直接用 FML 官方的 RecipeModifyEvent + RemoveRecipeModifier】
 * 实测会崩，而且是在 CraftingManager 的构造阶段就崩、整个游戏起不来：
 *
 *   java.lang.IllegalAccessError: failed to access class
 *     net.xiaoyu233.fml.reload.transform.registry.ItemBlockRecipeRegistryMixin$1
 *     from class net.minecraft.CraftingManager
 *   at CraftingManager.redirect$zdj000$injectApplyModifier(CraftingManager.java:926)
 *
 * 原因：FML 的 ItemBlockRecipeRegistryMixin 在**命中**一个 modifier 之后，会走一个
 * javac 为 switch(RecipeType) 生成的合成 switch-map 类（ItemBlockRecipeRegistryMixin$1）。
 * 这个类是包级私有的、由 app 类加载器加载，而 CraftingManager 由 KnotClassLoader 加载，
 * 运行期包不同 → IllegalAccessError。
 * （没有 modifier 命中时那行代码根本不会被执行，所以这个坑平时不暴露 —— 一注册就炸。）
 *
 * 所以这里改用 Mixin：在 CraftingManager 构造完成（此时 FML 已经跑完
 * "注册自定义配方 + 套用 modifier"）之后，把产物是面粉的配方直接从配方表里摘掉。
 * 效果和 RecipeType.REMOVE 完全一样，而且不碰那段有问题的代码。
 */
@Mixin(CraftingManager.class)
public abstract class CraftingManagerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void createmite$removeFlourRecipe(CallbackInfo ci) {
        List recipes = ((CraftingManager) (Object) this).getRecipeList();
        if (recipes == null) return;

        int removed = 0;
        for (int i = recipes.size() - 1; i >= 0; i--) {
            Object entry = recipes.get(i);
            if (!(entry instanceof IRecipe)) continue;
            ItemStack out = ((IRecipe) entry).getRecipeOutput();
            if (CMRecipes.isRemovedRecipe(out)) {
                recipes.remove(i);
                removed++;
            }
        }

        // 只从 CraftingManager 的列表里删还不够 —— MITE 另外按"产物"维护了一份
        // Item.recipes[] / Item.num_recipes（在 ShapedRecipes / ShapelessRecipes 构造时
        // 由 RecipeHelper.addRecipe 填进去）。MITE 的参考数据（item_recipes.txt）和
        // 制造难度都是从 Item.recipes[] 读的，不清掉就会留一条已经不存在的配方。
        //
        // 难度值先读出来、再原样写回：面粉的"制造难度"如果掉到 0，
        // 用面粉的蛋糕/曲奇会跟着一起变简单，那不是这次改动想要的。
        Item flour = Item.flour;
        if (flour != null && flour.num_recipes > 0) {
            float difficulty = flour.getLowestCraftingDifficultyToProduce();
            flour.recipes = new IRecipe[0];
            flour.num_recipes = 0;
            flour.setLowestCraftingDifficultyToProduce(difficulty);
        }

        // 骨粉：MITE 的参考数据同样读 Item.recipes[]，但**不能整条清空 dyePowder** ✗
        // （它名下还有一大堆别的染料配方）→ 这里**逐条过滤**，只摘掉"骨头 → 骨粉"那一条 ✓
        Item dye = Item.dyePowder;
        if (dye != null && dye.recipes != null && dye.num_recipes > 0) {
            float dyeDifficulty = dye.getLowestCraftingDifficultyToProduce();
            IRecipe[] filtered = new IRecipe[dye.num_recipes];
            int kept = 0;
            for (int i = 0; i < dye.num_recipes && i < dye.recipes.length; i++) {
                IRecipe rec = dye.recipes[i];
                if (rec == null) continue;
                ItemStack o = rec.getRecipeOutput();
                boolean boneMeal = o != null && o.getItem() == Item.dyePowder && o.getItemSubtype() == 15;
                if (!boneMeal) filtered[kept++] = rec;
            }
            if (kept != dye.num_recipes) {
                dye.recipes = filtered;
                dye.num_recipes = kept;
                dye.setLowestCraftingDifficultyToProduce(dyeDifficulty);
                System.out.println("[CreateMITE] 骨粉：从 Item.recipes 里摘掉 " + (filtered.length - kept) + " 条（骨头→骨粉）");
            }
        }

        System.out.println("[CreateMITE] 已删除 MITE 自带的配方 " + removed
                + " 条：面粉（小麦×3→面粉）+ 骨粉（骨头→骨粉×3）—— 两者都改成只能靠石磨磨 ✓");

        // ================= 最低工作台要求（2026-09-28 用户要求）=================
        // 用户原话：「除粉碎轮外其他配方最低的合成要求都是至少需要铁工作台（更高等级的工作台也能合成）；
        //            粉碎轮比较特殊得至少用秘银工作台合成」
        //
        // 【MITE 本来就是这么设计的 ✓】反汇编实证：IRecipe 上就有
        //     setMaterialToCheckToolBenchHardnessAgainst(Material) / getMaterialToCheckToolBenchHardnessAgainst()
        //   字面意思就是"用来跟工作台(ToolBench)硬度作比较的材料" ✓；
        //   而 GuiContainer 渲染配方产物时会拿它跟**当前工作台**比：
        //     · 配方没设 → 退回 item.getHardestMetalMaterial()（所以原版"秘银工具要秘银工作台"就是这么来的 ✓）
        //     · 比不过 → 置 Container.crafting_result_shown_but_prevented = true（**产物照样显示，但不给合成** ✓）
        //   所以这里**直接沿用 MITE 自己的机制** ✓，而不是另写一套判定 ✗。
        //
        // 落点：CraftingManager 构造之后（此时 FML 已经把我们注册的配方放进 recipes 列表 ✓）。
        // 只动"产物属于本模组"的配方 ✓（别人的配方一个字都不碰 ✗）。
        int gated = 0;
        for (int i = 0; i < recipes.size(); i++) {
            Object entry = recipes.get(i);
            if (!(entry instanceof IRecipe)) continue;
            IRecipe recipe = (IRecipe) entry;
            ItemStack out = recipe.getRecipeOutput();
            if (!CMRecipes.isOurRecipeOutput(out)) continue;

            // ★ 2026-09-29：等级改由 CMRecipes.toolBenchMaterialFor(out) 统一给 ✓
            //   （熔炉六件套里，圆石组=铜、黑曜石组=铁、地狱岩组=秘银；粉碎轮=秘银；其余=铁 ✓）
            net.minecraft.Material need = CMRecipes.toolBenchMaterialFor(out);   // 每个方块自己的要求 ✓
            recipe.setMaterialToCheckToolBenchHardnessAgainst(need);
            gated++;
        }
        System.out.println("[CreateMITE] 最低工作台要求已设置 " + gated + " 条配方："
                + "熔炉六件套按材质（圆石组=铜、黑曜石组=铁、地狱岩组=秘银）、"
                + "粉碎轮=秘银、其余本模组配方=铁 ✓（MITE 机制：更高级的工作台照样能合成 ✓）");
    }
}
