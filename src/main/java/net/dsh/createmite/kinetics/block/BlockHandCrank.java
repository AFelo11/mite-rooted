package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.kinetics.KineticHelper;
import net.dsh.createmite.kinetics.KineticNetwork;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumFace;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 手摇曲柄：右键摇动，提供会衰减的转速。
 *
 * 外观 = Create 原版模型：hand_crank/block.json（固定轴座）+ hand_crank/handle.json（随手柄转动），
 * 由 CreateModels.CRANK_BASE / CRANK_HANDLE 渲染。
 *
 * ★ 朝向规则（对照 Create 的 HandCrankBlock 与 blockstates/hand_crank.json）：
 *   - 轴座那根 4x4x5 的短轴**指向它依附的方块**；
 *   - 转轴 = facing 所在的轴（Create: getRotationAxis(state) = FACING.getAxis()）；
 *   - blockstate 用 x/y 旋转把"轴座朝下"的模型摆到 facing.getOpposite() 方向，
 *     这里由 KineticRenderer.applyCrankFacing 原样复刻。
 *   metadata 里除了轴，还必须存"朝向的正负"（见 KineticTileEntity.META_FACE_BIT），
 *   否则朝上/朝下、朝东/朝西会长得一模一样 —— 看起来就是"曲柄接反了"。
 */
public class BlockHandCrank extends BlockKineticBase {

    public BlockHandCrank(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("hand_crank", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 13;
    }

    /**
     * 放置时确定轴向与朝向。
     *
     * 【不要直接信 MITE 的 face 参数】它是"被点击方块的那一面"，
     * 在 MITE 的 tryPlaceAsBlock 里还有"点高处自动叠到上面一格""替换被点方块本身"等分支，
     * face 与实际落点不一定对得上。这里改为**扫描 6 个邻居找依附方块**，
     * 既稳又和 Create 的 canSurvive（要求 facing.getOpposite() 那侧有实体方块）语义一致。
     */
    @Override
    public int getMetadataForPlacement(World world, int x, int y, int z, ItemStack stack,
                                       Entity entity, EnumFace face, float hitX, float hitY, float hitZ) {
        int support = -1;

        // 1) 优先"玩家点的那个方块"。
        //    MITE 的 face 是"被点方块 -> 新方块"的方向（Item.tryPlaceAsBlock 里
        //    新方块取 RaycastCollision.neighbor_block，face 取 face_hit），
        //    所以被点的方块在新方块的 face.getOpposite() 那一侧。
        if (face != null) {
            int d = KineticHelper.oppositeDir(face.ordinal());
            if (isKinetic(world, x + KineticHelper.DX[d], y + KineticHelper.DY[d], z + KineticHelper.DZ[d])) {
                support = d;
            }
        }
        // 2) 否则取第一个相邻的动力方块
        if (support < 0) {
            for (int d = 0; d < 6; d++) {
                if (isKinetic(world, x + KineticHelper.DX[d], y + KineticHelper.DY[d], z + KineticHelper.DZ[d])) {
                    support = d;
                    break;
                }
            }
        }

        int axis;
        int facingDir;
        if (support >= 0) {
            axis = KineticHelper.DIR_AXIS[support];       // 转轴 = 依附方向所在的轴
            facingDir = KineticHelper.oppositeDir(support); // 短轴指向依附方块 → facing 朝反方向
        } else {
            // 没有依附方块：短轴指向被点的方块（它在 face 的方向上），facing 就朝 face
            axis = KineticHelper.axisFromFace(face);
            facingDir = face != null ? face.ordinal() : KineticHelper.POS_DIR[axis];
        }

        return axis | (KineticHelper.isPositiveDir(axis, facingDir) ? KineticTileEntity.META_FACE_BIT : 0);
    }

    private static boolean isKinetic(World world, int x, int y, int z) {
        return world.getBlockTileEntity(x, y, z) instanceof KineticTileEntity;
    }

    /**
     * 碰撞箱 / 选取箱。
     *
     * 【必须覆盖】基类默认给的是"沿轴的 4x4 细柱"，而曲柄的摇臂有 16px 宽、
     * 握把还要再伸出去 —— 用细柱当选取箱的话，右键点摇臂根本点不到方块，曲柄就摇不动。
     * 曲柄整块占据这一格，直接用整格箱最稳。
     */
    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    EnumFace face, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!(te instanceof KineticTileEntity)) return false;

        KineticTileEntity kinetic = (KineticTileEntity) te;

        // 先解算一次，看看这张网络是不是被应力压垮了。
        // 过载时曲柄是**摇不动**的：不给动力（crankTicks 保持不变），只响一声提示。
        // （应力容量是按"网络里有哪些动力源"算的，跟曲柄此刻转不转无关，
        //   所以曲柄停着的时候也能正确判断出过载。）
        KineticNetwork.resolve(kinetic);
        if (kinetic.overStressed) {
            if (!world.isRemote) {
                // 一声就够，不做循环：低音调把时长拉到约 1.5 秒。
                // 嫌长/短就调最后那个 pitch（越小越长）。
                world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                        "random.fizz", 0.7F, 0.5F);
            }
            return true;
        }

        // 原版：普通右击逆时针；**潜行右击顺时针**（反向）
        kinetic.crank(player != null && player.isSneaking());

        // ★ 摇曲柄的代价：每次右键扣 crank.hunger_cost（默认 0.1）点**饱和度**。
        //   直接扣饱和度，并给每个玩家记小数欠账（MITE 的饱和度是整数，
        //   0.1/次 必须攒够 1 点才真正 -1，否则会被取整吃掉）—— 见 CrankCost。
        //   过载"摇不动"的那次在上面就 return 了，走不到这里 —— 没产出动力就不扣。
        if (!world.isRemote && player != null) {
            net.dsh.createmite.CrankCost.consume(player,
                    net.dsh.createmite.CMConfig.getFloat("crank.hunger_cost", 0.1F));
        }
        if (!world.isRemote) {
            world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                    "tile.piston.out", 0.35F, 1.6F);
        }
        return true;
    }
}
