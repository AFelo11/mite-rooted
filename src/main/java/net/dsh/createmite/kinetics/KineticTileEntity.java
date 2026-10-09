package net.dsh.createmite.kinetics;

import net.minecraft.NBTTagCompound;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 动力方块实体（传动轴 / 齿轮 / 手摇曲柄 共用；石磨继承它）。
 *
 * 速度用"带符号 RPM"表示：
 *   - 服务端为权威，每 tick 由 KineticNetwork 计算
 *   - 客户端**不需要网络包**：客户端自己也跑一份 KineticNetwork（影子网络），
 *     用本地已有的 crankTicks 当动力源就能把整张网络解出来，本地零延迟
 *   - metadata 只同步"轴向 + 朝向"这类静态信息（4 bit 的限制见下）
 */
public class KineticTileEntity extends TileEntity {

    /**
     * metadata 位分配。
     *
     * 【必须先知道的硬约束】MITE / MC 1.6.4 的方块 metadata 只有 **4 bit**（0..15）：
     * ExtendedBlockStorage.blockMetadataArray 是一个 NibbleArray，
     * 它的 set() 里用 & 15 截断（已用 javap 核对过字节码）。所以 metadata 里**放不下转速**。
     *
     * 现在只存"放置时定死的静态信息"：
     *   bit0-1  轴向 0=X 1=Y 2=Z
     *   bit2    朝向符号：facing 是否指向该轴的**正方向**（只有手摇曲柄用）
     *   bit3-7  未使用（保留）
     *
     * 转速由客户端自己的影子网络得出（见 updateClientAnimation）。
     *
     * 【原来的两个真缺陷】
     *  1) 位冲突：翻转位取 4，而速度等级写成 level << 3，等级 2 时 2<<3 = 16 正好占了 bit4，
     *     两者互相覆盖（48 RPM = 等级 6 → 48，低 4 位是 0，反而看起来"完全不动"）。
     *  2) 4 bit 根本装不下：level << 3 最大 120，写进去只会被 NibbleArray 截断。
     */
    public static final int META_AXIS_MASK = 3;
    public static final int META_FACE_BIT = 4;
    /**
     * 水车转向位（bit 3）：1 = 正转、0 = 反转。
     *
     * 【为什么放 metadata 而不是 NBT】NBT 只有**服务端**有 —— 客户端要自己算一份"影子网络"
     * 来渲染转速，它读不到服务端的 NBT，于是方向退回默认值 ✗
     * （表现：重进世界后水车一律朝右转、或者两个方向放置时只有一个方向转 ✓）。
     * 放进 metadata 后用 setBlockMetadataWithNotify(..., 2) 会自动同步到客户端 ✓。
     */
    public static final int META_WATER_DIR_BIT = 8;

    /**
     * 传动杆的"**已被机壳封装**"标记。
     * 【为什么传动杆可以用 bit2(值4)】位 4 原本是曲柄的"朝向位"（META_FACE_BIT），
     * 但**曲柄和传动杆是两个方块**，互不冲突 ✓ —— 传动杆自己的高两位（4/8）都是空的 ✓。
     * 资料 227807/227809：「对传动带或**传动杆**右键可以进行封装，**不消耗机壳**」✓
     */
    public static final int META_ENCASED_BIT = 4;

    /**
     * 封装用的**是不是黄铜机壳**（用于选模型：安山箱 / 黄铜箱 ✓）。
     * 同样只给传动杆/齿轮/大齿轮用 —— bit8 在别处是水车的"水流方向位"，
     * 但水车和这三种方块是不同方块，不会冲突 ✓。
     */
    public static final int META_BRASS_ENCASED_BIT = 8;

    // ===================== 装壳齿轮的"接轴开关"（工程③，逐条复刻原版） =====================
    //
    // 原版 Create 的 EncasedCogwheelBlock 就是两个 blockstate：
    //     public static final BooleanProperty TOP_SHAFT    = ...;   // 沿轴**正**那一端能不能接传动杆
    //     public static final BooleanProperty BOTTOM_SHAFT = ...;
    // 交互（onWrenched）：
    //     if (点击面.getAxis() != 方块自己的轴) return super.onWrenched(...);   // 点侧面 = 普通扳手 = 转轴 ✓
    //     ... cycle(点击面是正方向 ? TOP_SHAFT : BOTTOM_SHAFT)                  // 点轴向两端 = 开/关那一端 ✓
    // 效果（RotationPropagator.getRotationSpeedModifier）：
    //     connectedByAxis = 轴对齐 && hasShaftTowards(自己, dir) && hasShaftTowards(对方, 反向)
    //     → **只有"沿轴的接轴"吃这个开关**；平面内的齿轮啮合（小↔小、大↔小 2:1 变速）
    //       走后面的 gear 分支，**一律不受影响** ✓（用户问的"对角会不会被面闸掐断"在原版里不存在）
    //
    // 【为什么能直接塞进 metadata】装壳方块是**独立方块**（bit0-1 只存轴 ✓），bit2/bit3 正好空着，
    //   和原版"两个 BooleanProperty"一一对应 ✓。
    // 【语义】置位 = **该端已封堵**（0 = 两端都通）——
    //   这样老存档读出来是 0 = 行为与今天完全一致 ✓（原版默认是"两端都堵"，我们是"两端都通"，
    //   只差初始状态；原因见交接文档：不能让用户现有机器一夜之间不传动力）。
    public static final int META_SHAFT_POS_BIT = 4;   // bit2 = 沿轴**正**方向那一端（对应原版 TOP_SHAFT）
    public static final int META_SHAFT_NEG_BIT = 8;   // bit3 = 沿轴**负**方向那一端（对应原版 BOTTOM_SHAFT）

    /** 服务端：当前转速（RPM，带符号） */
    public float speed;
    /** 本 tick 是否已经解析过（避免重复遍历） */
    public long validTick = Long.MIN_VALUE;
    /**
     * 本 tick 所在的整张网络是否**应力过载**。
     * v2 求解器算完整张分量后统一写回；客户端同样有值（影子网络）。
     */
    public boolean overStressed;
    /** 手摇曲柄的剩余动力 tick */
    public int crankTicks;
    /** 手摇曲柄的转向：true = 反向（潜行右击，原版行为） */
    public boolean crankReversed;

    /** 水车：有没有被水流推动（每 10 tick 重扫一次） */
    public boolean waterPowered;

    /**
     * 水车方向（**本侧**扫描结果）：+1 正转 / -1 反转 / 0 还没扫到。
     * 【为什么必须本侧存一份】方向位 bit 3 只有**服务端**会写；客户端一旦没同步到那一位，
     * 它会永远按 bit=0（负）算 → 水车方向恒定不变，表现就是"怎么改水流都没反应" ✗。
     * 客户端本来就在跑同一份扫描（updateEntity 两个侧都扫），所以直接用扫描结果最可靠。
     */
    private int waterDirSign;

    /** 本网络里是否存在与基准源**方向相反**的动力源（只用于打日志，不参与解算） */
    public boolean sourceConflict;

    /** 放置被拒绝（3x3 空间不够）：由方块实体在**下一 tick**撤掉主体并退还物品 */
    public boolean cancelPlacement;

    // 水车取数日志用（只在符号变化时打一行，避免刷屏）
    private int wheelLastSign = 99;
    private boolean wheelCensusDone;
    /** 水车：被推的**方向与力度**（正/负），决定正转还是反转 */
    public float waterTorque;
    private int waterScanCooldown;

    /**
     * 包裹传动杆"叫醒旁边大熔炉核心"的节奏（tick）。
     * 见 FurnaceMultiblock.wakeUp 的说明：Chunk 只在设置方块时创建 TE ✗，
     * 所以老存档里"核心还没 TE"的熔炉需要有人定期敲门 ✓
     */
    private int furnaceWakeCooldown = 20;
    /** 一次性诊断：确认包裹传动杆的 TE 到底有没有在 tick ✓ */
    private static boolean cm$wakeLogged = false;

    /**
     * 大齿轮「4 个正交挡位补齐」的冷却计数（每 10 tick 一次，只在服务端跑）。
     *
     * 【为什么不复用 waterScanCooldown】那是水车的扫描节奏（而且**两边都要跑**，
     * 见 updateEntity 里那段说明）。混用一个计数器会让两条逻辑互相抢计时，
     * 周期变成随机的。分开各管各的。
     */
    private int cogAssemblyCooldown;

