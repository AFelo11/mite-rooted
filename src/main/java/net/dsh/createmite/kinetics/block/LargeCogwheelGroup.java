package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.CMBlocks;
import net.minecraft.Block;
import net.minecraft.BlockBreakInfo;
import net.minecraft.World;

/**
 * 大齿轮的「轮盘平面挡位」逻辑：占位方块怎么组装、怎么反推主体、怎么整组消失。
 *
 * ============================ 为什么要有这个类 ============================
 * 大齿轮的轮盘直径约 1.88 格（模型 -7..23 px），齿尖探出方块外约 0.44 格。
 * 以前"探出去"只是**视觉上**的：方块本身仍然只占 1 格 →
 * 玩家能从齿缝里穿过去、能在轮盘里摆方块、活塞能把齿轮推走。
 * 现在让它**额外占住轮盘平面内的 4 个正交邻居格**（也就是轮盘的上下左右），
 * 用现成的隐形占位方块 {@link BlockLargeCogwheelPlaceholder}（id 2323）挡住。
 * 沿着自转轴看过去的平面俯视图（主 = 主体，占 = 占位格，· = 空且可放置）：
 * <pre>
 *        ·  占  ·
 *        占 主 占
 *        ·  占  ·
 * </pre>
 * 【这样做得到的效果】
 * <ul>
 *   <li>那 4 个正交邻居格**放不下任何东西** —— 占位方块不是空气，
 *       MITE 的放置检查（{@code Block.canReplaceBlock → canBeReplacedBy}）会直接拒绝；</li>
 *   <li>**四个角（对角那 4 格）保持可放置** ✓ —— 它们根本不在偏移表里，一个都不碰；</li>
 *   <li>**只有四个角能啮合变速** ✓ —— 这条规则在 {@code KineticHelper.connectsDiagonal} 里，
 *       本次**一个字都没动**（面贴面的大↔小仍然不啮合）；</li>
 *   <li>主体那一格仍然是**唯一**一格：碰撞箱 1x1x1、模型不平移、方块实体/动力网络/
 *       交互中心全都在它自己身上 ✓。</li>
 * </ul>
 *
 * ============================ ★ 为什么不是 2x2（别再试一次） ============================
 * 2026-09-26 用户实测后否决的那一版是"主体 + 沿两个正方向各扩 1 格 = 2x2 平面"
 * （大型水车 3x3 的缩小版）。它有两个**结构性**毛病，不是调参能救的：
 * <ol>
 *   <li>2 是偶数，"以主体为中心的 2x2"根本不存在 —— 2x2 的中心落在 4 格共用的那个**角**上。
 *       于是"能互动、能传动的本体"落在角落里，玩家看到的大齿轮中心却是那个角 ✗；</li>
 *   <li>要修就得让渲染、碰撞、连接判定三处**一起**改成按"组中心"重算 —— 代价远大于收益 ✗。</li>
 * </ol>
 * 正十字（主体 + 4 个正交邻居）没有这个问题：**主体自己就是中心格** ✓，
 * 所以"交互与传动以主体那格为中心"是天然成立的，不需要动渲染和连接判定。
 *
 * ============================ 平面怎么算 ============================
 * 这 4 格永远在**垂直于自转轴**的平面里（齿轮盘就躺在这个平面里），
 * 映射由下面 {@link #offset} 负责：
 * <pre>
 *   轴 X → 平面是 Y-Z（dx 恒为 0） → 邻居是 y±1 与 z±1
 *   轴 Y → 平面是 X-Z（dy 恒为 0） → 邻居是 x±1 与 z±1（平放的大齿轮）
 *   轴 Z → 平面是 X-Y（dz 恒为 0） → 邻居是 x±1 与 y±1
 * </pre>
 * 三个轴向都**只碰垂直于轴的那两维**，沿轴的一维恒为 0 ——
 * 所以"大齿轮永远不会沿自转轴方向多占格"（同轴串联、轴上装轴都照旧）。
 *
 * ============================ ★ 反推主体：唯一性从哪来 ============================
 * 占位格**不存 NBT**，只能靠"自己的坐标 + metadata 的 bit0-1（轴向）"现扫：
 * 组装时 {@code 占位格 = 主体 + 偏移表里的某一个偏移}，所以**反过来**，
 * 主体一定落在占位格的 4 个正交邻居里（见 {@link #findCore}）。
 * 这 4 个候选格里最多只有 1 格是主体，理由是几何上的硬约束：
 * <pre>
 *   组装前必须先过 {@link #planeIsFree} —— 那 4 格**全是空气**才肯组装；
 *   而两个大齿轮主体若在同一平面里正交相邻，后放的那个一定会被这条判据拒掉（对面不是空气）。
 * </pre>
 * 所以扫描顺序不影响结果（用世界编辑硬塞出来的畸形摆法除外，
 * 那种情况按偏移表顺序取第一个 —— 反正它已经不是"一组正常的大齿轮"了）。
 *
 * ============================ 占位格不存 NBT、不留幽灵 ============================
 * 占位方块**不保存任何指向主体的数据**，全靠现扫。存档、区块加载、活塞推动都不需要额外数据，
 * 也就没有"数据没同步 → 幽灵方块"这一类问题。反过来，"找不到主体"时的处理只有一种：
 * 把自己清成空气（见 {@link BlockLargeCogwheelPlaceholder#selfCheck}）。
 */
