package net.dsh.createmite.block;

import net.dsh.createmite.CMItems;
import net.dsh.createmite.CMMaterials;
import net.minecraft.Block;
import net.minecraft.BlockBreakInfo;
import net.minecraft.BlockOre;
import net.minecraft.CreativeTabs;

/**
 * 锌矿石：参数对齐 MITE 的铜/银矿（硬度 2.5F、挖掘等级 2、石质音效），
 * 矿脉材质用自建的 zinc（对应铜矿用 copper → 物品材质表会显示 "stone, zinc"），
 * 破坏时掉落「粗锌」而非自身（精准采集时仍掉落自身）。
 */
public class ZincOreBlock extends BlockOre {

    public ZincOreBlock(int blockID) {
        super(blockID, CMMaterials.zinc, 2);
        this.setHardness(2.5F);
        this.setResistance(5.0F);
        this.setStepSound(Block.soundStoneFootstep);
        this.setUnlocalizedName("oreZinc");
        this.setTextureName("zinc_ore");
        this.setCreativeTab(CreativeTabs.tabBlock);
    }

    @Override
    public int dropBlockAsEntityItem(BlockBreakInfo info) {
        if (info.wasSilkHarvested() || CMItems.rawZinc == null) {
            return super.dropBlockAsEntityItem(info);
        }
        return this.dropBlockAsEntityItem(info, CMItems.rawZinc.itemID, 0, 1, 1.0F);
    }
}
