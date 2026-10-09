package net.dsh.createmite;

import net.minecraft.World;

/**
 * ★★ 四季【环境温度】（单位 ℃）—— 用户 2026-10-01 定稿 ✓
 *
 * 【它是什么】"季节温度"本身 ✓ —— **与玩家所处环境无关** ✗（在洞里/水里/岩浆边都不影响它 ✓），
 *   也不影响冬季结冰那套机制 ✗（结冰仍然走"冬季把群系温度钳到 0.15 以下 + 原版机制" ✓）。
 *   它只服务于两件事：**玩家体温**（{@link CMBodyTemp}）与以后的**环境温度饰品/UI** ✓。
 *
 * 【数值表（用户给定 ✓）】
 * <pre>
 *   季节   第 1~5 天     第 6~14 天     第 15~20 天
 *   春     1 ~ 8 ℃      13 ~ 22 ℃     16 ~ 25 ℃
 *   夏     19 ~ 32 ℃    21 ~ 39 ℃     18 ~ 30 ℃
 *   秋     16 ~ 28 ℃    13 ~ 26 ℃     10 ~ 16 ℃
 *   冬     10 ~ 17 ℃    -10 ~ 8 ℃     -2 ~ 5 ℃
 * </pre>
 *
 * 【变化规则（用户给定 ✓）】
 *   · **每 2 分钟（真实时间 = 2400 tick ✓）换一次值** ✓，值**必须落在当天的区间内** ✓
 *   · 区间之间**平滑游走**：这一段的起点是上一次的值，终点是本段内新抽的值，
 *     2400 tick 内线性走过去 ✓（所以任何时刻都在区间内、且不会瞬移 ✓）
 *
 * 【★ 段与段的过渡（用户 2026-10-01：「段与段之间做 1-2 天的过渡吧，具体操作由你决定」✓）】
 *   做法：**区间本身**在换段时用 **2 天**线性混合 ✓ ——
 *     段1→段2 的混合窗口 = 第 4.5 ~ 6.5 天；段2→段3 = 第 13.5 ~ 15.5 天 ✓
 *   于是冬天不再出现"第 5 天 10~17 ℃、第 6 天直接 -10~8 ℃"这种 20 ℃ 的断崖 ✗，
 *   而是每天挪一点（冬天最陡处约 10 ℃/天）✓，配合"每 2 分钟的平滑游走"看起来是渐变 ✓。
 *
 * 【为什么不用随机数发生器】取值用**世界总时间 + 步号**的哈希 ✓ ⇒
 *   同一个世界时刻永远得到同一个温度 ✓（**不用存档、不用同步包** ✓，和季节本身一个思路 ✓）。
 */
public final class CMAmbient {

    /** 每季 3 段的区间表（℃ ✓）：{段1最低, 段1最高, 段2最低, 段2最高, 段3最低, 段3最高} */
    private static final float[][] TABLE = {
        /* 春 */ {   1.0F,  8.0F,  13.0F, 22.0F,  16.0F, 25.0F },
        /* 夏 */ {  19.0F, 32.0F,  21.0F, 39.0F,  18.0F, 30.0F },
        /* 秋 */ {  16.0F, 28.0F,  13.0F, 26.0F,  10.0F, 16.0F },
        /* 冬 */ {  10.0F, 17.0F, -10.0F,  8.0F,  -2.0F,  5.0F },
    };

    /** 段边界（季内第几天，**1 基** ✓）：段1 = 第 1~5 天、段2 = 第 6~14 天、段3 = 第 15~20 天 ✓ */
    private static final float SEG1_END_DAY = 5.0F;
    private static final float SEG2_END_DAY = 14.0F;

    /** ★ 过渡宽度（天 ✓）—— 用户要的"1-2 天过渡"取 2 天 ✓ */
    private static final float TRANSITION_DAYS = 2.0F;

