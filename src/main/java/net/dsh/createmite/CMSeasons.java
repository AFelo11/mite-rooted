package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.EntityPlayer;
import net.minecraft.World;

/**
 * MITE 四季系统（2026-09-30 开工；计划书见 `计划-四季系统.md`）。
 *
 * 【核心思想】**季节 = 世界天数的纯函数** ✓ —— 不存任何状态 ✗ →
 *   存档、重登、多人**零成本一致** ✓，连同步包都不用写 ✓。
 *   每 tick 由 `SeasonsWorldMixin` 从世界时钟刷新一次静态值，其它地方只读 ✓。
 *
 * 【用户定稿的规格（2026-09-30）】
 *   · **一年 80 天、每季 20 天** ✓
 *   · **新世界第 0 天 = 春** ✓（相位偏移默认 0 ✓）
 *   · **冬季前 3 天不结冰 → 第 4 天开始结冰 → 最后 3 天逐渐融冰** ✓
 *   · 季节之间要**平滑过渡** ✓（用余弦曲线 ✓）
 *   · 昼长随季节：**先保留设定、不实现** ✓（用户还在想 ✓）
 *   · 指令：**新增 `/se 1|2|3|4`** 调当前季节 ✓，**不碰原版看天数的指令** ✗
 *
 * 【温度偏移怎么生效（javap 实证：挂点只有一个 ✓）】
 *   `BiomeGenBase.getFloatTemperature()` 是**唯一的收口** ✓（它内部就一句 `return temperature;` ✓），
 *   而工程里引用它的只有：区块生成（雪/冰）、雾色/天空色（EntityRenderer）、雪人、
 *   还有 `getBiomeGrassColor()/getBiomeFoliageColor()`（**草色/叶色也是调它算的** ✓）——
 *   所以**改这一处，视觉+气候+草色全跟着变** ✓，不用逐个挂 ✓。
 *   ⚠️ 它是**无参**方法 ✗ → 偏移只能走静态值（就是本类每 tick 刷新的那个 ✓）。
 */
public final class CMSeasons {

    public static final int SPRING = 1;
    public static final int SUMMER = 2;
    public static final int AUTUMN = 3;
    public static final int WINTER = 4;

    /** 一年 80 天 / 每季 20 天 ✓（用户 2026-09-30 定；改这里就是改节奏 ✓） */
    public static final int DAYS_PER_SEASON = 20;
    public static final int DAYS_PER_YEAR = DAYS_PER_SEASON * 4;

    /** 冬季规则（用户定稿 ✓）：前 3 天不结冰、第 4 天起结冰、最后 3 天融冰 */
    private static final int WINTER_NO_FREEZE_DAYS = 3;
    private static final int WINTER_MELT_DAYS = 3;

    // ---- 冰情扫描（2026-09-30 用户改规格："以玩家为中心的水都上冻 / 水平面 60 层以上都冻" ✓）----
    /** 每 tick 扫几个区块（2 = 每秒 40 个区块 ✓ 温柔又不慢 ✓；调大更快也更吃性能 ✓） */
    private static final int ICE_CHUNKS_PER_TICK = 2;
    /** 只处理 y >= 这个高度的水（用户："水平面 60 层以上都冻" ✓；地下的水不动 ✓） */
    private static final int ICE_MIN_Y = 60;
    /** 从这一层往下找（1.6.4 世界高 128 ✓） */
    private static final int ICE_MAX_Y = 127;

    // ---- 每 tick 由世界刷新（静态共享：单机客户端/服务端同 JVM → 天然一致 ✓）----
    private static int dayOfYear = 0;
    private static int season = SPRING;
    private static float offset = 0.0F;
    private static long lastPassTick = Long.MIN_VALUE;

    /** `/se` 命令设的相位（把"今天"当成一年里的第几天 ✓；**只在本局有效** ✓） */
    private static int phaseOffsetDays = 0;

    private CMSeasons() {}

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    public static boolean enabled() {
        return CMConfig.getFloat("seasons.enabled", 1.0F) != 0.0F;
    }

    /** 温度偏移幅度（MITE 原版生物群系温度刻度 ✓；0.25 ≈ 能把温带推过 0.15 结冰线 ✓） */
    public static float amplitude() {
        return CMConfig.getFloat("seasons.amplitude", 0.25F);
    }

    // ------------------------------------------------------------------
    // 每 tick 刷新
    // ------------------------------------------------------------------

    /**
     * 由 `World.updateWeather()` 的 HEAD 注入调用 ✓（服务端与客户端都会走到 ✓）。
     * **只认主世界**（dimensionId == 0）✗ —— 这样在下界/地下世界时季节值不会被冲掉 ✓。
     */
    public static void update(World world) {
        // ★ 兜底：每 tick 把"生成中"计数清零 ✓
        //   （万一 provideChunk 抛异常没走到 RETURN，也不至于让季节永久失效 ✗）
        suppressDepth = 0;
        if (world == null) return;
        CMTimeSpeed.tick(world);            // ★ /S 时间流速（只在服务端推进 ✓）
        CMWeather.tick(world);              // ★ /Y 强制天气（每 tick 重新按 ✓）
        if (world.provider == null || world.provider.dimensionId != 0) return;
        if (!enabled()) {
            offset = 0.0F;
            return;
        }
        int doy = (int) Math.floorMod((long) world.getDayOfWorld() + phaseOffsetDays, (long) DAYS_PER_YEAR);
        dayOfYear = doy;
        season = doy / DAYS_PER_SEASON + 1;          // 0-19→春 20-39→夏 40-59→秋 60-79→冬 ✓
        offset = cm$offsetFor(doy);
        cm$seasonIcePass(world);
        if (world.isRemote) {
            cm$retintTick(world);      // ★ 季节色变了就把附近区块排队重建（只在客户端 ✓）
        }
    }

    /**
     * 每 tick 推进一步"重新上色" ✓ —— 季节色变了（换季 或 指令跳季 ✓）就把记账清空，
     * 然后由 {@link #cm$sweepTick} **围着玩家滚动补刷** ✓，不用再手动破坏方块 ✓。
     */
    private static void cm$retintTick(World world) {
        int g = grassColor();
        boolean changed = (lastGrassColor == Integer.MIN_VALUE
                || cm$colorDelta(g, lastGrassColor) >= RETINT_COLOR_THRESHOLD);
        lastGrassColor = g;

        // ★ 2026-10-01 修用户报的「地上雪片在渲染距离外看着没化、走近才消失」：
        //   根因是**客户端那块区块没重建**（服务端已经化了，客户端还是旧网格 ✗）。
        //   融冰期（冬季最后 3 天）把记账清掉 ⇒ 滚动补刷会**持续重建**周围的区块 ✓，
        //   于是走近时不会再有"残留的雪" ✗（用的是换季上色那套现成机制 ✓）。
        if (isWinterMeltWindow()) {
            tintStamp.clear();
        }
        if (changed) {
            tintStamp.clear();     // 换色：全部作废，接下来靠滚动补刷 ✓
        }
        cm$sweepTick(world);
    }

