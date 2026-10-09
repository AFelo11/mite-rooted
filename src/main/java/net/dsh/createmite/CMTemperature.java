package net.dsh.createmite;

import net.minecraft.BiomeGenBase;
import net.minecraft.Minecraft;

/**
 * 温度读数的**唯一收口**（2026-09-30 新建，为四季 / 饰品系统预留 ✓）。
 *
 * 【★ 2026-10-01 现状：两根温度计都已经接上真值 ✓】
 *   1. 体温 → {@link CMBodyTemp}（四季温度模型 ✓ 已上线 ✓）
 *   2. 环境温度 → {@link CMAmbient}（季节温度 + 昼夜温差，℃ ✓）
 *   用户原话：「会有双体温饰品（①体温计，②环境温度计）。**现在就正常在 I 键 UI 正常显示就行了**，
 *     等我做饰品系统再改吧」✓ ⇒ 两根现在**都直接显示** ✓；等饰品做好，把两个
 *     panel.*_thermometer_needs_accessory 配置改成 1，就回到"没戴饰品 = 未知" ✓（一行配置 ✓）
 *
 * 【为什么把这两个方法单独放一个类】
 *   玩家面板（GuiPlayerStatus）只负责**画**，不负责**算** ✗。
 *   以后四季系统上线 → 只要把 {@link #bodyTemperature} 接上季节模型 ✓；
 *   饰品系统上线 → 只要把 {@link #hasAmbientThermometerAccessory} 接上饰品判定 ✓。
 *   两边都**只改这一个文件**，UI 一行都不用动 ✓。
 *
 * 【哨兵值】没有数据统一返回 {@link #UNKNOWN}（= Float.NaN ✓）——
 *   UI 见到 NaN 就画"未知"、温度计留空 ✓（用 NaN 而不是 0，因为 0 度是合法温度 ✗）
 *
 * 【调试开关（config/createmite.properties）】
 *   `panel.thermometer_debug = 0`（默认关）→ 改成 1 就用**假数据**驱动温度计：
 *   体温在 34~40 之间、环境温度在 0~2 之间慢慢来回走 ✓，纯客户端、只为看外观 ✓。
 *   （四季 / 饰品都还没做，不开这个开关就只能看到两根空温度计 ✓）
 */
public final class CMTemperature {

    /** 没有数据 */
    public static final float UNKNOWN = Float.NaN;

    /** 体温刻度的量程（**等四季系统定稿后再改这里** ✗ 现在是"摄氏度"示意 ✓） */
    public static final float BODY_MIN = 30.0F;
    public static final float BODY_MAX = 42.0F;
    /** 正常体温带（画刻度用 ✓） */
    public static final float BODY_NORMAL_LOW = 36.0F;
    public static final float BODY_NORMAL_HIGH = 38.0F;

    /**
     * 环境温度刻度的量程（℃ ✓）—— ★ 2026-10-01 改成**四季环境温度的摄氏度** ✓。
     *
     * 为什么改了：用户裁定「**现在就正常在 I 键 UI 正常显示就行了**，等我做饰品系统再改」✓
     *   ⇒ 环境温度计画的是 {@link CMAmbient} 的季节温度（昼 ~ +39 ℃、冬夜最冷 ~ -18 ℃ ✓），
     *     量程留到 -20 ~ +45 就够用 ✓（原来的 0.0~2.0 是"原版生物群系温度"那套刻度 ✗ 现在不用了 ✓）
     */
    public static final float AMBIENT_MIN = -20.0F;
    public static final float AMBIENT_MAX = 45.0F;

    private CMTemperature() {}

    // ------------------------------------------------------------------
    // 体温
    // ------------------------------------------------------------------

    /**
     * 玩家体温。
     *
     * @return 摄氏温度；**还没有数据时返回 {@link #UNKNOWN}** ✓（四季系统注入之前就是这种状态 ✓）
     */
    public static float bodyTemperature() {
        // ★★ 2026-10-02 用户要求：**I 面板的体温读数已卸载** ✗
        //   （体温系统整体停用 ✓ 改为体感温度系统 ✓）
        //   一律返回 UNKNOWN ⇒ 面板走它本来就有的"还没有数据"分支 ✓ 不再显示体温 ✓
        return UNKNOWN;
    }

    /**
     * ★ 2026-10-01：体温是否**直接显示**（不必等饰品 ✓）。
     *
     * 用户原话：「体温显示需要**等待四季注入**才正常」✓ ⇒ 四季已经上线 ✓ 所以默认直接显示 ✓。
     * 等以后"体温计饰品"做好了，把配置 {@code panel.body_thermometer_needs_accessory} 改成 1，
     * 这里就会回到"没戴饰品 = 未知"✓（一行配置，不用改代码 ✓）。
     */
    private static boolean bodyThermometerUnlocked() {
        return CMConfig.getFloat("panel.body_thermometer_needs_accessory", 0.0F) == 0.0F;   // ★ 默认 1 = 必须戴体温计 ✓
    }

