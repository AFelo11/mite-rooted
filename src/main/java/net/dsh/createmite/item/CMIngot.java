package net.dsh.createmite.item;

import net.minecraft.ItemIngot;
import net.minecraft.Material;

/**
 * MITE 风格的金属锭。
 * ItemIngot 自带：材质绑定、贴图路径 "ingots/<材质名>"、堆叠上限 8、
 * 以及合成难度 = material.durability * 100。这里只需补上本地化名与最低产出难度。
 */
public class CMIngot extends ItemIngot {

    public CMIngot(int idArg, Material material, String unlocalizedName, float lowestDifficultyToProduce) {
        super(idArg, material);
        this.setUnlocalizedName(unlocalizedName);
        this.setLowestCraftingDifficultyToProduce(lowestDifficultyToProduce);
    }
}
