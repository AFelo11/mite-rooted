package net.dsh.createmite;

import net.minecraft.EntityPlayer;
import net.minecraft.ServerPlayer;
import net.minecraft.World;

/**
 * ★★ 体感温度 + 效率表（2026-10-02 用户方案第一轮）
 *
 * 【和体温的区别（重要 ✗）】
 *   体温  = 状态量：target -> 指数逼近 -> 存盘       （有惯性、会累积 ✗）
 *   体感  = 瞬时函数：feel = Σ 各项修正，每 tick 重算  （无惯性、不存盘 ✓）
 *
 * 【本轮已算的分项】季节基准(旬) + 昼夜 + 海拔/深度 + 群系偏置
 * 【本轮未算（第 2 轮）】天气、衣物、热源、建筑保温、热食、潮湿、风
 *
 * 【效率表（用户图 16）】
 *   < -15   : 挖50% 移70% 饿+30% 冻伤(极慢掉血)
 *   -15~-5  : 挖70% 移正常 饿+20% 无法回血
 *   -5~5    : 挖85% 移正常 饿+10%
 *   5~25    : 全部 100%，**缓慢回血（唯一正面）**
 *   25~35   : 挖90% 饿+10%
 *   35~45   : 挖75% 饿+20% 中暑
 *   > 45    : 挖50% 掉血 + 视野模糊
 *
 * ⚠️ MITE 本来**没有自然回血** ✓ ⇒ "无法回血"是默认状态、不用挂钩 ✓；
 *    我们只需**额外给** 5~25 档缓慢回血 ✓
 * ⚠️ 挖掘/移动的连续百分比**本轮没挂钩** ✗（那是全工程唯一的真难点，见交接文档 ✓）
 */
public final class CMAmbientFeel {

    private CMAmbientFeel() {}

    /** 调试：强制体感温度（NaN = 不强制 ✓）/se F <摄氏> 用 */
    private static float forced = Float.NaN;
    public static void force(float c) { forced = c; }
    public static void clearForce() { forced = Float.NaN; }
    public static boolean isForced() { return !Float.isNaN(forced); }

    /** 配置默认值（都没进 CMConfig，先用代码常量 ✓ 要调再说 ✓） */
    // ★ 2026-10-02 用户定稿（都在本类 tick 那条路上 ✓ 由 FeelTickMixin 每 tick 驱动）：
    //   · 舒适档回血 = **40 秒 1 点**（原版 MITE 的强度 ✓ 用户指定 ✓）
    //   · 冻伤掉血   = **60 秒 1 点**（极慢 ✓ "几乎没感觉但不能让人觉得没惩罚" ✓）
    //   · 灼热掉血   = **60 秒 1 点**（同上 ✓ 原来 5 秒太狠 ✗ 已削）
    private static final float FROST_DAMAGE_HP = 1.0F;
    private static final int FROST_DAMAGE_TICKS = 1200;    // 60 秒
    private static final float HEAT_DAMAGE_HP = 1.0F;
    private static final int HEAT_DAMAGE_TICKS = 1200;     // 60 秒
    private static final int REGEN_TICKS = 800;            // 40 秒回 1 点
    private static final float REGEN_HP = 1.0F;

    // ---- 旬表（每季 4 旬 × 5 天；昼/夜 摄氏度 ✓ 用户方案 §D/E）----
    private static final float[][] SPRING = {{8, 2}, {14, 6}, {18, 10}, {22, 14}};
    private static final float[][] SUMMER = {{26, 18}, {32, 22}, {36, 25}, {30, 22}};
    private static final float[][] AUTUMN = {{20, 12}, {14, 6}, {8, 0}, {2, -5}};
    private static final float[][] WINTER = {{-2, -8}, {-8, -15}, {-15, -22}, {-8, -14}};

    private static float[][] tableFor(int season) {
        switch (season) {
            case CMSeasons.SUMMER: return SUMMER;
            case CMSeasons.AUTUMN: return AUTUMN;
            case CMSeasons.WINTER: return WINTER;
            default: return SPRING;
        }
    }

