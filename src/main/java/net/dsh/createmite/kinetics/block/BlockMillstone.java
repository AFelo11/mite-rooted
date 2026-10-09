package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.kinetics.KineticTileEntity;
import net.dsh.createmite.kinetics.tile.MillstoneTileEntity;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumFace;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 石磨：接上转速后把丢进来的物品磨成产物。
 *
 * == 交互（2026-09-24 改版）==
 *  - **投料只能靠丢**：把物品丢到石磨上方那一格即可，方块自己吸进去；手持物品右键不再放入。
 *  - **空手右键取成品**：成品直接进背包，背包满了掉在石磨上方。
 *  - **磨到一半取不出来**：原料槽不对玩家暴露（这是刻意的，见 MillstoneTileEntity）。
 *  - 石磨没有 GUI，也不需要 GUI。
 *
 * == 驱动（2026-09-24 改版）==
 *  石磨**只能被齿轮带动**：水平相邻、轴向同为竖直的齿轮才啮合；
 *  传动轴与手摇曲柄都带不动它。规则实现在 KineticHelper.connects / flips。
 */
public class BlockMillstone extends BlockKineticBase {

    /**
     * ★ 石磨**自带齿轮** —— 资料 330130 原文：
     *   "有的元件自带齿轮，比如 石磨、动力搅拌器、动力合成器等，**也适用于齿轮变速的规则**"。
     * 所以石磨和大齿轮对角摆放时，同样吃 ×2 / ×0.5 的变速 ✓。
     */
    @Override
    public boolean hasBuiltInCog() {
        // ★ 用户 2026-09-26 明确：**大齿轮不能带动石磨** ✗
        //   （资料 330130 说"石磨等自带齿轮的元件也适用于齿轮变速的规则"，但用户实测/要求以他的为准 ✓）
        return false;
    }

    public BlockMillstone(int blockID) {
        super(blockID, Material.stone, machineConstants());
        // 本体立方体用透明贴图，真正的外观由 TESR / CreateModels 画
        this.applyMachineDefaults("millstone", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 5;
    }

    /**
     * ★ 和 BlockKineticBase 同一条根因修复：Chunk 传进来的 world 必须**当场**绑到 TE 上，
     * 否则"方块实体 tick 期间"懒创建出来的石磨会以 worldObj==null 的状态被交出去 → 崩游戏。
     * 详见 KineticTileEntity.hasWorld 上面那一整段。
     */
    @Override
    public TileEntity createNewTileEntity(World world) {
        return KineticTileEntity.withWorld(new MillstoneTileEntity(), world);
    }

    /**
     * 石磨**永远竖直轴（Y）**：磨盘的转轴只有一种朝向，
     * "被齿轮带动"这条规则才有确定含义（否则墙上挂一个横轴石磨，
     * 齿轮该怎么咬就没有定义了）。
     */
    @Override
    public int getMetadataForPlacement(World world, int x, int y, int z, ItemStack stack,
                                       Entity entity, EnumFace face, float hitX, float hitY, float hitZ) {
        return 1;   // 0=X 1=Y 2=Z
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    EnumFace face, float hitX, float hitY, float hitZ) {
        // 客户端直接吞掉这次右键：否则手持方块时客户端会走一遍"放置"逻辑，出现鬼影
        if (world.isRemote) return true;

        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!(te instanceof MillstoneTileEntity)) return false;
        MillstoneTileEntity mill = (MillstoneTileEntity) te;

        // 投料只能靠"丢到石磨上方"，手持物品右键什么都不做
        if (player.getHeldItemStack() != null) return false;

        // 空手：有成品就取走；没有成品就把状态报出来。
        // 石磨没有 GUI，"齿轮没啮合""应力过载"这类失败从外面完全看不出来，
        // 必须留一个能问的地方 —— 否则玩家只能对着不动的机器干等。
        if (mill.ejectOutput(player)) return true;
        if (net.dsh.createmite.CMHints.enabled(player)) {
            mill.reportStatus(player);
        }
        return true;
    }

    /**
     * 拆掉石磨时把里面的东西吐出来。
     *
     * 原料槽是不对玩家暴露的（磨到一半取不出来），如果拆方块时不留这一手，
     * 卡在里面的原料就永久损失了。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof MillstoneTileEntity) {
            MillstoneTileEntity mill = (MillstoneTileEntity) te;
            // 成品槽现在是"最多 8 件、可混装" → 一件一件吐 ✓
            for (int i = 0; i < mill.outputs.size(); i++) mill.dropStack(mill.outputs.get(i));
            mill.outputs.clear();
            mill.dropStack(mill.input);
            mill.input = null;
        }
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /** 石磨的轴向被锁死成竖直 Y，扳手转不了（转了"只能被齿轮带动"就没有确定含义了） */
    @Override
    public boolean isWrenchRotatable() {
        return false;
    }

    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0625D, 0.0D, 0.0625D, 0.9375D, 0.9375D, 0.9375D);
    }
}