    /**
     * 上一次看到的自转轴（-1 = 这个方块实体刚建出来、还没看过）。
     *
     * 【为什么要记它】扳手（ItemWrench）改轴走的是 {@code setBlockMetadataWithNotify} ——
     * **既不触发 onBlockAdded、也不触发 breakBlock**，所以没有任何回调能告诉我们"平面换了"。
     * 不处理的话，旧平面里那 4 个占位格会变成"看不见、撞不到、却永远占着格子"的幽灵
     * （它们的 metadata 还是旧轴，连自己都扫不到主体了，只有世界编辑能清掉）。
     * 于是只能轮询比较：代价是每 tick 一次 getBlockMetadata + 整数比较。
     */
    private int cogAssemblyAxis = -1;

    /**
     * 大型水车「3x3 占位格补齐」的冷却计数（每 10 tick 一次，只在服务端跑）。
     *
     * 【为什么不复用 waterScanCooldown】那是水车的水扫描节奏，而且那一段**两边都要跑**
     * （见 updateEntity 里那段说明）。混用一个计数器会让两条逻辑互相抢计时，周期变成随机的。
     * 和大齿轮一样各管各的。
     */
    private int wheelAssemblyCooldown;

    /**
     * 上一次看到的**大型水车**自转轴（-1 = 这个方块实体刚建出来 / 刚从存档读出来、还没看过）。
     *
     * 【为什么要记它】与 {@link #cogAssemblyAxis} 完全同构，而且是同一个真 bug：
     * 扳手（ItemWrench 第 113 行）改轴走的是 {@code setBlockMetadataWithNotify} ——
     * **既不触发 onBlockAdded、也不触发 breakBlock**，没有任何回调能告诉我们"平面换了"✗
     *
     * 大型水车比大齿轮更严重：它那片 3x3 里有 **8 个**占位格（大齿轮只有 3 个）。
     * 不处理的话，旧平面里那 8 格会变成"看不见、撞不到、却永远占着格子"的幽灵 ——
     * 而且它们的 metadata 还停在**旧轴**上，连自己都找不到"新平面"里的主体
     * （虽然 findCore 用旧轴仍能找到主体，所以自检也不会替我清掉它们），
     * 只有玩家手动世界编辑才能收拾。
     *
     * 于是只能轮询比较：代价是每 tick 一次 getBlockMetadata + 整数比较。
     * 【-1 的那一次为什么不清】刚从存档读出来时我们**不知道**旧轴是什么，
     * 拿一个猜的轴去清等于拿玩家的建筑冒险 —— 所以第一次只记录、只补齐，绝不清。
     */
    private int wheelAssemblyAxis = -1;

    /** 客户端渲染角度 */
    public float angle;
    /** 客户端当前转速（由 metadata 推出） */
    public float clientSpeed;

    public KineticTileEntity() {}

    // ==================================================================================
    // ★★★ 「这个元件到底挂进世界了没有」——**实机崩溃根因（2026-09-26 20:58）的兜底层**
    //
    // 崩溃原文：
    //   NullPointerException: Cannot invoke "net.minecraft.World.getBlockId(int,int,int)"
    //   because "this.worldObj" is null
    //     at net.minecraft.TileEntity.getBlockType(TileEntity.java:218)
    //     at KineticTileEntity.isGearbox(...)      ← 我们在这里解引用了它
    //     at KineticHelper.connects(...)            ← 网络的连接判定
    //     at KineticNetwork.collect(...)            ← 收集网络成员
    //     at KineticNetwork.resolve / updateClientAnimation / updateEntity
    //     at net.minecraft.World.updateEntities(World.java:4606)
    //
    // 那个 worldObj==null 的元件**不是本模组 new 出来的**，是 MITE 自己的"懒创建 + 待处理"时序：
    //   ① World.updateEntities 在遍历 loadedTileEntityList 调 te.updateEntity() 之前，会把
    //      scanningTileEntities 置为 true（已用 javap 核对字节码：iconst_1 → putfield）；
    //   ② 在这段时间里，任何一次 World.getBlockTileEntity(x,y,z) 只要落在
    //      "该格是带方块实体的方块、但 chunkTileEntityMap 里还没有它的 TE" 的格子上，
    //      Chunk.getChunkBlockTileEntity 就会**当场 new 一个** ——
    //      走的是 Block.createNewTileEntity(chunk.worldObj)；
    //   ③ 紧接着 World.setBlockTileEntity 会因为 scanningTileEntities==true 走"待处理"分支：
    //      只写 xCoord/yCoord/zCoord 并塞进 addedTileEntityList，**从头到尾不调 setWorldObj**；
    //   ④ Chunk.getChunkBlockTileEntity 之后又从 chunkTileEntityMap 里读了一次（那里仍然是空的）
    //      → 返回 null；于是 World.getBlockTileEntity 退回去扫 addedTileEntityList，
    //      **那一趟只比坐标、完全不看那一格是什么方块**，就把这个半成品原样交给了我们 ✗
    //   → 我们的网络拿到一个 worldObj == null、blockType/blockMetadata 都还是初始值的元件，
    //     一句 isGearbox() 就崩了。
    //
    // 【修复分两层，两层都必须有】
    //   · 根因层：工厂方法（BlockKineticBase / BlockMillstone / BlockCrushingWheel 的
    //     createNewTileEntity）现在用 {@link #withWorld} 把 Chunk 传进来的 world **当场写进 TE**。
    //     这样第③步之后它也是"有世界"的，网络可以正常把它当成一个真元件（那本来就是它）。
    //   · 兜底层：下面这些谓词在 worldObj == null 时一律返回"不是 / 0"，
    //     保证以后万一又冒出半成品元件（别的模组、别的路径、以后改坏的代码），
    //     结果只是"这个元件本 tick 不参与动力网络"，**绝不会再崩游戏** ✓
    //
    // 【为什么连 metadata 也要挡】1.6.4 的 TileEntity.getBlockMetadata() 在
    // blockMetadata == -1（刚 new 出来的 TE 就是这个值）时同样是 worldObj.getBlockMetadata(...)，
    // 一样 NPE（字节码已核对）。
    // ==================================================================================

    /** 这个方块实体真的挂进世界了吗（所有"读方块/读 metadata"的判断都必须先问这一句） */
    public boolean hasWorld() {
        return this.getWorldObj() != null;
    }

    /**
     * 安全地取"我是什么方块"。
     * TileEntity.getBlockType() 在 blockType 还没缓存时会去 worldObj.getBlockId(...) ——
     * worldObj == null 就是本次实机崩溃的那一行。没世界时返回 null（= "不是任何方块"）。
     */
    public net.minecraft.Block blockOrNull() {
        return this.getWorldObj() == null ? null : this.getBlockType();
    }

    /** 安全地取 metadata（worldObj == null 时按 0 处理，等价于"没接进网络"） */
    public int metaSafe() {
        return this.getWorldObj() == null ? 0 : this.getBlockMetadata();
    }

    /**
     * 把 Chunk 交过来的 world 当场绑到新 TE 上（**根因修复**，理由见上面那一大段）。
     *
     * 【为什么一定要在工厂里绑】MITE 只有"非扫描期"那条路径
     * （World.setBlockTileEntity → Chunk.setChunkBlockTileEntity）才会调 setWorldObj；
     * "扫描期"（= 方块实体自己 tick 的时候，也正是我们的网络在到处找邻居的时候）那条路径不调。
     * 在工厂里先绑好，两条路径就都安全了 —— 而且它**本来就是**这个世界的方块实体。
     *
     * @return 同一个实例（方便写成 return KineticTileEntity.withWorld(new XxxTE(), world);）
     */
    public static <T extends KineticTileEntity> T withWorld(T te, World world) {
        if (te != null && world != null && te.getWorldObj() == null) {
            te.setWorldObj(world);
        }
        return te;
    }

    public int axis() {
        // ★ metaSafe()：半成品元件（worldObj==null）不能直接读 metadata，见上面那段说明
        return this.metaSafe() & META_AXIS_MASK;
    }