public final class LargeCogwheelGroup {

    /**
     * ★★ 平面偏移的**唯一定义处**：组装、判空、反推主体、清理，五处逻辑全部只认这一份表。
     *
     * 表里是 4 个**正交**邻居：{+1,0} {-1,0} {0,+1} {0,-1}。
     * <ul>
     *   <li>写的是**平面内**的两个分量 (c1,c2)，落到世界上还要经 {@link #offset} 按轴向展开
     *       （沿自转轴那一维恒为 0 —— 齿轮盘只在垂直于轴的平面里铺开）；</li>
     *   <li>**不含 (0,0)**（那是主体自己），也**不含对角线** (±1,±1) ——
     *       四个角必须留空，否则小齿轮没地方放、也没有"只有四个角能啮合"这件事了 ✗；</li>
     *   <li>每条偏移都**非零**，所以"占位格 ≠ 主体"是天然的，
     *       任何循环里都不需要再写一次"跳过自己"。</li>
     * </ul>
     *
     * 【顺序为什么固定成"先 + 后 -"】正确性上无所谓（候选格里最多 1 个是主体，见类注释），
     * 但固定顺序能让"同一个畸形存档每次扫出来的结果一样"，调试时好复现。
     */
    private static final int[][] PLANE_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * 世界高度上限（MITE 的 {@code Chunk.setBlockIDWithMetadata} 判据是 {@code 0 <= y < 256}）。
     *
     * 【为什么必须自己判一手】轴向水平（X/Z）的大齿轮，平面里含 y±1 那两格。
     * 如果主体在 y=255，{@code world.getBlockId(x, 256, z)} 会让 Chunk 里
     * {@code ExtendedBlockStorage[y >> 4]} 取到下标 16 → **直接崩**。
     * 反过来 y=0 时 y-1 = -1 同样是越界。玩家几乎不可能把齿轮放在世界上下边界上，
     * 但"几乎不可能"不该写成崩溃 —— 所以两个方向都拦。
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
     * 而且中间会反复调用 {@code dropBlockAsItself}（可能掉出好几个大齿轮）。
     * 用一个静态布尔把整段拆除圈起来，**只有最外层那一次**真正干活，里层一律直接返回。
     *
     * 注意：World 的方块改动（含集成服务器的服务端 World 与客户端 World）都跑在同一线程上，
     * 所以一个普通布尔就够，不需要 ThreadLocal。
     *
     * 【为什么不能和大型水车共用一个标记】两边可以同时发生拆除（比如爆炸一次炸掉两样东西），
     * 共用一个会让后拆的那个**整个不干活**、留下幽灵占位格。各管各的。
     */
    private static boolean tearingDown = false;

    private LargeCogwheelGroup() {}

    /** 拆到一半了吗（主体和占位方块的 breakBlock 都要问这一句） */
    public static boolean isTearingDown() {
        return tearingDown;
    }

    /** 占位方块注册好了吗（没注册就整条编队逻辑停用，绝不 NPE） */
    public static boolean available() {
        return CMBlocks.blockLargeCogwheelPlaceholder != null
                && CMBlocks.blockLargeCogwheel != null;
    }

