package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.CMHints;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * 大齿轮：半径比小齿轮大一圈（模型 -7..23 px，齿尖伸到方块外约 0.44 格），
 * 与小齿轮啮合时 **2:1 变速**（大带小 ×2 转速，小带大 ×0.5）。
 *
 * 两个大齿轮**互相不啮合**（尺寸相同、齿距对不上；Create 里也放不下两个相邻的大齿轮）。
 * 规则都在 KineticHelper.connects / transfer 里，这里只负责"我是大齿轮"这个身份。
 *
 * ==================== ★ 额外占住轮盘的 4 个正交邻居格（主体仍是 1 格） ====================
 * 以前只有"模型"是 2x2 的，方块本身仍然只占 1 格：玩家能从齿缝里穿过去、
 * 能在轮盘里摆方块、活塞能把齿轮推走。现在：
 * <ul>
 *   <li>放置时（{@link #onBlockAdded}）在**垂直于自转轴的平面**里，给上下左右那 4 个正交邻居格
 *       补上 {@link BlockLargeCogwheelPlaceholder} 隐形占位格；</li>
 *   <li>空间不够 → 聊天栏提示 + **真正拒绝放置**（撤掉主体并退还物品）；</li>
 *   <li>拆除时拆任意一格 → 整组消失，只掉 1 个大齿轮（见 {@link LargeCogwheelGroup}）。</li>
 * </ul>
 *
 * 【主体自己一格没变 —— 这正是本方案能成立的关键】
 * 碰撞箱/选取箱仍是 1×1×1、模型不做任何平移、方块实体与交互中心都还在这一格。
 * 上一次"占 2x2"之所以被回退，就是因为 2 是偶数、2x2 的中心落在四格共用的**角**上，
 * 交互与传动中心跟着跑偏 ✗（详见 {@link LargeCogwheelGroup} 的类注释）。
 * 正十字（主体 + 上下左右）里**主体自己就是中心格** ✓，所以渲染、碰撞、连接判定三处都不用动。
 *
 * 【四个角仍然是空的】那 4 个对角格一个都不占 → 玩家照旧能在那里放小齿轮，
 * 而且**只有四个角能啮合变速**（{@code KineticHelper.connectsDiagonal}，本次一个字都没改）✓。
 */
public class BlockLargeCogwheel extends BlockKineticBase {

    public BlockLargeCogwheel(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("large_cogwheel", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 7;
    }

    /**
     * ★ 碰撞箱 / 选取箱 = **主体这一格（1x1x1）**，不做任何外扩。
     *
     * 【为什么不像大型水车那样撑满整组】大齿轮占的 4 个格子是"主体 + 上下左右"，不是一个
     * 连着主体的矩形：把碰撞箱撑出去，等于把"能互动 / 能被准星选中"的那一块挪到主体之外，
     * 玩家右键的位置和方块实体所在的那一格就对不上了 ✗（2x2 那版回退的根因正是这个）。
     * 所以这里保持最朴素的 1x1x1 ✓ —— 交互与传动永远以主体那格为中心。
     *
     * 【那 4 格还能不能走进去】能走 —— 占位方块是 0 体积、全透明的
     * （见 {@link BlockLargeCogwheelPlaceholder}）。本次要挡的是"**在那 4 格放方块**"
     * （占位格不是空气，MITE 的放置检查 {@code Block.canReplaceBlock → canBeReplacedBy}
     * 会直接拒绝），**不是挡路**，所以没必要给玩家一面隐形墙。
     * 代价只是走进那 4 格时轮盘外观有一点视觉重叠（齿尖原本就探出主体约 0.44 格），可以接受。
     */
    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    /**
     * 放置回调：把平面里那 4 个正交邻居格填成占位方块；放不下就拒绝这次放置。
     *
     * 【为什么挂这里】MITE 的放置链路是
     * {@code Item.tryPlaceAsBlock → Block.tryPlaceFromHeldItem → Block.tryPlaceBlock → World.setBlock
     *  → Chunk.setBlockIDWithMetadata → Block.onBlockAdded}。
     * 走到 onBlockAdded 时**方块 id 和 metadata 都已经写进区块了**（已用 javap 核对字节码顺序），
     * 所以这里能直接读到自己真实的轴向；而且无论玩家放置、/setblock、活塞推动还是世界生成，
     * 都会经过这里 —— 覆盖面最广。
     *
     * 【空间不够 == 真正拒绝】给最近的玩家一句聊天提示，然后打
     * {@link net.dsh.createmite.kinetics.KineticTileEntity#cancelPlacement} 标记：
     * 方块实体在**下一 tick** 会把主体撤掉并把大齿轮退还成掉落物。
     *
     * 【为什么不能当场撤】这一刻还在 {@code Chunk.setBlockIDWithMetadata} **内部**
     * （onBlockAdded 在字节码里排在"写 metadata"之后、但整段还没返回），
     * 在这里改本格的方块会和外层那次写回打架 → 所以只打标记、下一 tick 再撤。
     *
     * 【为什么这里 setFlag 一定拿得到方块实体】同一段字节码里，TE 的懒创建发生在
     * {@code Chunk.getChunkBlockTileEntity}：只要这一格是 ITileEntityProvider、map 里没有，
     * 它就**当场 new 一个并注册**。所以这里 world.getBlockTileEntity 返回的必然是那个
     * 即将被 Chunk 复用的同一个实例（外面随后那次 getChunkBlockTileEntity 拿到的就是它），
     * 标记不会丢。
     */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);

        // ⏸ **"正十字挡位"已停用（2026-09-27，用户实测后）**：
        //   用户搭的是原版那种"大-小-大-小 斜着一路串下去"的变速链 ✓，
        //   但那 4 个正交挡位会**占掉链路下一级必须放的位置** ✗ → 链条断掉、
        //   转速只停在 4/16 两档上不去（日志实证）✗。
        //   用户明确"现在只要变速能用"→ 挡位让位 ✓。要恢复挡位，删掉这一行即可。
        if (true) return;

        // ★ 组装"正十字"挡位：主体 + 平面内上下左右那 4 个正交邻居格 ✓
        //   （偏移表在 LargeCogwheelGroup.PLANE_OFFSETS，只定义那一处。）
        //
        // 【历史，别再走回头路】这里曾经有一行 `if (true) return;` 把组装整个停用 ——
        //   那一版占的是 2x2（主体 + 沿两个正方向各扩 1 格），用户实测发现
        //   "能互动/能传动的本体只有四格里的那一格"，传动中心落在**角落** ✗、
        //   四个角的小齿轮也只够得到那个角 ✗，于是被回退。
        //   现在换成"正十字"：**主体自己就是中心格**，渲染/碰撞/连接判定三处都不用动，
        //   四个角照旧留空、照旧只有它们能啮合 ✓ —— 所以重新打开组装 ✓。
        if (world.isRemote) return;          // 只在服务端组装，客户端等服务端同步方块就好

        int axis = world.getBlockMetadata(x, y, z) & 3;   // metadata 的 bit0-1 = 自转轴
        if (LargeCogwheelGroup.planeIsFree(world, x, y, z, axis)) {
            LargeCogwheelGroup.assemble(world, x, y, z, axis);
            return;
        }
        notifyNoRoom(world, x, y, z);

        // ★ **真正拒绝放置**：打标记，让方块实体在下一 tick 把主体撤掉并退还物品 ✓
        net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof net.dsh.createmite.kinetics.KineticTileEntity) {
            ((net.dsh.createmite.kinetics.KineticTileEntity) te).cancelPlacement = true;
        } else {
            // 理论到不了这里（见方法注释里的懒创建说明）。真到了就只留一行日志：
            // 那块齿轮会以"只有主体、没有占位格"的降级状态留在世界上，
            // 玩家把挡路的方块清掉后，KineticTileEntity 每 10 tick 的兜底 assemble 会把它补回来。
            System.out.println("[MITE] 大齿轮的 4 个正交挡位放不下，但拿不到方块实体，无法自动撤销："
                    + x + "," + y + "," + z);
        }
    }

    /** 空间不足时给最近的玩家一句提示（找不到玩家就只打日志，不刷屏） */
    private void notifyNoRoom(World world, int x, int y, int z) {
        EntityPlayer player = world.getClosestPlayer(x + 0.5D, y + 0.5D, z + 0.5D, 16.0D, false);
        if (player != null && CMHints.enabled(player)) {
            player.sendChatToPlayer(ChatMessageComponent.createFromText(
                    "§c大齿轮轮盘的上下左右 4 格要留空：那里有方块挡着，已取消放置并把大齿轮退还给你（四个角不受影响）。"));
        }
        System.out.println("[MITE] 大齿轮的 4 个正交挡位放不下：" + x + "," + y + "," + z
                + " 轴向=" + (world.getBlockMetadata(x, y, z) & 3) + " → 已标记撤销（下一 tick 退还物品）");
    }

    /**
     * 拆主体 → 4 个占位格整组消失。
     *
     * 【调用时机】{@code Chunk.setBlockIDWithMetadata} 在把旧方块换成空气之后会调旧方块的 breakBlock，
     * 而且**只在服务端**（客户端那一支走 removeBlockTileEntity）—— 所以这里不用再判 isRemote。
     * 里面那些 setBlockToAir 是在 breakBlock 里再改别的方块，和原版门/床的做法完全一致，
     * 重入由 LargeCogwheelGroup.tearingDown 挡住。
     *
     * 玩家正常挖掘时，掉落的**是主体自己那一个方块**（走 MITE 的挖掘 → BlockBreakInfo 那条路），
     * 占位格一格都不掉 → 整组永远只掉 1 个大齿轮 ✓
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        LargeCogwheelGroup.clearPlaceholders(world, x, y, z, meta & 3);
        // ★ 基类这一句里有 world.removeBlockTileEntity —— 那是修过的真 bug，不能删、也不能绕过
        super.breakBlock(world, x, y, z, blockID, meta);
    }
}
