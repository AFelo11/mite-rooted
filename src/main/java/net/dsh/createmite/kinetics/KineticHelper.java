package net.dsh.createmite.kinetics;

import net.dsh.createmite.CMBlocks;
import net.dsh.createmite.CMConfig;
import net.minecraft.Block;
import net.minecraft.EnumFace;
import net.minecraft.ItemStack;
import net.minecraft.World;

/** 动力系统的通用规则：轴向、连接判定、换向判定、磨粉配方 */
public final class KineticHelper {

    /** 六个方向：0=-Y,1=+Y,2=-Z,3=+Z,4=-X,5=+X */
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** 对应上面的方向属于哪个轴（0=X,1=Y,2=Z） */
    public static final int[] DIR_AXIS = {1, 1, 2, 2, 0, 0};

    /** 每个轴的"正方向"是哪个方向索引（0=X → +X=5，1=Y → +Y=1，2=Z → +Z=3） */
    public static final int[] POS_DIR = {5, 1, 3};

    public static final int CREATIVE_MOTOR_OUTPUT = 3;

    /** 该轴上与 POS_DIR 相反的那个方向（用于六向循环） */
    public static int negativeDir(int axis) {
        for (int d = 0; d < 6; d++) {
            if (DIR_AXIS[d] == axis && d != POS_DIR[axis]) return d;
        }
        return POS_DIR[axis];
    }

    /** 从 metadata 还原出朝向（6 向编号） */
    public static int dirOf(int axis, boolean positive) {
        return positive ? POS_DIR[axis] : negativeDir(axis);
    }

    /**
     * 转速等级：每级 8 RPM。
     *
     * 【注意】metadata 只有 4 bit（NibbleArray 会 & 15），**转速早就不能再写进 metadata 了**
     * （见 KineticTileEntity 的说明）。这两个成员现在只是"如果将来要给转速做粗量化"时的备用，
     * 当前没有任何调用点 —— 不要再拿它去填 metadata。
     */
    public static final float RPM_PER_LEVEL = 8.0F;

    private KineticHelper() {}

    public static int axisFromFace(EnumFace face) {
        if (face == null) return 1;
        if (face.isTopOrBottom()) return 1;
        if (face.isNorthOrSouth()) return 2;
        return 0;
    }

    public static int axisFromDirIndex(int dir) {
        return DIR_AXIS[dir];
    }

    /** 反方向索引（0<->1、2<->3、4<->5 正好是成对的） */
    public static int oppositeDir(int dir) {
        return dir ^ 1;
    }

    /** dir 是否是该轴的正方向 */
    public static boolean isPositiveDir(int axis, int dir) {
        return dir == POS_DIR[axis];
    }

    public static int speedLevel(float speed) {
        int lvl = (int) (Math.abs(speed) / RPM_PER_LEVEL + 0.5F);
        if (lvl < 0) lvl = 0;
        if (lvl > 15) lvl = 15;
        return lvl;
    }

    /**
     * 这个方块的输出面是不是朝着 dir 方向。
     *
     * 轴必须对上，**方向符号**还要和 metadata 里的 facing 位一致 ——
     * 这样"沿轴的另一个方向"就被挡掉了，只剩一个面能接。
     */
    private static boolean outputsToward(KineticTileEntity te, int dir) {
        return DIR_AXIS[dir] == te.axis()
                && isPositiveDir(te.axis(), dir) == te.facePositive();
    }

    /**
     * 装壳齿轮的 dir 面是不是被扳手关掉了接轴（工程③）。
     *
     * 只有**沿自转轴的那两个面**可能被关 ✓ —— 侧面（垂直于轴的面）是齿轮啮合用的，
     * 开关碰都不碰它 ✓（原版 hasShaftTowards 里就写着 `face.getAxis() == state.getValue(AXIS)`）。
     * 不是装壳齿轮（传动杆箱、普通齿轮、别的机器）→ 永远 false ✓ 行为不变。
     */
    private static boolean shaftEndBlocked(KineticTileEntity k, int dir) {
        if (!k.hasShaftSwitches()) return false;
        if (DIR_AXIS[dir] != k.axis()) return false;
        return k.isShaftEndClosed(dir);
    }

