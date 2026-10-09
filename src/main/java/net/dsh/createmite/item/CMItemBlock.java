package net.dsh.createmite.item;

import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.ItemBlock;

/**
 * 方块物品形态，可指定"背包图标"用哪张图。
 * 用途：像齿轮这种世界里画的是轴身、但背包里应该显示齿面的方块。
 */
public class CMItemBlock extends ItemBlock {

    private final String iconTextureName;
    private Icon iconOverride;

    public CMItemBlock(net.minecraft.Block block, String iconTextureName) {
        super(block);
        this.iconTextureName = iconTextureName;
    }

    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);
        if (this.iconTextureName != null) {
            this.iconOverride = register.registerIcon(this.iconTextureName);
        }
    }

    @Override
    public Icon getIconFromSubtype(int subtype) {
        if (this.iconOverride != null) return this.iconOverride;
        return super.getIconFromSubtype(subtype);
    }
}
