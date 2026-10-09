package net.dsh.createmite.kinetics;

import net.dsh.createmite.CMConfig;
import net.minecraft.TileEntity;
import net.minecraft.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 动力网络求解（v2：整分量 + 倍率 + 应力）。
 *
 * == M1 的做法有什么不够 ==
 * 老版本是"拉取式"的树式回传：从自己出发向 6 个邻居递归要转速，取第一个非 0 的就收工。
 * 这个模型只能回答"我这个方块转多快"，回答不了两件事：
 *   1. **倍率**：连线只带正负号，表达不了大齿轮的 2:1 变速；
 *   2. **应力**：应力是"整张网络求和"的概念，树式回传压根没有全网视野。
 *
 * == 现在的做法 ==
 * 每次求解以调用者所在的**整张连通分量**为单位，一次做完三件事：
 *   ① 收集分量（BFS，用 KineticHelper.connects 判定连通）
 *   ② 从动力源出发把转速**点亮**下去：每个成员转速 = 上游转速 × KineticHelper.transfer
 *   ③ 把全网"应力占用"与"应力容量"分别求和，占用 > 容量 即为过载
 *
 * 结果写回每个成员自己身上（speed / overStressed / validTick），
 * 同一 tick 里后面的成员直接命中缓存返回 —— 整张网络每 tick 只算一次，复杂度 O(N)。
 *
 * 客户端跑同一份代码（影子网络），所以停机/过载在客户端立刻可见，不需要发包。
 */
public final class KineticNetwork {

    /** 齿轮对角啮合的取数日志（每对只打一次、上限 60 行）：变速卡在哪一步一眼可见 ✓ */
    private static final java.util.Set<String> GEAR_LOG = new java.util.HashSet<String>();

    /** "更快的结果可以覆盖已点亮的值"的次数上限（每次 resolve 重置，防死循环）✓ */
    private static int overrideBudget = -1;

    private KineticNetwork() {}