    /**
     * 两个动力方块是否在 dir 方向上机械连接。
     *
     * 【石磨特例】石磨是"只能被齿轮带动"的机器：
     *   - 传动轴、手摇曲柄一律带不动它（真实磨盘也不是靠一根轴直接怼上去的）；
     *   - 只有**水平相邻、且齿轮轴向与石磨相同（都是竖直）**的齿轮才算啮合，
     *     也就是把齿轮平放在石磨旁边、让齿咬住磨盘边缘。
     * 这条判断必须写在"任意面接入"之前，否则会被提前放行。
     */
    public static boolean connects(KineticTileEntity a, KineticTileEntity b, int dir) {
        // ★★ 防御性前置判据（**不改任何齿轮/水车规则，只在最前面加一条"没世界就别判"**）：
        //   1.6.4/MITE 在"方块实体 tick 期间"懒创建出来的 TE 可能还没有 worldObj，
        //   而 World.getBlockTileEntity 会把它交出来（只比坐标、不比方块）——
        //   实机崩溃就是崩在本方法下面那句 b.isGearbox()（→ getBlockType() → worldObj.getBlockId → NPE）。
        //   完整的链路与两层修复见 KineticTileEntity.hasWorld() 上面那段。
        //   这里再挡一道，保证**任何调用点**（网络收集、对角探路、以后的新代码）都不可能踩到它。
        if (a == null || b == null || !a.hasWorld() || !b.hasWorld()) return false;

        // ★★ 工程③：装壳齿轮的"接轴开关"（逐条复刻原版 EncasedCogwheelBlock.hasShaftTowards）
        //   原版 RotationPropagator.getRotationSpeedModifier：
        //       connectedByAxis = 轴对齐 && hasShaftTowards(自己, dir) && hasShaftTowards(对方, 反向)
        //       → 只有**沿轴的接轴**吃这个开关 ✓；
        //   平面内的齿轮啮合（小↔小正相邻、大↔小对角 2:1 变速）走的是后面的 gear 分支，
        //   **完全不吃这个开关** ✓（所以"关掉某一端会不会把对角变速也掐了"——不会，原版也不会）。
        //   放在最前面：和原版一样，任何走 connectedByAxis 的连接（传动杆/齿轮/齿轮箱/机器）都受它管 ✓。
        if (shaftEndBlocked(a, dir) || shaftEndBlocked(b, oppositeDir(dir))) return false;

        // 复刻 Create 的 creative_motor blockstate（facing 六向，水平用 block、上下用 block_vertical）。
        // 之前它走的是下面那条通用规则（aa == dirAxis），也就是**沿自身轴两个方向都能接**，
        // 等于"两面可用"，和原版不符。
        //   朝向存在 metadata 的 轴 + 方向符号 里（放置时朝玩家、潜行反向；扳手可六向调）。

        // ---- 十字齿轮箱：只在垂直于自身轴的面接轴 ----
        // 复刻 Create 的 GearboxBlock.hasShaftTowards：dir.getAxis() != state.getValue(AXIS)
        // ★★ 齿轮箱 <-> 齿轮箱：**任意面都能对接** ✓
        //   用户实测：以前这里只允许"沿箱体自己的轴"对接 ✗ → 两台箱体并排（垂直于轴相接）
        //   完全不传动力 ✓。齿轮箱的职责本来就是**换轴**（输入一个轴、输出垂直的轴），
        //   两台串联时方向由各自箱体处理，连接判定不该再限制 ✗。
        // ★★ 2026-09-27：**只要有一方是齿轮箱就直接连通** ✓
        //   【为什么】齿轮箱的职责就是换轴，它的**六个面都是端口**（原版如此）✓：
        //   传动杆/齿轮/机器贴上来都能进、都能出 ✓，输出轴与输入轴垂直由箱体自己处理 ✓。
        //   ✘ 之前这里要求"方向必须等于齿轮箱自己的轴" ✗ → 传动杆贴着齿轮箱完全不通 ✓（用户实测）
        //   ✘ 我上一版把传动杆收紧成"双方轴都要对齐"之后，连齿轮箱↔齿轮箱也一起被掐 ✗
        //   ✔ 现在：任一方是齿轮箱 → 直接 true ✓（下面那条旧判定保留作兜底，不再起决定作用）
        // ★★ 2026-09-27 用户实测：以前**不管摆放状态对不对都能传** ✗ —— 太宽松 ✓。
        //   原版规则：齿轮箱的**端口在垂直于它自己轴向的那几个面**上，
        //   所以"接得对不对"的判据是：**连接方向上必须有一方的轴与它一致** ✓：
        //     · 传动杆/齿轮 贴着齿轮箱：**传动杆/齿轮的轴必须指向齿轮箱** ✓（状态不对就不传 ✗）
        //     · 齿轮箱 ↔ 齿轮箱：同样要求其中有箱体的轴与连接方向一致 ✓
        //   ✘ 上一版无条件 return true 是临时修法，已按用户要求收紧 ✓
        // ★★ 2026-09-27 用户实测原版：十字齿轮箱**只有垂直于它自己轴向的 4 个面是端口** ✓，
        //   **沿它轴的 2 个面不输出** ✗（用户原话："应该有 4 个输出面 + 2 个无法输出面"）。
        //   ✘ 上一版把 DIR_AXIS[dir] == a.axis() 也算合法 ✗ → 那 2 个面被放行 = 6 个输出面 ✓（用户实测）
        //   ✔ 现在：连接方向**必须垂直于齿轮箱自己的轴** ✓，并且**对方的轴要指向箱体** ✓（状态摆对才传 ✓）
        if (a.isGearbox() || b.isGearbox()) {
            KineticTileEntity box = a.isGearbox() ? a : b;
            if (DIR_AXIS[dir] == box.axis()) return false;       // 沿齿轮箱轴的两面：不输出 ✗
            KineticTileEntity other = (box == a) ? b : a;
            return other.axis() == DIR_AXIS[dir];                // 其余 4 面：对方轴要对准才传 ✓
        }

        if ((a.isGearbox() && DIR_AXIS[dir] == a.axis())
                || (b.isGearbox() && DIR_AXIS[dir] == b.axis())) {
            return false;
        }

        // ---- 离合器：通电 = 断开 ----
        if ((a.isClutch() && a.isPowered()) || (b.isClutch() && b.isPowered())) {
            return false;
        }

        // ---- 齿轮互相啮合的规则（复刻 Create 的 getRotationSpeedModifier / isLargeToSmallCog）----
        //   · 轴必须**平行** —— 垂直的两根轴不是靠齿轮咬，是靠十字齿轮箱换；
        //   · 小-小：面相邻即啮合；
        //   · 大-大：不啮合（尺寸相同、齿距对不上）；
        //   · 大-小：**面相邻不啮合**，必须对角（见 connectsDiagonal）。
        // ★★ 水车 / 大型水车：**只能沿自转轴传动力**。
        //   轴向就是轮盘的法线方向 —— 平放（轴竖直）时就是 **TOP / BOTTOM** 两个面，
        //   轴水平时就是轴两端那两个面。桨叶长在**轮缘**上，侧面/上下（相对轮盘）没有任何传动结构 ✗。
        //   （用户实测：我们的水车在非轴向的面上也能接上传动，属于错误。）
        net.minecraft.Block ab = a.getBlockType();
        net.minecraft.Block bb = b.getBlockType();
        boolean aw = ab == net.dsh.createmite.CMBlocks.blockWaterWheel
                || ab == net.dsh.createmite.CMBlocks.blockLargeWaterWheel;
        boolean bw = bb == net.dsh.createmite.CMBlocks.blockWaterWheel
                || bb == net.dsh.createmite.CMBlocks.blockLargeWaterWheel;
        if (aw || bw) {
            return DIR_AXIS[dir] == (aw ? a : b).axis();
        }

        if (a.isCog() && b.isCog()) {
            // ============ 齿轮之间的连接：逐条复刻原版 RotationPropagator.getRotationSpeedModifier ============
            // 原版跟齿轮有关的分支**只有这三条**（判定顺序也是死的）：
            //   ① connectedByAxis   —— 沿自转轴面贴面（同轴串联）→ ±1
            //   ② isLargeToSmallCog —— 大↔小：轴平行 + 同层 + **平面内对角 (±1,±1)** → −2 / −0.5
            //                          （对角那条在 connectsDiagonal 里 ✓）
            //   ③ connectedByGears  —— **小↔小**：平面内面贴面 + 轴相同 → −1
            // 另外还有一条不在这个分支里、但属于齿轮的：
            //   isLargeToLargeGear —— 两个**大**齿轮、**轴互相垂直**的异轴衔接 → ±1
            //                          （见 connectsLargeToLarge ✓）
            //
            // ✘ 我们过去"平面内 大↔小 **面贴面**也放行成 ×2"是**兜底写法**（当时大齿轮还是单格近似），
            //   和原版不符 ✓ —— 已按原版收回 ✗：
            //   原版大齿轮半径 1 格、小齿轮 0.5 格，面相邻时中心距只有 1.0，齿会直接插进对方轮盘里 ✓
            int dirAxis = DIR_AXIS[dir];
            // ① 同轴：**双方的轴都必须与连接方向一致**（原版要求两边 hasShaftTowards 都成立 ✓）。
            //    ✘ 旧写法只判了 a 一方 ✗ → 两根**互相垂直**的齿轮"首尾相接"也会被连上 ✓
            if (dirAxis == a.axis() && dirAxis == b.axis()) return true;
            // ② 轴不平行 → 面贴面一律不啮合（大↔大要靠异轴对角那条 ✓）
            if (a.axis() != b.axis()) return false;
            // ③ 平面内面贴面：**只允许小↔小** ✓（大↔小必须走对角 ✓）
            if (dirAxis == a.axis()) return false;      // 沿轴的那两格已由 ① 处理，这里只可能是平面内
            return !a.isLargeCog() && !b.isLargeCog();
        }

        if (a.needsGearDrive() || b.needsGearDrive()) {
            KineticTileEntity mill = a.needsGearDrive() ? a : b;
            KineticTileEntity other = a.needsGearDrive() ? b : a;
            // ★ 石磨 ↔ 石磨：**直接互传**。
            //   石磨中心本来就有一个齿轮，两个石磨贴在一起时那个齿轮是啮合的 ——
            //   不传动力才是错的。所以只要对面也是石磨就直接连上（谁有动力谁带谁）。
            if (other.needsGearDrive()) return true;
            return other.isCog()
                    && other.axis() == mill.axis()        // 平行啮合（两者轴都是竖直）
                    && DIR_AXIS[dir] != mill.axis();      // 连接方向垂直于轴 → 肩并肩
        }
        if (a.acceptsAnyAxis() || b.acceptsAnyAxis()) return true;  // 其它机器任意面都能接入
        int dirAxis = DIR_AXIS[dir];
        int aa = a.axis();
        int ba = b.axis();
        // ★★ 2026-09-27 用户实测 + 原版行为：传动杆**只有端对端才传动力** ✓
        //   ✘ 旧写法是 "aa == dirAxis 或 ba == dirAxis 任一成立就连" ✗ ——
        //     于是两根互相垂直、并排摆放的传动杆也连通了 = 用户说的"6 面都能传输" ✗。
        //   ✔ 原版：轴只能**沿自己的轴**首尾相接传递 ✓；垂直方向要传必须用**齿轮箱/齿轮** ✓。
        //   所以这里要求**双方的轴都与连接方向一致** ✓。
        //   （齿轮类/齿轮箱/自带齿轮的机器各有自己的分支，在更前面就返回了，不受影响 ✓）
        if (aa == dirAxis && ba == dirAxis) return true;
        return a.isCog() && b.isCog();           // 平行相邻的两个齿轮啮合
    }