    /** ① 季节基准（按"旬"取昼/夜值 ✓）*/
    public static float seasonBase(World w, boolean day) {
        int doy = CMSeasons.dayOfYear();
        int season = CMSeasons.currentSeason();
        int xun = (doy % CMSeasons.DAYS_PER_SEASON) / 5;     // 0..3 = 初/仲/深/晚 ✓
        if (xun < 0) xun = 0;
        if (xun > 3) xun = 3;
        float[] pair = tableFor(season)[xun];
        return day ? pair[0] : pair[1];
    }

    /** ② 是不是白天（用四季的日出/昼长锚点 ✓ 和昼夜温差同源 ✓）*/
    public static boolean isDay(World w) {
        try {
            // ★ 用 CMSeasons 现成的时刻接口 ✓（CMAmbient 的 nightFactor 就是这么读的 ✓
            //   —— MITE 的 World / WorldInfo **都没有 getWorldTime()** ✗ javac 实证 ✓）
            int sunrise = CMSeasons.rawSunriseTick();
            int rel = CMSeasons.rawTimeOfDayNow(w) - sunrise;
            if (rel < 0) rel += 24000;
            return rel < CMSeasons.seasonDayLength();
        } catch (Throwable e) {
            return true;
        }
    }

    /** ③ 海拔 / 深度（用户方案 §H ✓）*/
    public static float altitudeMod(EntityPlayer p) {
        int y = (int) Math.floor(p.posY);
        if (y < 0) return 35.0F;            // y<0 约 35
        if (y < 10) return 25.0F;
        if (y < 30) return 15.0F;
        if (y < 50) return 10.0F;           // y<50 约 10（季节影响减半 → 见 computed ✓）
        float m = 0.0F;
        if (y > 120) m -= 5.0F;
        m -= (y - 60) / 10.0F;              // 每高 10 格 -1（以 60 为基准 ✓）
        return m;
    }

    /** ④ 群系偏置：把 MITE 的 0~2 群系温度映射成摄氏偏置 ✓ */
    public static float biomeMod(World w, EntityPlayer p) {
        try {
            int bx = (int) Math.floor(p.posX);
            int bz = (int) Math.floor(p.posZ);
            float bt = w.getBiomeGenForCoords(bx, bz).getFloatTemperature();
            // MITE 温带 ≈ 0.8 ⇒ 0；每 0.1 ⇒ 2°C ✓
            return (bt - 0.8F) * 20.0F;
        } catch (Throwable e) {
            return 0.0F;
        }
    }

    // ================= ★ 天气修正（2026-10-05 用户定稿 ✓ 只做 5 个状态）=================
    //  MITE 的天气只有**两个变量**（javap 实证 ✓）：
    //    ① 有没有降水（+雨强度斜坡）② 这场降水里嵌不嵌"风暴"子段
    //  雨/雪、雷雨/暴风雪 = 这两个变量 × 温度 ⇒ 玩家实际能遇到的**只有 5 个状态** ✓
    //
    //  ✗ **没有"阴天"**：WeatherEvent.type 的 0/1/2 在功能上是惰性的
    //     （全 jar 唯一读者 EntityRenderer 只写不读；MITE 自己的 F3 也只打 "Current rain: …"）
    //     "天阴"只是 rainStrength 把天空压暗的副产品（calculateSkylightSubtracted）⇒ 不做 ✓
    //  ✗ **没有"雾天"**：MITE 的雾是**群系**属性（EntityRenderer 的 is_fog_supporting_biome）⇒ 不做 ✓
    //
    //  ★★ 用户 2026-10-05 要求「雪与雪暴的降温只存在于雪地群系和冬季」——**自动成立** ✓：
    //     判据 = 那一列温度 <= 0.15（MITE 自己的结冰线 ✓），而我们的四季**冬天把全世界钳到 0.15 以下**
    //     ⇒ 冬天到处下雪 ✓；反过来夏天连雪地群系都被抬到 0.15 以上 ⇒ 那里也下雨 ✓
    //  ⚠️⚠️ **千万别用 World.isSnowing(x,z)** ✗✗ —— 它内部走 BiomeGenBase.isFreezing()，
    //     而那个方法读的是**群系字段 temperature**、**绕过**我们的 getFloatTemperature() mixin
    //     ⇒ 冬天(温带)它永远返回 false ⇒ 雪/雪暴的数值**永远不会触发** ✗（javap 实证 ✓）