    /** 求解 te 所在的整张网络，返回 te 自己的转速（RPM，带符号） */
    public static float resolve(KineticTileEntity te) {
        World world = te.getWorldObj();
        if (world == null) return 0.0F;

        long tick = world.getTotalWorldTime();
        overrideBudget = 4096;   // 每次解算重置；够用且能兜住异常情况 ✓
        if (te.validTick == tick) return te.speed;   // 本 tick 已算过（同网络里先 tick 的那个算的）

        List<KineticTileEntity> members = collect(world, te);

        // ---- ① 先把速度全部清零（下面从动力源重新点亮）----
        //   注意应力**不能在这里算** —— 应力是"基础值 × 转速"，而转速要等 BFS 才有，
        //   所以统计挪到 ③（复刻原版公式：SU(元件) × RPM = SU(总)）。
        for (int i = 0; i < members.size(); i++) {
            members.get(i).speed = 0.0F;
        }

        // ---- ② 从动力源 BFS 点亮转速 ----
        Set<KineticTileEntity> lit = new HashSet<KineticTileEntity>();
        ArrayDeque<KineticTileEntity> queue = new ArrayDeque<KineticTileEntity>();
        // ★ 多应力源合成：取**最快**的那个（资料 330130 原文：
        //   "在同一个应力网络内，如果存在两个或多个转速不同、但是旋转方向不冲突的应力源，
        //     该应力网络的转速将选取多个应力源转速中最快的一个。"）
        //   ✘ 旧实现这里直接 break; —— 只认第一个遍历到的源，
        //     曲柄(32) + 水车(8) 接同一张网时结果由"谁先被遍历到"决定 = 玩家看到的是随机 ✗
        KineticTileEntity best = null;
        float bestSpeed = 0.0F;
        for (int i = 0; i < members.size(); i++) {
            KineticTileEntity s = members.get(i);
            if (!s.isSource()) continue;
            float src = s.getSourceSpeed();
            if (src == 0.0F) continue;
            if (best == null || Math.abs(src) > Math.abs(bestSpeed) + 0.0001F) {
                best = s;
                bestSpeed = src;
            }
        }
        if (best != null) {
            // 方向冲突：资料的前提是"旋转方向不冲突"才合成。
            // 冲突时仍然按"取最快"处理（慢的那个被反向拖动），但要留一条日志，
            // 否则玩家只会看到"网络转速不对"却完全不知道为什么。
            boolean hasOpposite = false;
            for (int i = 0; i < members.size(); i++) {
                KineticTileEntity s = members.get(i);
                if (s == best || !s.isSource()) continue;
                float src = s.getSourceSpeed();
                if (src != 0.0F && (src > 0.0F) != (bestSpeed > 0.0F)) { hasOpposite = true; break; }
            }
            if (best.sourceConflict != hasOpposite) {
                best.sourceConflict = hasOpposite;
                if (!world.isRemote && hasOpposite) {
                    System.out.println("[CreateMITE] 动力源方向冲突：网络取最快 "
                            + Math.abs(bestSpeed) + " RPM，方向相反的那个源会被反向拖动 @ "
                            + best.xCoord + "," + best.yCoord + "," + best.zCoord);
                }
            }
            best.speed = bestSpeed;
            lit.add(best);
            queue.add(best);
        }
        while (!queue.isEmpty()) {
            KineticTileEntity cur = queue.poll();

            // ★★ **对角（大↔小 = 变速）必须优先于 6 邻域（正相邻 = 1:1）** ✓
            //
            // 【为什么顺序是决定性的】BFS 用 lit 去重，一个节点只会被**第一条到达它的路**点亮 ✗。
            //   用户实测：整套齿轮搭成一个方块阵列时，小齿轮往往先被旁边某条 1:1 的路点亮 ✗，
            //   于是后面这条对角（本该 ×2 / ×0.5）被 lit.contains(n) 直接跳过 → "变速规则没生效" ✗。
            //   原版语义是"**存在对角啮合就按变速算**"，所以这里把对角排到前面 ✓。
            if (cur.isCogLike()) {
                // ★ 与 collect 一致：**12 条对角全探**（跨轴那 8 条是大↔大异轴衔接用的 ✓）
                for (int k = 0; k < DIAGONALS.length; k++) {
                    int[] o = DIAGONALS[k];
                    KineticTileEntity n = neighbour(world, cur, o[0], o[1], o[2]);
                    if (n == null) continue;
                    // ★ 取数日志（每对只打一次）：变速到底卡在哪一步，一眼可见 ✓
                    //   skip = 对角邻格不是齿轮 / no=几何条件没命中 / ratio=命中了、倍率是多少
                    if (GEAR_LOG.size() < 60 && n.isCogLike()) {
                        String key = cur.xCoord + "," + cur.yCoord + "," + cur.zCoord + "->"
                                + n.xCoord + "," + n.yCoord + "," + n.zCoord;
                        if (GEAR_LOG.add(key)) {
                            float ratio = KineticHelper.diagonalTransfer(cur, n, o[0], o[1], o[2]);
                            String verdict = lit.contains(n) ? "SKIP(lit)"
                                    : (ratio != 0.0F ? "OK ratio=" + ratio : "REJECT");
                            System.out.println("[CreateMITE][GEAR] " + key
                                    + " axis=" + cur.axis() + "/" + n.axis()
                                    + " large=" + cur.isLargeCog() + "/" + n.isLargeCog()
                                    + " off=" + o[0] + "," + o[1] + "," + o[2] + " -> " + verdict);
                        }
                    }
                    // 统一入口：大↔小（−2/−0.5）与大↔大异轴（±1）都在这里面判 ✓
                    float diagMult = KineticHelper.diagonalTransfer(cur, n, o[0], o[1], o[2]);
                    if (diagMult == 0.0F) continue;
                    float diagSpeed = cur.speed * diagMult;
                    // ★ 2026-09-27 关键修复：**不再"点亮过就跳过"** ✗
                    //   用户实测"放多少组齿轮都不累积、没有任何转速变化"✗ ——
                    //   根因就是一片齿轮簇里**第二条（更快的 ×2）路径被先到的慢路径吃掉** ✓。
                    //   现在：只有**更快**才覆盖，并重新入队继续往外传 ✓ → 级数能真正叠上去 ✓。
                    if (lit.contains(n)) {
                        if (Math.abs(diagSpeed) > Math.abs(n.speed) + 0.0001F && overrideBudget-- > 0) {
                            n.speed = diagSpeed;
                            queue.add(n);
                        }
                        continue;
                    }
                    n.speed = diagSpeed;
                    lit.add(n);
                    queue.add(n);
                }
            }

            for (int d = 0; d < 6; d++) {
                KineticTileEntity n = neighbour(world, cur, d);
                if (n == null || lit.contains(n)) continue;
                float mult = KineticHelper.transfer(cur, n, d);
                if (mult == 0.0F) continue;
                n.speed = cur.speed * mult;
                lit.add(n);
                queue.add(n);
            }
        }

        // ---- ③ 应力：SU(元件) × RPM = SU(总) ----
        //   消耗 = 基础值 × |本元件转速|；动力源容量 = 基础容量 × |动力源转速|。
        //   所以"转得越快，越费动力"是天然的，不需要额外规则。
        float capacity = 0.0F;
        float impact = 0.0F;
        for (int i = 0; i < members.size(); i++) {
            KineticTileEntity m = members.get(i);
            float rpm = Math.abs(m.speed);
            impact += m.stressImpact() * rpm;
            if (m.isSource()) capacity += m.stressCapacity() * rpm;
        }

        // ---- ③.5 超速损坏 ----
        //   复刻原版："默认配置下转速上限 256 RPM，越过上限的动力元件会被破坏"。
        //   只在服务端执行（客户端是影子网络，改不了世界）。
        // ★★ 转速取数日志（2026-09-27）：每 ~2 秒把**每个成员方块的转速**打一行（最多 16 条）✓
        //   【为什么加】"变速没效果"到底是网络没算出差别、还是渲染没表现出来，
        //   光靠描述分不清 ✗。有了这行：同一条链上出现 8.0 / 16.0 / 32.0 就说明网络对 ✓；
        //   全是同一个数才说明解算被覆盖 ✗。用户不必再手动点扳手报数 ✓。
        //   ★ 2026-09-27：**两个侧都打**（服务端 S / 客户端 C 分开标）✓
        //   用户实测：画面里齿轮转速一成不变 ✗，但服务端日志显示 4.0 → -8.0（×2 正常）✓。
        //   那就必须确认"客户端自己算出来的影子网络"是不是同一个结果 ——
        //   渲染用的是客户端那份 ✗，所以只看服务端会漏掉真正的问题 ✓。
        if ((world.getTotalWorldTime() % 40L) == 0L) {
            StringBuilder sb = new StringBuilder(world.isRemote ? "[CreateMITE][SPEED-C]" : "[CreateMITE][SPEED-S]");
            int shown = 0;
            for (int i = 0; i < members.size() && shown < 16; i++) {
                KineticTileEntity m = members.get(i);
                if (m.speed == 0.0F) continue;
                sb.append(' ').append(m.xCoord).append(',').append(m.yCoord).append(',').append(m.zCoord)
                        .append('=').append(m.speed);
                shown++;
            }
            System.out.println(sb.toString());
        }

        float maxSpeed = CMConfig.getFloat("kinetics.max_speed", 256.0F);
        for (int i = 0; i < members.size(); i++) {
            KineticTileEntity m = members.get(i);
            if (Math.abs(m.speed) > maxSpeed) {
                if (!world.isRemote) {
                    // ★ 资料 330130："如果元件被加速到更高速度则会**变成掉落物**"
                    //   ✘ 旧实现走 destroyBlock —— 元件被凭空销毁，玩家捡不回来 ✗
                    //   ✔ 现在先掉成物品再移除方块：能捡回来重摆，也更符合 MITE 的硬核节奏
                    // blockOrNull()：成员都已经过 asMember 过滤，这里只是同一套判据不重复踩坑
                    net.minecraft.Block broken = m.blockOrNull();
                    if (broken != null) {
                        // MITE 的掉落入口是 BlockBreakInfo（和它的 destroyBlock 同一套），
                        // 不是原版的 dropBlockAsItem(x,y,z,meta,fortune)。
                        broken.dropBlockAsItself(new net.minecraft.BlockBreakInfo(
                                world, m.xCoord, m.yCoord, m.zCoord));
                    }
                    System.out.println("[CreateMITE] 超速掉落：" + (broken == null ? "?" : broken.getUnlocalizedName())
                            + " @ " + m.xCoord + "," + m.yCoord + "," + m.zCoord
                            + " 转速 " + m.speed + " RPM 超过上限 " + maxSpeed + " → 变成掉落物");
                    world.setBlockToAir(m.xCoord, m.yCoord, m.zCoord);
                }
                m.speed = 0.0F;
            }
        }

        // ---- ③.6 应力过载 ----
        boolean overStressed = capacity > 0.0F && impact > capacity;
        float factor = 1.0F;
        if (overStressed) {
            CMConfig.OverstressMode mode = CMConfig.overstressMode();
            if (mode == CMConfig.OverstressMode.STOP) {
                factor = 0.0F;
            } else if (mode == CMConfig.OverstressMode.SLOW) {
                factor = capacity / impact;   // 占用越多降得越狠
            }                                 // IGNORE：factor 保持 1，只标记状态
        }

        boolean wasOverStressed = te.overStressed;
        for (int i = 0; i < members.size(); i++) {
            KineticTileEntity m = members.get(i);
            m.speed *= factor;
            m.overStressed = overStressed;
            m.validTick = tick;
        }

        // 只在"状态翻转"时打一行日志 —— 玩家能据此知道是应力把网络压停了，
        // 以及到底差多少（不然只能看着机器不转干着急）。
        if (overStressed != wasOverStressed) {
            System.out.println("[CreateMITE] 动力网络" + (overStressed ? "应力过载" : "已恢复")
                    + "：占用 " + impact + " / 容量 " + capacity
                    + "，共 " + members.size() + " 个动力方块"
                    + (overStressed ? " → 按配置停转（overstress.mode）" : ""));
        }
        return te.speed;
    }

