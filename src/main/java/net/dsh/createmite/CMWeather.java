package net.dsh.createmite;

import net.minecraft.World;

/**
 * 强制天气（`/Y 1|2|3` ✓，2026-09-30 用户要求 ✓）。
 *
 * 【为什么不是"设一次就完事" ✗】MITE 的天气是**按天事件表**驱动的 ✓ ——
 *   它每 tick 都会按 `WeatherEvent` 重算下雨/打雷强度 ✗ ⇒ 设一次会被立刻覆盖 ✗。
 *   ⇒ 这里记一个"强制模式" ✓，**每 tick 重新按**（`CMSeasons.update` 里顺带调 ✓）✓。
 *
 * 【能用哪些 API（javap 实证）】
 *   · `World.setRainStrength(float)` ✓（公开 ✓）
 *   · 打雷强度是 `World.thunderingStrength`（**protected** ✗）⇒ 用反射写 ✓（读不到就算了，不崩 ✓）
 */
public final class CMWeather {

    /** 0 = 不强制 ✗、1 = 下雨、2 = 雷暴雨、3 = 晴天 ✓ */
    private static int mode = 0;

    private CMWeather() {}

    public static int mode() {
        return mode;
    }

    public static void set(int m) {
        mode = m;
        System.out.println("[MITE][天气] 强制模式 = " + m + "（0 不强制 / 1 雨 / 2 雷雨 / 3 晴）✓");
    }

    // ---- 诊断（排查"雨雪粒子没了" ✓ 2026-09-30）----
    private static long lastLog = Long.MIN_VALUE;

    /** 每 tick 调一次：先报状态（每 30 秒），再按需强制 ✓ */
    public static void tick(World world) {
        if (world == null) return;
        cm$diagnose(world);
        // ★★ 2026-09-30 二改（javap 实证）：
        //   MITE 的 `CommandWeather.processCommand` **是个空壳** ✗ ——
        //   整段方法体只有一句 `throw new WrongUsageException("commands.weather.usage")` ✓
        //   ⇒ 调它必然报错 ✗（用户看到的 WrongUsageException 就是这么来的 ✓）
        //   ⇒ 走不通"调用原版指令"这条路 ✗，只能自己强制 ✓
        //   ⚠️ **关键**：粒子是**客户端**画的 ✓ ⇒ 强制必须在**两侧都做** ✓
        //      （只在服务端写 rainingStrength，客户端根本不知道 ⇒ 没粒子 ✗ ✓）
        if (mode == 0) return;
        boolean raining = (mode == 1 || mode == 2);
        boolean thundering = (mode == 2);
        try {
            world.setRainStrength(raining ? 1.0F : 0.0F);
        } catch (Throwable ignored) { }
        try {
            java.lang.reflect.Field f = net.minecraft.World.class.getDeclaredField("thunderingStrength");
            f.setAccessible(true);
            f.setFloat(world, thundering ? 1.0F : 0.0F);
            java.lang.reflect.Field p = net.minecraft.World.class.getDeclaredField("prevThunderingStrength");
            p.setAccessible(true);
            p.setFloat(world, thundering ? 1.0F : 0.0F);
        } catch (Throwable ignored) { }
    }

    /**
     * 每 30 秒把"天气状态"打一行出来 ✓ —— 用来分清"粒子没了"是哪一边的问题：
     *   · `rainStr=0`  ⇒ 状态本身就没在下雨 ⇒ 粒子当然没有（问题在天气系统/我们的强制 ✓）
     *   · `rainStr>0` 但粒子还是没有 ⇒ 状态没问题，**渲染/粒子那一侧**被影响了 ✗
     */
    private static void cm$diagnose(World world) {
        long now = world.getTotalWorldTime();
        if (lastLog == Long.MIN_VALUE) lastLog = now;
        if (now - lastLog < 600L) return;
        lastLog = now;
        String s;
        try {
            s = "rainStr=" + String.format("%.2f", world.getRainStrength(1.0F))
              + " isPrecipitating=" + world.isPrecipitating(true)
              + " forceMode=" + mode
              + " 季节=" + CMSeasons.seasonName(CMSeasons.currentSeason())
              + " 温度钳位=" + (CMSeasons.isWinter() && !CMSeasons.isWinterMeltWindow() ? "开(0.05)" : "关");
        } catch (Throwable t) {
            s = "诊断出错: " + t;
        }
        System.out.println("[MITE][天气] " + s);
    }
}