    /**
     * **对角啮合**：只用于"大齿轮 ↔ 小齿轮"。
     *
     * 为什么非得对角：小齿轮齿半径约 0.5 格、大齿轮约 1.0 格，两者中心距要 ≈√2 才咬得上，
     * 而面相邻的中心距只有 1.0（齿会直接插进对方轮盘里），对角（±1, ±1）才是 √2。
     * 所以 Create 的 isLargeToSmallCog 要求：轴平行 + 沿轴偏移为 0 + 剩余两个分量都是 ±1。
     *
     * 注意：对角邻居**不在 6 邻域里**，所以 KineticNetwork 对齿轮要多探 4 个对角偏移。
     */
    public static boolean connectsDiagonal(KineticTileEntity a, KineticTileEntity b,
                                           int dx, int dy, int dz) {
        // isCogLike()：齿轮本体 **或自带齿轮的机器**（石磨等）——
        // 资料 330130 明确写了自带齿轮的元件同样适用变速规则。
        if (!a.isCogLike() || !b.isCogLike()) return false;
        if (a.isLargeCog() == b.isLargeCog()) return false;   // 必须一大一小
        if (a.axis() != b.axis()) return false;               // 轴必须平行

        int along, c1, c2;
        if (a.axis() == 0) {          // 轴 X → 垂直平面 YZ
            along = dx; c1 = dy; c2 = dz;
        } else if (a.axis() == 1) {   // 轴 Y → 垂直平面 XZ
            along = dy; c1 = dx; c2 = dz;
        } else {                      // 轴 Z → 垂直平面 XY
            along = dz; c1 = dx; c2 = dy;
        }
        if (along != 0) return false;                          // 必须在同一层
        return Math.abs(c1) == 1 && Math.abs(c2) == 1;         // 两个垂直分量都是 ±1 → 对角
    }

