package net.dsh.createmite.item;

import net.minecraft.CreativeTabs;
import net.minecraft.Item;
import net.minecraft.Material;

/**
 * 通用材料物品（非锭类）。
 * 注意：MITE 的 Item(int, String) 第二个参数是**贴图名**（不是本地化名），
 * 本地化名必须另外用 setUnlocalizedName 设置，否则会显示成 item.null.name。
 *
 * ★ 2026-10-09 追加：三个养分开关（蛋白质 / 必需脂肪 / 植物营养 ✓）
 *   MITE 的规则是「开关打开 ⇒ 该项 = 本物品的 nutrition x 8000」（见 Item.getEssentialFats 反汇编 ✓）
 *   ⇒ 这里只要把开关暴露出来，我们的食物就能参与养分体系 ✓
 *   （以前我们的自制食物**一个养分都不给** ✗ 是既有的小漏洞，顺手一起补上 ✓）
 */
public class CMItem extends Item {

    private boolean cmProtein = false;
    private boolean cmEssentialFats = false;
    private boolean cmPhytonutrients = false;

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

    public CMItem setHasProtein(boolean v) { this.cmProtein = v; return this; }
    public CMItem setHasEssentialFats(boolean v) { this.cmEssentialFats = v; return this; }
    public CMItem setHasPhytonutrients(boolean v) { this.cmPhytonutrients = v; return this; }

    @Override
    public boolean hasProtein() { return this.cmProtein; }

    @Override
    public boolean hasEssentialFats() { return this.cmEssentialFats; }

    @Override
    public boolean hasPhytonutrients() { return this.cmPhytonutrients; }
}
