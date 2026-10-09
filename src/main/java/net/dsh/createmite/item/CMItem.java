package net.dsh.createmite.item;

import net.minecraft.CreativeTabs;
import net.minecraft.Item;
import net.minecraft.Material;

/**
 * 通用材料物品（非锭类）。
 * 注意：MITE 的 Item(int, String) 第二个参数是**贴图名**（不是本地化名），
 * 本地化名必须另外用 setUnlocalizedName 设置，否则会显示成 item.null.name。
 */
public class CMItem extends Item {

    public CMItem(int idArg, Material material, String unlocalizedName, String textureName, float difficulty) {
        super(idArg, textureName);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName(textureName);
        this.setMaxStackSize(64);
        this.setCreativeTab(CreativeTabs.tabMaterials);
        // 接入 MITE 的材料 / 合成难度体系，避免数据一致性告警
        this.setMaterial(material);
        this.setCraftingDifficultyAsComponent(difficulty);
        this.setLowestCraftingDifficultyToProduce(difficulty);
    }
}