    /**
     * 平面内偏移 (c1,c2) → 世界坐标偏移 (dx,dy,dz)。
     *
     * c1/c2 来自 {@link #PLANE_OFFSETS}（取值 ±1 或 0 的组合），
     * 而且**永远有一维恒为 0** —— 那一位就是自转轴，所以偏移不会跑到轴的另一个方向去。
     */
    public static int[] offset(int axis, int c1, int c2) {
        if (axis == 0) return new int[]{0, c1, c2};    // 轴 X → 平面 Y-Z
        if (axis == 1) return new int[]{c1, 0, c2};    // 轴 Y → 平面 X-Z
        return new int[]{c1, c2, 0};                   // 轴 Z → 平面 X-Y
    }

    /** 这一格是不是大齿轮的占位方块 */
    public static boolean isPlaceholder(World world, int x, int y, int z) {
        return CMBlocks.blockLargeCogwheelPlaceholder != null
                && world.getBlockId(x, y, z) == CMBlocks.blockLargeCogwheelPlaceholder.blockID;
    }

    /** 这一格是不是大齿轮主体 */
    public static boolean isCore(World world, int x, int y, int z) {
        return CMBlocks.blockLargeCogwheel != null
                && world.getBlockId(x, y, z) == CMBlocks.blockLargeCogwheel.blockID;
    }

    /**
     * 平面里那 4 个正交邻居格是不是**全是空气**。
     *
     * 判据用 {@code getBlockId != 0} 而不是别的：需求就是"不是空气就不放"，
     * 水、草、火把一律算占用 —— 宁可放不下，也不能把玩家的东西顶掉。
     *
     * 【顺带的一个性质】已经组装好的大齿轮再调本方法一定返回 false
     * （那 4 格是占位方块，不是空气）。所以"补齐占位格"那条兜底路径
     * （见 KineticTileEntity.updateEntity）对正常的齿轮是彻底的空转，不会反复写方块。
     */
    public static boolean planeIsFree(World world, int x, int y, int z, int axis) {
        for (int i = 0; i < PLANE_OFFSETS.length; i++) {
            int[] o = offset(axis, PLANE_OFFSETS[i][0], PLANE_OFFSETS[i][1]);
            int by = y + o[1];
            if (!validY(by)) return false;          // 越出世界上下边界 → 当作"放不下"
            if (world.getBlockId(x + o[0], by, z + o[2]) != 0) return false;
        }
        return true;
    }

    /**
     * 组装：把平面里那 4 个正交邻居格填成占位方块。
     *
     * 【一格都占就一格都不放】先整体判空，再统一写 —— 不存在"填到一半发现放不下"的中间态。
     * 标志位用 2（= 同步给客户端，但**不触发邻居更新**）：占位格没有碰撞、没有交互，
     * 让周围的水/沙子为它跑一遍更新纯属浪费，还可能引起不必要的流体重算。
     *
     * @return true = 已填好；false = 没动（空间被占 / 方块没注册 / 正在拆除 / 已经在客户端）
     */
    public static boolean assemble(World world, int x, int y, int z, int axis) {
        if (tearingDown || world.isRemote || !available()) return false;
        if (!planeIsFree(world, x, y, z, axis)) return false;

        int id = CMBlocks.blockLargeCogwheelPlaceholder.blockID;
        for (int i = 0; i < PLANE_OFFSETS.length; i++) {
            int[] o = offset(axis, PLANE_OFFSETS[i][0], PLANE_OFFSETS[i][1]);
            if (!validY(y + o[1])) continue;        // 越界格子直接跳过（planeIsFree 已经拦过一次）
            // metadata 的 bit0-1 = 自转轴，**和主体完全一致** ——
            // 占位格就是靠这一位才知道该往哪个平面去找主体的（见 findCore）。
            world.setBlock(x + o[0], y + o[1], z + o[2], id, axis & 3, 2);
        }
        return true;
    }

    /**
     * 由占位格反推出主体（占位格不存 NBT，只能这么找）。
     *
     * 候选集 = **占位格自己的 4 个正交邻居**：组装时占位格 = 主体 + 偏移，
     * 所以主体 = 占位格 - 偏移，4 条偏移正好反推出 4 个候选格。
     * 这里刻意写成"遍历同一份 PLANE_OFFSETS 再取反"，
     * 而不是另抄一份 {@code (0,±1)/(±1,0)} —— 偏移表改了这里自动跟着改，不会对不上。
     *
     * 【唯一性】这 4 格里最多只有 1 格是主体（见类注释里的论证：
     * 组装前要求那 4 格全空，所以同平面内不可能有两个正交相邻的主体），
     * 因此扫描顺序不影响结果。找不到（活塞推走了 / 世界编辑 / 主体被别的模组换掉了）
     * 就返回 null —— 调用方一律按"我自己消失"处理。
     */
    public static int[] findCore(World world, int x, int y, int z, int axis) {
        for (int i = 0; i < PLANE_OFFSETS.length; i++) {
            int[] o = offset(axis, PLANE_OFFSETS[i][0], PLANE_OFFSETS[i][1]);
            int bx = x - o[0], by = y - o[1], bz = z - o[2];
            if (!validY(by)) continue;
            if (isCore(world, bx, by, bz)) return new int[]{bx, by, bz};
        }
        return null;
    }

