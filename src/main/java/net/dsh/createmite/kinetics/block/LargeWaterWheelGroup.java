package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.CMBlocks;
import net.minecraft.Block;
import net.minecraft.BlockBreakInfo;
import net.minecraft.World;

/**
 * 大型水车的「3x3 编队」逻辑：占位方块怎么找主体、怎么整组消失。
 *
 * ============================ 为什么要有这个类 ============================
 * 大型水车在 Create 里就**实打实占 3x3 格**（主体 1 格 + 其余 8 格是隐形占位）。
 * 我们原来只把"碰撞箱 / 选取箱 / 模型"做成 3x3，方块本身仍然只占 1 格 →
 * 玩家能从轮子里穿过去、能在轮盘中间摆方块、活塞能把轮子推走。这里补上真正的占位格。
 *
 * ============================ 占位格不存 NBT ============================
 * 占位格**不保存任何指向主体的数据**。主体位置一律由
 * 「自己的坐标 + metadata 里的轴向」在 3x3 平面里现扫出来
 * （见 {@link #findCore}）—— 存档、区块加载、活塞推动都不需要额外数据，
 * 也就没有"数据没同步 → 幽灵方块"这一类问题。
 *
 * ============================ 平面怎么算 ============================
 * 平面永远**垂直于自转轴**，和 {@link BlockLargeWaterWheel#setBoundsForAxis} 的
 * 3x3 逻辑是同一套（偏移 -1 .. +1，主体在 (0,0)）：
 * <pre>
 *   轴 X → 平面是 Y-Z（dx 恒为 0）
 *   轴 Y → 平面是 X-Z（dy 恒为 0）   ← 平放的大型水车
 *   轴 Z → 平面是 X-Y（dz 恒为 0）
 * </pre>
 */
public final class LargeWaterWheelGroup {

    /** 平面半径：3x3 = 偏移 -1..1 */
    private static final int R = 1;

    /**
     * 世界高度上限（MITE 的 {@code Chunk.setBlockIDWithMetadata} 判据是 {@code 0 <= y < 256}）。
     *
     * 【为什么必须自己判一手】轴向水平（X/Z）的大水车，平面里含 y-1 / y+1 两格。
     * 如果主体正好在 y=0，{@code world.getBlockId(x, -1, z)} 会让 Chunk 里
     * {@code ExtendedBlockStorage[y >> 4]} 取到下标 -1 → **直接崩**。
     * 玩家几乎不可能在 y=0 放方块，但"几乎不可能"不该写成崩溃。
     */
    private static final int MAX_Y = 256;

    private static boolean validY(int y) {
        return y >= 0 && y < MAX_Y;
    }

    /**
     * 「正在整组拆除」重入标记。
     *
     * 【为什么必须有】拆除是**互相触发**的：
     *   拆占位格 → 去拆主体 → 主体的 breakBlock 又去清占位格 → 占位格的 breakBlock 又去找主体……
     * 虽然靠"方块已经是空气了"最终也会停下来，但那是一条深度递归，
     * 而且中间会反复调用 {@code dropBlockAsItself}（可能掉出好几台水车）。
     * 用一个静态布尔把整段拆除圈起来，**只有最外层那一次**真正干活，里层一律直接返回。
     *
     * 注意：World 的方块改动（含集成服务器的服务端 World 与客户端 World）都跑在同一线程上，
     * 所以一个普通布尔就够，不需要 ThreadLocal。
     */
    private static boolean tearingDown = false;

    private LargeWaterWheelGroup() {}

    /** 拆到一半了吗（两个方块的 breakBlock 都要问这一句） */
    public static boolean isTearingDown() {
        return tearingDown;
    }

    /** 占位方块注册好了吗（没注册就整条编队逻辑停用，绝不 NPE） */
    public static boolean available() {
        return CMBlocks.blockLargeWaterWheelPlaceholder != null
                && CMBlocks.blockLargeWaterWheel != null;
    }

