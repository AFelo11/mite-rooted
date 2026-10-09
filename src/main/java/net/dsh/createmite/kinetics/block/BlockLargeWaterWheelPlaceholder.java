package net.dsh.createmite.kinetics.block;

import net.minecraft.Block;
import net.minecraft.BlockBreakInfo;
import net.minecraft.BlockConstants;
import net.minecraft.IBlockAccess;
import net.minecraft.Material;
import net.minecraft.World;

import java.util.Random;

/**
 * 大型水车的**占位方块**（3x3 平面里除主体以外的那 8 格）。
 *
 * ============================ 它为什么不能继承 BlockKineticBase ============================
 * 占位格不是机器，只是"看不见的填充物"：
 *   - 一继承就会**自动带一个 KineticTileEntity** → 8 个额外的方块实体，每 tick 都要跑动力网络；
 *   - 会被 KineticHelper 当成"水车"认下来（水车是可以沿轴传动的动力源）→ 动力网络里凭空多出 8 个源；
 *   - 会被水车的水扫描认成"水车本体"，转向判定跟着乱。
 * 所以它直接继承最朴素的 {@link Block}：**没有 TE、不进动力网络、不参与任何扫描**。
 *
 * ============================ 外观 ============================
 * 贴图用 {@code createmite_blank}（和所有机器方块同一张全透明图），
 * 再加上 {@link #setBlockBoundsBasedOnStateAndNeighbors} 把碰撞箱收成 0 体积：
 * 结果是**世界里完全看不见、也完全撞不到**，但水/活塞/活塞推动这些"方块语义"照旧成立。
 *
 * 【为什么是 0 体积而不是 setBlockBounds(0,0,0,1,1,1)】
 * 0 体积同时解决了另一件事：玩家**没法用准星选中它** →
 * 不掉落、不会被误挖，挖轮子永远挖到主体那一格（主体是 3x3 的选取箱，覆盖整片区域）。
 *
 * ============================ 它不存 NBT ============================
 * 主体在哪，靠"自己的坐标 + metadata 的 bit0-1（轴向）"现扫出来，
 * 见 {@link LargeWaterWheelGroup#findCore}。见不到主体就把自己清成空气（见 {@link #selfCheck}）。
 */
public class BlockLargeWaterWheelPlaceholder extends Block {

    public BlockLargeWaterWheelPlaceholder(int blockID) {
        super(blockID, Material.wood, new BlockConstants().setNeverHidesAdjacentFaces());

        // 参数与机器方块对齐（BlockKineticBase.applyMachineDefaults 的同一套数值），
        // 但**不调 setCreativeTab**：这不是给玩家的方块，不该出现在创造模式物品栏里。
        this.setHardness(BlockKineticBase.HARDNESS_PER_BLOCK);
        this.setMinHarvestLevel(BlockKineticBase.MIN_HARVEST_LEVEL);
        this.setStepSound(Block.soundWoodFootstep);
        this.setUnlocalizedName("large_water_wheel_placeholder");
        this.setTextureName("createmite_blank");   // 全透明：原版那趟整方块渲染画出来是看不见的

        // 碰撞箱 0 体积（构造期先设一次；运行期每次查询还会由下面那个覆写再设一次）
        this.setBlockBoundsForAllThreads(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * ★ 必须声明"不是标准整方块"，和 BlockKineticBase 同一条理由：
     * MITE 的 {@code Block.getCollisionBounds} 第一句就是
     * <pre>if (isAlwaysStandardFormCube()) return 标准整方块包围盒;</pre>
     * 走那条快路径会**直接跳过我们设的 0 体积**（于是变成一个实心大方块，玩家撞在隐形墙上）。
     */
    @Override
    public boolean isStandardFormCube(boolean[] array, int metadata) {
        if (array != null && metadata >= 0 && metadata < array.length) {
            array[metadata] = false;
        }
        return false;
    }

    /** metadata 只用 bit0-1 存轴向（0=X 1=Y 2=Z），其余留空；不声明的话 MITE 会把 1/2 当非法值刷日志 */
    @Override
    public boolean isValidMetadata(int metadata) {
        return metadata >= 0 && metadata < 4;
    }

    /** 每次查询碰撞箱都重新钉成 0 体积（构造期那一次只是兜底） */
    @Override
    public void setBlockBoundsBasedOnStateAndNeighbors(IBlockAccess world, int x, int y, int z) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * 占位格**自己不掉落**。
     *
     * 掉落物统一由主体负责：拆占位格时会走
     * {@link LargeWaterWheelGroup#breakFromPlaceholder}，由它把主体正经破坏掉、掉出一台水车。
     * 不拦这一手的话，爆炸/活塞碾碎占位格会额外掉出一堆"大型水车·占位方块"。
     */
    @Override
    public int dropBlockAsEntityItem(BlockBreakInfo info) {
        return 0;
    }

    /**
     * 放下之后**推迟 1 tick** 做一次自检。
     *
     * 【为什么不当场检查、当场清掉自己】{@code onBlockAdded} 是在
     * {@code Chunk.setBlockIDWithMetadata} **内部**被调用的（这时外层还没写完它自己的 metadata 和光照），
     * 在这里直接改本格的方块会和外层那次写回打架。排一个计划刻，等这一格彻底安定下来再判，最干净。
     *
     * 正常组装出来的占位格下一 tick 会查到主体、什么都不做；只有"孤儿"才会被清掉。
     */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) return;                                  // 判定只在服务端做，客户端等服务端同步
        if (LargeWaterWheelGroup.isTearingDown()) return;            // 正在整组拆除，别再插一脚
        world.scheduleBlockUpdateWithPriority(x, y, z, this.blockID, 1, 0);
    }

    /**
     * 自检：**找不到主体就把自己清成空气**。
     *
     * 走到这里的都是"非正常出现"的占位格：活塞推动、世界编辑、存档里主体没了、别的模组换了方块……
     * 原则是**绝不留下幽灵方块**，也绝不去猜主体在哪 —— 猜错就会连累别人的建筑。
     * （updateTick 的返回值 MITE 只用来表示"这一格是否非法"，调用方一律丢弃，见 WorldServer。）
     */
    @Override
    public boolean updateTick(World world, int x, int y, int z, Random rand) {
        boolean ret = super.updateTick(world, x, y, z, rand);
        if (!world.isRemote && !LargeWaterWheelGroup.isTearingDown()) {
            selfCheck(world, x, y, z);
        }
        return ret;
    }

    /** 上面那个自检的实现，也供别处复用 */
    public static void selfCheck(World world, int x, int y, int z) {
        if (world.isRemote) return;
        int axis = world.getBlockMetadata(x, y, z) & 3;
        if (LargeWaterWheelGroup.findCore(world, x, y, z, axis) == null) {
            world.setBlockToAir(x, y, z, 2);
        }
    }

    /**
     * 占位格被拆 → **整组消失**（原版行为：拆哪一格都一样）。
     *
     * 先由 {@link LargeWaterWheelGroup#breakFromPlaceholder} 把主体按正常破坏掉（掉物品），
     * 再清掉其余占位格；重入由那个类里的 tearingDown 标记挡住，不会递归。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        LargeWaterWheelGroup.breakFromPlaceholder(world, x, y, z, meta & 3);
        super.breakBlock(world, x, y, z, blockID, meta);
    }
}