    /**
     * 朝向符号：曲柄的 facing 是否沿该轴的正方向。
     * 只对手摇曲柄有意义（其它部件绕轴对称，看不出朝向）。
     */
    public boolean facePositive() {
        return (this.metaSafe() & META_FACE_BIT) != 0;
    }

    /** 是否任意面都能接入动力（机器类方块覆写为 true） */
    public boolean acceptsAnyAxis() {
        return false;
    }

    /** 这个方块**占用**多少应力（查 KineticHelper 的登记表 + 配置文件） */
    public float stressImpact() {
        // blockOrNull()：半成品元件（worldObj==null）按"没登记过的方块"算 0 应力，见 hasWorld 的说明
        return KineticHelper.stressImpact(this.blockOrNull());
    }

    /** 作为动力源能**提供**多少应力（非动力源返回 0） */
    public float stressCapacity() {
        return KineticHelper.stressCapacity(this.blockOrNull());
    }

    /**
     * 是否"只能被齿轮带动"的机器（石磨）。
     *
     * 这类方块**不接受传动轴 / 手摇曲柄直连**，只有"水平相邻、且轴向与它相同"的齿轮才算啮合。
     * 见 KineticHelper.connects 里的说明。
     */
    /**
     * 这台机器**自带齿轮**吗？（资料 330130：石磨、动力搅拌器这类元件自带齿轮，
     * 因此同样适用"大齿轮 ↔ 齿轮"的对角变速规则）
     */
    public boolean hasBuiltInCog() {
        net.minecraft.Block b = this.blockOrNull();
        return b instanceof net.dsh.createmite.kinetics.block.BlockKineticBase
                && ((net.dsh.createmite.kinetics.block.BlockKineticBase) b).hasBuiltInCog();
    }

    /** 会被"齿轮啮合"规则处理的元件：齿轮本体，或自带齿轮的机器 */
    public boolean isCogLike() {
        return this.isCog() || this.hasBuiltInCog();
    }

    public boolean needsGearDrive() {
        return false;
    }

    public boolean isSource() {
        net.minecraft.Block b = this.blockOrNull();
        return b instanceof net.dsh.createmite.kinetics.block.BlockHandCrank
                || b instanceof net.dsh.createmite.kinetics.block.BlockWaterWheel
                || b instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel;
    }

    // ---- 方块类型判定（网络规则全都建立在这几个谓词上）----

    /**
     * 本格是**封装箱**吗？是的话里面包着什么机构：
     * 0 = 传动杆、1 = 齿轮、2 = 大齿轮；**-1 = 不是封装箱** ✓
     *
     * 【为什么要这个】封装箱是**独立方块**（资料 396859/396860/857833/857843/857834 ✓），
     * 它必须**照旧当成里面的那个机构**参与动力网络 —— 否则封装后齿轮就不啮合、传动杆就断了 ✗。
     */
    public int encasedVariant() {
        net.minecraft.Block b = this.blockOrNull();
        if (b instanceof net.dsh.createmite.kinetics.block.BlockEncased) {
            return ((net.dsh.createmite.kinetics.block.BlockEncased) b).variant();
        }
        return -1;
    }

    /** 是不是齿轮（小齿轮 / 大齿轮 / **封装的齿轮** 都算）——「两个齿轮平行相邻会啮合」用的是这个 */
    public boolean isCog() {
        net.minecraft.Block b = this.blockOrNull();
        if (b instanceof net.dsh.createmite.kinetics.block.BlockCogwheel
                || b instanceof net.dsh.createmite.kinetics.block.BlockLargeCogwheel) {
            return true;
        }
        int v = this.encasedVariant();
        return v == net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_COGWHEEL
                || v == net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_LARGE_COGWHEEL;
    }

    /** 是不是**大**齿轮（半径更大，与小齿轮 2:1 啮合；两个大齿轮互相不啮合）——封装的大齿轮也算 ✓ */
    public boolean isLargeCog() {
        if (this.blockOrNull() instanceof net.dsh.createmite.kinetics.block.BlockLargeCogwheel) return true;
        return this.encasedVariant() == net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_LARGE_COGWHEEL;
    }

    // ===================== 工程③：装壳齿轮的两端接轴开关 =====================

    /**
     * 本格带不带"两端接轴开关"。
     * **只有装壳的齿轮 / 大齿轮有** ✓ —— 装壳传动杆箱没有（原版的 EncasedShaftBlock 也没有
     * 这两个属性，传动杆箱本来就是一根贯通到底的轴）✓。
     */
    public boolean hasShaftSwitches() {
        int v = this.encasedVariant();
        return v == net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_COGWHEEL
                || v == net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_LARGE_COGWHEEL;
    }

    /** 该端（= 原版 TOP_SHAFT / BOTTOM_SHAFT 那一位）是不是被扳手关掉了接轴 */
    public boolean isShaftEndClosed(int dir) {
        int bit = KineticHelper.isPositiveDir(this.axis(), dir) ? META_SHAFT_POS_BIT : META_SHAFT_NEG_BIT;
        return (this.liveMeta() & bit) != 0;
    }

