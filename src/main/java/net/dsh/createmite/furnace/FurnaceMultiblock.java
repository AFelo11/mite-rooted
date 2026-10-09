package net.dsh.createmite.furnace;

import net.dsh.createmite.CMBlocks;
import net.dsh.createmite.CMHints;
import net.dsh.createmite.block.FurnaceCoreBlock;
import net.dsh.createmite.kinetics.block.BlockWrappedShaft;
import net.minecraft.Block;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.IBlockAccess;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 3x3x3 大熔炉的成型判定与状态（2026-09-30 开工）。
 *
 * == 形状（用户实机搭出来的 6 台结构导出的）==
 * <pre>
 *   y+1 / y-1 两层：9 格**全是机壳**（安山或黄铜，必须同一种）
 *   y   中层      ：中间 = 熔炉核心，
 *                   正十字 4 格 = 同材质的包裹传动杆（**可以用同款机壳顶替**），
 *                   4 个角 = 机壳
 * </pre>
 * 六台结构同构，只有「核心/传动杆的材质」和「机壳种类」不同。
 *
 * == 用户定的三条规则（2026-09-30）==
 *   1. **不固定坐标** —— 搭在哪里都行，核心在正中间，判定按"以核心为原点的 27 格"来 ✓
 *   2. **传动杆是特殊方块**，不够时可以用**该熔炉同款的机壳**顶替 ✓
 *   3. **至少要有 1 根传动杆**（0 根不算熔炉）；
 *      "缺杆有什么影响"和"不同机壳有什么影响"都要等 UI 做出来才定 —— 本类先不管 ✓
 *
 * == 正面朝向（用户 2026-09-30 口述）==
 *   模型本身是「炉口朝南、传动口朝北」画的（默认朝向）。
 *   **最后一块方块搭在哪一面，那一面就成为正面**；正面的背面就是传动口。
 *   最后一块搭在**顶面或底面** → 走默认：南为正面、北为传动口 ✓
 *   （中层四角/正中心这种拿不准的情况也按默认南处理 ✓）
 *
 * == 状态存哪儿 ==
 *   **只存在核心的 metadata 里**（meta = 0 没成型；meta = 1 + 正面方向 = 成型）✓
 *   为什么只用 3 bit：本工程实测过「bit3(8) 同步不到客户端」（见 CMBlocks 里封装箱那段），
 *   而 bit0-2 是可靠的 ✓ —— 成型标记 + 4 个朝向刚好塞满 3 bit ✓。
 *   机壳那边额外借 metadata 的 bit0 当"我是成型结构的一部分"的**缓存标记**
 *   （机壳的 metadata 本来没用途 ✓），这样渲染时 O(1) 就能判断该不该隐藏 ✓。
 *
 * == 所有世界改动都延迟到核心 TE 的下一 tick ==
 *   onBlockAdded / breakBlock 那一刻还在 Chunk 写块的**内部**，当场改方块会和外层写回打架 ✗
 *   （这条是 @see net.dsh.createmite.kinetics.block.BlockLargeWaterWheel#onBlockAdded 的教训）。
 */
public final class FurnaceMultiblock {

    private FurnaceMultiblock() {}

    // ===================== metadata =====================
    /** 核心 metadata 的有效位（bit0-2：成型标记 + 正面方向 ✓） */
    public static final int META_MASK = 7;
    /**
     * ★ 核心 metadata 的 **bit3 = 整机"真在烧"**（2026-10-01 接上真状态，替掉原来那个手动演示开关 ✓）。
     *
     * 【为什么用 metadata 当同步通道】整机外观全靠客户端那个 TESR 画 ✗，而炉子逻辑在服务端 ✓
     *   ⇒ 写 metadata = 零发包、跟着区块自动同步 ✓（和动力方块的 syncSpeedToMetadata 是同一条路 ✓）。
     * 【坑，务必记住】这一位**不在 META_MASK 里** ✗ ⇒ 凡是"写核心 metadata"的代码
     *   （本类的 setMeta，以及以后新加的任何一处）都**必须显式保留这一位**，
     *   否则一写"成型/正面"就把它抹掉 ✗ —— 这正是动力方块那边踩过的"掩码写窄了吃高位"同一个坑 ✓
     *   （血证见 KineticTileEntity.syncSpeedToMetadata ✓）
     */
    public static final int BURNING_BIT = 8;
    /** 机壳 metadata 的"属于成型结构"标记（机壳本来不用 metadata，借 bit0） */
    public static final int CASING_FORMED_BIT = 1;