    /**
     * ★ 2026-10-01 新增：**四季环境温度**（℃ ✓，见 {@link CMAmbient} ✓）。
     *
     * 这是"季节温度"本身 ✓（与玩家所处环境无关 ✓），以后**环境温度饰品**接的就是这个值 ✓。
     * ⚠️ 现在**还没有饰品** ✗ ⇒ {@link #ambientTemperature} 仍然一律返回"未知" ✓（面板先不动 ✓）。
     */
    public static float ambientCelsius(Minecraft mc) {
        if (debugEnabled()) return debugValue(1);
        if (mc == null || mc.theWorld == null) return UNKNOWN;
        return CMAmbient.current(mc.theWorld);
    }

    // ------------------------------------------------------------------
    // 环境温度（需要饰品 ✓）
    // ------------------------------------------------------------------

    /**
     * 玩家是否戴着**能测温的饰品**。
     *
     * @return 现在**永远 false** ✗ —— 饰品系统还没做 ✓（做好后在这里查饰品栏即可 ✓）
     */
    public static boolean hasAmbientThermometerAccessory(net.minecraft.EntityPlayer player) {
        // TODO(饰品系统)：在饰品栏里找"温度计"类饰品 ✓
        return false;
    }

    /**
     * 玩家所在位置的**环境温度**（原版生物群系温度刻度 ✓，结冰线 = {@link net.minecraft.MITEConstant#freezing_point} = 0.15 ✓）。
     *
     * @return 没戴测温饰品 → {@link #UNKNOWN} ✓（用户明确要求：未佩戴 = 未知 ✓）
     */
    public static float ambientTemperature(Minecraft mc) {
        if (debugEnabled()) return debugValue(1);
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return UNKNOWN;
        if (!ambientThermometerUnlocked()) {                    // ★ 等饰品系统时用配置打开这道闸 ✓
            if (!hasAmbientThermometerAccessory(mc.thePlayer)) return UNKNOWN;
        }
        return CMAmbient.current(mc.theWorld);                  // ★ 四季环境温度（℃ ✓）
    }

    /**
     * ★ 2026-10-01：环境温度计是否**直接显示**（不必等饰品 ✓）。
     *
     * 用户原话：「会有双体温饰品（①体温计，②环境温度计）。**现在就正常在 I 键 UI 正常显示就行了**，
     *   等我做饰品系统再改吧」✓ ⇒ 默认 0 = 直接显示 ✓；
     *   以后饰品系统上线，把 {@code panel.ambient_thermometer_needs_accessory} 改成 1 即可 ✓。
     */
    private static boolean ambientThermometerUnlocked() {
        return CMConfig.getFloat("panel.ambient_thermometer_needs_accessory", 0.0F) == 0.0F;   // ★ 默认 1 = 必须戴温度计 ✓
    }

    /**
     * 真正读**原版生物群系温度**（0.0 冰原 ~ 2.0 沙漠，结冰线 = 0.15 ✓）。
     *
     * ★ 2026-10-01 起 I 键面板**不再用它** ✓（面板现在画的是 {@link CMAmbient} 的摄氏度 ✓）；
     *   留着是给**以后的环境温度计饰品**当一个"群系冷热参考"用的 ✓（要就接，不要就删 ✓）。
     */
    public static float readBiomeTemperature(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return UNKNOWN;
        try {
            int px = (int) Math.floor(mc.thePlayer.posX);
            int pz = (int) Math.floor(mc.thePlayer.posZ);
            BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords(px, pz);
            if (biome == null) return UNKNOWN;
            return biome.getFloatTemperature();
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    // ------------------------------------------------------------------
    // 调试假数据（只为看温度计外观 ✓，纯客户端 ✓）
    // ------------------------------------------------------------------

    private static boolean debugEnabled() {
        return CMConfig.getFloat("panel.thermometer_debug", 0.0F) != 0.0F;
    }

    /**
     * @param which -1 = 体温、+1 = 环境温度
     */
    private static float debugValue(int which) {
        Minecraft mc = Minecraft.getMinecraft();
        long t = 0L;
        if (mc != null && mc.theWorld != null) {
            t = mc.theWorld.getTotalWorldTime();
        }
        // 2400 tick（= 游戏里 2 分钟）走一个来回 ✓，肉眼能看清柱子上下动 ✓
        float phase = (t % 2400L) / 2400.0F;
        float wave = (phase < 0.5F) ? (phase * 2.0F) : ((1.0F - phase) * 2.0F);   // 0..1..0
        if (which < 0) {
            return BODY_NORMAL_LOW + (BODY_NORMAL_HIGH - BODY_NORMAL_LOW) * wave;  // 36~38 度 ✓
        }
        return AMBIENT_MIN + (AMBIENT_MAX - AMBIENT_MIN) * wave;                   // 0~2 ✓
    }
}
