package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.kinetics.KineticTileEntity;
import net.dsh.createmite.kinetics.tile.CrushingWheelTileEntity;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 粉碎轮（Create 的 crushing_wheel）：**成对**夹碎物品。
 *
 * 【行为（按需求）】
 *   - 两轮中间**上方**吸取物品（每 tick 吸一个，和石磨同一套做法）；
 *   - 有转速 + 旁边有配对的另一个粉碎轮 → 开始粉碎；
 *   - 成品从**下方**自动吐出（掉成物品实体）；
 *   - **不能右键取出**输入或产物（不覆写 onBlockActivated，右键没反应）。
 *
 * 外观 = Create crushing_wheel.obj → assets/createmite/meshes/crushing_wheel.mesh，
 * 轮子比方块大（模型 X/Z 从 -0.57 到 1.57），成对摆放时两个轮子会互相咬合。
 */
public class BlockCrushingWheel extends BlockKineticBase {

    public BlockCrushingWheel(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("crushing_wheel", "createmite_blank");
    }

    @Override
    protected int particleLayer() {
        return 19;   // crushing_wheel_plates
    }

    /** ★ 同 BlockKineticBase 的根因修复：world 当场绑上，见 KineticTileEntity.hasWorld 的说明 */
    @Override
    public TileEntity createNewTileEntity(World world) {
        return KineticTileEntity.withWorld(new CrushingWheelTileEntity(), world);
    }

    /** 轮子比一格大，选取/碰撞箱给整格 */
    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    /** 粉碎轮**不能**用扳手转轴向：一对轮子的朝向必须互相对着，转了就没法配对 */
    @Override
    public boolean isWrenchRotatable() {
        return false;
    }

    /**
     * 破坏时把**还没加工完的物品**掉出来。
     *
     * 【为什么必须写】铲掉方块时 TE 会被移除，槽里的东西会跟着一起消失 ——
     * 玩家丢进去一批矿、手滑拆了轮子就全没了。石磨有这段处理，粉碎轮一开始漏了。
     * 注意要在 super.breakBlock（它负责移除 TE）**之前**读，否则已经取不到了。
     */
    @Override
    public void breakBlock(net.minecraft.World world, int x, int y, int z, int blockID, int meta) {
        net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!world.isRemote && te instanceof CrushingWheelTileEntity) {
            net.minecraft.ItemStack left = ((CrushingWheelTileEntity) te).input;
            if (left != null) {
                net.minecraft.EntityItem drop = new net.minecraft.EntityItem(world,
                        (double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D, left);
                world.spawnEntityInWorld(drop);
                ((CrushingWheelTileEntity) te).input = null;
            }
        }
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /** 右键不做任何事 —— 需求里明确"无法右键取出原产品和产物" */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, net.minecraft.EntityPlayer player,
                                    net.minecraft.EnumFace face, float hitX, float hitY, float hitZ) {
        return false;
    }
}
