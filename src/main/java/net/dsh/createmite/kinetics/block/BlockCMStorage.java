package net.dsh.createmite.kinetics.block;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.Material;

/**
 * 本模组的"金属 / 合金块"：**锌块、黄铜块、安山合金块**（2026-09-28 新增）。
 *
 * 【为什么单独一个类】用户要求：**这三个块的最低挖掘等级不是铁，而是"秘银级及以上"** ✓
 *   → 不能走 {@link BlockKineticBase#applyMachineDefaults}（那里写死 MIN_HARVEST_LEVEL = 3 = 铁）✗，
 *     这里自己设 {@code setMinHarvestLevel(4)} ✓
 *     （MITE 等级表：木 0 ／ 燧石·石·锈铁 1 ／ 铜·银·金 2 ／ **铁·远古金属 3** ／ **秘银·钻石 4** ／ 艾德曼 5）
 *
 * 【为什么继承 Block 而不是 BlockKineticBase】它们不是机器 ✓：不转、不进动力网络、不需要方块实体 ✓
 *   （和机壳 BlockCasing 同样的理由，见那个类的注释）。
 */
public class BlockCMStorage extends Block {

    /** 秘银级 = 4（用户指定：这三个块要秘银级及以上的镐/斧才挖得动）✓ */
    public static final int STORAGE_MIN_HARVEST_LEVEL = 4;

    public BlockCMStorage(int blockID, String unlocalizedName, String textureName) {
        super(blockID, Material.iron, new BlockConstants());
        this.setHardness(BlockKineticBase.HARDNESS_PER_BLOCK);
        this.setMinHarvestLevel(STORAGE_MIN_HARVEST_LEVEL);
        this.setStepSound(Block.soundMetalFootstep);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName(textureName);
        this.setCreativeTab(CreativeTabs.tabBlock);
    }
}