    public static final int WX_CLEAR = 0, WX_RAIN = 1, WX_STORM = 2, WX_SNOW = 3, WX_BLIZZARD = 4;
    /** ★ 世界在下雨、但玩家**没淋到**（屋檐下 / 洞里 / 干旱群系）⇒ 修正 = **0** ✓
     *  （用户 2026-10-05 裁定：这种情况**不能白拿晴天的 +3** ✗ 应该是"天气对我没影响" ✓）*/
    public static final int WX_SHELTERED = 5;

    private static final java.util.HashMap<Integer, int[]> WX_CACHE = new java.util.HashMap<Integer, int[]>();

    /** 这一刻玩家头顶是什么天气 ✓（每 tick 只算一次 ✓ 含"露天"判定 ✓ 屋檐下/矿洞里 = 晴 ✓）*/
    public static int weatherKind(EntityPlayer p) {
        if (p == null || p.worldObj == null) return WX_CLEAR;
        Integer k = Integer.valueOf(p.entityId);
        int[] c = WX_CACHE.get(k);
        if (c != null && c[0] == p.ticksExisted) return c[1];
        int kind = cm$computeWeather(p, p.worldObj);
        WX_CACHE.put(k, new int[]{p.ticksExisted, kind});
        return kind;
    }

    private static int cm$computeWeather(EntityPlayer p, World w) {
        try {
            int px = (int) Math.floor(p.posX);
            int py = (int) Math.floor(p.posY + 1.0D);
            int pz = (int) Math.floor(p.posZ);
            // ★ isPrecipitatingAt 自带"头顶见不见天"判定 ⇒ 屋檐下 / 矿洞里直接 false ✓
            if (!w.isPrecipitatingAt(px, py, pz)) {
                // ★★ 关键区分（用户 2026-10-05 裁定 ✓）：
                //   世界根本没在下雨        ⇒ 晴（+3）
                //   世界在下雨但玩家没淋到  ⇒ **遮挡（0）** ✗ 不能白拿晴天的 +3
                //   （isPrecipitating(true) 是**世界级**的雨强度判定 ⇒ 与玩家位置无关 ✓
                //     它同时覆盖"屋顶挡雨""矿洞""沙漠下雨"三种"没淋到" ✓）
                return w.isPrecipitating(true) ? WX_SHELTERED : WX_CLEAR;
            }
            // ★ 雪 = 那一列冻住（温度 <= 0.15，和 MITE 的结冰线同一条 ✓ 走我们的 mixin ✓）
            boolean snow = isSnowColumn(w, px, pz);
            // ★ 雷暴判定（2026-10-05 用户实测后修正 ✓）
            //   ① isStormingAt(now) = 现在正处在**事件表里的风暴子段** ✓（自然天气走这条 ✓）
            //   ② isThundering(true) = 加权雷强度 > 0.9 ✓
            //      ⇒ 这条路是给 **/Y 2 强制雷雨** 用的 ✗：那个指令只写 rainStrength/thunderingStrength
            //        两个字段（反射 ✓），**根本不碰事件表** ⇒ 只查①的话永远是"雨" ✗
            //        （用户实测："咋下雪都是 -8" 就是这个原因 ✓）
            //   ③ isThundering(false) = MITE 自己的 is_storming 标志 ✓ 兜底 ✓
            boolean storm = w.isStormingAt(w.getTotalWorldTime())
                         || w.isThundering(true)
                         || w.isThundering(false);
            if (snow) return storm ? WX_BLIZZARD : WX_SNOW;
            return storm ? WX_STORM : WX_RAIN;
        } catch (Throwable t) {
            return WX_CLEAR;
        }
    }