    /** 正面方向：0=南 1=西 2=北 3=东（存进核心 meta 时整体 +1） */
    public static final int FRONT_SOUTH = 0;
    public static final int FRONT_WEST = 1;
    public static final int FRONT_NORTH = 2;
    public static final int FRONT_EAST = 3;

    // ===================== 材质 / 机壳编号 =====================
    /** ★ 材质编号**必须与 CreateModelsFurnaceBig.VARIANT_TEXTURES 的行顺序一致** */
    public static final int MATERIAL_COBBLE = 0;
    public static final int MATERIAL_OBSIDIAN = 1;
    public static final int MATERIAL_NETHERRACK = 2;

    public static final int CASING_ANDESITE = 0;
    public static final int CASING_BRASS = 1;

    /**
     * ★★ 每种材质的大熔炉的**热值上限**（用户 2026-10-01 第 ③ 条："每种炉子的上限不一样" ✓）
     *
     * 数值**直接沿用 MITE 自己的熔炉**（javap 实证 ✓ 见 BlockFurnaceXxx.getMaxHeatLevel ✓）：
     *   黏土/砂岩/硬化黏土 = 1 ／ **圆石 = 2** ／ **黑曜石 = 3** ／ **下界岩 = 4**
     *
     * ⇒ 效果（用户原话 ✓）：**"圆石大熔炉最多可以烧铁！就算圆石大熔炉达到了 800 热力值，
     *   多出来的热力值也只能当做燃烧时长算！"** ✓ —— 这道天花板卡的是**能烧到第几档矿** ✗，
     *   **不是**能存多少热力值 ✓（池子照样能存到 10000，多出来的全变成燃烧时长 ✓）
     *
     *   一档 = 金/银/铜/锌(400) ／ 二档 = 铁(600) ／ 三档 = 秘银(800) ／ 四档 = 艾德曼(1000) ✓
     */
    public static int heatCeilingOf(int material) {
        if (material == MATERIAL_OBSIDIAN) return 800;      // 黑曜石：最多三档（秘银）✓
        if (material == MATERIAL_NETHERRACK) return 1000;   // 下界岩：最多四档（艾德曼）✓
        return 600;                                         // 圆石：最多二档（铁）✓
    }

    /** 27 格偏移表（含 0,0,0 自己） */
    public static final int[] OFF_DX = new int[27];
    public static final int[] OFF_DY = new int[27];
    public static final int[] OFF_DZ = new int[27];