    /**
     * 服务端：开/关 dir 这一端的接轴，返回**开关之后**的状态（true = 现在已封堵）。
     *
     * ★ 写 metadata 之外**必须**再调 markBlockForUpdate —— 光写 metadata 不保证把这一格重发给客户端 ✗
     *   （封装位当年就是这么坑的，见 setEncased 里的说明）。
     */
    public boolean toggleShaftEnd(int dir) {
        if (this.worldObj == null || this.worldObj.isRemote) return false;
        int bit = KineticHelper.isPositiveDir(this.axis(), dir) ? META_SHAFT_POS_BIT : META_SHAFT_NEG_BIT;
        int meta = this.metaSafe();
        int want = (meta & bit) != 0 ? (meta & ~bit) : (meta | bit);
        this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, want, 2);
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        boolean closed = (want & bit) != 0;
        System.out.println("[CreateMITE][SHAFT-END] " + this.xCoord + "," + this.yCoord + "," + this.zCoord
                + " dir=" + dir + " axis=" + this.axis() + " bit=" + bit
                + " meta " + meta + " -> " + want + " closed=" + closed);
        return closed;
    }

    /** 十字齿轮箱：只在「垂直于自身轴」的 4 个面接轴（复刻 Create 的 hasShaftTowards） */

    /** 是否已被机壳封装（只有传动杆会用到，见 META_ENCASED_BIT） */
    public boolean isEncased() {
        return (this.liveMeta() & META_ENCASED_BIT) != 0;
    }

    /**
     * **实时**读本格的 metadata（直接问世界，不走 TE 的缓存字段）。
     * 【为什么】MITE 的 {@code TileEntity.blockMetadata} 是缓存，同步之后可能滞后一帧 ✗ ——
     *   用户实测："黄铜机壳封装后**闪一帧黄铜**、随后变回安山" ✓ = 第一帧读到了新值、
     *   之后又读回旧缓存 ✓。封装位/水车方向位这类**渲染要用**的状态必须实时读 ✓。
     */
    public int liveMeta() {
        if (this.worldObj == null) return 0;
        return this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
    }

    /**
     * 设置/取消"已封装"。**只在服务端写** metadata 并同步给客户端（flag 2）✓ ——
     * 客户端拿不到就得靠 metadata 同步（这正是当初水车方向位踩过的坑 ✗）。
     */
    public void setEncased(boolean encased, boolean brass) {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        int meta = this.metaSafe();
        // ★ 解除封装时必须**两个位一起清** ✓ ——
        //   ✘ 旧写法是"按 brass 参数决定 bit3 的去留" ✗，于是解除后残留 meta=9（轴1+黄铜位 ✓）
        //     日志实测：[ENCASE] server set meta=9 ... encased=false ✓（错在这里）
        int want;
        if (!encased) {
            want = meta & ~(META_ENCASED_BIT | META_BRASS_ENCASED_BIT);
        } else if (brass) {
            want = meta | META_ENCASED_BIT | META_BRASS_ENCASED_BIT;
        } else {
            want = (meta | META_ENCASED_BIT) & ~META_BRASS_ENCASED_BIT;
        }
        if (want != meta) {
            this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, want, 2);
            // ★★ 关键：光写 metadata **不保证**会把这一格重发给客户端 ✗ ——
            //   用户实测"闪一帧黄铜又变回安山""齿轮只有安山" ✓ = 客户端根本没收到这个位 ✓。
            //   markBlockForUpdate 会强制把该格重新下发（含 metadata）✓。
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            System.out.println("[CreateMITE][ENCASE] server set meta=" + want + " @ "
                    + this.xCoord + "," + this.yCoord + "," + this.zCoord
                    + " brass=" + brass + " encased=" + encased);
        }
    }

    /** 封装用的是不是黄铜机壳（没封装时无意义） */
    public boolean isBrassEncased() {
        return (this.liveMeta() & META_BRASS_ENCASED_BIT) != 0;
    }

    public boolean isGearbox() {
        // ★★ 实机崩溃就崩在这一行（worldObj == null 时 getBlockType() 会 NPE）——
        //    改用 blockOrNull()，见 hasWorld() 上面那一整段说明。
        return this.blockOrNull() instanceof net.dsh.createmite.kinetics.block.BlockGearbox;
    }

    /** 离合器：通电时断开动力 */
    public boolean isClutch() {
        return this.blockOrNull() instanceof net.dsh.createmite.kinetics.block.BlockClutch;
    }

    /** 反转齿轮箱：通电时输出反向 */
    public boolean isGearshift() {
        return this.blockOrNull() instanceof net.dsh.createmite.kinetics.block.BlockGearshift;
    }

    /**
     * 方块是否被红石充能。
     *
     * **不存 metadata、不存 NBT** —— 每次要用的时候现读。
     * 好处：客户端与服务端天然一致（不需要发包），红石一变立刻生效。
     */
    public boolean isPowered() {
        World w = this.getWorldObj();
        if (w == null) return false;
        return w.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord);
    }

    /** 动力源的输出转速（子类/曲柄覆写） */
    public float getSourceSpeed() {

        // ★ 防御（只加前置判据，**一个字的转向规则都没动**）：
        //   这个方法下面会读 getBlockType()/getBlockMetadata()，两者都会解引用 worldObj
        //   （见 hasWorld 上面那段崩溃说明）。没进世界的元件按"不发电"处理。
        if (!this.hasWorld()) return 0.0F;

        // 水车：有水才转；大型水车更慢但力更大（容量见 KineticHelper.stressCapacity）
        // 水车/大型水车：转速的**符号跟着水流方向走**（水从哪边冲就往哪边转）
        // 方向读 **metadata**（服务端算好写进去，客户端靠同步拿到）—— 不能用 NBT，客户端没有 NBT
        // 方向优先用**本侧扫描**结果；没扫到才退回 metadata 的 bit 3（老存档/刚放下的那一瞬）。
        boolean waterPositive = this.waterDirSign != 0
                ? (this.waterDirSign > 0)
                : ((this.getBlockMetadata() & META_WATER_DIR_BIT) != 0);

        // ★★ 旋向标定：**必须取反**（waterPositive ? -s : s）。
        // 【实机证据 2026-09-26】水流向西时 waterTorque() 返回 -2.0（日志 f90 → fx=-1 ✓ 解码正确），
        //   而渲染端**负转速**让轮子底部往**东**走 = 逆流 ✗。
        //   要让"轮子底部顺流"（玩家要的唯一观感），必须在源头上把这个符号翻过来。
        //   这是唯一一处相信实测、不信推导的地方 —— 推导已经错过两次了。
        // ★★ 2026-09-28 用户定稿：**"周围有水"不等于"被驱动"** ✗ —— 必须"有**流动水**推过"。
        //   waterTorque 字段 = 这次扫描/上次扫描算出来的**驱动力矩**（见 updateEntity 里的记忆逻辑）：
        //     · 现在就有流水 → 每次扫描刷新 ✓
        //     · 水还在、但已经流到尽头（死水）→ 保留上一次的力矩 → **照原方向继续转** ✓
        //     · 周围彻底没水 → 清零 → 停 ✓
        //     · 从头到尾都是死水 → 力矩恒为 0 → **不转** ✓（这就是用户报的 bug）
        if (this.getBlockType() instanceof net.dsh.createmite.kinetics.block.BlockWaterWheel) {
            if (!this.waterPowered || Math.abs(this.waterTorque) < 1.0E-4F) return 0.0F;
            float s = net.dsh.createmite.CMConfig.getFloat("water_wheel.speed", 8.0F);
            return waterPositive ? -s : s;
        }
        if (this.getBlockType() instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel) {
            if (!this.waterPowered || Math.abs(this.waterTorque) < 1.0E-4F) return 0.0F;
            float s = net.dsh.createmite.CMConfig.getFloat("large_water_wheel.speed", 4.0F);
            return waterPositive ? -s : s;
        }
        if (isSource()) {
            if (crankTicks <= 0) return 0.0F;
            // 原版：手摇曲柄固定 32 RPM，右击后持续 10 ticks（资料：227791）
            float cs = 32.0F * ((float) crankTicks / 10.0F);
            return this.crankReversed ? -cs : cs;
        }
        return 0.0F;
    }

    /**
     * 水车：周围有没有水（流动或静止都算）—— 决定"还转不转"。
     *
     * ★ 判据必须和 {@link #waterTorque()} **完全一致**，否则会出现
     * "力矩算得出来但 hasAnyWater() 说没有 → getSourceSpeed() 直接返回 0"这种自相矛盾。
     * 详见 waterTorque() 里关于大型水车那段的说明。
     */
    private boolean hasAnyWater() {
        World w = this.worldObj;
        if (w == null) return false;
        int[][] offs = this.waterOffsets(this.axis());
        for (int i = 0; i < offs.length; i++) {
            int[] o = offs[i];
            if (w.getBlockMaterial(this.xCoord + o[0], this.yCoord + o[1], this.zCoord + o[2])
                    == net.minecraft.Material.water) {
                return true;
            }
        }
        return false;
    }

    /** 这台机器是不是大型水车（占 3x3，判据要跟着变） */
    public boolean isLargeWaterWheel() {
        return this.blockOrNull() instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel;
    }

    /**
     * ★★★ 「哪些格子的水算驱动水」—— **逐条照搬原版 {@code WaterWheelBlockEntity} 的静态偏移表** ✓。
     *
     * 原版原文（1.20.1）：
     * <pre>
     *   // 小水车：垂直于自转轴的 4 个方向，各一格
     *   for (Direction d : directions) if (d.getAxis() != axis) offsets.add(ZERO.relative(d));
     *
     *   // 大型水车：平面内距离 2 的 4 格 + 它们各自沿"另一个平面轴"±1 的两格 = 12 格
     *   BlockPos centralOffset = ZERO.relative(d, 2);
     *   offsets.add(centralOffset);
     *   offsets.add(centralOffset.relative(d2));   // d2 = 另一个平面轴的两个方向
     * </pre>
     *
     * 【为什么大型水车必须这么改】它**实打实占 3×3**（主体 + 8 个实心占位格，水进不去）✗，
     *   于是水只能待在**平面内距离 2 的那一圈**上。
     *   ✘ 我们以前只扫主体周围 3×3×3 一格 ✗ → **永远够不到那一圈** →
     *     玩家明明看见水贴着轮子，却"完全不会转、也没动力" ✓（用户实测报的 bug，本次修复）。
     *
     * 【两张表都只取"同层"（沿自转轴偏移 = 0）】✓ 与原版一致：桨叶长在**轮缘**上，
     *   沿着轴向偏出去的水打不到桨叶 ✗（小水车那条老规则也是这么定的 ✓）。
     *
     * ★【小水车我们**故意多留了 4 个对角格**】原版小水车只认正交 4 格；
     *   但我们的 8 格（正交 4 + 对角 4）是**用户实测通过**的旧行为 ✓，
     *   而"多认几格"只会让水车**更容易转**、不会造成"该转不转" ✗ →
     *   这次只修大型水车（那才是真 bug），不去动已经验证过的小水车 ✓。
     *   要完全对齐原版的话，把上面那段"对角 4 格"删掉即可 ✓。
     */
    /** 下标 = 自转轴（0=X 1=Y 2=Z）；每个轴下挂一串 {dx,dy,dz} 偏移 ✓ */
    private static final int[][][] SMALL_WATER_OFFSETS = new int[3][][];
    private static final int[][][] LARGE_WATER_OFFSETS = new int[3][][];

    static {
        int[][] dirs = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (int axis = 0; axis < 3; axis++) {
            java.util.ArrayList<int[]> small = new java.util.ArrayList<int[]>();
            java.util.ArrayList<int[]> big = new java.util.ArrayList<int[]>();
            for (int i = 0; i < dirs.length; i++) {
                int[] d = dirs[i];
                if (d[axis] != 0) continue;                        // 只要垂直于自转轴的方向
                small.add(new int[]{d[0], d[1], d[2]});            // 正交 4 格
                for (int j = 0; j < dirs.length; j++) {
                    int[] d2 = dirs[j];
                    if (d2[axis] != 0) continue;
                    if (axisOfDir(d2) == axisOfDir(d)) continue;
                    if (d2[0] < 0 || d2[1] < 0 || d2[2] < 0) continue;   // 每个对角只加一次
                    small.add(new int[]{d[0] + d2[0], d[1] + d2[1], d[2] + d2[2]});   // 对角 4 格
                }
                int[] base = {d[0] * 2, d[1] * 2, d[2] * 2};       // 平面内距离 2
                big.add(base);
                for (int j = 0; j < dirs.length; j++) {
                    int[] d2 = dirs[j];
                    if (d2[axis] != 0) continue;                   // 也在这个平面里
                    if (axisOfDir(d2) == axisOfDir(d)) continue;   // 必须是**另一个**平面轴
                    big.add(new int[]{base[0] + d2[0], base[1] + d2[1], base[2] + d2[2]});
                }
            }
            SMALL_WATER_OFFSETS[axis] = small.toArray(new int[small.size()][]);
            LARGE_WATER_OFFSETS[axis] = big.toArray(new int[big.size()][]);
        }
    }

    /** 方向向量属于哪个轴（0=X 1=Y 2=Z） */
    private static int axisOfDir(int[] d) {
        if (d[0] != 0) return 0;
        if (d[1] != 0) return 1;
        return 2;
    }

    /**
     * 本次扫描要看的偏移表（小水车 4 格 / 大型水车 12 格）✓。
     * ★ {@link #waterTorque()} 与 {@link #hasAnyWater()} **必须共用这一份** ✗ ——
     *   两处判据一旦写歪，就会"力矩算得出来但 hasAnyWater 说没水 → 转速直接归零" ✓。
     */
    private int[][] waterOffsets(int axis) {
        return (this.isLargeWaterWheel() ? LARGE_WATER_OFFSETS : SMALL_WATER_OFFSETS)[axis];
    }

    /**
     * 水车：算出被水推的**方向**（正 / 负 / 0）。
     *
     * 【物理模型】每一块流动的水都产生一个力矩：
     *   切向 = 自转轴 × 水相对轮心的位移
     *   推力 = 水流向量 · 切向
     * 全部加起来，符号就是该往哪边转 —— 和 Create 的"水从哪边冲过来就往哪边转"一致。
     *
     * 【流向怎么拿】用原版 {@code BlockFluid.getFlowDirection} 的返回值（弧度角）反解水流向量：
     *   它返回的是 atan2(z, x) - π/2，所以 x = -sin(角)、z = cos(角)。
     *   返回 -1000 表示这块水没有流向（死水）→ 跳过，推不动轮子。
     *
     * 只在服务端算（每 10 tick 一次），算完写进 waterPowered / waterTorque，
     * 客户端靠影子网络读同一个值 —— 所以扫描逻辑只要一份。
     */
    public float waterTorque() {
        World w = this.worldObj;
        if (w == null) return 0.0F;
        int axis = this.axis();
        // ★ 大型水车占 3×3 ✓ → 要检查的格子比小水车多得多，见 waterOffsets() 里那张原版偏移表
        float sum = 0.0F;        // ① 接触面顺流（主判据）
        float sumPure = 0.0F;    // ② 纯流向（水与轮轴同高时的兜底）
        // （原 sumAxial 轴向兜底已删除：轴向水流不驱动水车，见下面的说明）

        // ★★ 2026-09-28 用户定稿（原版同）：**只有流动的水才驱动水车** ✗
        //   这里原来有一个"找最近水源、把流向当成背离水源"的兜底 —— 那正是
        //   "放一潭死水水车也照转"的**元凶** ✗，已整段删除。
        //   代价：MITE 的 getFlowDirection 返回 -1000（死水）时本函数返回 0 —— 这是**故意**的，
        //   0 不再等于"没救"：方向上头有记忆（见 updateEntity 的 waterScan 段）。

        // ★ 扫描范围 = 上面那张原版偏移表（小 4 格 / 大 12 格，全部同层）✓，
        //   和 hasAnyWater() 用的是同一份，不会再出现"两处判据不一致" ✗
        int[][] offs = this.waterOffsets(axis);
        for (int oi = 0; oi < offs.length; oi++) {
                    int dx = offs[oi][0];
                    int dy = offs[oi][1];
                    int dz = offs[oi][2];
                    int bx = this.xCoord + dx;
                    int by = this.yCoord + dy;
                    int bz = this.zCoord + dz;
                    // ★★ 真 bug 修复：这里以前判的是 Block.waterMoving.blockID ✗
                    //    —— "周围有没有水"用的是**材质**，两边不一致。只要 MITE 里流动的水不是那个 ID，
                    //    本方法就恒返回 0 → metadata 的方向位（bit 3）**从来没被写过**
                    //    → 水车永远按初始值 waterPositive=false 转 -8 RPM，朝一个固定方向 ✗
                    //    （现象：改水流方向没用、始终朝一边、所有水车都"反"）。
                    //    现在只认材质，和 hasAnyWater() 完全一致。
                    if (w.getBlockMaterial(bx, by, bz) != net.minecraft.Material.water) continue;

                    // 【哪些格子算驱动水】已经在 waterOffsets() 那张原版偏移表里定死了 ✓ ——
                    //   小水车 = 垂直于轴的 4 格、大型水车 = 平面内距离 2 那一圈的 12 格，**全部同层** ✓。
                    //   ✘ 老代码在这里又判一次"轮缘"，那套判据对大型水车是错的（够不到轮缘外那一圈）✗

                    float fx = 0.0F, fz = 0.0F;
                    boolean gotFlow = false;

                    // 路线一：原版 API（rad 角 → 流向向量）
                    double ang = net.minecraft.BlockFluid.getFlowDirection(w, bx, by, bz,
                            net.minecraft.Material.water);
                    if (ang != -1000.0D) {
                        fx = (float) -Math.sin(ang);
                        fz = (float) Math.cos(ang);
                        gotFlow = Math.abs(fx) > 1.0E-4F || Math.abs(fz) > 1.0E-4F;
                    }

                    // ★★ 2026-09-28 用户定稿：**只有能算出流向的水才驱动** ✓
                    //   · 路线二（"背离最近水源"造方向）已删除 ✗ —— 它让**死水**也有力矩，
                    //     表现就是用户报的"有水无论流动与否都可以转动" ✗
                    //   · 竖直（平放）轴那条"水不流动也加 1.0 分"也已删除 ✗（同一个病）
                    //   → 算不出流向就 continue，力矩**只由真的在流的水**贡献 ✓
                    if (!gotFlow) continue;

                    // ★ 轴竖直（平放）用**位置感知**的力矩：水从旁边流过就推动桨叶 ——
                    //   力矩 = (r × F)·ŷ = dz*fx − dx*fz（r 是水相对轮心的水平偏移，F 是流向）。
                    //   【为什么不能沿用"切向基准"】up × 轴 在轴竖直时是**退化向量**（叉积为 0），
                    //   原来那套基准在这里只取得到一个分量 → 水换个水平方向流就算出 0 ✗。
                    //   直接用 r × F 就没有这个问题：水在轮子哪一侧、往哪边流，符号都跟着变 ✓。
                    if (axis == 1) {
                        // ★★ 桨叶在**轮缘**（侧面）—— 正上/正下方的水**不驱动**它 ✗
                        //   （用户实测：TOP/BOTTOM 面接触水时水车不该转，因为那两个面根本没有水扇）
                        if (dx == 0 && dz == 0) continue;

                        // ★ 符号按**实测**取反：实机里"平放的水车摆在水流旁边"时，
                        //   用 (r × F)·ŷ = dz*fx − dx*fz 得到的方向与实际相反 ✗
                        //   （其他轴（X/Z）的摆法当时是正确的，所以只翻这一支 ✓）。
                        //   和 getSourceSpeed() 里那处"旋向标定取反"是同一类事：**信实测**。
                        float ty = dx * fz - dz * fx;
                        if (Math.abs(ty) > 1.0E-4F) { sum += ty; sumPure += ty; }
                        else { sum += 1.0F; sumPure += 1.0F; }   // 无切向分量（正上/正下方）→ 方向由轮子定
                        continue;
                    }

                    // ① 流向项（原版规则）：所有驱动流体的**流向**决定转向，反向流互相抵消。
                    //    切向基准 ref = up × 轴，垂直于自转轴的一个固定水平方向：
                    //      轴 Z → (1,0,0)：看水流 X 分量
                    //      轴 X → (0,0,-1)：看 -水流 Z 分量
                    //      轴 Y → 退化成看水流 X 分量
                    //    ✘ 旧实现用的是"流向 × 相对位移"，水与轮子同高时恒为 0，
                    //      表现就是"怎么改水流方向都不动"。
                    float tx, tz;
                    if (axis == 0) {
                        tx = 0.0F; tz = -1.0F;
                    } else {
                        tx = 1.0F; tz = 0.0F;
                    }
                    // ★ **纯流向**规则 —— 资料 196531 原话：
                    //   "水车某一面只要存在任意流动的流体，且所有驱动流体的流动方向相同，即可产生应力"
                    //   "0.5.1 …… 取消了水车浆板的方向性"
                    //   → 水在轮子的哪一侧**不影响**转向 ✗（我上一版加的"侧别系数"违背资料，已撤）
                    //   → 反向流互相抵消：下面这两项加起来正好自然抵消 ✓
                    float flow = fx * tx + fz * tz;

                    // ★★ 唯一判据：**纯流向**（水在轮子的哪一侧都不影响转向）。
                    //
                    // 【证据链，别再改回去】
                    //  1) 资料 196531 原话："水车某一面只要存在任意流动的流体，且所有驱动流体的
                    //     流动方向相同，即可产生应力"、"0.5.1 …… 取消了水车浆板的方向性"
                    //     → 水在哪一侧**不该**影响转向。
                    //  2) 反汇编 MITE 的 BlockFluid.getFlowDirection（字节码 ldc2_w 1.5707963267948966）：
                    //     返回值 = atan2(z, x) − π/2，与这里 −sin/cos 的解码**完全一致** → 流向向量是对的。
                    //  3) 实机：水自东往西流，轮子却往东转 —— 正是"侧别系数"翻出来的：
                    //     水在轮子上方时顶部顺流，底部就必然**逆流**，玩家看到的就是"反向转" ✗
                    //  所以：低处的水推底部、高处的水也按底部算 → 轮子**永远顺流向下滚** ✓
                    sum += flow;
                    sumPure += flow;

                    // ✘ 这里原来有一个"轴向流兜底"（用流向沿轴的分量硬给一个方向）—— 已删除：
                    //   水顺着自转轴流来时物理力矩就是 0（桨叶只在轮平面内运动），原版同样不认。
        }
        // 三级兜底。原则：**宁可方向判得粗，也绝不能算成 0** ——
        // 算成 0 就不会更新 metadata 里的方向位（bit 3），
        // 玩家看到的就是"怎么改水流都没反应"。
        // 返回 0 = 这半秒没有可用的驱动力 → updateEntity 会**保持上一次记住的方向**继续转
        // （玩家要的是"水流到头了也照原方向转"）。注意这不再是"永远算不出方向"那个 bug：
        // 认水判据已改成材质、流向解码已用字节码核实，正常水流一定算得出非 0。
        return sum;
    }

    /**
     * 水车现场取数（一行 ASCII，方便我直接读 latest.log）：
     * 位置 / 轴向 / 元数据 / 力矩 / 周围 26 格中**非空气**方块的 id:元数据，
     * 其中凡是水，再附上 getFlowDirection 的原始角度（dead = 返回 -1000）。
     */
    private String wheelDebugLine(float t) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("[CreateMITE][WHEEL] at ").append(this.xCoord).append(',').append(this.yCoord)
                .append(',').append(this.zCoord)
                .append(" axis=").append(this.axis())
                .append(" meta=").append(this.getBlockMetadata())
                .append(" t=").append(t)
                .append(" powered=").append(this.waterPowered)
                .append(" | nb:");
        World w = this.worldObj;
        if (w != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int bx = this.xCoord + dx, by = this.yCoord + dy, bz = this.zCoord + dz;
                        int id = w.getBlockId(bx, by, bz);
                        boolean isWater = w.getBlockMaterial(bx, by, bz) == net.minecraft.Material.water;
                        if (id == 0 && !isWater) continue;
                        sb.append(' ').append(dx).append(',').append(dy).append(',').append(dz)
                                .append('=').append(id).append(':').append(w.getBlockMetadata(bx, by, bz));
                        if (isWater) {
                            double a = net.minecraft.BlockFluid.getFlowDirection(w, bx, by, bz,
                                    net.minecraft.Material.water);
                            sb.append("/f").append(a == -1000.0D ? "dead" : String.valueOf((int) Math.toDegrees(a)));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 手摇一次 */
    public void crank(boolean reversed) {
        this.crankTicks = 10;          // 原版：持续 10 ticks
        this.crankReversed = reversed; // 原版：潜行右击 → 顺时针（反向）
    }

    @Override
    public void updateEntity() {
        World world = this.worldObj;
        if (world == null) return;

        // ★ 放置被拒绝 → 下一 tick 撤掉主体并把方块退还成物品 ✓
        //   为什么不在 onBlockAdded 当场撤：那一刻还在 Chunk.setBlockIDWithMetadata 内部，
        //   改本格会和外层写回打架 ✗（见 BlockLargeWaterWheel.onBlockAdded 的注释）。
        if (this.cancelPlacement) {
            this.cancelPlacement = false;
            if (!world.isRemote) {
                net.minecraft.Block self = this.getBlockType();
                if (self != null) {
                    self.dropBlockAsItself(new net.minecraft.BlockBreakInfo(
                            world, this.xCoord, this.yCoord, this.zCoord));
                }
                world.setBlockToAir(this.xCoord, this.yCoord, this.zCoord);
            }
            return;
        }

        // 自检（兜底）：这个位置已经不是"带方块实体的方块"了，就立刻作废自己。
        // 正经路径是 BlockKineticBase.breakBlock 里显式移除 TE，但爆炸、活塞、
        // 世界编辑等路径不一定走到那里；残留的 TE 会继续 tick（石磨会继续吸物品），
        // 同一格重新放方块时还会被复用、继承旧的原料/进度。
        net.minecraft.Block here = net.minecraft.Block.blocksList[world.getBlockId(this.xCoord, this.yCoord, this.zCoord)];
        if (!(here instanceof net.minecraft.ITileEntityProvider)) {
            this.invalidate();
            return;
        }

        // ★ 水车：每 10 tick 扫一次"周围有没有水"，**两边都要算**。
        //
        // 【为什么不能只放服务端】客户端靠自己的"影子网络"算转速来渲染，
        // 而 getSourceSpeed() 对水车会读 waterPowered —— 只在服务端算的话，
        // 客户端的 waterPowered 恒为 false → 影子网络算出 0 转速 →
        // **世界里水车看着正常但根本不转**。（曲柄没有这个问题，因为它的 crankTicks
        // 在客户端右键时就地设好了。）
        //
        // 26 次 getBlockMaterial 不适合每 tick 做；水车本来就慢（4~8 RPM），半秒延迟看不出来。
        net.minecraft.Block selfBlock = this.getBlockType();

        // ★ 包裹传动杆每秒叫醒一次旁边的熔炉核心（服务端专属 ✓）。
        //   目的只有一个：让**老存档**里那些"核心还没有方块实体"的熔炉也能自动成型 ✓
        //   （新搭的熔炉走 onBlockAdded 那条路，根本不需要这个 ✓）
        if (selfBlock instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft) {
            if (!cm$wakeLogged) {
                cm$wakeLogged = true;
                System.out.println("[CreateMITE] 包裹传动杆 TE 正在 tick @ " + this.xCoord + "," + this.yCoord + ","
                        + this.zCoord + " 客户端=" + world.isRemote);
            }
            if (--this.furnaceWakeCooldown <= 0) {
                this.furnaceWakeCooldown = 20;
                net.dsh.createmite.furnace.FurnaceMultiblock.wakeUp(world, this.xCoord, this.yCoord, this.zCoord);
            }
        }

        if (selfBlock instanceof net.dsh.createmite.kinetics.block.BlockWaterWheel
                || selfBlock instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel) {
            if (--this.waterScanCooldown <= 0) {
                this.waterScanCooldown = 10;
                // ★ 记住流向：MITE 的水只流几格就到尽头（变成死水），
                //   但玩家要的是"到头了也照原方向继续转"——所以只在**真的被流动水推**的时候更新方向，
                //   方向一旦定下来就保持不变；只要周围还有水（流动或静止）就继续转。
                float t = this.waterTorque();

                // 本侧方向：t==0（死水）时**保留上次方向** —— 玩家要的是"水到头了也照原方向继续转"。
                if (t > 0.0001F) this.waterDirSign = 1;
                else if (t < -0.0001F) this.waterDirSign = -1;

                // ★ 取数日志：方向符号一变就打一行（服务端，只在服务端打免得重复）。
                //   要把"水车转向"这类问题一次定死，必须看到：周围每格水的**方块 id + 元数据**
                //   和 getFlowDirection 的原始返回值 —— 光看结果永远只能猜。
                if (!this.worldObj.isRemote) {
                    int sign = (t > 0.0001F) ? 1 : (t < -0.0001F ? -1 : 0);
                    if (sign != this.wheelLastSign) {
                        this.wheelLastSign = sign;
                        this.wheelCensusDone = true;
                        System.out.println(this.wheelDebugLine(t));
                    }
                }

                if (Math.abs(t) > 0.0001F) {
                    this.waterTorque = t;
                    // ★ 方向写进 metadata（bit 3），客户端才收得到
                    if (!this.worldObj.isRemote) {
                        int meta = this.getBlockMetadata();
                        int want = t > 0.0F ? (meta | META_WATER_DIR_BIT) : (meta & ~META_WATER_DIR_BIT);
                        if (want != meta) {
                            this.worldObj.setBlockMetadataWithNotify(
                                    this.xCoord, this.yCoord, this.zCoord, want, 2);
                        }
                    }
                }
                this.waterPowered = this.hasAnyWater();
                if (!this.waterPowered) {
                    this.waterTorque = 0.0F;
                }

                // ★ 大型水车的"3x3 占位格兜底补齐"**已经从这一段搬走了**。
                //
                // 【为什么必须搬】它原来就挂在这里，而**这个分支客户端也会跑**（理由见上面那段：
                //   客户端要靠 waterPowered 解影子网络）✗ 补占位格是纯粹的方块改动，只有服务端该做。
                //   当时这里带着 !isRemote 判据，看上去没事，但"改世界"的逻辑放在一条两边都跑的分支里，
                //   安全性就完全押在那个判据上：后人一删、一挪、一复制到别处，客户端立刻开始改世界
                //   → 幽灵方块 / 不同步，而且这种 bug 极难复现。
                //   现在它和"大齿轮的正十字挡位"并列，落在下面 {@code if (world.isRemote) return;} **之后**的
                //   服务端专属区里 —— 结构上客户端根本执行不到 ✓ 见 updateEntity 末尾那段。
            }
        }

        if (world.isRemote) {
            net.dsh.createmite.kinetics.client.KineticRendererHook.ensureRegistered();
            // 客户端也自己解算一份"影子网络"：曲柄在右键时客户端就设好了 crankTicks
            // （onBlockActivated 客户端同样会被调用），所以齿轮/轴能立刻跟着转，
            // 不必等服务端算完写 metadata 再传回来（那是 1~2 tick 的延迟）。
            if (crankTicks > 0) crankTicks--;
            this.updateClientAnimation();
            return;
        }

        if (crankTicks > 0) crankTicks--;

        // ★ 大齿轮：4 个正交挡位的兜底补齐（与大型水车"每 10 tick 补一次 3x3"完全同构）。
        //
        // 【为什么需要】"放置时组装"只在放下那一瞬间跑过一次：
        //   · 老存档里已经存在的大齿轮身上没有占位格（那时还没有这套逻辑），
        //     重进世界不会再触发放置回调 → 永远停在"只占 1 格"的旧状态；
        //   · 本次改动之后"空间不够"会直接拒绝放置，但万一标记没打上（见
        //     BlockLargeCogwheel.onBlockAdded 里那条兜底日志），也要有个东西把它救回来。
        //
        // 【为什么放在客户端分支之后】补占位格是纯粹的方块改动，只有服务端该做；
        // 客户端等服务端同步方块就好（放在这里天然只有服务端会执行）。
        //
        // 【代价】已组装好的齿轮下一次就会短路：那 4 格是占位方块（不是空气）
        // → planeIsFree=false → 什么都不做，只花 4 次 getBlockId。绝不拆玩家的建筑。
        if (selfBlock instanceof net.dsh.createmite.kinetics.block.BlockLargeCogwheel) {
            int nowAxis = this.axis();

            // ① 轴向被扳手改了 → 先把**旧平面**里的 4 个占位格清掉（详见 cogAssemblyAxis 的字段注释）。
            //    必须用旧轴向去算平面：那 4 格在旧平面里，拿新轴向当然扫不到它们 ——
            //    清不掉的后果就是"看不见、撞不到、却永远占着格子"的幽灵。
            if (nowAxis != this.cogAssemblyAxis) {
                if (this.cogAssemblyAxis >= 0) {
                    net.dsh.createmite.kinetics.block.LargeCogwheelGroup.clearPlaceholders(
                            this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.cogAssemblyAxis);
                }
                this.cogAssemblyAxis = nowAxis;
                this.cogAssemblyCooldown = 0;      // 立刻按新轴向补一次
            }

            // ② 兜底补齐（新平面放不下就什么都不做，等玩家腾地方 —— 和大齿轮同一条原则：
            //    宁可降级成"只有主体、没有挡位"，也绝不顶掉玩家的建筑）。
            //    正常情况下①刚清完，新平面那 4 格是空气 → 这里当场补上，玩家只看到齿轮换了个方向 ✓。
            if (--this.cogAssemblyCooldown <= 0) {
                this.cogAssemblyCooldown = 10;
                // ★ 现在组装的是"正十字"挡位：主体 + 平面内上下左右 4 个正交邻居格
                //   （偏移表只定义在 LargeCogwheelGroup.PLANE_OFFSETS 一处）✓
                //   历史：2×2 那版被回退时，这里曾临时改成 clearPlaceholders（专门清旧版漏下的孤儿格）；
                //   现在方案换成"主体就是中心格"的正十字，组装重新打开 ✓
                //   四个对角格一个都不碰 —— 那里是小齿轮的位置，也是唯一能啮合变速的位置 ✓。
                net.dsh.createmite.kinetics.block.LargeCogwheelGroup.assemble(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord, nowAxis);
            }
        }

        // ★ 大型水车：3x3 占位格的兜底补齐 + **轴向轮询**（与大齿轮那套完全同构，两件事一起做）。
        //
        // 【为什么需要补】"放置时组装"只在放下那一瞬间跑过一次：
        //   · 老存档里已经存在的大型水车身上没有占位格（那时还没有这套逻辑），
        //     重进世界不会再触发放置回调 → 永远停在"只占 1 格"的旧状态；
        //   · 放置时空间不够（BlockLargeWaterWheel.onBlockAdded 只提示、不组装）→
        //     玩家把挡路的方块清掉之后，这里自动补齐。
        //
        // 【★ 为什么会在这里】这一段原来挂在**水车扫描分支**里，而那个分支客户端也会跑 ✗
        //   （客户端要靠 waterPowered 跑影子网络，见上面那段说明）。
        //   现在它在 {@code if (world.isRemote) return;} **之后** —— 只有服务端能走到这里，
        //   所以客户端**结构上不可能改世界**，不依赖任何单个布尔判据的存亡 ✓
        //   （LargeWaterWheelGroup.assemble 自己还有一道 isRemote 兜底，
        //     clearPlaceholders 这一道本次也补上了，见那个方法。）
        //
        // 【代价】已组装好的水车下一次就会短路：那 8 格是占位方块（不是空气）
        //   → planeIsFree=false → 什么都不做，只花 8 次 getBlockId。绝不拆玩家的建筑。
        if (selfBlock instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel) {
            int nowAxis = this.axis();

            // ① 轴向被扳手改了 → 先把**旧平面**里的 8 个占位格清掉
            //    （详见 wheelAssemblyAxis 的字段注释：扳手改轴没有任何回调，只能轮询）。
            //    必须用**旧**轴向去算平面：那 8 格在旧平面里，拿新轴向当然扫不到它们 ——
            //    清不掉的后果就是 8 格"看不见、撞不到、却永远占着格子"的幽灵。
            //    清的时候只动 blockLargeWaterWheelPlaceholder（见 removePlaceholdersInPlane），
            //    玩家的建筑一格都不碰 ✓
            if (nowAxis != this.wheelAssemblyAxis) {
                if (this.wheelAssemblyAxis >= 0) {
                    net.dsh.createmite.kinetics.block.LargeWaterWheelGroup.clearPlaceholders(
                            this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.wheelAssemblyAxis);
                }
                this.wheelAssemblyAxis = nowAxis;
                this.wheelAssemblyCooldown = 0;      // 立刻按新轴向补一次，不等下一个 10 tick
            }

            // ② 兜底补齐（新平面放不下就什么都不做，等玩家腾地方 —— 和大齿轮同一条原则：
            //    宁可降级成"只占 1 格"，也绝不顶掉玩家的建筑）。
            //    正常情况下①刚清完，新平面那 8 格是空气 → 这里当场补上，玩家只看到轮子换了个方向。
            if (--this.wheelAssemblyCooldown <= 0) {
                this.wheelAssemblyCooldown = 10;
                net.dsh.createmite.kinetics.block.LargeWaterWheelGroup.assemble(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord, nowAxis);
            }
        }

        KineticNetwork.resolve(this);
        this.syncSpeedToMetadata();
        this.spawnOverstressEffects(world);
    }

    /**
     * 应力过载的**视觉**表现：冒烟。
     *
     * 只在服务端生成粒子 —— 服务端会广播给附近玩家，单机（集成服务器）一样看得到，
     * 多人也不会各算各的。
     *
     * 【这里刻意不放声音】过载音效是"玩家摇曲柄时发现摇不动"才响一声，
     * 由 BlockHandCrank.onBlockActivated 触发。放在这里会变成"只要过载就一直呲"，
     * 那不是提示，是噪音。
     */
    private void spawnOverstressEffects(World world) {
        if (!this.overStressed) return;
        if (world.rand.nextInt(8) != 0) return;   // 每块每 8 tick 最多 1 个粒子，免得糊屏
        world.spawnParticle(net.minecraft.EnumParticle.smoke,
                (double) this.xCoord + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.6D,
                (double) this.yCoord + 1.05D,
                (double) this.zCoord + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.6D,
                0.0D, 0.02D, 0.0D);
    }

    /**
     * 服务端：把"静态信息"同步进 metadata。
     *
     * 只保留轴向与朝向这两个 bit —— 转速不进 metadata（4 bit 装不下，见上面的说明）。
     * 客户端靠自己的影子网络算转速；曲柄的 crankTicks 在客户端右键时就已经设好，
     * 因此整张网络在客户端可以完整解算，本地零网络延迟。
     */
    private void syncSpeedToMetadata() {
        // ★★ 2026-09-27 修：这里原来是 `& (META_AXIS_MASK | META_FACE_BIT)` = **只留 bit0-2** ✗，
        //   于是它**每个 tick 都把 bit3 抹掉** ✗ —— 这就是"服务端明明写了 meta=13、
        //   客户端只读到 5"的真凶（13 & 7 = 5，一位不差 ✓），水车方向位当年"同步不过去"也是被它抹的 ✓。
        //   现在 bit2/bit3 都有正经用途（装壳齿轮两端接轴开关 / 水车方向位）→ 四位一律保留 ✓。
        int meta = getBlockMetadata() & (META_AXIS_MASK | META_FACE_BIT | META_WATER_DIR_BIT);
        // 上面这个掩码现在等于 & 0xF，也就是"什么都不抹" —— 保留方法只是留着说明这段历史，
        // 免得以后有人又顺手加一个"只保留某某位"的掩码，把别人新加的位一格一格抹掉。
        if (meta == getBlockMetadata()) return;
        if (meta != getBlockMetadata()) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 2);
        }
    }

    /**
     * 客户端旋转动画。
     *
     * 转速**只**来自本地影子网络（KineticNetwork 在客户端也跑一份）：
     * 曲柄的 crankTicks 在客户端右键时就已经设好，所以整张网络在客户端能完整解算，
     * 立刻响应、零网络延迟。metadata 里没有转速（4 bit 装不下），也不再有"量化兜底"。
     * 对 clientSpeed 做指数平滑，避免曲柄动力衰减时转速跳变、看着一顿一顿。
     */
    private void updateClientAnimation() {
        KineticNetwork.resolve(this);
        float target = this.speed;
        this.clientSpeed += (target - this.clientSpeed) * 0.4F;
        this.angle += this.clientSpeed * 0.3F;
        if (this.angle > 360.0F) this.angle -= 360.0F;
        if (this.angle < -360.0F) this.angle += 360.0F;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("cm_crank", this.crankTicks);
        // ★ 水车的**转向**必须存盘！
        //   水车靠"流动的水"定方向，而 MITE 的水只流几格就到尽头（变死水），
        //   重进世界后附近多半已经没有流动水 → 力矩算出来是 0 → 方向退回默认符号，
        //   表现就是"水明明往左流，轮子却往右转；重新放一次水才恢复"。
        //   存下来之后，重进世界仍然按上次的方向转。
        nbt.setFloat("cm_water_torque", this.waterTorque);
        nbt.setBoolean("cm_water_powered", this.waterPowered);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.crankTicks = nbt.getIntegerWithDefault("cm_crank", 0);
        // ★★ 2026-09-28：**故意不把"驱动记忆"读回来** ✗
        //   力矩/有没有水都会在载入后 10 tick 内由本侧扫描重新算出来；
        //   读回旧值会让"老存档里那潭早就静止的水"继续驱动轮子 ✓（正是用户报的现象）。
        //   方向上仍然有 metadata bit3 兜底（那一位是持久化的），所以载入瞬间方向不会乱 ✓。
        this.waterTorque = 0.0F;
        this.waterPowered = false;
    }

    /**
     * ★★ 2026-09-30 修用户报的 bug：**动力方块离远了整块消失 / 直接变透明** ✗
     *
     * 【根因（javap 实证）】我们的动力方块**本体贴图是全透明的**（createmite_blank ✓），
     *   世界里那一坨几何**全靠 TESR 画** ✓；而原版 `TileEntity.getMaxRenderDistanceSquared()`
     *   默认只返回 **4096.0 = 64 格** ✗，`TileEntityRenderer.renderTileEntity(TileEntity, float)`
     *   头一句就是
     *   `if (te.getDistanceFrom(playerX, playerY, playerZ) < te.getMaxRenderDistanceSquared())`
     *   → 超过 64 格，TESR 直接不执行 ⇒ 方块本体又是透明的 ⇒ **整台机器凭空消失** ✗
     *   （水车 / 齿轮 / 大齿轮 / 粉碎轮 / 石磨 / 传动杆 / 包裹传动杆 …… 全中 ✓）
     *
     * 【修法】照原版"特殊方块实体"（信标 256×、附魔台 256×）的老办法把范围抬高 ✓ ——
     *   这里给 **64 倍 = 512 格** ✓：足够覆盖 MITE 最远的视野（Far = 16 区块 = 256 格 ✓），
     *   又不是无限大 ✗，远处照样会被剔掉，不至于把每台机器都画到天边 ✓。
     *   ⚠️ MITE 的 **大熔炉核心**是另一套（一台机器只有一个方块实体 ✓ 更便宜）→
     *     它单独给到 1024 倍 = 2048 格，见 `FurnaceCoreTileEntity` ✓。
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return super.getMaxRenderDistanceSquared() * 64.0D;
    }
}
