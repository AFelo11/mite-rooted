package net.dsh.createmite;

import net.minecraft.WeatherEvent;
import net.minecraft.World;

import java.util.List;
import java.util.Random;

/**
 * 四季 × 天气偏置（用户 2026-10-01 定稿 ✓）。
 *
 * 【MITE 的天气机制（javap 实证 ✓）】
 *   · 每天用**确定性随机数**生成最多 **3 个事件**（种子 = 世界创建时刻 + 天数 + 维度盐 ✓）
 *   · 每个事件：start 随机 ✓、duration ≤ 12000 ✓、**type**：0 = 不降水 ✓、1/2 = 普通降雨 ✓、
 *     3 = 雷暴雨（= 这个事件**挂了风暴** ✓，由 `addStorm()` 追加一段 ≥4000 tick 的子段 ✓）
 *   · **只有主世界**有天气 ✓
 *
 * 【本类做什么】在当天那张表**生成完之后后处理** ✓（挂 `generateWeatherEvents` 的 RETURN ✓）：
 *   ① 事件个数（春秋冬多、夏少 ⇒ 夏天晴日多 ✓）
 *   ② 事件时长（冬长 ✓ 夏秋短 ✓）
 *   ③ 挂不挂风暴（夏=大部分雷暴但**留一部分普通降雨** ✓、春≈0 ✓、秋冬低 ✓）
 *
 * 【一定要确定性】用和 MITE 同源的种子 ✓ —— 同一存档同一天永远得到同一张表 ✓
 *   （回档/重登/多人一致 ✓ 这也是我们整个四季的做法 ✓）
 */
public final class CMSeasonsWeather {

    /** 每季参数：{最小事件数, 最大事件数, 时长倍率, 挂风暴概率} ✓ */
    private static final float[][] P = {
        // 春：雨多、几乎不打雷 ✓
        {2, 3, 1.0F, 0.02F},
        // 夏：晴日多（事件少）✓ 但一旦下，**大部分**是雷暴、留一部分普通降雨 ✓（用户 ② ✓）
        {1, 2, 0.7F, 0.70F},
        // 秋：整体少雨多晴 ✓
        {0, 1, 0.7F, 0.15F},
        // 冬：降水多且长（会下成雪 ✓）、少雷 ✓
        {2, 3, 1.4F, 0.10F},
    };

    private CMSeasonsWeather() {}

    /** 供 mixin 调用：把当天这张表按当季参数改一遍 ✓（确定性 ✓ 可重复调用 ✓） */
    public static void apply(List events, World world, int day) {
        if (events == null || !CMSeasons.enabled()) return;
        if (world == null || world.provider == null || world.provider.dimensionId != 0) return;

        int s = CMSeasons.currentSeason() - 1;
        if (s < 0 || s > 3) return;
        float[] p = P[s];
        long seed = 0L;
        try {
            seed = world.getWorldCreationTime() + day * 24000L + 6000L
                    + world.provider.dimensionId * 938473L;
        } catch (Throwable ignored) { }
        Random r = new Random(seed ^ 0x5EEDCAFE5EEDCAFEL);

        int minN = (int) p[0], maxN = (int) p[1];
        int target = minN + (maxN > minN ? r.nextInt(maxN - minN + 1) : 0);

        // ① 多了就砍 ✓
        while (events.size() > target) {
            events.remove(events.size() - 1);
        }
        // ② 少了就补（自己造事件 ✓）
        while (events.size() < target) {
            int start = r.nextInt(24000);
            int dur = 6000 + r.nextInt(6000);
            try {
                events.add(new WeatherEvent(start, dur));
            } catch (Throwable t) {
                break;
            }
        }

        // ③ 逐个事件：改时长 + 决定挂不挂风暴 ✓
        for (int i = 0; i < events.size(); i++) {
            Object o = events.get(i);
            if (!(o instanceof WeatherEvent)) continue;
            WeatherEvent ev = (WeatherEvent) o;
            try {
                int dur = Math.max(2000, Math.round(ev.duration * p[2]));
                if (dur > 12000) dur = 12000;
                ev.duration = dur;
                ev.setStartAndEnd(ev.start, ev.start + dur);
            } catch (Throwable ignored) { }
            try {
                if (r.nextFloat() < p[3]) {
                    // ★★ 2026-10-05 修正：**不能再用 addStorm()** ✗✗
                    //   反汇编 WeatherEvent.addStorm()：它内部先掷一次骰子
                    //       if (r.nextInt(4) > 0) return;      // ★ 4 次里只有 1 次真的挂上 ✗
                    //   ⇒ 我们那张"挂风暴概率"被**静默乘了 0.25** ✗
                    //     （夏季写 0.70 ⇒ 实际只有 ~17.5% ⇒ 雷暴少得可怜 ✓）
                    //   ⇒ 改成 setStorm(start, end) **直接钉** ✓：
                    //     它内部会写 start_of_storm/end_of_storm/duration_of_storm
                    //     并且**顺手把 type 置成 3** ✓（hasStorm() 成立时 ✓）——连 randomizeType 都省了 ✓
                    long span = ev.duration;
                    long stormDur = 2400L + (long) r.nextInt(3600);          // 2~5 分钟
                    if (stormDur > span - 600L) stormDur = Math.max(200L, span - 600L);
                    long maxOff = span - stormDur;
                    long off = maxOff > 0L ? (long) r.nextInt((int) Math.min(Integer.MAX_VALUE, maxOff)) : 0L;
                    long ss = ev.start + off;
                    // ⚠️ hasStorm() 的内部判据是 **start_of_storm > 0** ✗
                    //   ⇒ 事件正好从当天第 0 tick 开始时，ss 会是 0 ⇒ 风暴"挂不上"、type 也不变 3 ✗
                    if (ss < 1L) ss = 1L;
                    ev.setStorm(ss, ss + stormDur);   // ★ type 自动变 3 ✓（不再是 25% 抽奖 ✗）
                } else if (ev.hasStorm()) {
                    ev.start_of_storm = 0;          // 没抽中：把风暴摘掉 ✓
                    ev.duration_of_storm = 0;
                    ev.end_of_storm = 0;
                    ev.randomizeType();             // 重算 type（回到 0/1/2 ✓）
                }
            } catch (Throwable ignored) { }
        }
    }

    /**
     * `/se` 跳季时调用：把 MITE 缓存的"今天那张表"作废 ✓
     * （否则你跳进冬天，当天的天气早就定好了 ✗ 要等第二天才生效 ✗ —— 用户 ④ 要求立刻生效 ✓）
     * 做法：反射把私有字段 `weather_events_for_day` 设成 -1 ✓，MITE 下次问就会重算 ✓
     */
    public static void invalidateTodayCache(World world) {
        if (world == null) return;
        try {
            java.lang.reflect.Field f = World.class.getDeclaredField("weather_events_for_day");
            f.setAccessible(true);
            f.setInt(world, -1);
            System.out.println("[CreateMITE][四季][天气] 已作废今天的天气表缓存 ✓（跳季立刻生效 ✓）");
        } catch (Throwable t) {
            System.out.println("[CreateMITE][四季][天气] 作废天气表缓存失败（不影响运行）: " + t);
        }
    }
}