    static {
        int i = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    OFF_DX[i] = dx;
                    OFF_DY[i] = dy;
                    OFF_DZ[i] = dz;
                    i++;
                }
            }
        }
    }

    public static boolean isFormed(int meta) {
        return (meta & META_MASK) != 0;
    }

    public static int frontOf(int meta) {
        return (meta & META_MASK) - 1;
    }

    public static int casingTypeOf(Block b) {
        if (b == CMBlocks.blockBrassCasing) return CASING_BRASS;
        if (b == CMBlocks.blockAndesiteCasing) return CASING_ANDESITE;
        return -1;
    }

    /** 包裹传动杆的材质 -> 本类编号 */
    public static int materialOf(BlockWrappedShaft shaft) {
        int k = shaft.kind();
        if (k == BlockWrappedShaft.KIND_COBBLESTONE) return MATERIAL_COBBLE;
        if (k == BlockWrappedShaft.KIND_OBSIDIAN) return MATERIAL_OBSIDIAN;
        return MATERIAL_NETHERRACK;
    }

    /**
     * 整机现在是不是"真在烧" —— **读核心那格 metadata 的 bit3** ✓（2026-10-01 接上真状态）
     *
     * 谁写：服务端的炉子逻辑（FurnaceCoreTileEntity.cm$smeltTick → cm$setBurning ✓），只在该位翻转那一刻写 ✓
     * 谁读：① 客户端渲染器 FurnaceBigRenderer（决定整机用"燃烧中"还是"待机"模型 ✓）
     *       ② 服务端界面容器 BigFurnaceContainer（走原版进度条通道把火焰点亮 ✓）
     *
     * ★ 原来那个"右键核心手动切换燃烧/待机外观"的**演示开关已按用户要求整段删除** ✗
     *   （用户 2026-10-01：「蹲下右键切换燃烧和未燃烧的模型操作不需要了」）
     *   —— 外观从此只跟真实工作状态走 ✓
     */
    public static boolean isBurning(World world, int x, int y, int z) {
        return world != null && (world.getBlockMetadata(x, y, z) & BURNING_BIT) != 0;
    }

    // ===================== 判定 =====================

    /** 判定结果（单例复用：判定只在服务端主线程跑 ✓） */
    public static final class Result {
        public boolean valid;
        public int material = -1;
        public int casing = -1;
        /** 失败原因（只用于日志：成型判定不通过时能一眼看出是哪一格不对 ✓） */
        public String reason = "";
    }

    private static final Result RESULT = new Result();
    /** 上一次"自检未通过"的原因（只在变化时打日志 ✓） */
    private static String LAST_REASON = "";

    /**
     * 判定「以 (cx,cy,cz) 为核心」的 3x3x3 是否成立。
     * 不读世界写状态，纯查询 ✓（渲染侧也可能调到）。
     */
    public static Result validate(IBlockAccess a, int cx, int cy, int cz) {
        RESULT.valid = false;
        RESULT.material = -1;
        RESULT.casing = -1;
        RESULT.reason = "";

        Block center = Block.blocksList[a.getBlockId(cx, cy, cz)];
        if (!(center instanceof FurnaceCoreBlock)) { RESULT.reason = "中心不是核心方块"; return RESULT; }
        int material = ((FurnaceCoreBlock) center).material();
        int casing = -1;
        int shafts = 0;

        for (int i = 0; i < 27; i++) {
            int dx = OFF_DX[i], dy = OFF_DY[i], dz = OFF_DZ[i];
            Block b = Block.blocksList[a.getBlockId(cx + dx, cy + dy, cz + dz)];
            if (b == null) {
                RESULT.reason = "有空气 @" + dx + "," + dy + "," + dz;
                return RESULT;
            }

            if (dy != 0) {                       // 上下两层：全是机壳
                int c = casingTypeOf(b);
                if (c < 0) { RESULT.reason = "顶层/底层该是机壳，实际 id=" + b.blockID + " @" + dx + "," + dy + "," + dz; return RESULT; }
                if (casing < 0) casing = c;
                else if (casing != c) { RESULT.reason = "机壳混用（安山/黄铜）@" + dx + "," + dy + "," + dz; return RESULT; }
                continue;
            }
            if (dx == 0 && dz == 0) continue;    // 正中心就是核心自己

            if (dx != 0 && dz != 0) {            // 中层四角：机壳
                int c = casingTypeOf(b);
                if (c < 0) { RESULT.reason = "中层四角该是机壳，实际 id=" + b.blockID + " @" + dx + "," + dy + "," + dz; return RESULT; }
                if (casing < 0) casing = c;
                else if (casing != c) { RESULT.reason = "机壳混用（安山/黄铜）@" + dx + "," + dy + "," + dz; return RESULT; }
                continue;
            }

            // 中层正十字四格：同材质的包裹传动杆，或者用同款机壳顶替 ✓（规则 2）
            if (b instanceof BlockWrappedShaft) {
                if (materialOf((BlockWrappedShaft) b) != material) {
                    RESULT.reason = "传动杆材质与核心不符 @" + dx + "," + dy + "," + dz;
                    return RESULT;
                }
                shafts++;
                continue;
            }
            int c = casingTypeOf(b);
            if (c < 0) { RESULT.reason = "中层十字格既不是传动杆也不是机壳，实际 id=" + b.blockID + " @" + dx + "," + dy + "," + dz; return RESULT; }
            if (casing < 0) casing = c;
            else if (casing != c) { RESULT.reason = "机壳混用（安山/黄铜）@" + dx + "," + dy + "," + dz; return RESULT; }
        }

        if (shafts < 1) { RESULT.reason = "一根传动杆都没有（规则 3）"; return RESULT; }   // ★ 规则 3：至少 1 根传动杆

        RESULT.valid = true;
        RESULT.material = material;
        RESULT.casing = casing;
        return RESULT;
    }

    /** 最后一块落点（相对核心的偏移）-> 正面方向 */
    public static int frontFromOffset(int dx, int dy, int dz) {
        if (dy != 0) return FRONT_SOUTH;                 // 顶/底层完成 -> 默认南
        if (dx > 0) return FRONT_EAST;
        if (dx < 0) return FRONT_WEST;
        if (dz > 0) return FRONT_SOUTH;
        if (dz < 0) return FRONT_NORTH;
        return FRONT_SOUTH;                              // 核心自己 / 角上 -> 默认南
    }

    public static String frontName(int front) {
        if (front == FRONT_WEST) return "西";
        if (front == FRONT_NORTH) return "北";
        if (front == FRONT_EAST) return "东";
        return "南";
    }

    // ===================== 变化通知 =====================

    /**
     * 某格方块变了（放上/挖掉）→ 通知周围 27 格里的每一个熔炉核心。
     * 只打标记，真正的判定在核心 TE 的下一 tick ✓
     */
    public static void onChange(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < 27; i++) {
            int cx = x + OFF_DX[i], cy = y + OFF_DY[i], cz = z + OFF_DZ[i];
            if (!(Block.blocksList[world.getBlockId(cx, cy, cz)] instanceof FurnaceCoreBlock)) continue;
            TileEntity te = world.getBlockTileEntity(cx, cy, cz);
            if (te instanceof FurnaceCoreTileEntity) {
                ((FurnaceCoreTileEntity) te).markChanged(x - cx, y - cy, z - cz);
            }
        }
    }

    /**
     * 只叫醒、不改"最后一块落在哪"的提示（自检路径用 ✓）。
     *
     * 【为什么要这个】Chunk 只在**设置方块**时才创建方块实体（javap 实证：整段
     * {@code instanceof ITileEntityProvider} 的创建循环都在 setBlockIDWithMetadata 里 ✗），
     * 所以"核心还是一堆普通方块时"搭好的老熔炉，读档后核心**根本没有 TE** → 没人做判定 ✗。
     * 包裹传动杆从第一天起就有 TE 在 tick ✓，于是让它每秒钟敲一下旁边核心的门 ✓
     * —— 老存档一进游戏就会自动补齐 ✓。
     */
    public static void wakeUp(World world, int x, int y, int z) {
        if (world == null) return;
        boolean remote = world.isRemote;
        if (!WAKE_CALLED) {
            WAKE_CALLED = true;
            System.out.println("[MITE] wakeUp 被调用 @ " + x + "," + y + "," + z + " 客户端=" + remote);
        }
        for (int i = 0; i < 27; i++) {
            int cx = x + OFF_DX[i], cy = y + OFF_DY[i], cz = z + OFF_DZ[i];
            if (!(Block.blocksList[world.getBlockId(cx, cy, cz)] instanceof FurnaceCoreBlock)) continue;

            // ★ 客户端**不在这里**补 TE：这段是在"遍历方块实体列表"的过程中跑的 ✗，
            //   往同一个列表里加元素有并发修改风险 ✗。客户端的渲染锚点交给
            //   RenderBlocksMixin 那条路（在区块重建时补，跟 TE 遍历无关 ✓）。
            if (remote) continue;

            TileEntity te = world.getBlockTileEntity(cx, cy, cz);
            if (te instanceof FurnaceCoreTileEntity) {
                if (!WAKE_LOGGED) {
                    WAKE_LOGGED = true;
                    System.out.println("[MITE] 大熔炉：包裹传动杆已叫醒核心 @ " + cx + "," + cy + "," + cz
                            + "（老存档补 TE 那条路 ✓）");
                }
                ((FurnaceCoreTileEntity) te).markSelfCheck();
            }
        }
    }

    private static boolean WAKE_LOGGED = false;
    private static boolean WAKE_CALLED = false;

    // ===================== 状态刷新 =====================

    /**
     * 核心 TE 每 tick 调一次（延迟已过）。
     * 已成型 → 复核，坏了就撤销；没成型 → 复核，够格就成型 ✓
     */
    public static void updateCore(World world, int cx, int cy, int cz, int dx, int dy, int dz) {
        if (world == null || world.isRemote) return;
        int meta = world.getBlockMetadata(cx, cy, cz) & META_MASK;
        Result r = validate(world, cx, cy, cz);
        boolean formed = meta != 0;

        if (formed) {
            if (r.valid) {
                // ★ 顺手把机壳档位同步给方块实体（界面按它决定 4+4 还是 8+8 ✓）
                //   老存档里成型早于本改动，所以这条每 5 秒的自检也要补写 ✓
                syncCasing(world, cx, cy, cz, r.casing);
                return;
            }
            // ★ 2026-10-01 用户 ①：结构散了 ⇒ **里面的东西全掉出来** ✓（热力值不返还 ✓）
            spillAt(world, cx, cy, cz);
            setMeta(world, cx, cy, cz, 0);
            markCasing(world, cx, cy, cz, false);
            System.out.println("[MITE] 大熔炉失型 @ " + cx + "," + cy + "," + cz);
            return;
        }

        if (!r.valid) {
            // 诊断（只在原因变化时打，避免刷屏）：让"为什么没成型"一眼可见 ✓
            if (!r.reason.equals(LAST_REASON)) {
                LAST_REASON = r.reason;
                System.out.println("[MITE] 大熔炉自检未通过 @ " + cx + "," + cy + "," + cz + " : " + r.reason);
            }
            return;
        }
        LAST_REASON = "ok";
        syncCasing(world, cx, cy, cz, r.casing);
        int front = frontFromOffset(dx, dy, dz);
        setMeta(world, cx, cy, cz, 1 + front);
        markCasing(world, cx, cy, cz, true);
        // ★ 2026-10-01 用户 ②：「提醒构建完成/被破坏的提示可以不需要了」⇒ 聊天栏提示**已整段删除** ✗
        //   （控制台那行日志**留着** ✓ —— 那是我排查用的，玩家看不到 ✓）
        System.out.println("[MITE] 大熔炉成型 @ " + cx + "," + cy + "," + cz
                + " 材质=" + r.material + " 机壳=" + r.casing + " 正面=" + frontName(front));
    }

    /** 把核心方块实体里的东西吐出来 ✓（用户 ①：结构被破坏时 ✓）*/
    private static void spillAt(World world, int cx, int cy, int cz) {
        TileEntity te = world.getBlockTileEntity(cx, cy, cz);
        if (te instanceof FurnaceCoreTileEntity) {
            ((FurnaceCoreTileEntity) te).spillContents();
        }
    }

    /** 把"这台用的是安山还是黄铜机壳"写进核心方块实体（界面用它决定槽位数 ✓）*/
    private static void syncCasing(World world, int cx, int cy, int cz, int casing) {
        TileEntity te = world.getBlockTileEntity(cx, cy, cz);
        if (te instanceof FurnaceCoreTileEntity) {
            ((FurnaceCoreTileEntity) te).setCasing(casing);
        }
    }

    /**
     * 核心自己那格写 metadata（flags=3：通知客户端 + 标记重绘 ✓）。
     * ★ 必须**顺手保留 bit3（燃烧中）** ✗ —— 这里是"成型/正面"的写入口，
     *   直接拿掩码盖上去会把燃烧位一起抹掉 ✓（同 syncSpeedToMetadata 那个坑 ✓）
     */
    private static void setMeta(World world, int x, int y, int z, int meta) {
        int cur = world.getBlockMetadata(x, y, z);
        world.setBlockMetadataWithNotify(x, y, z, (meta & META_MASK) | (cur & BURNING_BIT), 3);
    }

    /**
     * 给 27 格里的机壳打/清「属于成型结构」标记。
     * 机壳自己没有 TE，靠这个标记：① 渲染 O(1) 判断要不要隐藏 ✓ ② metadata 一变客户端就会重画那一格 ✓
     */
    private static void markCasing(World world, int cx, int cy, int cz, boolean formed) {
        for (int i = 0; i < 27; i++) {
            int x = cx + OFF_DX[i], y = cy + OFF_DY[i], z = cz + OFF_DZ[i];
            Block b = Block.blocksList[world.getBlockId(x, y, z)];
            if (casingTypeOf(b) < 0) continue;
            int meta = world.getBlockMetadata(x, y, z);
            int want = formed ? (meta | CASING_FORMED_BIT) : (meta & ~CASING_FORMED_BIT);
            if (want != meta) world.setBlockMetadataWithNotify(x, y, z, want, 3);
            else world.markBlockForUpdate(x, y, z);   // 值没变也要让客户端重画（隐藏状态可能变了）
        }
    }

    /**
     * 渲染侧：这一格是不是"已经成型的大熔炉的一部分"（据此隐藏方块本体）。
     * 只对**我们自己这几类方块**调用 ✓
     */
    public static boolean isHidden(IBlockAccess a, int x, int y, int z) {
        Block b = Block.blocksList[a.getBlockId(x, y, z)];
        if (b instanceof FurnaceCoreBlock) return isFormed(a.getBlockMetadata(x, y, z));
        if (casingTypeOf(b) >= 0) {
            if ((a.getBlockMetadata(x, y, z) & CASING_FORMED_BIT) == 0) return false;
            // 防御：标记是缓存，真正作数的是"旁边有成型核心" ✓
            return hasFormedCoreNear(a, x, y, z);
        }
        return false;
    }

    /**
     * 找出旁边那个"已成型大熔炉的核心"坐标（找不到返回 null）。
     * 用途：包裹传动杆右键切换燃烧外观时要知道该切哪一台 ✓
     * （核心被封在结构正中间、玩家够不着 ✗ —— 传动杆在正十字四格，正好是外侧能点到的地方 ✓）
     */
    public static int[] findFormedCoreNear(IBlockAccess a, int x, int y, int z) {
        for (int i = 0; i < 27; i++) {
            int cx = x + OFF_DX[i], cy = y + OFF_DY[i], cz = z + OFF_DZ[i];
            if (!(Block.blocksList[a.getBlockId(cx, cy, cz)] instanceof FurnaceCoreBlock)) continue;
            if (isFormed(a.getBlockMetadata(cx, cy, cz))) return new int[]{cx, cy, cz};
        }
        return null;
    }

    /** 附近 27 格里有没有成型核心（包裹传动杆的 TESR 也用它决定画不画 ✓） */
    public static boolean hasFormedCoreNear(IBlockAccess a, int x, int y, int z) {
        for (int i = 0; i < 27; i++) {
            int cx = x + OFF_DX[i], cy = y + OFF_DY[i], cz = z + OFF_DZ[i];
            if (!(Block.blocksList[a.getBlockId(cx, cy, cz)] instanceof FurnaceCoreBlock)) continue;
            if (isFormed(a.getBlockMetadata(cx, cy, cz))) return true;
        }
        return false;
    }

    public static boolean isHiddenShell(IBlockAccess a, int x, int y, int z) {
        Block b = Block.blocksList[a.getBlockId(x, y, z)];
        if (b instanceof FurnaceCoreBlock) return isFormed(a.getBlockMetadata(x, y, z));
        return false;
    }

    // ===================== 打开界面（2026-09-30）=====================

    /**
     * 右键任意一格：如果是**已成型**的大熔炉，且点的是**四个侧面**（不是顶/底面 ✓ 用户指定），
     * 就给玩家开界面 ✓。核心被封在中间点不到 ✗ —— 所以 26 格里的机壳 / 传动杆都走这一条 ✓。
     */
    public static void tryOpenUi(World world, int x, int y, int z, EntityPlayer player, net.minecraft.EnumFace face) {
        if (world == null || player == null) return;
        int f = face == null ? -1 : face.ordinal();
        String side = world.isRemote ? "客户端" : "服务端";
        System.out.println("[MITE][UI] 右键 " + x + "," + y + "," + z + " 面=" + f + " " + side);
        if (f == 0 || f == 1) {                             // 底面 0 / 顶面 1 -> 不开 ✓
            System.out.println("[MITE][UI]  -> 顶面/底面，不开 ✓");
            return;
        }
        int[] core = findFormedCoreNear(world, x, y, z);
        if (core == null) {
            System.out.println("[MITE][UI]  -> 附近没有成型核心，不开 ✗");
            return;
        }
        int meta = world.getBlockMetadata(core[0], core[1], core[2]) & META_MASK;
        if (meta != 0) {
            int portFace = frontFace(frontOf(meta)) ^ 1;    // 正面 vs 传动口：世界面序 2/3 与 4/5 各自成对 ✓
            if (f == portFace) {                            // ★ 传动口那一面不开 ✓（用户 2026-09-30 指定）
                System.out.println("[MITE][UI]  -> 这一面是传动口，不开 ✓");
                return;
            }
        }
        if (world.isRemote) {
            // ★★ 关键：MITE 的服务端**根本不会**调用 Block.onBlockActivated ✗
            //   （javap 实证：NetServerHandler.XXXhandlePlace 已被禁用 ✗，
            //     整包里只有 EntityPlayer.checkForBlockActivation 调它，而那条路只在客户端跑 ✓
            //     —— 实测日志：73 次右键全是客户端线程，服务端 0 次 ✓）
            //   所以服务端那条"建容器 + 发开窗包"必须由**客户端转过去** ✓。
            //   单机里客户端与服务端同 JVM ✓（本工程的结构选择器也是靠这个 ✓）
            //   → 直接找服务端那位玩家替他把界面开出来 ✓
            openOnServer(player, x, y, z);
            return;
        }
        TileEntity te = world.getBlockTileEntity(core[0], core[1], core[2]);
        System.out.println("[MITE][UI]  -> 核心 TE = " + te);
        if (te instanceof FurnaceCoreTileEntity) {
            BigFurnaceUi.open(player, (FurnaceCoreTileEntity) te);
        }
    }

    /** 客户端右键 -> 转到同 JVM 的服务端玩家去开界面（MITE 单机 ✓）*/
    private static void openOnServer(EntityPlayer clientPlayer, int x, int y, int z) {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null) {
                System.out.println("[MITE][UI]  -> 服务端还没起（server == null）✗");
                return;
            }
            net.minecraft.WorldServer sw = server.worldServerForDimension(clientPlayer.worldObj.provider.dimensionId);
            if (sw == null) return;
            java.util.List players = sw.playerEntities;
            if (players == null || players.isEmpty()) return;
            Object o = players.get(0);
            if (!(o instanceof net.minecraft.ServerPlayer)) return;
            net.minecraft.ServerPlayer sp = (net.minecraft.ServerPlayer) o;

            int[] core = findFormedCoreNear(sw, x, y, z);
            if (core == null) {
                System.out.println("[MITE][UI]  -> 服务端那边没找到成型核心 ✗");
                return;
            }
            TileEntity te = sw.getBlockTileEntity(core[0], core[1], core[2]);
            System.out.println("[MITE][UI]  -> 服务端核心 TE = " + te);
            if (te instanceof FurnaceCoreTileEntity) {
                BigFurnaceUi.open(sp, (FurnaceCoreTileEntity) te);
            }
        } catch (Throwable t) {
            System.out.println("[MITE][UI]  -> 转服务端失败: " + t);
        }
    }

    // ===================== 提示 =====================
    // ★ 2026-10-01 用户：「提醒大熔炉构建完成和被破坏的提示可以不需要了」
    //   ⇒ 原来的 announce()（聊天栏那条 §6[大熔炉] 成型！/ §7 结构被破坏）**已整段删除** ✗
    //   控制台那两行日志留着 ✓（玩家看不到，是给我排查用的 ✓）


    /** 正面方向（0=南 1=西 2=北 3=东）-> 世界面序（0=下 1=上 2=北 3=南 4=西 5=东） */
    public static int frontFace(int front) {
        if (front == FRONT_NORTH) return 2;
        if (front == FRONT_SOUTH) return 3;
        if (front == FRONT_WEST) return 4;
        return 5;
    }

    public static String materialName(int m) {
        if (m == MATERIAL_OBSIDIAN) return "黑曜石";
        if (m == MATERIAL_NETHERRACK) return "地狱岩";
        return "圆石";
    }

    public static String casingName(int c) {
        return c == CASING_BRASS ? "黄铜" : "安山";
    }
}