    /**
     * **两个大齿轮的异轴衔接**（复刻原版 {@code isLargeToLargeGear}）—— 这条我们以前完全没有 ✗。
     *
     * 原版条件：
     *   · 两个都是**大**齿轮；
     *   · 两根自转轴**互相垂直**（{@code fromAxis != toAxis}）；
     *   · 对每个轴：若它是那两根自转轴之一 → 该方向上的偏移**必须非 0**；
     *     否则（第三根轴）→ 偏移**必须为 0**。
     * 倍率见 {@link #transferLargeToLarge}（±1，1:1 只翻方向 ✓）。
     *
     * 【几何直觉】两个大齿轮"立着"咬住彼此的角：各自绕**垂直的两根轴**转，
     *   位置差正好是"两根轴各偏一格、第三轴不偏" ✓（例：X 轴大齿轮在 (0,0,0)、Z 轴大齿轮在 (1,0,1)）。
     */
    public static boolean connectsLargeToLarge(KineticTileEntity a, KineticTileEntity b,
                                               int dx, int dy, int dz) {
        if (!a.isLargeCog() || !b.isLargeCog()) return false;
        int ax = a.axis();
        int bx = b.axis();
        if (ax == bx) return false;                    // 轴必须垂直（轴平行的大↔大不啮合 ✓）
        int[] d = {dx, dy, dz};
        for (int axis = 0; axis < 3; axis++) {
            if (axis == ax || axis == bx) {
                if (d[axis] == 0) return false;        // 两根自转轴上都必须有偏移 ✓
            } else if (d[axis] != 0) {
                return false;                          // 第三根轴上不许有偏移 ✓
            }
        }
        return true;
    }