    /**
     * ★★ 滚动补色（2026-09-30 修用户报的"偶尔一整条区块链没上色" ✓）
     *
     * 【为什么不用"换色时排一个绝对坐标队列"了 ✗】
     *   javap 实证：`RenderGlobal.markBlocksForUpdate` 是**按当前渲染网格**
     *   （`renderChunksWide/Tall/Deep`）**取模**去找渲染器的 ✓ —— 网格外的坐标**静默忽略** ✗；
     *   而渲染网格是**跟着玩家走**的 ⇒ 一次排好的绝对坐标，排到后面几 tick 时
     *   可能已经落在网格外 ✗ ⇒ 那一小片没被重建 ⇒ 保持旧色（挖一下重建了就"补上色" ✓）
     *
     * 【现在】每 tick **按玩家当前位置**算一圈里的 3 个区块 ✓：
     *   凡是"这一季还没上过色"的就标一下并记账 ✓
     *   一圈 = (8*2+1)² = 289 个区块 ⇒ 约 5 秒走完 ✓
     *   ⇒ 就算换季那一刻某块正好在网格外 ✗，**几秒后就会自动补上** ✓
     */
    private static void cm$sweepTick(World world) {
        java.util.List players = world.playerEntities;
        if (players == null || players.isEmpty()) return;

        // ★ 2026-09-30 用户："上色能不能以玩家为中心向四周上色" ✓
        //   原来按行从左到右扫（dx 外层）⇒ 看起来像"从左往右刷" ✗
        //   现在用**由内向外的环形顺序** ✓（`cm$ringOrder` 里半径 0 的 1 个 → 半径 1 的 8 个 → … ✓）
        java.util.List<int[]> ring = cm$ringOrder(sweepRadiusChunks());
        int total = ring.size();
        int budget = sweepPerTick();
        while (budget-- > 0) {
            if (sweepCursor >= total) sweepCursor = 0;
            int[] off = ring.get(sweepCursor++);
            int dx = off[0];
            int dz = off[1];

            for (int i = 0; i < players.size(); i++) {
                Object o = players.get(i);
                if (!(o instanceof EntityPlayer)) continue;
                EntityPlayer p = (EntityPlayer) o;
                int cx = (((int) Math.floor(p.posX)) >> 4) + dx;
                int cz = (((int) Math.floor(p.posZ)) >> 4) + dz;

                if (isChunkTinted(cx, cz)) continue;      // 这一季已经真重建过了 ✓

                // 只要标到那个区块里的任意一个方块，整块就会重建 ✓
                // ⚠️ 这里**故意不盖章** ✗ —— 盖章由"区块真的重建完"那一刻来做 ✓
                //   （见 `SeasonsChunkRebuildMixin` → `stampChunkTinted` ✓）
                //   否则：万一这次标记落在渲染网格外被静默忽略 ✗，就再也不会补了 ✗✗
                world.markBlockForRenderUpdate(cx * 16, 64, cz * 16);
            }
        }
        // 兜底：记账表太大就清一次（下一圈重扫，代价很小 ✓）
        if (tintStamp.size() > 40000) tintStamp.clear();
    }

    /** 这个区块是不是**已经按本赛季重建过**了 ✓（查盖章表 ✓） */
    public static boolean isChunkTinted(int cx, int cz) {
        Integer v = tintStamp.get((((long) cx) << 32) ^ (cz & 0xFFFFFFFFL));
        return v != null && v.intValue() == season;
    }

    /**
     * 区块**真的重建完了**就盖个章 ✓（由 `SeasonsChunkRebuildMixin` 注入 `WorldRenderer.updateRenderer` 的 RETURN 调用 ✓）
     *
     * 【为什么盖章要放在"重建完成"这一刻】
     *   `markBlockForRenderUpdate` 对**渲染网格之外**的坐标是**静默忽略**的 ✗（javap 实证 ✓）。
     *   如果扫描时就直接盖章，被忽略的那块就永远不会再补 ✗✗；
     *   改成"重建完成才盖章"以后：没生效的下一圈还会再标一次 ✓，生效的一次就够 ✓。
     */
    public static void stampChunkTinted(int cx, int cz) {
        tintStamp.put((((long) cx) << 32) ^ (cz & 0xFFFFFFFFL), Integer.valueOf(season));
    }