    /**
     * ★★ 那一列的温度（走 getFloatTemperature() ⇒ **吃我们的四季偏移** ✓）
     *   ⚠️ 别改成读群系字段 temperature ✗ 那样就绕过四季了 ✓
     */
    public static float columnTemperature(World w, int x, int z) {
        try {
            return w.getBiomeGenForCoords(x, z).getFloatTemperature();
        } catch (Throwable t) {
            return 0.8F;
        }
    }

    /** 这一列会不会下雪（温度 <= 0.15 = MITE 的结冰线 ✓）*/
    public static boolean isSnowColumn(World w, int x, int z) {
        return columnTemperature(w, x, z) <= 0.15F;
    }

    /** 玩家脚下那一列（日志/报告用 ✓）*/
    public static float columnTemperature(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 0.8F;
        return columnTemperature(p.worldObj, (int) Math.floor(p.posX), (int) Math.floor(p.posZ));
    }

    /** 中文名（聊天栏用 ✓）*/
    public static String weatherName(int kind) {
        switch (kind) {
            case WX_RAIN: return "雨";
            case WX_STORM: return "雷暴";
            case WX_SNOW: return "雪";
            case WX_BLIZZARD: return "雪暴";
            case WX_SHELTERED: return "遮挡";
            default: return "晴";
        }
    }

    /** ★ ASCII 名（日志用 ✓ 中文进日志会被毁成 U+FFFD ✗ 搜不到 ✓）*/
    public static String weatherKey(int kind) {
        switch (kind) {
            case WX_RAIN: return "RAIN";
            case WX_STORM: return "STORM";
            case WX_SNOW: return "SNOW";
            case WX_BLIZZARD: return "BLIZZARD";
            case WX_SHELTERED: return "SHELTER";
            default: return "CLEAR";
        }
    }

    /** 天气对体感的修正（℃）✓ 晴 +3 ／ 雨 -5 ／ 雷暴 -7 ／ 雪 -8 ／ 雪暴 -12（用户 2026-10-05 定稿 ✓）*/
    public static float weatherMod(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 0.0F;
        if (!CMConfig.feelWeatherHook()) return 0.0F;
        switch (weatherKind(p)) {
            case WX_RAIN:     return CMConfig.getFloat("feel.weather.rain", -5.0F);
            case WX_STORM:    return CMConfig.getFloat("feel.weather.storm", -7.0F);
            case WX_SNOW:     return CMConfig.getFloat("feel.weather.snow", -8.0F);
            case WX_BLIZZARD: return CMConfig.getFloat("feel.weather.blizzard", -12.0F);
            case WX_SHELTERED: return CMConfig.getFloat("feel.weather.sheltered", 0.0F);
            default:          return CMConfig.getFloat("feel.weather.clear", 3.0F);
        }
    }

    /** ★ 体感温度（摄氏度 ✓ 瞬时、不存盘 ✓）*/
    public static float feel(EntityPlayer p) {
        if (!Float.isNaN(forced)) return forced;
        World w = p.worldObj;
        if (w == null) return 15.0F;
        int y = (int) Math.floor(p.posY);
        boolean deep = y < 50;                       // 地下：季节影响减半 ✓
        float season = seasonBase(w, isDay(w));
        if (deep) season *= 0.5F;
        // ★ 热源（2026-10-05 用户拍板 ✓）：方块那一项（取最强、不叠加 ✓）＋ 物品那一项（独立 ✓）
        // ★ 三套互相独立的体感系统（用户 2026-10-06 拍板 ✓）：
        //     ① 环境（方块热源 ✓ CMHeat.blockHeat）② 物品（暖手石 ✓ CMHeat.itemHeat）
        //     ③ 食物（CMFood.foodHeat ✓）—— 三者**相加** ✓
        float feel = season + altitudeMod(p) + biomeMod(w, p) + weatherMod(p)
                   + CMHeat.blockHeat(p) + CMHeat.itemHeat(p) + CMFood.foodHeat(p)
            + net.dsh.createmite.CMFats.insulation();   // fats insulation hook;
        if (y < 50) feel = Math.max(feel, 10.0F);    // 地下有下限 ✓
        return feel;
    }