    /**
     * 平面内偏移 (c1,c2) → 世界坐标偏移 (dx,dy,dz)。
     * c1/c2 ∈ [-1,1]，且**永远有一个分量恒为 0**（那一位就是自转轴）。
     */
    public static int[] offset(int axis, int c1, int c2) {
        if (axis == 0) return new int[]{0, c1, c2};    // 轴 X → 平面 Y-Z
        if (axis == 1) return new int[]{c1, 0, c2};    // 轴 Y → 平面 X-Z
        return new int[]{c1, c2, 0};                   // 轴 Z → 平面 X-Y
    }

    /** 这一格是不是大型水车的占位方块 */
    public static boolean isPlaceholder(World world, int x, int y, int z) {
        return CMBlocks.blockLargeWaterWheelPlaceholder != null
                && world.getBlockId(x, y, z) == CMBlocks.blockLargeWaterWheelPlaceholder.blockID;
    }

    /** 这一格是不是大型水车主体 */
    public static boolean isCore(World world, int x, int y, int z) {
        return CMBlocks.blockLargeWaterWheel != null
                && world.getBlockId(x, y, z) == CMBlocks.blockLargeWaterWheel.blockID;
    }

    /**
     * 3x3 平面里**除 (x,y,z) 自己以外**的 8 格是不是全是空气。
     *
     * 判据用 {@code getBlockId != 0} 而不是别的：需求就是"不是空气就不放"，
     * 水、草、火把一律算占用 —— 宁可放不下，也不能把玩家的东西顶掉。
     */
    public static boolean planeIsFree(World world, int x, int y, int z, int axis) {
        for (int c1 = -R; c1 <= R; c1++) {
            for (int c2 = -R; c2 <= R; c2++) {
                if (c1 == 0 && c2 == 0) continue;      // 主体自己那一格不算
                int[] o = offset(axis, c1, c2);
                int by = y + o[1];
                if (!validY(by)) return false;          // 顶到世界边界 → 当作"放不下"
                if (world.getBlockId(x + o[0], by, z + o[2]) != 0) return false;
            }
        }
        return true;
    }

    /**
     * 组装：把 3x3 平面里其余 8 格填成占位方块。
     *
     * 【一格都不占就一格都不放】先整体判空，再统一写 —— 不存在"填到一半发现放不下"的中间态。
     * 标志位用 2（= 同步给客户端，但**不触发邻居更新**）：占位格没有碰撞、没有交互，
     * 让周围的水/沙子为它跑一遍更新纯属浪费，还可能引起不必要的流体重算。
     *
     * @return true = 已填好；false = 没动（空间被占 / 方块没注册 / 正在拆除）
     */
    public static boolean assemble(World world, int x, int y, int z, int axis) {
        if (tearingDown || world.isRemote || !available()) return false;
        if (!planeIsFree(world, x, y, z, axis)) return false;

        int id = CMBlocks.blockLargeWaterWheelPlaceholder.blockID;
        for (int c1 = -R; c1 <= R; c1++) {
            for (int c2 = -R; c2 <= R; c2++) {
                if (c1 == 0 && c2 == 0) continue;
                int[] o = offset(axis, c1, c2);
                if (!validY(y + o[1])) continue;        // 越界格子直接跳过（planeIsFree 已经拦过一次）
                // metadata 的 bit0-1 = 自转轴，**和主体完全一致** ——
                // 占位格就是靠这一位才知道该往哪个平面去找主体的（见 findCore）。
                world.setBlock(x + o[0], y + o[1], z + o[2], id, axis & 3, 2);
            }
        }
        return true;
    }

    /**
     * 在 3x3 平面里找出主体。
     *
     * 占位格自己那一格肯定不是主体，所以扫出来的必然是别的那 8 格之一。
     * 找不到（活塞推走了 / 世界编辑 / 主体被别的模组换掉了）就返回 null —— 调用方一律按"我自己消失"处理。
     */
    public static int[] findCore(World world, int x, int y, int z, int axis) {
        for (int c1 = -R; c1 <= R; c1++) {
            for (int c2 = -R; c2 <= R; c2++) {
                int[] o = offset(axis, c1, c2);
                int bx = x + o[0], by = y + o[1], bz = z + o[2];
                if (!validY(by)) continue;
                if (isCore(world, bx, by, bz)) return new int[]{bx, by, bz};
            }
        }
        return null;
    }