    /**
     * 清掉以 (x,y,z) 为主体的整组 4 个占位格（**不掉任何物品**）。
     *
     * 三个调用点：
     * <ol>
     *   <li>主体的 {@code breakBlock} —— 拆主体时顺带清干净；</li>
     *   <li>拆占位格那条路（{@link #breakFromPlaceholder}）；</li>
     *   <li>KineticTileEntity 里"扳手改了自转轴"那条轮询 —— 拿**旧轴**来清旧平面。</li>
     * </ol>
     * 标志位仍然是 2；这些格子本来也不该掉东西（{@code BlockBreakInfo} 那条路才产生掉落，
     * {@code setBlockToAir} 本身不掉）。
     *
     * 【必须是主体坐标】理由见 {@link #removePlaceholdersInPlane}。
     * 注意第 3 个调用点用的是**旧轴**：那一组格子躺在旧平面里，拿新轴当然扫不到它们。
     */
    public static void clearPlaceholders(World world, int x, int y, int z, int axis) {
        if (tearingDown || !available()) return;
        tearingDown = true;
        try {
            removePlaceholdersInPlane(world, x, y, z, axis);
        } finally {
            tearingDown = false;
        }
    }

    /**
     * 拆**占位格**时调（行为：拆任意一格，整组消失、只掉 1 个大齿轮）：
     * <ol>
     *   <li>先在平面里反推出主体；</li>
     *   <li>把主体**按正常破坏**掉 —— 走 BlockBreakInfo + dropBlockAsItself，玩家能捡回一个大齿轮
     *       （写法和 KineticNetwork 里"超速掉落"、以及大型水车那套完全一致：
     *       MITE 的掉落入口是 BlockBreakInfo，不是原版的 dropBlockAsItem(x,y,z,meta,fortune)）；</li>
     *   <li>再把其余占位格一起清掉（用**主体**的坐标算平面，理由见 removePlaceholdersInPlane）。</li>
     * </ol>
     * 找不到主体（孤儿占位格）就跳过第 2 步，直接清自己这一组 —— 不影响别的东西。
     */
    public static void breakFromPlaceholder(World world, int x, int y, int z, int axis) {
        if (tearingDown || !available()) return;
        tearingDown = true;
        try {
            // ★★ 清第二遍时必须用**主体**的坐标，不能用调用者自己的坐标！
            //   偏移表是"从主体出发"的：围着占位格自己画一圈，盖到的是它自己的四个邻居
            //   （主体 + 三个本来就空的格子），**另外 3 个占位格一个都扫不到** ✗
            //   → 它们会活下来变成"看不见、撞不到、却永远占着格子"的孤儿。
            //   所以先找主体、拿主体的坐标去清；实在找不到主体（孤儿）才退回自己的坐标。
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

    /**
     * 把以 (x,y,z) 为主体的 4 个占位格清成空气
     * （拆占位格那条路会连调用者自己那一格一起清掉；本来就是空气的格子会被跳过）。
     *
     * ★ 传进来的**必须是主体坐标**：偏移表是相对主体的，只有从主体出发才能一次扫全整组。
     *   传占位格的坐标会漏格（见 breakFromPlaceholder 的注释）。
     *   只动 {@code blockLargeCogwheelPlaceholder} 这一种方块，玩家的建筑一格都不碰。
     */
    private static void removePlaceholdersInPlane(World world, int x, int y, int z, int axis) {
        for (int i = 0; i < PLANE_OFFSETS.length; i++) {
            int[] o = offset(axis, PLANE_OFFSETS[i][0], PLANE_OFFSETS[i][1]);
            int bx = x + o[0], by = y + o[1], bz = z + o[2];
            if (!validY(by)) continue;
            if (isPlaceholder(world, bx, by, bz)) {
                world.setBlockToAir(bx, by, bz, 2);
            }
        }
    }
}