    // ---- ⑤ 效率表 ----
    public static final int TIER_FREEZE = 0;   // < -15
    public static final int TIER_COLD = 1;     // -15 ~ -5
    public static final int TIER_CHILL = 2;    // -5 ~ 5
    public static final int TIER_OK = 3;       // 5 ~ 25
    public static final int TIER_WARM = 4;     // 25 ~ 35
    public static final int TIER_HOT = 5;      // 35 ~ 45
    public static final int TIER_SCORCH = 6;   // > 45

    public static int tierOf(float feel) {
        if (feel < -15.0F) return TIER_FREEZE;
        if (feel < -5.0F) return TIER_COLD;
        if (feel < 5.0F) return TIER_CHILL;
        if (feel <= 25.0F) return TIER_OK;
        if (feel <= 35.0F) return TIER_WARM;
        if (feel <= 45.0F) return TIER_HOT;
        return TIER_SCORCH;
    }

    public static String tierName(int t) {
        switch (t) {
            case TIER_FREEZE: return "冻伤";
            case TIER_COLD: return "严重寒冷（无法回血）";
            case TIER_CHILL: return "轻度寒冷";
            case TIER_OK: return "舒适（缓慢回血）";
            case TIER_WARM: return "轻微炎热";
            case TIER_HOT: return "中暑";
            default: return "灼热（掉血）";
        }
    }

    /** 挖掘倍率（%）—— ⚠️ 本轮**还没挂钩** ✗ 只用于显示 ✓ */
    public static int digPercent(int t) {
        switch (t) {
            case TIER_FREEZE: return 50;
            case TIER_COLD: return 70;
            case TIER_CHILL: return 85;
            case TIER_WARM: return 90;
            case TIER_HOT: return 75;
            case TIER_SCORCH: return 50;
            default: return 100;
        }
    }

    /** 移动倍率（%）—— 只在极寒档减速 ✓（2026-10-05 已挂钩 ✓ 见 FeelEfficiencyMixin ✓）*/
    public static int movePercent(int t) {
        return t == TIER_FREEZE ? 70 : 100;
    }

    // ================= ★ 效率挂钩（2026-10-05 用户拍板 ✓ 交接文档 P0「挖掘/移动挂钩」）=================
    //  挖掘：EntityPlayer.getDamageVsBlock(IIIZ)F ⇒ **客户端**拿它累加 curBlockDamageMP
    //        （达到 1.0 才发"挖完了"包 ⇒ 客户端才是挖掘的时钟 ✓）服务端只拿它判瞬破方块 ✓
    //  移动：EntityPlayer.getAIMoveSpeed()F       ⇒ EntityLivingBase.moveEntityWithHeading 用它算位移 ✓
    //  两者都注入在 FeelEfficiencyMixin 里 ✓，这里只负责"给多少" ✓
    //  ★ 每 tick 只算一次体感：key = entityId，值 = {ticksExisted, tier}
    //    （否则每 tick 被问 3~5 次，每次都要跑 feel() ✓）

    private static final java.util.HashMap<Integer, int[]> EFF_CACHE = new java.util.HashMap<Integer, int[]>();

    private static int tierCached(EntityPlayer p) {
        Integer k = Integer.valueOf(p.entityId);
        int[] c = EFF_CACHE.get(k);
        if (c != null && c[0] == p.ticksExisted) return c[1];
        int t = tierOf(feel(p));
        EFF_CACHE.put(k, new int[]{p.ticksExisted, t});
        return t;
    }

    /** 挖掘效率（50/70/85/90/75/100% ✓ 用户 2026-10-05 确认口径：直接乘 ✓）；/se F 锁定立刻生效 ✓ */
    public static float digMultiplierOf(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 1.0F;
        if (!CMSeasons.enabled() || !CMConfig.feelDigHook()) return 1.0F;
        return digPercent(tierCached(p)) / 100.0F;
    }