    /**
     * 大↔大 异轴衔接的倍率：复刻原版那一行
     * {@code return sourceAxisDiff > 0 ^ targetAxisDiff > 0 ? -1 : 1;} ✓
     * 即 **1:1**，方向由"两根自转轴上的偏移符号"异或决定 ✓（对称：from/to 互换结果相同 ✓）。
     */
    public static float transferLargeToLarge(KineticTileEntity from, KineticTileEntity to,
                                             int dx, int dy, int dz) {
        int[] d = {dx, dy, dz};
        return ((d[from.axis()] > 0) ^ (d[to.axis()] > 0)) ? -1.0F : 1.0F;
    }

    /**
     * 对角方向的**统一入口**：两套几何条件互斥，最多只有一条命中 ✓。
     * 网络解算与分量收集都用它，避免两处各写一遍判据（写歪一处就"看着连、其实不通" ✗）。
     */
    public static float diagonalTransfer(KineticTileEntity from, KineticTileEntity to,
                                         int dx, int dy, int dz) {
        if (connectsDiagonal(from, to, dx, dy, dz)) return transferDiagonal(from, to);   // 大↔小：−2 / −0.5
        if (connectsLargeToLarge(from, to, dx, dy, dz)) {                                // 大↔大：±1
            return transferLargeToLarge(from, to, dx, dy, dz);
        }
        return 0.0F;
    }