    /**
     * 拆主体时调：把平面里剩下的占位格全部清成空气（**不掉任何物品**）。
     *
     * 标志位仍然是 2 —— 需求里写死的那一条；而且这些格子本来就不该掉东西，
     * 掉落是由 BlockBreakInfo 那条路管的，setBlockToAir 本身不产生掉落物。
     */
    public static void clearPlaceholders(World world, int x, int y, int z, int axis) {
        // ★ 客户端**绝不改世界**：清占位格是纯粹的方块改动，只有在服务端做才有意义
        //   （客户端看到的是服务端用标志位 2 同步下来的"那 8 格变空气"）。
        //   这一道判据是本次补的：同一个类里的 assemble() 一直有，它却没有 —— 而
        //   凡是"改世界"的方法都该自己兜住这一手，不能全靠调用方记得站在服务端。
        //   配合 KineticTileEntity.updateEntity 里把补齐逻辑挪到 isRemote 早退之后，
        //   两道防线互相独立，任何一道单独成立都不会让客户端改世界。
        if (tearingDown || world.isRemote || !available()) return;
        tearingDown = true;
        try {
            removePlaceholdersInPlane(world, x, y, z, axis);
        } finally {
            tearingDown = false;
        }
    }

    /**
     * 拆**占位格**时调（原版行为：拆任意一格，整组消失）：
     * <ol>
     *   <li>先在平面里找到主体；</li>
     *   <li>把主体**按正常破坏**掉 —— 走 BlockBreakInfo + dropBlockAsItself，玩家能捡回一台水车
     *       （写法和 KineticNetwork 里"超速掉落"完全一致：MITE 的掉落入口是 BlockBreakInfo，
     *       不是原版的 dropBlockAsItem(x,y,z,meta,fortune)）；</li>
     *   <li>再把其余占位格一起清掉。</li>
     * </ol>
     * 找不到主体就跳过第 2 步，直接清自己这一组（不影响别的东西）。
     */
    public static void breakFromPlaceholder(World world, int x, int y, int z, int axis) {
        if (tearingDown || !available()) return;
        tearingDown = true;
        try {
            // ★★ 清第二遍时必须用**主体**的坐标，不能用调用者自己的坐标！
            //   【实测踩到的坑（离线模拟跑出来的）】占位格在平面里的位置是五花八门的：
            //   以 (1,0) 那个边中格为例，围着它画 3x3 只盖得住 x ∈ [0,2]，
            //   而整组占位格横跨 x ∈ [-1,1] —— x=-1 那一整列会**活下来变成孤儿**。
            //   所以先找到主体、拿主体的坐标去清；实在找不到主体（孤儿）才退回自己的坐标。
            int cx = x, cy = y, cz = z;
            int[] core = findCore(world, x, y, z, axis);
            if (core != null) {
                cx = core[0];
                cy = core[1];
                cz = core[2];
                Block block = Block.blocksList[world.getBlockId(cx, cy, cz)];
                if (block != null && block.canBeCarried()) {
                    // ★ 必须在 setBlockToAir **之前**掉：BlockBreakInfo 构造时要读那一格的方块与方块实体
                    block.dropBlockAsItself(new BlockBreakInfo(world, cx, cy, cz));
                }
                world.setBlockToAir(cx, cy, cz, 2);
                // 主体的 breakBlock 会在上面这一句里被调到，但它看到 tearingDown=true 会直接返回，
                // 所以"清掉其余占位格"这件事由下面这一句自己做完。
            }
            removePlaceholdersInPlane(world, cx, cy, cz, axis);
        } finally {
            tearingDown = false;
        }
    }

    /** 把平面里所有占位格清成空气（含调用者自己那一格；已经是空气的格子会被跳过） */
    private static void removePlaceholdersInPlane(World world, int x, int y, int z, int axis) {
        for (int c1 = -R; c1 <= R; c1++) {
            for (int c2 = -R; c2 <= R; c2++) {
                int[] o = offset(axis, c1, c2);
                int bx = x + o[0], by = y + o[1], bz = z + o[2];
                if (!validY(by)) continue;
                if (isPlaceholder(world, bx, by, bz)) {
                    world.setBlockToAir(bx, by, bz, 2);
                }
            }
        }
    }
}