    /** 移动效率（只有 < -15 档 = 70% ✓ 其余档 100% ✓ 用户 2026-10-05 确认 ✓）*/
    public static float moveMultiplierOf(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 1.0F;
        if (!CMSeasons.enabled() || !CMConfig.feelMoveHook()) return 1.0F;
        return movePercent(tierCached(p)) / 100.0F;
    }

    /**
     * 诊断：**只有不舒适档**才打一行（每侧最多 5 秒一行 ✓ 舒适档一声不响 ✓）
     *   ★ 标签一律用 **ASCII** ✗ —— 日志文件是 UTF-16 且中文会被毁成 U+FFFD，
     *     中文关键词**永远搜不到** ✓（2026-10-05 踩到的坑 ✓ 见交接文档）
     *   S = 服务端 / C = 客户端（挖掘进度是客户端在累加 ⇒ 两边都要能看到才对 ✓）
     */
    private static long effLogS = 0L, effLogC = 0L;

    public static void efficiencyLog(EntityPlayer p, float dig, float move,
                                     String kind, float before, float after) {
        boolean remote = p.worldObj != null && p.worldObj.isRemote;
        long now = System.currentTimeMillis();
        if (remote) {
            if (now - effLogC < 5000L) return;
            effLogC = now;
        } else {
            if (now - effLogS < 5000L) return;
            effLogS = now;
        }
        int t = tierCached(p);
        System.out.println("[MITE][FEEL-EFF-" + (remote ? "C" : "S") + "]"
                + " tier=" + t + " dig=" + digPercent(t) + "% move=" + movePercent(t) + "%"
                + " " + kind + " " + Float.toString(before) + "->" + Float.toString(after)
                + (isForced() ? " forced=" + CMAmbient.fmt(forced) : ""));
    }

    /** 饥饿倍率（用户表：正常 / +10% / +20% / +30% ✓）*/
    public static float hungerMultiplier(int t) {
        switch (t) {
            case TIER_FREEZE: return 1.30F;
            case TIER_COLD: return 1.20F;
            case TIER_CHILL: return 1.10F;
            case TIER_WARM: return 1.10F;
            case TIER_HOT: return 1.20F;
            case TIER_SCORCH: return 1.30F;
            default: return 1.00F;
        }
    }

    /**
     * ★★ 回血系数（2026-10-02 用户最终定稿 ✓）
     *   表：< -15 = 15% ｜ -15~-5 = 30% ｜ -5~5 = 65% ｜ 5~25 = 100%
     *       25~35 = 70% ｜ 35~45 = 25% ｜ >45 = 15%
     * 【怎么落地】挂在 MITE 自己的 `EntityPlayer.shouldHeal()` 上 ✓
     *   它是**布尔**判定 ⇒ 用**概率**表达百分比：return true 的次数按系数减少 ✓
     *   （统计上 = 回血速度变成原来的 x% ✓ 见 FeelRegenMixin ✓）
     * ⚠️ 和"我们自己 heal"二选一 ⇒ 已经**删掉自己 heal** ✗（避免偷偷加倍 ✓）
     */
    public static float regenMultiplier(int t) {
        switch (t) {
            case TIER_FREEZE: return 0.15F;
            case TIER_COLD: return 0.30F;
            case TIER_CHILL: return 0.65F;
            case TIER_WARM: return 0.70F;
            case TIER_HOT: return 0.25F;
            case TIER_SCORCH: return 0.15F;
            default: return 1.00F;
        }
    }

    /** 给 mixin 用：这个玩家当前的回血系数（0~1 ✓） */
    public static float regenMultiplierOf(EntityPlayer p) {
        if (p == null || !CMSeasons.enabled()) return 1.0F;
        return regenMultiplier(tierOf(feel(p)));
    }

