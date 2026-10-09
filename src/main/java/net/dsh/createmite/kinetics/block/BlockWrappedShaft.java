package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.kinetics.KineticHelper;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.dsh.createmite.kinetics.client.CreateModels;
import net.dsh.createmite.kinetics.client.CreateModelsFurnace;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.EnumFace;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * 熔炉结构的「包裹传动杆」：黑曜石 / 下界岩 / 圆石 把一根传动轴整块包住（2026-09-29 新增）。
 *
 * 【几何】用户给的 Blockbench 工程是 6 个元素的自定义几何（不是六图标方块），
 * 由 CreateModelsFurnace 画（那个文件由 _analysis 下的生成脚本从 .bbmodel 直接生成）：
 * 4 片机壳 + 1 片端盖 = 外壳（静止），中间那根轴（自转）—— 与「安山传动杆箱」同一套画法 ✓。
 *
 * 【取向】工程里轴沿 Z、开口朝北；生成时已旋转成「轴 = 局部 +Y、开口 = 局部 +Y」。
 * 于是本类只需要两件事：
 *   ① 轴向（metadata bit0-1，与传动杆完全相同的放置/扳手规则 ✓）；
 *   ② 开口朝轴的正方向还是负方向（metadata bit2 = META_FACE_BIT ✓）。
 *      放置时取「玩家点的那一面」→ 开口永远朝着放它的玩家 ✓（点顶面就朝上 ✓）。
 *      这一位在本方块上是独占的：bit2 在普通传动杆那儿是 META_ENCASED_BIT，
 *      而包裹传动杆是另一个方块，互不冲突 ✓（与 KineticTileEntity 里那条注释同理）。
 *
 * 【材质与手感】与同材质方块对齐（见 CMBlocks 里的登记表）：
 *   圆石 / 下界岩 = 挖掘等级 2（铜·银·金镐就能挖），黑曜石 = 3（铁镐起步）✓ 用户指定 ✓。
 *   硬度仍按「机器方块统一 3.0 = 每次挖掘扣 300 耐久」这条既有规格（BlockKineticBase）✓，
 *   不跟着材质走 —— 这里只是包了层壳的机器，不是矿石 ✓。
 */
public class BlockWrappedShaft extends BlockKineticBase {

    public static final int KIND_OBSIDIAN = 0;
    public static final int KIND_NETHERRACK = 1;
    public static final int KIND_COBBLESTONE = 2;

    private final int kind;
    /** 挖掘粒子 / 破坏粒子的贴图层（0/1 是轴，31/32/33 才是机壳 → 取机壳那张） */
    private final int shellLayer;

    public BlockWrappedShaft(int blockID, int kind, String unlocalizedName,
                             Material material, int minHarvestLevel, int shellLayer) {
        super(blockID, material, machineConstants());
        this.kind = kind;
        this.shellLayer = shellLayer;
        this.applyMachineDefaults(unlocalizedName, "createmite_blank");
        // applyMachineDefaults 给的是「铁起步 / 金属脚步声」，这里按材质改回来 ✓
        this.setMinHarvestLevel(minHarvestLevel);
        this.setStepSound(Block.soundStoneFootstep);
    }

    /** 材质种类（KIND_*）—— 大熔炉成型判定要拿它跟核心的材质对齐 ✓ */
    public int kind() {
        return this.kind;
    }

    /** 机壳几何（静止）—— 三种材质各一份，只有贴图层不同 */
    public CreateModels.El[] shellModel() {
        if (this.kind == KIND_OBSIDIAN) return CreateModelsFurnace.WRAPPED_SHELL_OBSIDIAN;
        if (this.kind == KIND_NETHERRACK) return CreateModelsFurnace.WRAPPED_SHELL_NETHERRACK;
        return CreateModelsFurnace.WRAPPED_SHELL_COBBLESTONE;
    }

    /** 里面那根轴（自转）—— 三种材质共用 */
    public CreateModels.El[] shaftModel() {
        return CreateModelsFurnace.WRAPPED_SHAFT;
    }

    @Override
    protected int particleLayer() {
        return this.shellLayer;
    }

    /**
     * 右键包裹传动杆 = 切换整机「燃烧中 / 待机」外观（**临时演示开关**，2026-09-30）。
     *
     * 【为什么挂这儿而不是核心】核心被 26 格封在正中央，玩家根本点不到 ✗；
     * 包裹传动杆就在中层正十字四格，正好是外侧那四面能点到的地方 ✓
     * （整机模型只挡视线不挡碰撞，所以打到的就是它 ✓）。
     *
     * 纯客户端：不写世界、不存盘、不参与判定，退出重进即复位 ✓
     * 真正的燃烧态要等阶段 3 炉子逻辑 + 阶段 4 UI，到那时删掉这里、改读炉子状态即可 ✓
     */
    @Override
    public boolean onBlockActivated(net.minecraft.World world, int x, int y, int z,
                                    net.minecraft.EntityPlayer player, net.minecraft.EnumFace face,
                                    float hitX, float hitY, float hitZ) {
        // ★ 2026-10-01 用户：「之前留下的蹲下右键切换燃烧/未燃烧的模型操作不需要了」
        //   ⇒ 那个临时演示开关（sneak + 右键包裹传动杆 = 切整机外观）**整段删掉** ✓
        //   现在不管蹲不蹲，右键都是**打开界面** ✓；
        //   整机的"燃烧中/待机"外观改由炉子**真实工作状态**驱动（见 FurnaceMultiblock.isBurning ✓）
        net.dsh.createmite.furnace.FurnaceMultiblock.tryOpenUi(world, x, y, z, player, face);
        return false;
    }

    // ===================== 多方块熔炉的成型通知（2026-09-30）=====================

    @Override
    public void onBlockAdded(net.minecraft.World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
    }

    @Override
    public void breakBlock(net.minecraft.World world, int x, int y, int z, int blockID, int meta) {
        // 父类负责移除 TE（BlockKineticBase 那段真 bug 修复 ✓），这里补一句"通知核心复核"
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /** 整格碰撞箱：机壳占满这一格 ✓（与齿轮箱同样的做法） */
    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    /**
     * 放置元数据 = 轴向（bit0-1）+ 开口朝向位（bit2）。
     * 轴向沿用手摇曲柄那条：点哪个面，轴就朝那个面；
     * 开口方向取「点的那一面所在的正方向」→ 玩家放下去时开口朝着自己 ✓。
     */
    @Override
    public int getMetadataForPlacement(World world, int x, int y, int z, ItemStack stack,
                                       Entity entity, EnumFace face, float hitX, float hitY, float hitZ) {
        int axis = KineticHelper.axisFromFace(face);
        int openDir = face != null ? face.ordinal() : KineticHelper.POS_DIR[axis];
        return axis | (KineticHelper.isPositiveDir(axis, openDir) ? KineticTileEntity.META_FACE_BIT : 0);
    }
}