    /**
     * ★★ 2026-10-01 用户定稿：**昼夜温差**（夜里比白天低多少 ℃ ✓）
     *   春 5 / 夏 7 / 秋 6 / 冬 6 —— 「我需要昼夜温差值」（用户选了推荐组 ✓）
     *
     * 【曲线】用 MITE 自己的"日出/日落"锚点 + 当季昼长（{@link CMSeasons#seasonDayLength()} ✓）：
     *   日出 = 50% → **午后 = 0%（最暖）** → 日落 = 50% → **日出前 1 小时 = 100%（最冷）** → 回到日出 ✓
     *   段与段之间用 smoothstep 过渡 ✓ ⇒ 夏天夜短、降温时段也短 ✓；冬天夜长、冷得久 ✓（和昼夜时长那套联动 ✓）
     *
     * ⚠️ 它只影响"环境温度 → 体温"这一条链 ✗ —— **冬季结冰机制一点都没碰** ✓（用户明确要求 ✓）
     */
    private static final float[] NIGHT_DROP = { 5.0F, 7.0F, 6.0F, 6.0F };

    /** 四季关掉时给的中性温度（℃ ✓） */
    public static final float NEUTRAL = 20.0F;

    private CMAmbient() {}

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    public static boolean enabled() {
        return CMConfig.getFloat("temp.enabled", 1.0F) != 0.0F;
    }

    /** 换值周期（tick ✓；默认 2400 = 真实 2 分钟 ✓ 用户指定 ✓） */
    public static int stepTicks() {
        int v = (int) CMConfig.getFloat("temp.ambient_step_ticks", 2400.0F);
        if (v < 20) v = 20;
        return v;
    }

    // ------------------------------------------------------------------
    // 区间（当天）
    // ------------------------------------------------------------------

    /** 本季第几天（1 基 ✓） */
    public static int dayInSeason() {
        int doy = CMSeasons.dayOfYear();
        int d = doy % CMSeasons.DAYS_PER_SEASON + 1;
        return d;
    }

    /**
     * 今天的环境温度区间 {最低, 最高}（℃ ✓）—— 段边界处按 {@link #TRANSITION_DAYS} 天混合 ✓
     */
    public static float[] bandToday() {
        return bandFor(CMSeasons.currentSeason(), dayInSeason());
    }

    /** 指定季节/季内天数的区间（℃ ✓，给报告与自查用 ✓） */
    public static float[] bandFor(int season, float dayInSeason) {
        int idx = season - 1;
        if (idx < 0) idx = 0;
        if (idx > 3) idx = 3;
        float[] row = TABLE[idx];

        // 段1→段2、段2→段3 的混合系数（窗口**跨在边界两侧** ⇒ 两边各让半天 ✓）
        float t1 = clamp01((dayInSeason - (SEG1_END_DAY - TRANSITION_DAYS * 0.5F)) / TRANSITION_DAYS);
        float t2 = clamp01((dayInSeason - (SEG2_END_DAY - TRANSITION_DAYS * 0.5F)) / TRANSITION_DAYS);

        float lo = lerp(lerp(row[0], row[2], t1), row[4], t2);
        float hi = lerp(lerp(row[1], row[3], t1), row[5], t2);
        return new float[]{ lo, hi };
    }

    // ------------------------------------------------------------------
    // 当前值
    // ------------------------------------------------------------------

    /**
     * 现在的环境温度（℃ ✓）= **白天值 − 昼夜温差** ✓。
     *
     * ⚠️ **和玩家无关** ✗（用户明确："不会因为玩家处于何种环境改变" ✓）——
     *   所以这里只吃"世界时间"和"季节"两个量 ✓，全世界一个值 ✓。
     *   （雨雪带来的个人修正**不在这里** ✗ —— 那是"被淋湿了"的体感，算在体温那一侧 ✓）
     */
    public static float current(World world) {
        if (!enabled()) return NEUTRAL;
        return daytimeValue(world) - nightDrop() * nightFactor(world);
    }

    /** 夜里的降温幅度（℃ ✓；用户给的 春5/夏7/秋6/冬6 ✓，可在配置里逐季改 ✓） */
    public static float nightDrop() {
        int s = CMSeasons.currentSeason();
        String key;
        switch (s) {
            case CMSeasons.SUMMER: key = "temp.night_drop.summer"; break;
            case CMSeasons.AUTUMN: key = "temp.night_drop.autumn"; break;
            case CMSeasons.WINTER: key = "temp.night_drop.winter"; break;
            default:               key = "temp.night_drop.spring"; break;
        }
        float def = NIGHT_DROP[Math.max(0, Math.min(3, s - 1))];
        float v = CMConfig.getFloat(key, def);
        return (v < 0.0F) ? 0.0F : v;
    }