    /**
     * ★ HUD 预警边框（从体温系统搬过来 ✓ 2026-10-02）
     *   冷（feel < 5）= 蓝 ／ 热（feel > 25）= 红 ✓
     *   浓度按**偏离舒适带多远**算：5°C 起 0.10，最多 1.0 ✓
     */
    public static float[] borderInfo() {
        try {
            if (!CMSeasons.enabled()) return null;
            net.minecraft.EntityPlayer p = net.minecraft.Minecraft.getMinecraft().thePlayer;
            if (p == null) return null;
            float f = feel(p);
            if (f >= 5.0F && f <= 25.0F) return null;      // 舒适带不画 ✓
            boolean cold = f < 5.0F;
            float d = cold ? (5.0F - f) : (f - 25.0F);
            float strength = Math.min(1.0F, 0.10F + d / 40.0F);
            return new float[]{ strength, cold ? 1.0F : -1.0F };
        } catch (Throwable t) {
            return null;
        }
    }

    /** 视野模糊强度 0..1（>45 才有 ✓ 给 HUD 用 ✓）*/
    public static float blurStrength(int t) {
        return t == TIER_SCORCH ? 1.0F : 0.0F;
    }

    // ---- ⑥ 每 tick：只做"掉血 / 回血"（饥饿走 BodyTempHungerMixin ✓）----

    private static final java.util.HashMap<String, Integer> TIMERS = new java.util.HashMap<String, Integer>();

    private static int timer(EntityPlayer p) {
        String k = p.getCommandSenderName();
        Integer v = TIMERS.get(k);
        if (v == null) { TIMERS.put(k, Integer.valueOf(0)); return 0; }
        return v.intValue();
    }

    /** 由 FeelTickMixin 每 tick 调一次（只在服务端 ✓；2026-10-09 体温系统卸载后入口就是它 ✓）*/
    // ================= ★ 取数统计（2026-10-02 用户要求：掉血/回血肉眼看不出来 ⇒ 用日志看 ✓）===
    private static int statPass = 0, statBlock = 0, statDmg = 0;
    private static long statLast = 0L;

    /** 给 FeelRegenMixin 计数 ✓ */
    public static void countRegen(boolean passed) {
        if (passed) statPass++; else statBlock++;
    }

    /** 每 30 秒一行：体感 / 档位 / 系数 / 三项计数 / 当前血量 ✓ */
    private static void cm$statLog(EntityPlayer p, float feel, int t) {
        long now = System.currentTimeMillis();
        if (statLast == 0L) { statLast = now; return; }
        if (now - statLast < 30000L) return;
        statLast = now;
        System.out.println("[MITE][体感] " + p.getCommandSenderName()
                + " 体感=" + CMAmbient.fmt(feel) + "C 档=" + tierName(t)
                + " 回血系数=" + ((int) (regenMultiplier(t) * 100.0F)) + "%"
                + " | 近30秒: 放行=" + statPass + " 挡掉=" + statBlock + " 掉血=" + statDmg + "点"
                + " | 血=" + CMAmbient.fmt(p.getHealth())
                + " | wx=" + weatherKey(weatherKind(p)) + " mod=" + CMAmbient.fmt(weatherMod(p))
                + " heat=" + CMAmbient.fmt(CMHeat.blockHeat(p))
                + " heatSrc=" + (CMHeat.sourceKey(p) == null ? "NONE" : CMHeat.sourceKey(p))
                + " item=" + CMAmbient.fmt(CMHeat.itemHeat(p))
                + " warm=" + (CMHeat.isItemWarmActive(p) ? 1 : 0)
                + " food=" + CMAmbient.fmt(CMFood.foodHeat(p))
                + " foodSrc=" + CMFood.activeKey(p)
                + " bt=" + CMAmbient.fmt(columnTemperature(p))
                + " snowCol=" + (isSnowColumn(p.worldObj, (int) Math.floor(p.posX), (int) Math.floor(p.posZ)) ? 1 : 0)
                + " precip=" + (p.worldObj != null && p.worldObj.isPrecipitating(true))
                + " storm=" + (p.worldObj != null && p.worldObj.isStormingAt(p.worldObj.getTotalWorldTime())));
        statPass = 0; statBlock = 0; statDmg = 0;
    }