    /**
     * 对角偏移表 —— **大小齿轮是在对角啮合的**，而对角邻居根本不在 6 邻域里。
     *
     * 按轴分组，每组 4 条（就是"同一平面内的四条对角线"）：
     *   轴 X → YZ 面；轴 Y → XZ 面；轴 Z → XY 面。查表用 axis*4 .. axis*4+3。
     */
    private static final int[][] DIAGONALS = {
        {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1},   // 轴 = X
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},   // 轴 = Y
        {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0},   // 轴 = Z
    };

    private static KineticTileEntity neighbour(World world, KineticTileEntity te, int dir) {
        TileEntity n = world.getBlockTileEntity(
                te.xCoord + KineticHelper.DX[dir],
                te.yCoord + KineticHelper.DY[dir],
                te.zCoord + KineticHelper.DZ[dir]);
        return asMember(n);
    }

    private static KineticTileEntity neighbour(World world, KineticTileEntity te, int dx, int dy, int dz) {
        TileEntity n = world.getBlockTileEntity(te.xCoord + dx, te.yCoord + dy, te.zCoord + dz);
        return asMember(n);
    }

    /**
     * ★★★ **实机崩溃（2026-09-26 20:58，客户端一进世界就崩）的兜底闸门**：
     * 只有"真的挂进世界"的动力元件才允许进入动力网络。
     *
     * 【为什么 World.getBlockTileEntity 会交出半成品】MITE 在"方块实体 tick 期间"
     * （World.updateEntities 把 scanningTileEntities 置 true 的那一段，我们的 resolve 正好跑在里面）
     * 懒创建出来的 TE **不带 worldObj**：Chunk.getChunkBlockTileEntity 当场 new 一个，
     * World.setBlockTileEntity 只写坐标就把它塞进 addedTileEntityList，而
     * Chunk 那边再从 chunkTileEntityMap 读一次仍然是空的 → 返回 null →
     * World.getBlockTileEntity 于是扫 addedTileEntityList，**只比坐标不比方块**，把它交了出来。
     * 完整链路和修复见 {@link KineticTileEntity#hasWorld()} 上面那一段。
     *
     * 【为什么必须在这里挡】这种元件的 getBlockType()/getBlockMetadata() 都会解引用 worldObj，
     * 一句 isGearbox() 就是 NullPointerException → ReportedException("Ticking tile entity") → 崩游戏。
     * 挡住它的代价只是"这一格本 tick 不参与网络"，而根因那一层（工厂里当场绑定 world）
     * 已经保证它下一 tick 会以正常元件的身份回来 ✓
     */
    private static KineticTileEntity asMember(TileEntity n) {
        if (!(n instanceof KineticTileEntity)) return null;
        KineticTileEntity k = (KineticTileEntity) n;
        if (!k.hasWorld()) { noteWorldless(k); return null; }
        return k;
    }

    /** 只打一行日志（整个进程一次）：既不刷屏，又能让"跳过了一个元件"这件事留下证据 */
    private static boolean worldlessLogged = false;

    private static void noteWorldless(KineticTileEntity k) {
        if (worldlessLogged) return;
        worldlessLogged = true;
        System.out.println("[CreateMITE] 跳过了一个还没挂进世界的动力元件 @ "
                + k.xCoord + "," + k.yCoord + "," + k.zCoord
                + "（MITE 懒创建时序，见 KineticTileEntity.hasWorld 的说明）");
    }

    /** 把 start 所在的整张连通分量收集出来 */
    private static List<KineticTileEntity> collect(World world, KineticTileEntity start) {
        List<KineticTileEntity> members = new ArrayList<KineticTileEntity>();
        // ★ 起点自己也要挡一道：resolve 虽然已经判过 getWorldObj()==null，
        //   但那是"调用方"的判据；collect 是对外只认"网络成员"的地方，判据必须自洽。
        //   半成品元件 → 返回空网络（不是崩游戏），见 asMember 的说明。
        if (start == null || !start.hasWorld()) {
            if (start != null) noteWorldless(start);
            return members;
        }
        Set<KineticTileEntity> seen = new HashSet<KineticTileEntity>();
        ArrayDeque<KineticTileEntity> queue = new ArrayDeque<KineticTileEntity>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            KineticTileEntity cur = queue.poll();
            members.add(cur);
            for (int d = 0; d < 6; d++) {
                KineticTileEntity n = neighbour(world, cur, d);
                if (n == null || seen.contains(n)) continue;
                if (!KineticHelper.connects(cur, n, d)) continue;
                seen.add(n);
                queue.add(n);
            }
            // 齿轮的**全部 12 条对角偏移**都要探 ✓（以前只探了"自己轴那一组"的 4 条 ✗）：
            //   · 同一平面内的 4 条 → 大↔小 的 2:1 变速 ✓
            //   · 另外 8 条（跨轴）  → 大↔大 的"轴互相垂直"衔接 ✓（见 connectsLargeToLarge）
            if (cur.isCog()) {
                for (int k = 0; k < DIAGONALS.length; k++) {
                    int[] o = DIAGONALS[k];
                    KineticTileEntity n = neighbour(world, cur, o[0], o[1], o[2]);
                    if (n == null || seen.contains(n)) continue;
                    if (!KineticHelper.connectsDiagonalAny(cur, n, o[0], o[1], o[2])) continue;
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return members;
    }
}