    /**
     * 夜间降温系数（0 ~ 1 ✓）：**午后 = 0（最暖）**、**日出前 1 小时 = 1（最冷）** ✓。
     * 锚点用"相对日出的偏移"表达 ✓，并且用的是**当季拉伸后**的时刻 + 当季昼长 ✓
     * ⇒ 夏天降温时段短、冬天冷得久 ✓（和四季昼夜时长联动 ✓）。
     */
    public static float nightFactor(World world) {
        if (!enabled() || !CMSeasons.enabled()) return 0.0F;
        int raw = CMSeasons.rawTimeOfDayNow(world);
        int t = CMSeasons.warpTimeOfDay(raw);                 // 已按当季昼长拉伸 ✓
        int sunrise = CMSeasons.rawSunriseTick();
        int rel = t - sunrise;
        if (rel < 0) rel += 24000;                            // 相对日出（0 = 日出 ✓）

        float dayLen = CMSeasons.seasonDayLength();
        if (dayLen <= 0.0F || dayLen >= 24000.0F) dayLen = 14000.0F;

        float a0 = 0.0F,                 f0 = 0.50F;          // 日出：一半
        float a1 = dayLen * 0.6F,        f1 = 0.00F;          // 午后：最暖
        float a2 = dayLen,               f2 = 0.50F;          // 日落：一半
        float a3 = 24000.0F - 1200.0F,   f3 = 1.00F;          // 日出前 1 小时：最冷

        float x = rel;
        if (x < a1) return cm$smooth(f0, f1, (x - a0) / (a1 - a0));
        if (x < a2) return cm$smooth(f1, f2, (x - a1) / (a2 - a1));
        if (x < a3) return cm$smooth(f2, f3, (x - a2) / (a3 - a2));
        return cm$smooth(f3, f0, (x - a3) / (24000.0F - a3));
    }

    /** smoothstep 插值（两头平、中间快 ⇒ 不出现折角 ✓） */
    private static float cm$smooth(float a, float b, float u) {
        if (u < 0.0F) u = 0.0F;
        if (u > 1.0F) u = 1.0F;
        float w = u * u * (3.0F - 2.0F * u);
        return a + (b - a) * w;
    }

    /** **白天**的环境温度（不含昼夜温差 ✓；表里的值就是它 ✓，给报告/核对用 ✓） */
    public static float daytimeValue(World world) {
        if (!enabled()) return NEUTRAL;
        float[] band = bandToday();
        long step = stepTicks();
        long now = timeOf(world);
        long idx = Math.floorDiv(now, step);
        float frac = (now - idx * step) / (float) step;

        float a = hash01(idx);
        float b = hash01(idx + 1L);
        float r = a + (b - a) * frac;                 // 本段内的平滑游走 ✓
        if (r < 0.0F) r = 0.0F;
        if (r > 1.0F) r = 1.0F;
        return band[0] + (band[1] - band[0]) * r;
    }

    private static long timeOf(World world) {
        if (world == null) return 0L;
        try {
            return world.getTotalWorldTime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------
    // 报告（日志 / 指令自查 ✓）
    // ------------------------------------------------------------------

    public static String reportLine(World world) {
        if (!enabled()) return "环境温度：已关闭（config/createmite.properties → temp.enabled = 0）";
        float[] band = bandToday();
        float nf = nightFactor(world);
        return "环境温度：" + fmt(current(world)) + " ℃（" + CMSeasons.seasonName(CMSeasons.currentSeason())
                + "季第 " + dayInSeason() + "/" + CMSeasons.DAYS_PER_SEASON + " 天，今日区间 "
                + fmt(band[0]) + " ~ " + fmt(band[1]) + " ℃，白天 " + fmt(daytimeValue(world))
                + " ℃，昼夜温差 " + fmt(nightDrop()) + " ℃ x " + fmt(nf) + "）";
    }

    public static String fmt(float v) {
        return String.format("%.2f", v);
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 稳定哈希 → [0,1) ✓（同一个步号永远同一个值 ✓） */
    private static float hash01(long x) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (float) ((h >>> 11) * (1.0D / 9007199254740992.0D));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }
}
