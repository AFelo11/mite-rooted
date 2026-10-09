package net.dsh.createmite;

import net.minecraft.World;

/**
 * 时间流速（`/S <倍速>` ✓）—— 2026-09-30 用户要求的单个实例管理员工具 ✓。
 *
 * 实现：**只在服务端**每 tick 额外推进世界时间 ✓（客户端的时间由服务端同步 ✓）。
 *   · `World.advanceTotalWorldTime(long)` ✓ —— MITE 自己那份总时间（季节/天数都看它 ✓）
 *   · `WorldInfo.setWorldTime(...)` ✓ —— 原版那份（太阳/月亮位置看它 ✓）
 *   两份一起推，免得"天数跑了、天还是黑的" ✗。
 *
 * ⚠️ 小数部分会累积 ✓（`/S 1.5` = 每两 tick 多推一格 ✓）。
 * ⚠️ `/S 0` 目前**不真的暂停** ✗（只是不再加速 ✓）—— 要暂停得把时间往回拨，风险大 ✗，用户要用再说 ✓。
 */
public final class CMTimeSpeed {

    private static float multiplier = 1.0F;
    private static float accum = 0.0F;

    private CMTimeSpeed() {}

    public static float multiplier() {
        return multiplier;
    }

    public static void set(float v) {
        multiplier = v;
        accum = 0.0F;
        System.out.println("[MITE][时间] 流速设为 " + v + " 倍 ✓");
    }

    /** 每 tick 调一次（由 `CMSeasons.update` 顺带调 ✓，只在服务端生效 ✓） */
    public static void tick(World world) {
        // ★ 2026-10-01：**/S 同时加速作物** ✓（原来只推日历 ⇒ 作物看起来纹丝不动 ✗）
        net.dsh.createmite.CMCropSpeedup.tick(world);

        if (world == null || world.isRemote) return;
        if (multiplier <= 1.0F) return;

        float extraF = multiplier - 1.0F + accum;
        int extra = (int) extraF;
        accum = extraF - extra;
        if (extra <= 0) return;

        try {
            world.advanceTotalWorldTime(extra);
        } catch (Throwable ignored) { }
        // ⚠️ MITE 里 WorldInfo **没有** setWorldTime(long) ✗（javap 实证 ✓）
        //   它用的是 `setTotalWorldTime(long, World)` ✓ ⇒ 走这个 ✓
        try {
            long wt = world.getWorldInfo().getWorldTotalTime(0);
            world.getWorldInfo().setTotalWorldTime(wt + extra, world);
        } catch (Throwable ignored) { }
    }
}