    /** 两个颜色任一通道的最大差值（0~255 ✓） */
    private static int cm$colorDelta(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /**
     * 温度偏移曲线：**余弦**，最热在夏季正中、最冷在冬季正中 ✓
     * （春秋正中正好是 0 ✓ → 保持 MITE 原本的手感 ✓）
     */
    private static float cm$offsetFor(int doy) {
        // 冬季正中 = 第 70 天（0 基）✓ 夏季正中 = 第 30 天 ✓
        double phase = (doy - 70.0D) / (double) DAYS_PER_YEAR * 2.0D * Math.PI;
        float base = (float) (-amplitude() * Math.cos(phase));

        // ★★ 2026-09-30 用户："用**原版自带的结冰机制**冻湖面" ✓
        //   原版机制看的是**群系温度 < 结冰线(0.15)** ✓（javap: World.canBlockFreeze / BlockIce ✓）
        //   所以冬天必须把温度真正**压到结冰线以下** ✓ —— 只靠 ±0.25 的话温带(0.8)还有 0.55 ✗ 冻不上 ✓
        //   做法：冬季再叠一个"深降"，并按用户给的百分比逐日加深 ✓
        //     第 1 天 15% → 第 2 天 50% → 第 3 天 85% → 第 4 天起 100% ✓
        //   （深降 = 1.0 ⇒ 温带 0.8 + (-0.25) + (-1.0) 远低于 0.15 ⇒ 原版机制全湖冻 ✓）
        if (season == WINTER) {
            float ramp;
            int w = winterDayIndex();
            if (w <= 0) ramp = 0.15F;
            else if (w == 1) ramp = 0.50F;
            else if (w == 2) ramp = 0.85F;
            else ramp = 1.0F;
            base -= CMConfig.getFloat("seasons.winter_deep_dip", 1.0F) * ramp;
        }
        return base;
    }

    // ------------------------------------------------------------------
    // 对外读数
    // ------------------------------------------------------------------

    // ==================================================================
    // ★★ 世界生成期间"屏蔽季节偏移"（2026-09-30 用户选 A ✓）
    //
    // 【为什么】MITE 的 `ChunkProviderGenerate` 在生成区块时会读 `getFloatTemperature()`
    //   来决定**放不放雪/冰**（javap 实证：读一次存进局部变量，后面配 `Block.ice`、
    //   `World.canSnowAt`、`Block.snow` 用 ✓）—— 而我们的季节偏移正好加在那个方法上 ✗
    //   ⇒ 夏天生成的雪地群系（0.05+0.25=0.30 > 结冰线 0.15）**一片雪都不放** ✗
    //     冬天又铺得更多 ⇒ 老区块有雪、新区块没雪，看起来"怪怪的" ✓（用户报的现象 ✓）
    // 【修法】只在**生成那一下**把偏移当 0 ✓ → 世界生成 **100% 保持 MITE 原样** ✓；
    //   季节继续只管"运行时看得见的地方"（草色 / 冰雪 / 结冰判定 ✓）。
    // ⚠️ 用**计数器**而不是布尔 ✓：populate 会触发邻区块 provideChunk，是**嵌套**的 ✓
    // ==================================================================

    private static int suppressDepth = 0;

    /** 进入"世界生成"（由 SeasonsWorldGenMixin 调 ✓） */
    public static void pushGenSuppress() {
        suppressDepth++;
    }

    /** 退出"世界生成" */
    public static void popGenSuppress() {
        if (suppressDepth > 0) suppressDepth--;
    }

    /** 当前是不是在生成里（给日志/调试用 ✓） */
    public static boolean isGenerating() {
        return suppressDepth > 0;
    }

    /**
     * ★★ 2026-09-30 用户定稿：**冬天交给原版机制自己冻** ✓
     *
     * 「1. 原版自己冻吧  2. 保持屏蔽（生成期不动）」
     *
     * 做法：不再需要"前三天斜坡"✗，直接把冬季的群系温度**钳到结冰线 0.15 以下** ✓ ——
     *   于是整片世界在冬天就"相当于雪地群系" ✓，MITE 自己的 `canBlockFreeze` / `BlockIce`
     *   会按它自己的规则去冻湖面 ✓（边缘/浅水先冻、有火把的地方不冻 ✓ 全是原版行为 ✓）。
     * **最后 3 天**：把温度放回正常值（> 0.15）✓ ⇒ 原版那套化冰机制接管 ✓
     *   （配合 `SeasonsIceMeltMixin` 把冬季其余时间的 `BlockIce.melt` 拦死 ✓）。
     *
     * ⚠️ 生成期（`suppressDepth > 0`）**返回原值** ✗ —— 用户明确要求"别动世界生成规则" ✓
     */
    public static float adjustTemperature(float base) {
        if (!enabled()) return base;
        if (suppressDepth > 0) return base;                 // 生成期：原样 ✗（不动世界生成 ✓）
        if (isWinter()) {
            if (isWinterMeltWindow()) return base;          // 最后 3 天：回升 ⇒ 原版化冰 ✓
            return base < 0.05F ? base : 0.05F;             // 其余冬季：钳到结冰线以下 ✓
        }
        return base + offset;                               // 其它季节：原来的余弦偏移 ✓
    }

    public static float temperatureOffset() {
        if (!enabled()) return 0.0F;
        if (suppressDepth > 0) return 0.0F;     // ★ 生成期间：当没有四季 ✓
        return offset;
    }

    public static int dayOfYear() {
        return dayOfYear;
    }

    public static int currentSeason() {
        return season;
    }

    /** 当前是不是冬季 */
    public static boolean isWinter() {
        return season == WINTER;
    }

    /** 冬季已过几天（0 基 ✓；非冬季返回 -1 ✓） */
    public static int winterDayIndex() {
        return isWinter() ? (dayOfYear - 3 * DAYS_PER_SEASON) : -1;
    }

    /**
     * ★ 2026-09-30 用户澄清后的最终规则：「冬季前三天**逐渐结冰**，第四天开始**全都结上冰**，
     *   最后三天**逐渐溶解**」✓ —— 所以**没有"不结冰"的窗口了** ✗（我之前理解反了 ✓）
     *   这个方法保留只为兼容旧调用，永远返回 false ✓
     */
    public static boolean isWinterNoFreezeWindow() {
        return false;
    }

    /** 冬季最后 3 天：**逐渐融冰** ✓ */
    public static boolean isWinterMeltWindow() {
        int w = winterDayIndex();
        return w >= 0 && w >= DAYS_PER_SEASON - WINTER_MELT_DAYS;
    }

    /** 冬季"结冰进行时" = 整个冬季**除了最后 3 天** ✓（第 1~3 天也在结，只是"逐渐" ✓） */
    public static boolean isWinterFreezeWindow() {
        int w = winterDayIndex();
        return w >= 0 && w < DAYS_PER_SEASON - WINTER_MELT_DAYS;
    }

    // ===================== ★★ 作物四季生长（2026-10-01 用户口述定稿）=====================

    /** 作物分类（决定查哪张季节表 ✓） */
    public static final int CROP_WHEAT = 0;          // 小麦
    public static final int CROP_CARROT_ONION = 1;   // 胡萝卜 / 洋葱
    public static final int CROP_POTATO = 2;         // 马铃薯（特殊：全季可长 ✓）
    public static final int CROP_STEM = 3;           // 瓜梗（西瓜 / 南瓜）

    /** 作物四季开关（config: seasons.crops ✓）*/
    public static boolean cropsEnabled() {
        return enabled() && CMConfig.getFloat("seasons.crops", 1.0F) != 0.0F;
    }

    /**
     * ★★ 作物当季生长倍率（用户 2026-10-01 口述定稿 ✓）
     *
     * <pre>
     * | 作物        | 春   | 夏                    | 秋   | 冬        |
     * |-------------|------|-----------------------|------|-----------|
     * | 小麦        | 0.30 | 前10天0.30/后10天1.00 | 1.00 | 0（不长） |
     * | 胡萝卜·洋葱 | 1.00 | 0.40                  | 0.60 | 0（不长） |
     * | 马铃薯      | 1.00 | 1.00                  | 1.00 | 0.75      |
     * | 瓜梗        | 0.75 | 0.75                  | 1.00 | 0.60      |
     * </pre>
     *
     * ⚠️ 口径：**"最快的那一季 = ×1.0"** ✓ —— 只减速、不加超过天然的速 ✓
     *   （"3 天一熟"能不能成立取决于 MITE 的天然基线 ≈7 天 ✗ ——
     *    想整体更快要走 getGrowthBits 那条全局旋钮 ✗，不是改这里 ✓）
     *
     * ⚠️ 冬季 0 = **完全不长**；而 MITE 的 BlockCrops 在"速率 0 且耕地附近没水"时
     *   每次随机 tick 有 5% 概率**枯死** ✓ ⇒ 用户明确认同"**浇水就不枯**" ✓
     *   （这是 MITE 原有的硬核规则 ✓；想放宽就把冬季那个 0.00F 改成 0.10F ✓）
     */
    /**
     * ★★ 天然基线天数：**耕地浇了水 + 不密植 + 全光照** 时，rate=1 几天一熟 ✓
     * ⚠️ 这个数**必须实测校准** ✗ —— 它是所有"目标天数"的锚 ✓
     *   （估算是 ≈7 天 ✓ 想整体快/慢就调它，不用改任何一张表 ✓）
     */
    public static float cropBaselineDays() {
        float v = CMConfig.getFloat("seasons.crop_baseline_days", 7.0F);
        return v <= 0.0F ? 7.0F : v;
    }

    /**
     * 各作物**当季的目标天数**（≤0 = 不长 ✓；<0 = 不管它 ✓）—— 用户 2026-10-01 口述 ✓
     *
     * <pre>
     * | 作物        | 春 | 夏                    | 秋  | 冬 |
     * |-------------|----|-----------------------|-----|----|
     * | 小麦        | 10 | 前10天10 / 后10天 3   | 3   | 不长 |
     * | 胡萝卜·洋葱 | 4  | 10                    | 6.7 | 不长 |
     * | 马铃薯      | 6  | 6                     | 6   | 8  |
     * | 瓜梗        | 4  | 4                     | 3   | 5  |
     * </pre>
     */
    public static float cropTargetDays(int kind) {
        int s = season;
        int inSeason = dayOfYear % DAYS_PER_SEASON;      // 0..19 ✓
        switch (kind) {
            case CROP_WHEAT:
                if (s == SPRING) return 10.0F;
                if (s == SUMMER) return inSeason < 10 ? 10.0F : 3.0F;   // ★ 夏季剩 10 天起转快 ✓
                if (s == AUTUMN) return 3.0F;                            // ★ 一直到秋季结束 ✓
                return 0.0F;                                             //   冬：不长 ✓
            case CROP_CARROT_ONION:
                if (s == SPRING) return 4.0F;                            // ★ 主场在春天 ✓
                if (s == SUMMER) return 10.0F;
                if (s == AUTUMN) return 6.7F;
                return 0.0F;
            case CROP_POTATO:
                return s == WINTER ? 8.0F : 6.0F;                        // ★ 全季可长，冬天略慢 ✓
            case CROP_STEM:
                if (s == AUTUMN) return 3.0F;                            // ★ 秋天加成 ✓
                if (s == WINTER) return 5.0F;                            // ★ 冬天慢一点 ✓
                return 4.0F;                                             //   春 / 夏 不变 ✓
            default:
                return -1.0F;
        }
    }

    /**
     * ★★ 当季生长倍率 = **基线天数 ÷ 目标天数** ✓（用户 2026-10-01 定稿）
     *
     * 【为什么这次敢 >1 了】把 MITE 的生长判定反编译出来看清楚了 ✓：
     * <pre>
     *   Random.nextInt( (int)(25.0f / rate) + 1 ) == 0   ⇒ 这次随机 tick 长 1 个生长位
     * </pre>
     * ⇒ **rate 越大，25/rate 越小，抽中概率越高** —— rate=2 就是快一倍 ✓✓
     *   （上一版我只敢 ≤1 倍减速，所以"快季"也只能等于天然速度 ⇒ 用户实测"没有任何加速感" ✗）
     *
     * 【和 MITE 自己的加速手段相乘 ✓】`BlockCrops.getGrowthRate` 里还会读
     *   `BlockFarmland.isFertilized(meta)`（**施肥耕地** ✓）⇒ 施肥 + 当季快 = 更快 ✓
     *
     * ⚠️ 冬季返回 0 ⇒ 完全不长；而 MITE 在"速率 0 且耕地附近没水"时 5% 枯死 ✓
     *   ⇒ 用户认同的"**浇水就不枯**" ✓
     */
    public static float cropGrowthFactor(int kind) {
        if (!cropsEnabled()) return 1.0F;
        float target = cropTargetDays(kind);
        if (target < 0.0F) return 1.0F;      // 不认识的作物：别管 ✓
        if (target == 0.0F) return 0.0F;     // 当季不长 ✓
        return cropBaselineDays() / target;
    }

    public static String seasonName(int s) {
        switch (s) {
            case SUMMER: return "夏";
            case AUTUMN: return "秋";
            case WINTER: return "冬";
            default: return "春";
        }
    }

    /**
     * `/se R` 的报告 ✓（用户 2026-09-30 定稿：**只报当前季节 + 世界天数** ✓，
     * 原版 MITE 自己那套看天数/时间的指令**一个字都不碰** ✗）。
     */
    public static String reportLine(World world) {
        if (!enabled()) return "四季：已关闭（config/createmite.properties → seasons.enabled = 0）";
        int totalDays = (world == null) ? -1 : world.getDayOfWorld();
        return "当前季节：" + seasonName(season) + "季｜世界第 " + totalDays + " 天（本年第 "
                + (dayOfYear + 1) + "/" + DAYS_PER_YEAR + " 天，本季第 "
                + (dayOfYear % DAYS_PER_SEASON + 1) + "/" + DAYS_PER_SEASON + " 天）"
                + "｜温度偏移 " + String.format("%+.3f", offset)
                + (isWinter() ? "｜冰情：" + (isWinterMeltWindow() ? "融冰中" : "结冰中") : "");
    }

    /**
     * `/se 1|2|3|4` —— 把**今天**拨到该季的第 1 天 ✓（只改相位，不动世界天数 ✓，
     * 所以原版看天数的指令一点不受影响 ✓）。
     */
    public static void jumpToSeason(int target, World world) {
        if (target < SPRING || target > WINTER) return;
        int targetDoy = (target - 1) * DAYS_PER_SEASON;
        int todayDoy = (world == null) ? 0
                : (int) Math.floorMod((long) world.getDayOfWorld() + phaseOffsetDays, (long) DAYS_PER_YEAR);
        phaseOffsetDays += (targetDoy - todayDoy);
        invalidateColorCache();                 // ★ 跳季：颜色缓存作废，下一 tick 立刻重算 ✓
        System.out.println("[MITE][四季] /se " + target + " → 相位偏移变为 " + phaseOffsetDays + " 天");
    }

    public static int phaseOffsetDays() {
        return phaseOffsetDays;
    }


    // ==================================================================
    // 四季颜色（用户 2026-09-30 指定 ✓）
    //   春 = 草原群系、夏 = 雨林群系、秋 = 热带草原、冬 = 雪地群系 ✓
    //   ⚠️ 1.6.4 里**没有"热带草原"(savanna) 这个群系** ✗（它是 1.7 才加的 ✓）
    //      → 秋天先拿 **沙漠**（干黄）当替代 ✓，用户要换随时说 ✓
    //   ⚠️ 目标色**不能**调 `biome.getBiomeGrassColor()` ✗ —— 那个方法被我们自己 mixin 了，
    //      会绕回季节色 ⇒ 自己吃自己 ✗✗。所以这里**照抄原版公式**直接从颜色表取：
    //      `ColorizerGrass.getGrassColor(clamp(biome.temperature), clamp(biome.rainfall))` ✓
    //      注意用**原始字段 `biome.temperature`** ✗ 而不是 getFloatTemperature()（那个带季节偏移 ✗）
    // ==================================================================

    private static final int[] seasonGrassColors = new int[4];
    private static final int[] seasonFoliageColors = new int[4];
    private static boolean seasonColorsReady = false;

    /** 每个季节对应的目标群系 ✓ */
    private static net.minecraft.BiomeGenBase cm$seasonBiome(int season) {
        switch (season) {
            case SUMMER: return net.minecraft.BiomeGenBase.jungle;
            case AUTUMN: return net.minecraft.BiomeGenBase.desert;
            case WINTER: return net.minecraft.BiomeGenBase.icePlains;
            default:     return net.minecraft.BiomeGenBase.plains;
        }
    }

    private static void cm$prepareSeasonColors() {
        if (seasonColorsReady) return;
        seasonColorsReady = true;
        for (int s = 1; s <= 4; s++) {
            net.minecraft.BiomeGenBase b = cm$seasonBiome(s);
            if (b == null) continue;
            double t = cm$clamp01(b.temperature);
            double r = cm$clamp01(b.getFloatRainfall());
            seasonGrassColors[s - 1] = net.minecraft.ColorizerGrass.getGrassColor(t, r);
            seasonFoliageColors[s - 1] = net.minecraft.ColorizerFoliage.getFoliageColor(t, r);
        }
    }

    private static double cm$clamp01(float v) {
        if (v < 0.0F) return 0.0D;
        if (v > 1.0F) return 1.0D;
        return (double) v;
    }

    // ------------------------------------------------------------------
    // ★★ 颜色缓存（2026-09-30 用户定稿："1. 缓存季节色 ✓ 2. 阶跃换色 ✓"）
    //
    // 【为什么要缓存】原来 `grassColor()` 是**每次调用都算一遍** ✗ ——
    //   而它被"每个草方块、每片树叶"各调一次 ⇒ 一个区块上千次 ⇒ 拖慢**所有**区块重建 ✗
    //   ＋每算一次都 new 一个数组 ⇒ GC 垃圾 ✓（用户问性能时我们摊开算过 ✓）
    //   → 现在**只在季节变了才重算一次** ✓，其余时间直接返回缓存的 int ✓
    //     （每方块 0 次计算、0 次分配 ✓）
    //
    // 【阶跃换色】一季之内颜色**恒定** ✓，到季节交界**直接跳到下一季的配色** ✓
    //   —— 好处：一年只触发 4 次"区块重建" ✓（平滑过渡那版是一年 ~36 次 ✗，差 9 倍 ✓）
    //   想换回平滑过渡：把下面改成"按天插值"即可（当初的写法在 git/交接文档里 ✓）
    // ------------------------------------------------------------------

    private static int cachedGrass = 0xFFFFFF;
    private static int cachedFoliage = 0xFFFFFF;
    private static int cachedForSeason = -1;

    /** 只有季节真的换了（或指令跳季 ✓）才重算一次 ✓ */
    private static void cm$ensureColorCache() {
        if (cachedForSeason == season) return;                 // 没换季：白拿 ✓
        cachedForSeason = season;
        cm$prepareSeasonColors();
        cachedGrass = seasonGrassColors[season - 1];
        cachedFoliage = seasonFoliageColors[season - 1];
    }

    /** 指令跳季 / 换世界之后强制重算一次 ✓ */
    public static void invalidateColorCache() {
        cachedForSeason = -1;
    }

    /** 当前草色（全世界统一按季节走 ✓ —— 用户就是这么指定的 ✓） */
    public static int grassColor() {
        cm$ensureColorCache();
        return cachedGrass;
    }

    /** 当前叶色（同上 ✓） */
    public static int foliageColor() {
        cm$ensureColorCache();
        return cachedFoliage;
    }

    // ==================================================================
    // 气候作用半径（用户 2026-09-30 指定 ✓）
    //   「P2 至于气候的判定［**以玩家为中心 1000 格**（一千格为以玩家为中心的圆半径）］」✓
    //   ⚠️ 只有**拿得到坐标**的挂点才能真的判半径 ✓（canBlockFreeze / canSnowAt / 结冰融冰 pass ✓）；
    //      像"温度读取""草色"这种**无参**挂点没法判 ✗ —— 但它们只影响玩家附近看到的东西 ✓，
    //      实际效果等价 ✓（已跟用户说明 ✓）
    // ==================================================================

    // ==================================================================
    // 换季"重新上色"（2026-09-30 用户报的 bug ✓）
    //   用户原话：「现在确实会上色了，但是**需要我手动破坏一下该区块**，该区块才能上色
    //             ［要不然色不变］」✓
    // 【为什么】草色/叶色是**烘进区块网格的顶点色**里的 ✗（建网格那一刻就定死了 ✓），
    //   季节色变了以后，已经建好的区块**不会自己重画** ✗ —— 破坏一个方块会让那个区块重建，
    //   所以"手动破坏一下才变色" ✓（用户看到的正是这个 ✓）。
    // 【做法】本帧颜色和"上次实际用的颜色"差得够多（或跳季导致突变 ✓）就把
    //   **玩家周围 6 个区块**排进队列 ✓，每 tick 只重建 6 个区块 ✓（不卡帧 ✓）。
    // ⚠️ 只在**客户端**做 ✓（服务端不画区块 ✗）。
    // ==================================================================

    /** 颜色差多少才值得重建（0~255 的任一通道 ✓） */
    private static final int RETINT_COLOR_THRESHOLD = 6;
    /**
     * 每 tick 补刷几个区块（★ 2026-09-30 用户："上色速度不够，再快些" → 3 → **8** ✓）
     *
     * 一圈 = (2*半径+1)² 个区块；半径 8 ⇒ 289 个、每 tick 8 个 ⇒ 约 **1.8 秒**刷完一圈 ✓
     * ⚠️ 每个区块每季只重建一次（有记账 ✓），所以这只是一次性开销 ✓；
     *   但**数值越大 = 每 tick 重建越多 = 越容易掉帧** ✗ —— 卡就往小调 ✓
     */
    private static int sweepPerTick() {
        int v = (int) CMConfig.getFloat("seasons.retint_per_tick", 15.0F);   // 2026-09-30 用户定：15 ✓
        if (v < 1) v = 1;
        if (v > 32) v = 32;
        return v;
    }

    /**
     * 滚动补色的半径（区块 ✓；8 区块 = 128 格 ✓）
     * ⚠️ 别调太大 ✗ —— 一圈走完的时间 ≈ 边长² / 每 tick 预算 ✓
     */
    private static int sweepRadiusChunks() {
        int v = (int) CMConfig.getFloat("seasons.retint_radius_chunks", 15.0F);   // 2026-09-30 用户定：15 ✓
        if (v < 2) v = 2;
        if (v > 24) v = 24;
        return v;
    }

    private static int lastGrassColor = Integer.MIN_VALUE;
    private static int sweepCursor = 0;
    private static int iceCursor = 0;
    /** 已经处理过活的"冬季第几天" ✓（-1 = 还没跑过 ✓）—— 一天只推进一次 ✓ */
    private static int lastIceDay = -1;
    /** "由内向外"的区块顺序表（缓存 ✓ 半径变了才重建 ✓） */
    private static java.util.List<int[]> ringList = null;
    private static int ringListRadius = -1;
    /** 记"这个区块已经按第几季上过色" ✓（防止反复重建 ⇒ 卡顿 ✗） */
    private static final java.util.HashMap<Long, Integer> tintStamp = new java.util.HashMap<Long, Integer>();


    // ==================================================================
    // ★★ 四季昼夜时长（用户 2026-10-01 定稿 ✓）
    //   一天仍然固定 24000 tick（20 分钟 ✓），只把**四段长度**按季节改 ✓：
    //     春 昼8.5/夜8.5 + 晨昏各1.5 ✓｜夏 11/6 + 1.5 ✓｜秋 8/8 + 2 ✓｜冬 6/11 + 1.5 ✓
    //   （1 分钟 = 1200 tick ✓，四段和必须正好 24000 ✓）
    // 【实现】把"世界时间 → 一天中的时刻"**分段重标定** ✓：用 MITE 自己的三个锚点
    //   （日出/日落/睡觉 ✓）把一天切成 晨/昼/昏/夜 四段 ✓，每段内部线性拉伸到当季长度 ✓
    //   ⇒ 太阳走得慢/快、判昼夜、刷怪、光照全跟着变 ✓。
    //   ⚠️ 锚点**只抓一次并缓存** ✗（不能边改边读，会自己吃自己 ✗）
    // ==================================================================

    /** 当季四段长度（tick ✓）：{晨昏, 白天, 晨昏, 夜晚}，和 = 24000 ✓ */
    private static final int[][] DAY_PHASES = {
        {1800, 10200, 1800, 10200},   // 春：1.5 / 8.5 / 1.5 / 8.5 分钟 ✓
        {1800, 13200, 1800,  7200},   // 夏：1.5 / 11  / 1.5 / 6   分钟 ✓
        {2400,  9600, 2400,  9600},   // 秋：2   / 8   / 2   / 8   分钟 ✓
        {1800,  7200, 1800, 13200},   // 冬：1.5 / 6   / 1.5 / 11  分钟 ✓
    };

    private static int rawSunrise = -1;
    private static int rawSunset = -1;
    private static int rawSleeping = -1;

    private static void cm$ensureRawAnchors() {
        if (rawSunrise >= 0) return;
        try {
            rawSunrise = World.getTimeOfSunrise();
            rawSunset = World.getTimeOfSunset();
            rawSleeping = World.getTimeOfSleeping();
            System.out.println("[MITE][四季][昼夜] 锚点：日出=" + rawSunrise + " 日落="
                    + rawSunset + " 睡觉=" + rawSleeping + " ✓");
        } catch (Throwable t) {
            rawSunrise = 0; rawSunset = 12000; rawSleeping = 13000;
        }
    }

    /**
     * 取"**原始**一天中的 tick" ✓ —— 绕开我们自己的挂点 ✗（否则会自己吃自己 ✓）
     * 用 `WorldInfo.getWorldTotalTime(0)` 取总时间再取模 ✓，这条路上没有我们的注入 ✓
     */
    public static int rawTimeOfDayNow(World world) {
        try {
            long total = world.getWorldInfo().getWorldTotalTime(0);
            int t = (int) (total % 24000L);
            return t < 0 ? t + 24000 : t;
        } catch (Throwable t) {
            return lastRaw;
        }
    }

    private static int lastRaw = 0;

    /** 最近一次看到的**原始**时刻（给天体角度换算用 ✓） */
    public static int lastRawTimeOfDay() {
        return lastRaw;
    }

    /** 把"原始的一天中 tick"重映射成"按季节拉伸后的一天中 tick" ✓ */
    public static int warpTimeOfDay(int raw) {
        lastRaw = raw;
        if (!enabled()) return raw;
        cm$ensureRawAnchors();
        int t = raw % 24000;
        if (t < 0) t += 24000;

        // ★ 2026-10-01 修正：MITE 的真实结构（日志实证 ✓）
        //   日出=5000、日落=19000 ⇒ **白天 = [5000,19000) = 14000 tick（11.67 分）** ✓
        //   **夜晚 = [19000, 5000) = 10000 tick（8.33 分）** ✓
        //   ⇒ 只按"白天段 / 夜晚段"两段来拉伸 ✓（不要把夜晚拆成两半 ✗ 那会出现"夜里黑两次" ✗）
        //   当季"白天"目标 = 用户给的白天 + 两段晨昏 ✓（晨昏本来就在日出/日落前后 ✓，跟着一起伸缩 ✓）
        int[] len = DAY_PHASES[season - 1];
        int newDay = len[1] + 2 * len[0];        // 白天段（含两头晨昏 ✓）
        int newNight = len[3];                   // 夜晚段 ✓
        if (newDay + newNight != 24000) newNight = 24000 - newDay;   // 兜底 ✓

        int dayLen = rawSunset - rawSunrise;                 // 原始白天 14000 ✓
        if (dayLen <= 0 || dayLen >= 24000) dayLen = 14000;
        int nightLen = 24000 - dayLen;                       // 原始夜晚 10000 ✓

        // 把 t 归一到"相对日出的偏移" ✓（夜晚跨越 0 点，这样处理不会拆开 ✓）
        int rel = t - rawSunrise;
        if (rel < 0) rel += 24000;

        if (rel < dayLen) {
            float f = rel / (float) dayLen;
            int warped = Math.round(f * newDay);
            return (rawSunrise + warped) % 24000;
        }
        float f = (rel - dayLen) / (float) nightLen;
        int warped = newDay + Math.round(f * newNight);
        return (rawSunrise + warped) % 24000;
    }

    /** ★ 2026-10-01 新增：当季"白天段"长度（tick ✓，含两头晨昏 ✓）—— 昼夜温差要用 ✓ */
    public static int seasonDayLength() {
        int[] len = DAY_PHASES[season - 1];
        return len[1] + 2 * len[0];
    }

    /** ★ 当季"夜晚段"长度（tick ✓） */
    public static int seasonNightLength() {
        return DAY_PHASES[season - 1][3];
    }

    /** ★ 原始日出时刻（tick ✓，懒加载一次并缓存 ✓） */
    public static int rawSunriseTick() {
        cm$ensureRawAnchors();
        return rawSunrise;
    }

    /** 当季昼夜长度报告（给日志与核对用 ✓） */
    public static String dayLengthReport() {
        int[] len = DAY_PHASES[season - 1];
        return seasonName(season) + "季：白天 " + (len[1] / 1200.0F) + " 分 ｜ 夜晚 "
                + (len[3] / 1200.0F) + " 分 ｜ 晨昏各 " + (len[0] / 1200.0F) + " 分 ✓";
    }

    /** 气候作用半径（格 ✓） */
    public static final double EFFECT_RADIUS = 1000.0D;

    /** 给定坐标是不是在"季节气候生效范围"内（以**玩家**为圆心 ✓） */
    public static boolean withinEffectRadius(World world, int x, int z) {
        if (world == null) return false;
        java.util.List players = world.playerEntities;
        if (players == null || players.isEmpty()) return true;      // 没玩家：按原版来 ✓
        double r2 = EFFECT_RADIUS * EFFECT_RADIUS;
        for (int i = 0; i < players.size(); i++) {
            Object o = players.get(i);
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - (x + 0.5D);
            double dz = p.posZ - (z + 0.5D);
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    /**
     * `/se R <数字>` —— 把**今天**拨到"**当前季节的第 n 天**" ✓（季节本身不变 ✓）。
     *
     * 用户 2026-09-30 追加原话：「/se R ［数字］，可以改变当前季节的天数，
     *   例如从季节第一天改到第 13 天」✓
     *
     * 实现还是**只改相位**（不动世界天数 ✗ ✓）：把今年的第几天挪到
     * `(当前季 - 1) * 20 + (n - 1)` 即可 ✓。
     */
    public static void jumpToDayOfSeason(int dayInSeason, World world) {
        if (dayInSeason < 1 || dayInSeason > DAYS_PER_SEASON) return;
        int seasonStart = (season - 1) * DAYS_PER_SEASON;          // 本季第一天（本年第 0 基 ✓）
        int targetDoy = seasonStart + (dayInSeason - 1);
        int todayDoy = (world == null) ? 0
                : (int) Math.floorMod((long) world.getDayOfWorld() + phaseOffsetDays, (long) DAYS_PER_YEAR);
        phaseOffsetDays += (targetDoy - todayDoy);
        invalidateColorCache();                 // ★ 同上 ✓
        System.out.println("[MITE][四季] /se R " + dayInSeason + " → 相位偏移变为 " + phaseOffsetDays + " 天");
    }

    /** `/se 9` —— 清掉相位偏移，回到"按世界天数"的配置默认 ✓ */
    public static void clearPhase() {
        phaseOffsetDays = 0;
        System.out.println("[MITE][四季] 相位偏移已清除 ✓");
    }

    // ------------------------------------------------------------------
    // 冰：结冰 / 融冰 pass（只在玩家附近随机采样 ✓ 便宜且"逐渐" ✓）
    // ------------------------------------------------------------------

    /**
     * 冬季结冰 / 融冰（2026-09-30 按用户新规格重写 ✓）
     *
     * 【★★ 之前"完全不结冰"的根因】这个 pass 原来在**客户端也跑** ✗ ——
     *   客户端 `WorldClient` 的 `markBlockForRenderUpdate`/`setBlock` 只在本地改 ✗，
     *   下一帧就被服务端同步覆盖 ✗（而且客户端先 tick，把共享的节流时间戳也吃掉了 ✗）
     *   ⇒ 玩家眼里"水一点都不冻" ✓。**现在只在服务端做** ✓（`world.isRemote` 直接返回 ✓）。
     *
     * 【扫描方式】从"玩家周围随机采样 28 格"✗ 改成**按区块逐列扫** ✓：
     *   每 tick 取 2 个区块（由内向外的环形顺序 ✓），逐列从 y=127 往下找**最高的那一层方块** ✓，
     *   是**露天**的水（含水流水 ✓）就冻 ✓、是冰块就化 ✓，且只处理 **y >= 60** ✓
     *   （用户指定："水平面 60 层以上都冻" ✓ —— 地下的水一律不动 ✓）。
     *
     * 【性能】一个区块 256 列、每列最多 ~68 次读 ⇒ 约 1.7 万次方块读取（~0.2 ms ✓），
     *   每 tick 2 个区块 ⇒ 每秒约 40 个区块 ✓（默认半径 12 区块 = 625 格区块 ⇒ 约 16 秒扫完一遍 ✓）
     */
    private static void cm$seasonIcePass(World world) {
        if (world.isRemote) return;
        // ★★ 2026-09-30 用户："我要你用**原版自带的结冰机制**结冰，而不是直接填充" ✗
        //   ⇒ 这个"逐个塞冰块"的 pass **整个停用** ✓（保留代码 + 诊断，方便以后回看 ✓）
        //   改由**温度**驱动 MITE 自己的结冰机制（见 cm$offsetFor 里的冬季深降 ✓）
        if (true) return;
        if (!isWinter()) return;
        boolean freeze = isWinterFreezeWindow();
        boolean melt = isWinterMeltWindow();
        if (!freeze && !melt) return;                      // 冬季前 3 天什么都不做 ✓

        // ★★ 2026-09-30 用户："你反复检测湖面反复填充！完全背离自然规律" ✗
        //   → 改成**每个冬季日只推进一次** ✓：当天该冻的冻完（一圈扫完就停），
        //     然后**一整天不再碰湖面** ✓，直到第二天（或进入融冰日）才开始下一轮 ✓
        //     这样湖面是"每天往外长一圈"，而不是被反复扫描反复补 ✗
        int wDay = winterDayIndex();
        if (!world.isRemote && wDay == lastIceDay) return;     // 今天已经干过活了 ✓
        lastIceDay = wDay;
        iceCursor = 0;                                         // 新的一天：从头扫一圈 ✓

        java.util.List players = world.playerEntities;
        if (players == null || players.isEmpty()) return;
        java.util.List<int[]> ring = cm$ringOrder(iceRadiusChunks());
        if (ring.isEmpty()) return;

        int budget = ICE_CHUNKS_PER_TICK;
        while (budget-- > 0) {
            if (iceCursor >= ring.size()) iceCursor = 0;
            int[] off = ring.get(iceCursor++);
            for (int i = 0; i < players.size(); i++) {
                Object o = players.get(i);
                if (!(o instanceof EntityPlayer)) continue;
                EntityPlayer p = (EntityPlayer) o;
                int cx = (((int) Math.floor(p.posX)) >> 4) + off[0];
                int cz = (((int) Math.floor(p.posZ)) >> 4) + off[1];
                if (!world.blockExists(cx << 4, ICE_MIN_Y, cz << 4)) continue;   // 没加载就跳过 ✓
                iceChunkScanCount++;
                cm$processIceChunk(world, cx, cz, freeze);
            }
        }
        cm$iceDiagnostics(world, freeze);            // ★ 每 30 秒在日志里报一次战果 ✓
    }

    // ==================================================================
    // ★★ "逐渐结冰"怎么实现（2026-09-30 用户："前三天应该逐渐结冰，现在是直接补满了" ✗）
    //
    // 【思路】给每一格水算一个**固定不变的随机数** `h(x,y,z) ∈ [0,1)` ✓，
    //   再按"冬季进度"决定门槛：
    //     冬季第 1 天 → h < 1/3 的格子冻 ✓（约三分之一，一片一片的）
    //     冬季第 2 天 → h < 2/3 ✓
    //     冬季第 3 天 → h < 1   ⇒ **全冻** ✓
    //     第 4 天起   → 满分 ✓（用户："第四天开始全都结上冰" ✓）
    //   融冰期反过来：最后 3 天依次化掉 1/3、2/3、全部 ✓
    // 【为什么用哈希而不是随机数】同一个坐标每次都得到同一个值 ✓ ⇒
    //   已经冻上的不会化、没冻的也不会闪 ✓（而且每格只处理一次就够 ✓）
    // ==================================================================

    /** 稳定哈希 → [0,1) ✓（同一个坐标永远同一个值 ✓） */
    private static float cm$hash01(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    /**
     * 结冰进度（用户 2026-09-30 定稿 ✓）：第 1 天 15% → 第 2 天 50% → 第 3 天 85% → 第 4 天起 100% ✓
     * 这里的"百分比"= **从岸边往湖心推进了多远** ✓（见 `cm$shoreDistance` ✓）
     */
    public static float freezePct() {
        int w = winterDayIndex();
        if (w < 0) return 0.0F;
        if (w == 0) return 0.15F;
        if (w == 1) return 0.50F;
        if (w == 2) return 0.85F;
        return 1.0F;                                          // 第 4 天起：全冻 ✓
    }

    /** 融冰进度（用户定稿 ✓）：倒数第 3 天 25% → 倒数第 2 天 75% → 最后 1 天 100% ✓（**从湖心往外化** ✓） */
    public static float meltPct() {
        int w = winterDayIndex();
        int start = DAYS_PER_SEASON - WINTER_MELT_DAYS;        // 17 ⇒ 冬季第 18/19/20 天 ✓
        if (w < start) return 0.0F;
        int step = w - start;                                  // 0/1/2 ✓
        if (step == 0) return 0.25F;
        if (step == 1) return 0.75F;
        return 1.0F;
    }

    /** 离岸距离的上限（格 ✓）—— 4 个方向各最多探这么多格 ✓ */
    private static final int MAX_SHORE_DIST = 16;

    /**
     * ★★ 这一格水面**离岸边有多远**（0 = 紧贴岸边 ✓，越大越靠湖心 ✓）
     *
     * 【为什么要它】用户要的是"**像原版一样从湖的四周往中心逐渐结冰**" ✓，
     *   而不是"按坐标随机撒冰块" ✗ —— 所以要用"离岸距离"当进度尺 ✓：
     *     结冰：`距离 <= 上限 * 结冰进度` ⇒ 冰从**岸边一圈圈往里长** ✓
     *     融冰：`距离 >= 上限 * (1 - 融冰进度)` ⇒ 冰从**湖心一圈圈往外化** ✓（用户指定 ✓）
     *
     * 【怎么算】从这一格朝**东西南北**各走，数连续的水/冰格数 ✓，
     *   取四个方向里**最短**的那个 ✓ —— 那就是"离最近的岸边还有多远" ✓
     *   （最多探 16 格 ✓ 够用又便宜 ✓；四个方向 × 16 = 最多 64 次读方块 ✓）
     */
    private static int cm$shoreDistance(World world, int x, int y, int z) {
        int best = MAX_SHORE_DIST;
        best = Math.min(best, cm$runLength(world, x, y, z, 1, 0));
        best = Math.min(best, cm$runLength(world, x, y, z, -1, 0));
        best = Math.min(best, cm$runLength(world, x, y, z, 0, 1));
        best = Math.min(best, cm$runLength(world, x, y, z, 0, -1));
        return best;
    }

    /** 朝 (dx,dz) 方向连续有多少格水/冰（含自己 ✓，最多 MAX_SHORE_DIST ✓） */
    private static int cm$runLength(World world, int x, int y, int z, int dx, int dz) {
        int n = 0;
        for (int i = 0; i < MAX_SHORE_DIST; i++) {
            int id = world.getBlockId(x + dx * i, y, z + dz * i);
            if (id != Block.waterStill.blockID && id != Block.waterMoving.blockID
                    && id != Block.ice.blockID) {
                break;                                   // 碰到岸（或别的方块）就停 ✓
            }
            n++;
        }
        return n;
    }

    /** 找这一列里**最高的水/冰**（允许上面盖着雪层 ✓ 找不到返回 -1 ✓） */
    private static int cm$findWaterOrIceY(World world, int x, int z) {
        for (int y = ICE_MAX_Y; y >= ICE_MIN_Y; y--) {
            int id = world.getBlockId(x, y, z);
            if (id == Block.waterStill.blockID || id == Block.waterMoving.blockID
                    || id == Block.ice.blockID) {
                return y;
            }
        }
        return -1;
    }

    /** 扫一个区块的 256 列：只处理**露天的水面/冰面** ✓，且只看 y >= ICE_MIN_Y ✓ */
    private static void cm$processIceChunk(World world, int cx, int cz, boolean freeze) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                // ★★ 2026-09-30 修"完全不结冰"：**不能再"看到第一个非空气就 break"** ✗✗
                //   冬天下雪时，湖面会被**雪层**盖住 ⇒ 那一列最高的方块是雪 ✗ ⇒ 旧写法直接跳过 ✗
                //   → 现在往下**一直找到水/冰**为止 ✓（只在 y >= ICE_MIN_Y 范围内 ✓）
                int wy = cm$findWaterOrIceY(world, x, z);
                if (wy < 0) continue;
                int id = world.getBlockId(x, wy, z);
                // ★★ 2026-09-30 二修：**去掉 canBlockSeeTheSky 这道条件** ✗✗
                //   诊断日志实证：pass 每 30 秒能扫到 1.5 万列"有水/冰"，却**一块都没冻** ✗
                //   ⇒ 卡在这道条件上 ✓：冬天下雪时水面盖着**雪层** ⇒ 那格"看不到天空" ✗
                //     （而我上一版又刚改成"透过雪层去找水" ⇒ 找到的水全都过不了这道闸 ✗✗）
                //   用户给的规则本来就是"**水平面 60 层以上都冻**" ✓ —— 那就只按 y 判 ✓
                // ★★ 按"离岸距离"筛 ⇒ 冰从岸边往湖心一圈圈长 / 从湖心往岸边一圈圈化 ✓
                int shore = cm$shoreDistance(world, x, wy, z);
                if (freeze) {
                    int limit = Math.round(MAX_SHORE_DIST * freezePct());     // 15%→2格、50%→8格、85%→14格、100%→全部 ✓
                    if ((id == Block.waterStill.blockID || id == Block.waterMoving.blockID)
                            && shore <= limit) {
                        world.setBlock(x, wy, z, Block.ice.blockID);
                        iceFreezeCount++;
                    }
                } else if (id == Block.ice.blockID) {
                    int need = Math.round(MAX_SHORE_DIST * (1.0F - meltPct()));  // 25%→12格、75%→4格、100%→0（全化）✓
                    if (shore >= need) {
                        world.setBlock(x, wy, z, Block.waterStill.blockID);
                        iceMeltCount++;
                    }
                }
                iceColumnScanCount++;
            }
        }
    }

    /**
     * 由内向外一圈一圈的 (dx, dz) 顺序表 ✓ —— 上色和冰情扫描都用它 ✓
     * （用户 2026-09-30："上色能不能以玩家为中心向四周上色，现在从左到右一点怪怪的" ✓）
     */
    private static java.util.List<int[]> cm$ringOrder(int radius) {
        if (ringList != null && ringListRadius == radius) return ringList;
        java.util.ArrayList<int[]> out = new java.util.ArrayList<int[]>();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;   // 只取这一圈 ✓
                    out.add(new int[]{dx, dz});
                }
            }
        }
        ringList = out;
        ringListRadius = radius;
        return out;
    }

    // ---- 冰情诊断（给日志看"到底有没有在冻" ✓ 2026-09-30 加）----
    private static int iceFreezeCount = 0;
    private static int iceMeltCount = 0;
    private static int iceColumnScanCount = 0;
    private static int iceChunkScanCount = 0;
    private static long iceLogTick = Long.MIN_VALUE;

    /** 每 30 秒在日志里报一次"冻了几块 / 化了几块" ✓（方便不进游戏也能核对 ✓） */
    private static void cm$iceDiagnostics(World world, boolean freeze) {
        long now = world.getTotalWorldTime();
        if (iceLogTick == Long.MIN_VALUE) {
            iceLogTick = now;
            System.out.println("[MITE][四季][冰] pass 活着：模式=" + (freeze ? "结冰" : "融冰")
                    + " 半径=" + iceRadiusChunks() + " 区块，每 tick " + ICE_CHUNKS_PER_TICK
                    + " 个区块，只处理 y>=" + ICE_MIN_Y + " 的露天水面 ✓");
        }
        if (now - iceLogTick < 600L) return;             // 30 秒报一次 ✓
        // ★ 哪怕一块没冻也要报 ✓ —— 这样一眼能看出是"没扫到水"还是"扫到了但没改" ✓
        System.out.println("[MITE][四季][冰] 近 30 秒：扫了 " + iceChunkScanCount + " 个区块 / "
                + iceColumnScanCount + " 列有水或冰；冻了 " + iceFreezeCount + " 块、化了 "
                + iceMeltCount + " 块（模式=" + (freeze ? "结冰" : "融冰") + " ✓）");
        iceFreezeCount = 0;
        iceMeltCount = 0;
        iceColumnScanCount = 0;
        iceChunkScanCount = 0;
        iceLogTick = now;
    }

    /** 冰情扫描半径（区块 ✓；配置项 `seasons.ice_radius_chunks` ✓ 默认 12 = 192 格 ✓） */
    public static int iceRadiusChunks() {
        int v = (int) CMConfig.getFloat("seasons.ice_radius_chunks", 12.0F);
        if (v < 1) v = 1;
        if (v > 48) v = 48;      // 上限 48 区块 = 768 格 ✓（再大扫一遍要几分钟 ✗）
        return v;
    }
}
