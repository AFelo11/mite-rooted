package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.CMHints;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * 大型水车（Create 的 large_water_wheel）：水车的加强版。
 *
 * 【和 Create 的对应】
 *   - 转速更低（4 RPM）但**应力容量翻倍**（512）—— 拿转速换力气；
 *   - **碰撞箱 3x3**：原版大型水车放下去就占 3x3 格；模型本身更大（X/Z 跨 4.88 格），
 *     是**故意往外伸**的 —— 和 Create 一致（轮子探出结构外）。
 *   - 沿轴 2 格：主体 + 延伸段（原版 blockstate 里的 axis + extension=true/false）。
 *
 * 【水流】和普通水车同一套：被**流动的水**推着转，方向跟着水流走；
 * 水流到尽头（变成死水）后**保持原方向继续转**，只要周围还有水就不停。
 *
 * 外观 = Create large_water_wheel.obj → assets/createmite/meshes/large_water_wheel.mesh
 *
 * ==================== ★ 真正占 3x3（主体 + 8 个隐形占位格） ====================
 * 以前只有"碰撞箱/选取箱/模型"是 3x3，方块本身仍然只占 1 格，
 * 玩家能从轮子里穿过去、能在轮盘中间摆方块。现在：
 *   - 放置时（{@link #onBlockAdded}）在**垂直于自转轴的 3x3 平面**里补齐
 *     {@link BlockLargeWaterWheelPlaceholder} 占位格；
 *   - 拆除时（{@link #breakBlock}）把 8 个占位格一起清掉。
 *
 * 【水位判据为什么要跟着改】占位格是有方块语义的实心格（水进不去，否则水流会把它们冲掉），
 * 于是"和轮子同一层的水"从此**不可能存在** —— 原来那条"必须同层"的判据会让大型水车永远不转。
 * KineticTileEntity 里的 waterTorque()/hasAnyWater() 因此对大型水车改用了**轮缘判据**
 * （不带自转轴分量的水都算驱动水），详见那两处的注释。
 */
public class BlockLargeWaterWheel extends BlockKineticBase {

    public BlockLargeWaterWheel(int blockID) {
        super(blockID, Material.wood, machineConstants());
        this.applyMachineDefaults("large_water_wheel", "createmite_blank");
    }

    @Override
    protected int particleLayer() {
        return 21;   // log_oak
    }

    /**
     * ★ 碰撞箱 / 选取箱 = **3x3**（原版就是这么占的）。
     *
     * 轮面方向铺满 3 格（-1 ~ 2，以本方块为中心），**轴向只占本格**。
     * 模型比这个还大（往外伸）是正常的 —— Create 的轮子本来就会探出结构。
     *
     * 【注意】这里的 3x3 和占位格那 3x3 是**同一片平面**（见 LargeWaterWheelGroup.offset），
     * 两者必须一起改，否则"撞得到的范围"和"实际占住的范围"会错位。
     */
    @Override
    protected void setBoundsForAxis(int axis) {
        if (axis == 0) {
            // 轴 X：轮面是 Y-Z
            this.setBlockBoundsForCurrentThread(0.0D, -1.0D, -1.0D, 1.0D, 2.0D, 2.0D);
        } else if (axis == 1) {
            // 轴 Y：轮面是 X-Z
            this.setBlockBoundsForCurrentThread(-1.0D, 0.0D, -1.0D, 2.0D, 1.0D, 2.0D);
        } else {
            // 轴 Z：轮面是 X-Y
            this.setBlockBoundsForCurrentThread(-1.0D, -1.0D, 0.0D, 2.0D, 2.0D, 1.0D);
        }
    }

    /**
     * 放置回调：把 3x3 平面里其余 8 格填成占位方块。
     *
     * 【为什么挂这里】MITE 的放置链路是
     * {@code Item.tryPlaceAsBlock → Block.tryPlaceFromHeldItem → Block.tryPlaceBlock → World.setBlock
     *  → Chunk.setBlockIDWithMetadata → Block.onBlockAdded}。
     * 走到 onBlockAdded 时**方块 id 和 metadata 都已经写进区块了**（已用 javap 核对字节码顺序），
     * 所以这里能直接读到自己真实的轴向；而且无论玩家放置、/setblock、活塞推动还是世界生成，
     * 都会经过这里 —— 覆盖面最广。
     *
     * 【空间不够怎么办】**一格都不放**（绝不顶掉玩家的建筑），给最近的玩家一句聊天提示。
     * 主体已经落地了没法当场撤销（这一刻还在 Chunk.setBlockIDWithMetadata 内部，改本格会和外层写回打架），
     * 所以只是"缺 8 个占位格"的降级状态；玩家拆掉重放即可。
     * 顺带：KineticTileEntity 每 10 tick 会对大型水车补一次组装，空间腾出来后会自动补齐。
     */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) return;          // 只在服务端组装，客户端等服务端同步方块就好

        int axis = world.getBlockMetadata(x, y, z) & 3;   // metadata 的 bit0-1 = 自转轴
        if (LargeWaterWheelGroup.planeIsFree(world, x, y, z, axis)) {
            LargeWaterWheelGroup.assemble(world, x, y, z, axis);
            return;
        }
        notifyNoRoom(world, x, y, z);

        // ★ **真正拒绝放置**：打标记，让方块实体在下一 tick 把主体撤掉并退还物品 ✓
        //   （用户实测：以前只提示、主体照样留下 → 等于没拒绝 ✗）
        net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof net.dsh.createmite.kinetics.KineticTileEntity) {
            ((net.dsh.createmite.kinetics.KineticTileEntity) te).cancelPlacement = true;
        }
    }

    /** 空间不足时给最近的玩家一句提示（找不到玩家就只打日志，不刷屏） */
    private void notifyNoRoom(World world, int x, int y, int z) {
        EntityPlayer player = world.getClosestPlayer(x + 0.5D, y + 0.5D, z + 0.5D, 16.0D, false);
        if (player != null && CMHints.enabled(player)) {
            player.sendChatToPlayer(ChatMessageComponent.createFromText(
                    "§c大型水车要占 3x3 格：这里有方块挡着，没有生成隐形占位格。请清空轮盘平面后拆掉重放。"));
        }
        System.out.println("[CreateMITE] 大型水车放不下 3x3：" + x + "," + y + "," + z
                + " 轴向=" + (world.getBlockMetadata(x, y, z) & 3) + " → 只放主体，未生成占位格");
    }

    /**
     * 拆主体 → 8 个占位格整组消失。
     *
     * 【调用时机】{@code Chunk.setBlockIDWithMetadata} 在把旧方块换成空气之后会调旧方块的 breakBlock，
     * 而且**只在服务端**（客户端那一支走 removeBlockTileEntity）—— 所以这里不用再判 isRemote。
     * 里面那些 setBlockToAir 是在 breakBlock 里再改别的方块，和原版门/床的做法完全一致，
     * 重入由 LargeWaterWheelGroup.tearingDown 挡住。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        LargeWaterWheelGroup.clearPlaceholders(world, x, y, z, meta & 3);
        // ★ 基类这一句里有 world.removeBlockTileEntity —— 那是修过的真 bug，不能删、也不能绕过
        super.breakBlock(world, x, y, z, blockID, meta);
    }
}