    /** 收集网络分量时用：两个对角条件任一成立就算连通 ✓ */
    public static boolean connectsDiagonalAny(KineticTileEntity a, KineticTileEntity b,
                                              int dx, int dy, int dz) {
        return connectsDiagonal(a, b, dx, dy, dz) || connectsLargeToLarge(a, b, dx, dy, dz);
    }

    /**
     * 对角啮合的转速倍率。
     *
     * ⏸ **变速规则已按用户要求卸载（2026-09-26）**：现在只做**换向**，倍率恒为 1（-1 = 反向）。
     *   原来这里是"大带小 ×2 / 小带大 ×0.5"✗ —— 用户指出他对原版规则的理解与我不同，
     *   要求先把变速卸掉、等他给准确规则后再重做。**在拿到规则前不要再改回 ±2/±0.5** ✗。
     *   （对角连接本身仍然连通，只是不再改变转速。）
     */
    public static float transferDiagonal(KineticTileEntity from, KineticTileEntity to) {
        // ★★ 2026-09-27 定稿（用户授权"只要能达成变速效果即可，其它自行决定"✓）：
        //   **平面内（侧面/对角）= 变速 ×2 / ×0.5、反向** ✓ ← 依据 mcmod 6589「同一平面内 2:1」
        //   这是唯一能让"逐级爬升"成立的组合 ✓：
        //     平面内 ×2  →  同轴 1:1（接力、转速不变）→  平面内再 ×2  → … ✓
        //   ✘ 曾按用户要求"反过来"（同轴变速、平面内不变速）—— 那会让链条 ×2 后立刻 ÷2，
        //     转速在 8/16 之间来回跳、永远爬不上去（日志实证）✗
        return cogRatio(from, to);
    }

