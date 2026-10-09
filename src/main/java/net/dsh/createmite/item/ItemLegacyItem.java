package net.dsh.createmite.item;

import net.minecraft.Entity;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * ★ 一次性「旧 id 迁移」物品（2026-10-07 ✓ 用完可以整个删掉 ✓）
 *
 * 【为什么有它】run317 里苹果派胚因为 id 位移的坑（`newItemSafely` 要的是 itemID − 256 ✗）
 *   实际落在了 **2640**，而我们随后把源码修成了计划的 **2384** ✓
 *   ⇒ 玩家箱子里/身上可能还有 id=2640 的旧苹果派胚 ✗
 *   ⇒ MITE 里 `Item.itemsList[2640]` 一旦变成 null，渲染物品栏时会 **NPE 崩游戏** ✗✗
 *   ⇒ 所以在这个 id 上挂一个**隐形过渡物品** ✓ 它一进背包/箱子就**自己改成新 id** ✓
 *     （两侧都跑 onUpdate ✓ 改法一致 ⇒ 不会造成"两侧 ItemStack 不一致"那个老坑 ✓）
 *
 * 【什么时候能删】确认存档里再没有 id=2640 的堆叠之后 ✓（删掉它 + CMItems 里那三行 ✓）
 */
public class ItemLegacyItem extends CMItem {

    /** 迁移目标 id（新物品 ✓）*/
    private final int targetId;

    public ItemLegacyItem(int idArg, String unlocalizedName, String textureName, Material material, int targetId) {
        super(idArg, material, unlocalizedName, textureName, 60.0F);
        this.targetId = targetId;
        this.setCreativeTab(null);            // ★ 不进创造栏（它是隐形过渡件 ✓）
    }

    /** ★ 一被 tick 就自动换成新 id ✓（等价于"旧堆叠自动升级" ✓）*/
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (stack == null) return;
        if (targetId > 0 && targetId < Item.itemsList.length && Item.itemsList[targetId] != null) {
            stack.itemID = targetId;
        }
    }
}
