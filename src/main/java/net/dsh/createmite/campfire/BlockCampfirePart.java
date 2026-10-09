package net.dsh.createmite.campfire;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.Material;
import net.minecraft.World;

/** 营火占位格（3x3 里除核心外的 8 格 ✓ 自己什么都不画 ✓ 由核心的 TESR 一起画 ✓）*/
public class BlockCampfirePart extends Block {

    public static final int ID_PART = 2303;

    public BlockCampfirePart(int blockID) {
        super(blockID, Material.stone, new BlockConstants().setNeverHidesAdjacentFaces());
        this.setHardness(2.0F);
        this.setResistance(2.0F);
        this.setStepSound(Block.soundWoodFootstep);
        this.setUnlocalizedName("campfirePart");
        this.setTextureName("createmite_blank");
        // 碰撞箱跟核心一致 ✓（0.875 格 ✓ 站上去不掉血不烧人 ✓）
        this.setBlockBoundsForCurrentThread(0.0F, 0.0F, 0.0F, 1.0F, 14.0F / 16.0F, 1.0F);
    }

    @Override
    public int getRenderType() { return 0; }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        CampfireForm.onPartAdded(world, x, y, z);
    }

    /** ★ 挖掉占位格 ⇒ 整机 3x3 全部消失 ✓ 不掉落 ✓（用户定稿 ✓）延后一 tick 执行 ✓ */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        try { CampfireForm.demolishLater(world, x, y, z, false); } catch (Throwable t) { }
        super.breakBlock(world, x, y, z, blockID, meta);
    }
}