    /**
     * 大↔小齿轮这一对的倍率。
     *
     * 【用户给的规则（2026-09-26），这是最终依据】
     *        大齿轮(右转)   小齿轮(左转)
     *            ↓               ↓        ← 两个齿轮各自**传动杆的朝向**
     *        小齿轮[左转]   大齿轮[右转]
     *   《这样为一组提速，**转动方向反过来则为一组降速**》
     *
     * → 也就是说：倍率由**两个齿轮传动杆的朝向关系**决定，不是"大带小永远 ×2" ✗
     *   · 朝向**同向**（两根传动杆指向一致）→ 一组**提速**：大带小 ×2、小带大 ×0.5
     *   · 朝向**相反**（齿轮翻转放置）      → 一组**降速**：大带小 ×0.5、小带大 ×2
     *   （朝向就是放置时定的 facePositive() 那一位：换个面放/用扳手翻面即可"反过来" ✓）
     * 负数 = 啮合必然**反向**。
     */
    private static float cogRatio(KineticTileEntity from, KineticTileEntity to) {
        // ★ 用户 2026-09-26 决定：**MITE 目前只需要"增速齿轮组"** ✓
        //   所以这里恒为提速，不做"翻面变降速"那一套 ✗（朝向参数保留但暂不参与）。
        //   实测校准（用户在高版本亲测）：
        //     水车 8 RPM  → 大小齿轮提速 **5 次** = 256 RPM（8→16→32→64→128→256）✓
        //     大型水车 4 RPM → **6 次** = 256 RPM（4→8→16→32→64→128→256）✓
        //   即每经过一组"大↔小"提速一倍，与下面的 ×2 一致 ✓
        boolean bigDrives = from.isLargeCog();
        return bigDrives ? -2.0F : -0.5F;
    }

    /**
     * 从 from 传到 to 的**转速倍率**（带符号）。
     *
     * 旧版本这里只有一个 boolean（"要不要反向"），为了支持大齿轮 2:1 变速，
     * 现在升级成乘数：**to 的转速 = from 的转速 × 本方法的返回值**。
     * 目前所有部件都还是 ±1（纯换向）；大齿轮进来之后，
     * 小齿轮→大齿轮给 -0.5、大齿轮→小齿轮给 -2，就落在这个函数里。
     *
     * 返回 0 表示"传不过去"。
     */
    public static float transfer(KineticTileEntity from, KineticTileEntity to, int dir) {
        if (!connects(from, to, dir)) return 0.0F;

        float sign = flips(from, to, dir) ? -1.0F : 1.0F;

        // 反转齿轮箱：通电时"它输出给别人的那一侧"反向 → 只在由它驱动别人时取反
        if (from.isGearshift() && from.isPowered()) {
            sign = -sign;
        }

        // 大齿轮 ↔ 小齿轮：**同轴**（同一根传动杆）→ 1:1 同向 ✓
        //   （原版 connectedByAxis 对普通齿轮就是 +1 ✓）
        //   平面内的 2:1 变速**只走对角**（connectsDiagonal → transferDiagonal ✓）；
        //   面贴面的大↔小已在 connects() 里被拒 ✗ → 这里不会再出现"平面内大↔小"这条路 ✓
        if (from.isCog() && to.isCog() && from.isLargeCog() != to.isLargeCog()) {
            return 1.0F;
        }

        return sign;
    }

    // ---- 应力 ----

    /**
     * 这个方块**占用**多少应力。
     *
     * 数值全部可以在 config/createmite.properties 里改。
     * 手摇曲柄提供 8 应力，而一台石磨占 4 ——
     * 所以"一台曲柄带一台磨"刚好够，两台就过载。
     */
    public static float stressImpact(Block block) {
        if (block == null) return 0.0F;
        // ★ 这里返回的是**基础应力值**，不是最终消耗 ——
        //   最终消耗 = 基础值 × |转速(RPM)|（复刻原版公式 SU(元件) × RPM = SU(总)）。
        //   数值口径：M1/M2 原来的"固定应力"是按手摇曲柄 48 RPM 定的，
        //   所以统一 **除以 48**，这样"一个曲柄正好带一个石磨"的配比完全不变，
        //   但转速一变消耗就跟着变（原版行为）。
        //   ★ 基础值的单位就是**原版的 su/RPM**（石磨 4 su/RPM 是资料里写死的），不要再除以 48。
        if (block == CMBlocks.blockShaft) return CMConfig.getFloat("stress.impact.shaft", 0.5F);
        if (block == CMBlocks.blockCogwheel) return CMConfig.getFloat("stress.impact.cogwheel", 1.0F);
        if (block == CMBlocks.blockMillstone) return CMConfig.getFloat("stress.impact.millstone", 4.0F);
        if (block == CMBlocks.blockLargeCogwheel) return CMConfig.getFloat("stress.impact.large_cogwheel", 1.5F);
        if (block == CMBlocks.blockGearbox) return CMConfig.getFloat("stress.impact.gearbox", 1.0F);
        if (block == CMBlocks.blockClutch) return CMConfig.getFloat("stress.impact.clutch", 0.5F);
        if (block == CMBlocks.blockGearshift) return CMConfig.getFloat("stress.impact.gearshift", 0.5F);
        // 粉碎轮：原版 **8 su/RPM**（资料 196534）—— 以前完全没算，等于白嫖动力 ✗
        if (block == CMBlocks.blockCrushingWheel) return CMConfig.getFloat("stress.impact.crushing_wheel", 8.0F);
        return 0.0F;   // 没登记过的方块不占应力（免得新增方块忘了配置就全网过载）
    }