    public static void tick(EntityPlayer p) {
        if (!(p instanceof ServerPlayer)) return;
        // ★ 暖手石的"冷却条"（用物品耐久条画 ✓ 用户 2026-10-05：要跟末影珍珠一样 ✓）
        //   放在这里是因为**这里本来就是每 tick 一次的服务端钩子** ✓ 不用再加 mixin ✓
        try { net.dsh.createmite.item.ItemHandWarmer.tickCooldownBar(p); } catch (Throwable ignored) { }
        if (!CMSeasons.enabled()) return;
        int t = tierOf(feel(p));
        int n = timer(p) + 1;

        if (t == TIER_FREEZE) {
            if (n >= FROST_DAMAGE_TICKS) { n = 0; hurt(p, FROST_DAMAGE_HP); }
        } else if (t == TIER_SCORCH) {
            if (n >= HEAT_DAMAGE_TICKS) { n = 0; hurt(p, HEAT_DAMAGE_HP); }
        } else {
            // ★ 回血**不在这里做** ✗ —— 改由 FeelRegenMixin 缩放 MITE 自己的回血 ✓
            //   （用户 2026-10-02 定稿：5~25 = 100% = 原版速度 ✓ 我们不再额外 heal ✓）
            n = 0;
        }
        TIMERS.put(p.getCommandSenderName(), Integer.valueOf(n));
        cm$statLog(p, feel(p), t);
    }

    /**
     * 掉血：**直接扣血**（不走护甲 ✓ 也不吃伤害免疫帧 ✓）
     *   ⚠️ MITE 的 EntityPlayer **没有**公开的 attackEntityFrom(DamageSource,float) ✗（javac 实证 ✓）
     *   ⇒ 直接改血量 ✓ 效果 = 冻伤/中暑**无视护甲** ✓（本来就是环境伤害 ✓ 合理 ✓）
     */
    private static void hurt(EntityPlayer p, float hp) {
        try {
            float now = p.getHealth();
            if (now <= 0.0F) return;
            float v = now - hp;
            p.setHealth(v < 0.0F ? 0.0F : v);
            statDmg += (int) hp;
            System.out.println("[MITE][体感] 掉血 " + CMAmbient.fmt(hp) + " 点"
                    + "（" + (tierOf(feel(p)) == TIER_FREEZE ? "冻伤" : "灼热") + "）血=" + CMAmbient.fmt(p.getHealth()));
        } catch (Throwable ignored) { }
    }

    /** 报告（/se F 用 ✓）*/
    public static String report(EntityPlayer p) {
        World w = p.worldObj;
        boolean day = isDay(w);
        float season = seasonBase(w, day);
        float alt = altitudeMod(p);
        float bio = biomeMod(w, p);
        float feel = feel(p);
        int t = tierOf(feel);
        StringBuilder sb = new StringBuilder();
        sb.append("体感 ").append(CMAmbient.fmt(feel)).append("°C ⇒ ").append(tierName(t));
        sb.append(" ｜ 季节基准 ").append(CMAmbient.fmt(season)).append("（").append(day ? "昼" : "夜").append("）");
        sb.append(" ｜ 海拔 ").append(CMAmbient.fmt(alt));
        sb.append(" ｜ 群系 ").append(CMAmbient.fmt(bio));
        sb.append(" ｜ 天气 ").append(weatherName(weatherKind(p)))
          .append("(").append(CMAmbient.fmt(weatherMod(p))).append("°C)");
        String hsrc = CMHeat.sourceName(p);
        sb.append(" ｜ 热源 ").append(CMAmbient.fmt(CMHeat.blockHeat(p))).append("°C")
          .append(hsrc == null ? "（附近没有）" : "（" + hsrc + "）")
          .append(" ＋物品 ").append(CMAmbient.fmt(CMHeat.itemHeat(p)));
        String fsrc = CMFood.activeName(p);
        sb.append(" ＋食物 ").append(CMAmbient.fmt(CMFood.foodHeat(p))).append("°C")
          .append(fsrc == null ? "" : "（" + fsrc + "）");
        sb.append(" ｜ 挖掘 ").append(digPercent(t)).append("% 移动 ").append(movePercent(t))
          .append("% 饥饿 x").append(CMAmbient.fmt(hungerMultiplier(t)));
        return sb.toString();
    }
}