    /** 这个动力源**提供**多少应力（非动力源返回 0） */
    public static float stressCapacity(Block block) {
        if (block == null) return 0.0F;
        // ★ 同样是**基础容量**：最终容量 = 基础值 × |动力源转速|。
        //   原版：曲柄 256 su @32 RPM → 基础 8；水车 256 su @8 RPM → 32；大型水车 512 su @4 RPM → 128。
        if (block == CMBlocks.blockHandCrank) return CMConfig.getFloat("stress.capacity.hand_crank", 8.0F);
        // 水车：慢但力大（Create 原版 256 / 512）—— 前期最实用的动力源
        if (block == CMBlocks.blockWaterWheel) return CMConfig.getFloat("stress.capacity.water_wheel", 256.0F / 8.0F);
        if (block == CMBlocks.blockLargeWaterWheel) return CMConfig.getFloat("stress.capacity.large_water_wheel", 512.0F / 4.0F);
        return 0.0F;
    }

    /** 连接后是否需要反向（齿轮啮合换向） */
    public static boolean flips(KineticTileEntity a, KineticTileEntity b, int dir) {
        if (a.needsGearDrive() || b.needsGearDrive()) {
            return true;   // 齿轮与磨盘啮合 → 反向转（和两个平行齿轮啮合一致）
        }
        if (a.acceptsAnyAxis() || b.acceptsAnyAxis()) return false; // 机器的接入面不换向
        int dirAxis = DIR_AXIS[dir];
        int aa = a.axis();
        int ba = b.axis();
        if (aa != ba) return true;               // 垂直啮合
        if (a.isCog() && b.isCog()) return aa != dirAxis; // 平行齿轮换向；同轴叠放不换向
        return false;
    }

    public static boolean isKinetic(World world, int x, int y, int z) {
        return world.getBlockTileEntity(x, y, z) instanceof KineticTileEntity;
    }

    /** 石磨配方（M1 版：MITE 原生材料 + 本模组的粉碎粗锌） */
    public static ItemStack grind(ItemStack in) {
        if (in == null) return null;
        int id = in.itemID;
        if (id == Block.cobblestone.blockID) return new ItemStack(Block.gravel, 1, 0);
        if (id == Block.gravel.blockID) return new ItemStack(Block.sand, 1, 0);
        if (id == Block.stone.blockID) return new ItemStack(Block.cobblestone, 1, 0);
        if (id == net.minecraft.Item.wheat.itemID) return new ItemStack(net.minecraft.Item.flour, 1, 0);
        // ★ 2026-09-28：骨头 → 骨粉 ×1 ✓（MITE 自带的"骨头 → 骨粉 ×3"合成配方已删，
        //   骨粉改成只能磨出来；石磨那边还有 30% 额外多出 1 个 ✓）
        if (id == net.minecraft.Item.bone.itemID) return new ItemStack(net.minecraft.Item.dyePowder, 1, 15);
        // ★ 2026-09-28 用户要求：**锌矿石不再走石磨**（原版也没有这条，矿石只该由粉碎轮处理）✓
        return null;
    }
}
