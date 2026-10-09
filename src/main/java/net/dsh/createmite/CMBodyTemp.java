package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.EntityPlayer;
import net.minecraft.Material;
import net.minecraft.NBTTagCompound;
import net.minecraft.Potion;
import net.minecraft.PotionEffect;
import net.minecraft.ServerPlayer;
import net.minecraft.World;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 玩家体温（用户 2026-10-01 定稿 + 当天追加裁定）。
 *
 * 【基准与公式（用户裁定：保留不对称）】
 *   体温 = 36.4 + delta
 *     delta = 36.4 x T / 1000     （T 大于等于 0，即环境不冷）
 *     delta = 36.4 x T / 100      （T 小于 0，即环境寒冷；冷得比热快 10 倍，故意的）
 *   例：环境 39 度 到 37.82 度；环境 -10 度 到 32.76 度（用户给的例子）。
 *
 * 【逐渐升降】不是瞬移：每 tick 按 temp.body_rate 向目标指数逼近
 *   （默认 0.0005 / tick，约 100 秒走完 63%），显示时保留两位小数。
 *
 * 【玩家附近（用户给定）】
 *   12 格内出现岩浆 则体温逐渐升高，上限 39 度
 *   玩家泡在水里 则降温，最低 35.7 度
 *   2026-10-01 用户裁定：泡水时水优先。两者同时成立时按水算。
 *   （顺带一个好处：泡水天然是寒冷加速的逃逸手段）
 *
 * 【六档 buff / debuff（用户给定；35.6~35.7 的空档按用户裁定合并）】
 *   低于 34.5      缓慢 I                     饥饿 x1.5
 *   34.5 ~ 35.7    速度 I + 急迫 I            饥饿 x1.2   持续 5 分钟则加速转冷
 *   35.7 ~ 36.4    无                          饥饿 x1.0
 *   36.4 ~ 37.3    速度 I + 急迫 II           饥饿 x0.8
 *   37.3 ~ 38.0    缓慢 I                     饥饿 x1.2   持续 5 分钟则加速转热
 *   高于 38.0      缓慢 II + 失明 I + 反胃 I   饥饿 x1.2
 *   合并方式：35.7 归第三档，即第二档 = [34.5, 35.7)，第三档 = [35.7, 36.4]。
 *
 * 【持续 5 分钟这条：B 方案（用户 2026-10-01 裁定）】
 *   不是到点就硬钳死，而是：同一档待满 5 分钟后，逼近速度最多 x3，并把目标再往极端
 *   推 temp.accelerate_push 度（默认 1.0）。于是最终一定会掉到 34.5 以下 / 涨到 38 以上，
 *   但随时可以逃：离开那个环境、泡水（水温 35.7 直接接管）、或者注册一个缓解源（火堆已内置）。
 *   接口见 TemperatureRelief（用户要求 A 也要留接口）。
 *
 * 【服务端权威 + 存盘】buff 与饥饿倍率只有服务端才有效，所以计算只在 ServerPlayer 上跑；
 *   数值按玩家名存在表里，并写进玩家 NBT（用户裁定存盘）。
 *   客户端 UI 直接读同一份值（单机同一 JVM，和天气/四季那套一个思路）。
 */
public final class CMBodyTemp {

    /** 正常体温（度） */
    public static final float NORMAL = 36.4F;
    /** 泡水时的下限（度） */
    public static final float WATER_FLOOR = 35.7F;
    /** 附近有岩浆时的上限（度） */
    public static final float LAVA_TARGET = 39.0F;
    /** 档位阈值（度） */
    /** ★ 2026-10-01 新增：极端档阈值（用户裁定 < 33.0 = 缓慢 II + 挖掘疲劳 II ✓）*/
    public static final float T0 = 33.0F;
    public static final float T1 = 34.5F;
    public static final float T2 = 35.7F;
    public static final float T3 = 36.4F;
    public static final float T4 = 37.3F;
    public static final float T5 = 38.0F;
    /** 兜底范围（度）：正常玩碰不到，防呆用 */
    private static final float HARD_MIN = 30.0F;
    private static final float HARD_MAX = 39.5F;
    /** 每 tick 最大变化量（度）：防止卡顿后一步跳过去 */
    private static final float MAX_STEP = 0.02F;
    /** 药水效果时长（tick）：每 20 tick 刷新一次，60 足够不断档 */
    private static final int EFFECT_TICKS = 60;
    /** 存档键 */
    private static final String NBT_KEY = "CMBodyTemp";

    private CMBodyTemp() {}

    // ---- 配置 ----

    /** 体温系统是否启用（四季关掉时一并关掉） */
    public static boolean enabled() {
        if (!CMAmbient.enabled()) return false;
        return CMConfig.getFloat("temp.body_enabled", 1.0F) != 0.0F;
    }

    /** 逼近系数（每 tick，默认 0.0005，约 100 秒走 63%） */
    private static float bodyRate() {
        float v = CMConfig.getFloat("temp.body_rate", 0.0005F);
        if (v < 0.00001F) v = 0.00001F;
        if (v > 0.05F) v = 0.05F;
        return v;
    }

    /** 岩浆探测半径（格，默认 12，用户指定） */
    private static int lavaRadius() {
        int v = (int) CMConfig.getFloat("temp.lava_radius", 12.0F);
        if (v < 1) v = 1;
        if (v > 32) v = 32;
        return v;
    }

    /** ★ 运动产热：在动的时候把体温往正常拉，最多拉到正常（度，默认 1.2）*/
    private static float exerciseWarm() {
        // ★ 用户实测：「跑动还是会降温」✗ ⇒ 1.2 抵不过环境的拉力 ⇒ 默认提到 4.0 ✓
        //   （4.0 足够把"偏冷环境"的 target 拉回正常档 ✓ 但仍然**封顶在正常** ✓ 不会变热 ✓）
        return CMConfig.getFloat("temp.exercise_warm", 4.0F);
    }

    /** ★ 降温速率倍率（用户 2026-10-01 定稿：空气 3 分钟 / 泡水 1 分钟掉 1 度 ✓）
     *  推导：体温按指数逼近目标 ⇒ 38→37 空气中(目标36.4) 需 0.98 个时间常数
     *        body_rate 0.0005 ⇒ τ≈100s ⇒ 0.98τ≈98s（1.6 分钟）✗ 太快
     *        ⇒ 空气乘 0.55 ⇒ τ≈180s ⇒ ≈3.0 分钟 ✓
     *        水里目标 35.7，38→37 只需 0.57τ ⇒ 乘 0.95 ⇒ τ≈105s ⇒ ≈1.0 分钟 ✓
     */
    private static float coolAirMult() { return CMConfig.getFloat("temp.cool_air_mult", 0.55F); }
    private static float coolWaterMult() { return CMConfig.getFloat("temp.cool_water_mult", 0.95F); }

    /** 同一档待多久开始加速（tick，默认 6000 = 真实 5 分钟，用户指定） */
    private static int accelerateAfterTicks() {
        int v = (int) CMConfig.getFloat("temp.accelerate_after_ticks", 6000.0F);
        if (v < 200) v = 200;
        return v;
    }

    /** 加速阶段把目标往极端推多少（度，默认 1.0） */
    private static float acceleratePush() {
        return CMConfig.getFloat("temp.accelerate_push", 1.0F);
    }

    // ---- 雨雪（用户 2026-10-01 定稿：雨 -3、雷暴 -4、雪 -5；体温再各降 0.3 / 0.6）----

    /** 淋湿 / 干透要多久（tick，默认 600 = 真实 30 秒；两头都是斜坡，防止进进出出刷状态） */
    private static int wetRampTicks() {
        int v = (int) CMConfig.getFloat("temp.wet_ramp_ticks", 600.0F);
        if (v < 20) v = 20;
        return v;
    }

    /** 雨雪对**环境温度**的削减（度）：1 = 小雨/普通雨、2 = 雷暴、3 = 下雪 */
    private static float wetAmbientPenalty(int kind) {
        switch (kind) {
            case 1: return CMConfig.getFloat("temp.rain_ambient", 3.0F);
            case 2: return CMConfig.getFloat("temp.storm_ambient", 4.0F);
            case 3: return CMConfig.getFloat("temp.snow_ambient", 5.0F);
            default: return 0.0F;
        }
    }

    /** 雨雪对**体温**的直接削减（度，保底用：夏天按不对称公式几乎看不出降温） */
    private static float wetBodyPenalty(int kind) {
        switch (kind) {
            case 1:
            case 2: return CMConfig.getFloat("temp.rain_body", 0.30F);
            case 3: return CMConfig.getFloat("temp.snow_body", 0.60F);
            default: return 0.0F;
        }
    }

    // ---- 状态（按玩家名；服务端算；客户端读同一份） ----

    private static final class State {
        float temp = NORMAL;
        /** 第二档连续待了多久（tick） */
        int coldExposure = 0;
        /** 第五档连续待了多久（tick） */
        int heatExposure = 0;
        /** 上次算出来的档位 */
        int tier = 3;
        /** 我们上一次给每个药水设的等级（-1 = 没给） */
        int[] applied = new int[]{ -1, -1, -1, -1, -1, -1 };   // ★ 加了挖掘疲劳 ⇒ 6 个 ✓
        /** 岩浆探测的缓存（每 10 tick 才扫一次） */
        boolean lavaNear = false;
        /** 雨雪类型（每 10 tick 探一次）：0 = 没淋到、1 = 小雨/普通雨、2 = 雷暴、3 = 下雪 */
        int wetKind = 0;
        /** 淋湿进度（0 ~ temp.wet_ramp_ticks） */
        int wetTicks = 0;
        /** 上一次写日志的 tick */
        long logTick = -100000L;
        /** ★ 上一 tick 的位置（运动产热用 ✓）*/
        double lastX = 0.0D, lastZ = 0.0D;
        boolean posInited = false;
        /** ★ 上一帧的档位（极端档提示用 ✓）*/
        int lastTier = 3;
    }

    private static final HashMap<String, State> STATES = new HashMap<String, State>();

    private static State state(EntityPlayer p) {
        String k = key(p);
        State s = STATES.get(k);
        if (s == null) {
            s = new State();
            STATES.put(k, s);
        }
        return s;
    }

    private static String key(EntityPlayer p) {
        try {
            String n = p.getCommandSenderName();
            if (n != null && n.length() > 0) return n;
        } catch (Throwable t) {
            // 忽略：退回 identityHashCode
        }
        return "#" + System.identityHashCode(p);
    }

    // ---- 缓解源（用户要求留的接口） ----

    private static final ArrayList<TemperatureRelief> RELIEFS = new ArrayList<TemperatureRelief>();
    private static boolean vanillaReliefInstalled = false;

    /** 注册一个缓解源（附属 / 功能方块走这里） */
    public static void registerRelief(TemperatureRelief r) {
        if (r == null) return;
        RELIEFS.add(r);
        System.out.println("[CreateMITE][体温] 已注册缓解源: " + r.getClass().getName());
    }

    /** 内置的火堆取暖（最朴素的 A 方案；附属可以再叠加自己的） */
    private static void installVanillaRelief() {
        if (vanillaReliefInstalled) return;
        vanillaReliefInstalled = true;
        registerRelief(new TemperatureRelief() {
            @Override
            public float adjustTarget(EntityPlayer player, float target, boolean cold) {
                // ★ 用户裁定：取暖**上限 35.7**（火把目前最多把冬季体温维持在 35.7 ✓）
                //   ⚠️ 岩浆那一侧**不设上限** ✓ —— 它在后面单独覆盖成 39 ✓（见 tick ✓）
                if (cold) return cm$fireNear(player, 4) ? WATER_FLOOR : target;
                // ★ 热侧：附近有冰/雪 ⇒ 拉回正常（用户同意加的降温源 ✓）
                return cm$snowNear(player, 6) ? NORMAL : target;   // ★ 半径 4→6（用户"看不真切"）
            }

            @Override
            public float reliefStrength(EntityPlayer player, boolean cold) {
                if (cold) return cm$fireNear(player, 4) ? 1.0F : 0.0F;
                return cm$snowNear(player, 6) ? 1.0F : 0.0F;
            }
        });
    }

    /** ★ 附近有没有冰/雪（热侧的降温源 ✓；和火那套同构 ✓） */
    private static boolean cm$snowNear(EntityPlayer p, int radius) {
        World w = p.worldObj;
        if (w == null) return false;
        String k = key(p) + "#snow";
        int tick = p.ticksExisted;
        int[] c = FIRE_CACHE.get(k);
        if (c != null && tick - c[0] < 10) return c[1] != 0;
        boolean found = false;
        int px = (int) Math.floor(p.posX);
        int py = (int) Math.floor(p.posY + 1.0D);
        int pz = (int) Math.floor(p.posZ);
        outer:
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    int id = w.getBlockId(px + dx, py + dy, pz + dz);
                    if (id <= 0) continue;
                    Block b = Block.blocksList[id];
                    if (b == null) continue;
                    if (b.blockMaterial == Material.ice || b.blockMaterial == Material.snow
                            || b.blockMaterial == Material.craftedSnow) {
                        found = true;
                        break outer;
                    }
                }
            }
        }
        FIRE_CACHE.put(k, new int[]{ tick, found ? 1 : 0 });
        return found;
    }

    /** 附近有没有火（火方块 / 火材质；每 10 tick 才真扫一次） */
    private static final HashMap<String, int[]> FIRE_CACHE = new HashMap<String, int[]>();

    private static boolean cm$fireNear(EntityPlayer p, int radius) {
        World w = p.worldObj;
        if (w == null) return false;
        String k = key(p);
        int tick = p.ticksExisted;
        int[] c = FIRE_CACHE.get(k);
        if (c != null && tick - c[0] < 10) return c[1] != 0;

        boolean found = false;
        int px = (int) Math.floor(p.posX);
        int py = (int) Math.floor(p.posY + 1.0D);
        int pz = (int) Math.floor(p.posZ);
        outer:
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    int id = w.getBlockId(px + dx, py + dy, pz + dz);
                    if (id <= 0) continue;
                    Block b = Block.blocksList[id];
                    if (b == null) continue;
                    // ★ 用户 2026-10-01：「火堆是啥玩意？」⇒ 原来只认"火方块"，太窄 ✗
                    //   ⇒ 现在**火把也算** ✓（MITE 玩家实际点的是火把 ✓）
                    if (b == Block.fire || b.blockMaterial == Material.fire || b == Block.torchWood) {
                        found = true;
                        break outer;
                    }
                }
            }
        }
        FIRE_CACHE.put(k, new int[]{ tick, found ? 1 : 0 });
        return found;
    }

    // ---- 每 tick ----

    /**
     * 每 tick 一次（由 BodyTempPlayerMixin 挂在 EntityPlayer.onUpdate() 头上）。
     * 只对服务端的 ServerPlayer 干活（客户端不重复算，免得两边抢同一个数值）。
     */
    public static void tick(EntityPlayer p) {
        if (p == null) return;
        if (!(p instanceof ServerPlayer)) return;
        // ★ 2026-10-02 用户新方案：**体感温度系统**（瞬时值 ✓）在这里每 tick 跑一次 ✓
        //   （它自己会判维度/开关 ✓ 只做掉血与回血 ✓ 饥饿走 BodyTempHungerMixin ✓）
        try { CMAmbientFeel.tick(p); } catch (Throwable ignored) { }
        World w = p.worldObj;
        if (w == null || w.isRemote) return;
        if (!enabled()) return;

        installVanillaRelief();


        State s = state(p);
        // ★ 用户裁定：**下界 / 地下世界 ⇒ 环境温度与体温一律按正常值**（无 buff 也无增幅 ✓）
        //   顺带把"切维度"那条一起解决了 ✓（切过去体温直接归正常 ✓）
        int dim = 0;
        try { dim = w.provider.dimensionId; } catch (Throwable ignored) { }
        if (dim != 0) {
            s.temp = NORMAL;
            s.coldExposure = 0;
            s.heatExposure = 0;
            s.tier = 3;
            s.lastTier = 3;
            if ((p.ticksExisted % 20) == 0) applyEffects((ServerPlayer) p, s);
            return;
        }
        float ambient = CMAmbient.current(w);

        // 雨雪：被淋湿的体感修正（用户 2026-10-01 定稿）
        //   javap 实证：isPrecipitatingAt(x,y,z) / isInRain(x,y,z) **自带"头顶见不见天"判定**
        //   ⇒ 站屋檐下 / 洞里 / 树底下不吃这一条（天然遮蔽 = 天然逃逸手段）
        if ((p.ticksExisted % 10) == 0) s.wetKind = cm$wetKind(p, w);
        int wetRamp = wetRampTicks();
        if (s.wetKind > 0) {
            if (s.wetTicks < wetRamp) s.wetTicks++;
        } else if (s.wetTicks > 0) {
            s.wetTicks--;
        }
        float wet = (wetRamp <= 0) ? 0.0F : (s.wetTicks / (float) wetRamp);
        float wetAmbient = wetAmbientPenalty(s.wetKind) * wet;
        float wetBody = wetBodyPenalty(s.wetKind) * wet;

        float target = targetFromAmbient(ambient - wetAmbient) - wetBody;
        boolean cold = target < NORMAL;

        // 缓解源（A 接口；内置火堆取暖）
        float relief = 0.0F;
        for (int i = 0; i < RELIEFS.size(); i++) {
            TemperatureRelief r = RELIEFS.get(i);
            try {
                target = r.adjustTarget(p, target, cold);
                float str = r.reliefStrength(p, target < NORMAL);
                if (str > relief) relief = str;
            } catch (Throwable t) {
                System.out.println("[CreateMITE][体温] 缓解源出错（已忽略）: " + t);
            }
        }
        // ★ 运动产热 / 睡觉回温（用户同意 ✓ —— 给"自救"补上闭环 ✓）
        if (cold) {
            if (!s.posInited) { s.lastX = p.posX; s.lastZ = p.posZ; s.posInited = true; }
            double moved = Math.abs(p.posX - s.lastX) + Math.abs(p.posZ - s.lastZ);
            if (moved > 0.08D) {
                float warmed = target + exerciseWarm();
                target = Math.min(warmed, NORMAL);        // ★ 最多拉回正常，不会把你弄热 ✓
            }
            if (p.isSleeping()) target = NORMAL;    // ★ 睡觉 ⇒ 直接回正常 ✓
        }
        s.lastX = p.posX; s.lastZ = p.posZ; s.posInited = true;

        target = clamp(target);
        cold = target < NORMAL;

        // 持续 5 分钟则加速（B 方案）
        int tier = tierOf(s.temp);
        if (tier == 2) s.coldExposure++; else s.coldExposure = 0;
        if (tier == 5) s.heatExposure++; else s.heatExposure = 0;
        if (relief >= 1.0F) {
            s.coldExposure = 0;
            s.heatExposure = 0;
        }

        float rateMult = 1.0F;
        int after = accelerateAfterTicks();
        if (cold && s.coldExposure > after) {
            float ramp = Math.min(1.0F, (s.coldExposure - after) / (float) after);
            target -= acceleratePush() * ramp;
            rateMult += 2.0F * ramp;
        } else if (!cold && s.heatExposure > after) {
            float ramp = Math.min(1.0F, (s.heatExposure - after) / (float) after);
            target += acceleratePush() * ramp;
            rateMult += 2.0F * ramp;
        }

        // 水 / 岩浆（用户裁定：泡水时水优先；放在加速之后，泡水天然能掐掉加速）
        if (p.isInWater()) {
            target = WATER_FLOOR;
        } else {
            if ((p.ticksExisted % 10) == 0) s.lavaNear = cm$lavaNear(p, w);
            if (s.lavaNear) target = LAVA_TARGET;
        }
        target = clamp(target);

        // 逐渐逼近（指数 + 每 tick 上限）
        float diff = target - s.temp;
        // ★ 饿肚子更怕冷（用户 2026-10-01：可以，但**别降得很快** ✓）⇒ 只 +15% ✓
        if (diff < 0.0F) {
            try {
                if (p.getFoodStats() != null && p.getFoodStats().getSatiation() <= 6) rateMult += 0.15F;
            } catch (Throwable ignored) { }
        }
        // ★ 降温速率（用户定稿：**空气 3 分钟 / 泡水 1 分钟**掉 1 度 ✓ —— 推导见 coolAirMult 注释 ✓）
        float coolMult = 1.0F;
        if (diff < 0.0F) coolMult = p.isInWater() ? coolWaterMult() : coolAirMult();
        float step = diff * bodyRate() * rateMult * coolMult;
        if (step > MAX_STEP) step = MAX_STEP;
        else if (step < -MAX_STEP) step = -MAX_STEP;
        s.temp = clamp(s.temp + step);

        // 每秒刷一次效果（服务端）
        if ((p.ticksExisted % 20) == 0) {
            s.tier = tierOf(s.temp);
            // ★★ 2026-10-02 用户切换方案：**旧的体温六档 buff 已停用** ✗
            //   （体感温度系统接手 ✓ 见 CMAmbientFeel；这里不再上/下 buff ✓
            //    原来给过的药水效果会在 60 tick 内自然消失 ✓ 不用手动清 ✓）
            // applyEffects((ServerPlayer) p, s);
            // ★ 极端档提示（用户原话 ✓ 只在**刚跨进**极端档时发一次 ✓）
            if (s.tier == 0 && s.lastTier != 0) {
                try {
                    p.sendChatToPlayer(net.minecraft.ChatMessageComponent.createFromText(
                            "§b我觉得我得去暖和点的地方了"));
                } catch (Throwable ignored) { }
            }
            s.lastTier = s.tier;
        }

        cm$diagnostics(p, s, ambient, target, relief);
    }

    private static float clamp(float v) {
        if (v < HARD_MIN) return HARD_MIN;
        if (v > HARD_MAX) return HARD_MAX;
        return v;
    }

    /** 环境温度 到 目标体温（用户要求的不对称公式） */
    public static float targetFromAmbient(float ambientC) {
        float delta;
        if (ambientC >= 0.0F) {
            delta = NORMAL * ambientC / 1000.0F;
        } else {
            delta = NORMAL * ambientC / 100.0F;
        }
        return NORMAL + delta;
    }

    /**
     * 玩家这一刻被什么淋着（0 = 没淋到、1 = 雨、2 = 雷暴、3 = 雪）。
     *
     * MITE 现成的三个方法就够，不用自己判屋顶：
     *   isPrecipitatingAt(x,y,z) = 群系有降雨 且 正在降水 且 **y 在降水高度之上（= 露天）**
     *   isSnowing(x,z)           = 正在降水 且 该列**冻结**（我们的冬季钳温会让它成立）
     *   isInRain(x,y,z)          = 同上但要求**非冻结** ⇒ 就是"下雨"而不是"下雪"
     */
    private static int cm$wetKind(EntityPlayer p, World w) {
        int px = (int) Math.floor(p.posX);
        int py = (int) Math.floor(p.posY + 1.0D);
        int pz = (int) Math.floor(p.posZ);
        try {
            if (!w.isPrecipitatingAt(px, py, pz)) return 0;
            if (w.isSnowing(px, pz)) return 3;
            if (w.isInRain(px, py, pz)) {
                return (w.getPrecipitationType(0) == 3) ? 2 : 1;   // 3 = 雷暴事件
            }
        } catch (Throwable t) {
            return 0;
        }
        return 0;
    }

    /** 雨雪类型名（报告 / 日志用） */
    public static String wetName(int kind) {
        switch (kind) {
            case 1: return "雨";
            case 2: return "雷暴";
            case 3: return "雪";
            default: return "无";
        }
    }

    /** 附近有没有岩浆（球体采样，步长 2，每 10 tick 一次，开销可忽略） */
    private static boolean cm$lavaNear(EntityPlayer p, World w) {
        int r = lavaRadius();
        int px = (int) Math.floor(p.posX);
        int py = (int) Math.floor(p.posY + 1.0D);
        int pz = (int) Math.floor(p.posZ);
        for (int dy = -r; dy <= r; dy += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                for (int dx = -r; dx <= r; dx += 2) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    int id = w.getBlockId(px + dx, py + dy, pz + dz);
                    if (id <= 0) continue;
                    Block b = Block.blocksList[id];
                    if (b != null && b.blockMaterial == Material.lava) return true;
                }
            }
        }
        return false;
    }

    // ---- 档位 / 效果 ----

    /** 体温 到 档位（1~6；35.7 归第三档，用户裁定合并） */
    public static int tierOf(float t) {
        if (t < T0) return 0;      // ★ 极端档（用户 2026-10-01 新增 ✓）
        if (t < T1) return 1;
        if (t < T2) return 2;
        if (t <= T3) return 3;
        if (t <= T4) return 4;
        if (t <= T5) return 5;
        return 6;
    }

    public static String tierName(int tier) {
        switch (tier) {
            case 0: return "极冷（缓慢 II + 挖掘疲劳 II）";
            case 1: return "过冷（缓慢 I）";
            case 2: return "偏冷（速度 I + 急迫 I）";
            case 3: return "正常";
            case 4: return "偏热（速度 I + 急迫 II）";
            case 5: return "发热（缓慢 I）";
            default: return "高烧（缓慢 II + 失明 + 反胃）";
        }
    }

    /** 饥饿消耗倍率（用户给的六档） */
    public static float hungerMultiplierOf(int tier) {
        switch (tier) {
            case 0: return 1.5F;
            case 1: return 1.5F;
            case 2: return 1.2F;
            case 4: return 0.8F;
            case 5: return 1.2F;
            case 6: return 1.2F;
            default: return 1.0F;
        }
    }

    /**
     * ★ HUD 边框取数（2026-10-01 用户定稿：冷=浅蓝→深蓝 / 热=浅红→深红 ✓
     *   "同一档待满 5 分钟越深" ✓）
     *
     * @return {强度 0..1, 冷=+1 热=-1}；不在"过冷/过热"档 ⇒ null（不画 ✓）
     */
    public static float[] borderInfo() {
        try {
            if (!enabled()) return null;
            net.minecraft.EntityPlayer p = net.minecraft.Minecraft.getMinecraft().thePlayer;
            if (p == null) return null;
            State s = STATES.get(key(p));
            if (s == null) return null;
            int tier = tierOf(s.temp);
            // ★ 用户 2026-10-01 反馈：「圈应该**一直有，直到恢复正常体温**」✓
            //   ⇒ 只有**正常档（3）不画** ✗，其余全画，按偏离程度分浓淡 ✓
            if (tier == 3) return null;
            boolean cold = (tier <= 2);
            float base;
            if (tier == 0 || tier == 6) base = 0.55F;          // 极冷 / 高烧
            else if (tier == 1 || tier == 5) base = 0.28F;     // 过冷 / 发热
            else base = 0.10F;                                 // 偏冷 / 偏热（很淡 ✓）
            int exposure = cold ? s.coldExposure : s.heatExposure;
            int after = accelerateAfterTicks();
            float expo = after <= 0 ? 0.0F : Math.min(1.0F, exposure / (float) after);
            float strength = Math.min(1.0F, base + expo * 0.6F);
            return new float[]{ strength, cold ? 1.0F : -1.0F };
        } catch (Throwable t) {
            return null;
        }
    }

    /** 这个玩家现在的饥饿消耗倍率（给 BodyTempHungerMixin 用） */
    public static float hungerRateMultiplier(EntityPlayer p) {
        if (p == null) return 1.0F;
        // ★ 2026-10-02：改由**体感温度**的效率表给值 ✓（旧的体温六档倍率已弃用 ✗）
        if (CMSeasons.enabled()) {
            return CMAmbientFeel.hungerMultiplier(CMAmbientFeel.tierOf(CMAmbientFeel.feel(p)));
        }
        return 1.0F;
    }

    /** 这个药水在这一档应该是几级（-1 = 不该有） */
    private static int ampForTier(int tier, int potionId) {
        if (potionId == Potion.moveSlowdown.id) {
            if (tier == 0) return 1;          // ★ 缓慢 II ✓
            if (tier == 1 || tier == 5) return 0;
            if (tier == 6) return 1;
            return -1;
        }
        if (potionId == Potion.moveSpeed.id) {
            return (tier == 2 || tier == 4) ? 0 : -1;
        }
        if (potionId == Potion.digSpeed.id) {
            if (tier == 2) return 0;
            if (tier == 4) return 1;
            return -1;
        }
        if (potionId == Potion.digSlowdown.id) return tier == 0 ? 1 : -1;   // ★ 挖掘疲劳 II ✓
        if (potionId == Potion.blindness.id) return tier == 6 ? 0 : -1;
        if (potionId == Potion.confusion.id) return tier == 6 ? 0 : -1;
        return -1;
    }

    private static int[] tracked = null;

    private static int[] trackedPotions() {
        if (tracked == null) {
            tracked = new int[]{
                Potion.moveSlowdown.id,
                Potion.moveSpeed.id,
                Potion.digSpeed.id,
                Potion.blindness.id,
                Potion.confusion.id,
                Potion.digSlowdown.id,      // ★ 2026-10-01 新增（极端档的挖掘疲劳 ✓）
            };
        }
        return tracked;
    }

    /**
     * 按档位上 / 下 buff（服务端）。
     * 只会撤掉我们自己给过的那一级，玩家自己喝的药水（等级不一样）不动。
     */
    private static void applyEffects(ServerPlayer p, State s) {
        int tier = s.tier;
        int[] ids = trackedPotions();
        for (int i = 0; i < ids.length; i++) {
            int want = ampForTier(tier, ids[i]);
            int have = s.applied[i];
            if (want == have) {
                if (want >= 0) {
                    p.addPotionEffect(new PotionEffect(ids[i], EFFECT_TICKS, want, true));
                }
                continue;
            }
            if (have >= 0) {
                PotionEffect cur = p.getActivePotionEffect(ids[i]);
                if (cur != null && cur.getAmplifier() == have) p.removePotionEffect(ids[i]);
            }
            if (want >= 0) {
                p.addPotionEffect(new PotionEffect(ids[i], EFFECT_TICKS, want, true));
            }
            s.applied[i] = want;
        }
    }

    // ---- 交给外部的读数 ----

    /** 显示用体温（度；还没数据就返回 NaN = 未知） */
    public static float displayTemperature(EntityPlayer p) {
        if (p == null || !enabled()) return Float.NaN;
        State s = STATES.get(key(p));
        return (s == null) ? Float.NaN : s.temp;
    }

    /** 调试用：直接改体温（命令 se T 值） */
    public static void setTemperature(EntityPlayer p, float v) {
        State s = state(p);
        s.temp = clamp(v);
        s.coldExposure = 0;
        s.heatExposure = 0;
        s.tier = tierOf(s.temp);
        if (p instanceof ServerPlayer) applyEffects((ServerPlayer) p, s);
    }

    /** 给命令报告用的一行 */
    public static String reportLine(EntityPlayer p) {
        if (!enabled()) return "体温：已关闭（config/createmite.properties 里 temp.body_enabled = 0）";
        State s = (p == null) ? null : STATES.get(key(p));
        if (s == null) return "体温：暂无数据（服务端还没 tick 到）";
        int tier = tierOf(s.temp);
        return "体温：" + CMAmbient.fmt(s.temp) + " 度 ｜ " + tierName(tier)
                + " ｜ 饥饿 x" + hungerMultiplierOf(tier)
                + " ｜ 暴露计时 冷/热 = " + s.coldExposure + "/" + s.heatExposure + " tick"
                + " ｜ 雨雪：" + wetName(s.wetKind) + "（" + s.wetTicks + "/" + wetRampTicks() + "）";
    }

    private static void cm$diagnostics(EntityPlayer p, State s, float ambient, float target, float relief) {
        long now = p.worldObj.getTotalWorldTime();
        if (now - s.logTick < 600L) return;
        s.logTick = now;
        float wet = (wetRampTicks() <= 0) ? 0.0F : (s.wetTicks / (float) wetRampTicks());
        System.out.println("[CreateMITE][体温] " + key(p)
                + " 环境=" + CMAmbient.fmt(ambient) + " 昼夜因子=" + CMAmbient.fmt(CMAmbient.nightFactor(p.worldObj))
                + " 雨雪=" + wetName(s.wetKind) + "(" + s.wetTicks + ")"
                + " 体感=" + CMAmbient.fmt(ambient - wetAmbientPenalty(s.wetKind) * wet)
                + " 体温=" + CMAmbient.fmt(s.temp) + " 目标=" + CMAmbient.fmt(target)
                + " 档=" + s.tier + " 水中=" + p.isInWater() + " 岩浆近=" + s.lavaNear
                + " 缓解=" + relief + " 冷暴露=" + s.coldExposure + " 热暴露=" + s.heatExposure);
    }

    // ---- 存盘（用户裁定存盘） ----

    public static void writeNBT(EntityPlayer p, NBTTagCompound nbt) {
        if (p == null || nbt == null) return;
        State s = STATES.get(key(p));
        if (s == null) return;
        try {
            nbt.setFloat(NBT_KEY, s.temp);
        } catch (Throwable t) {
            // 忽略
        }
    }

    public static void readNBT(EntityPlayer p, NBTTagCompound nbt) {
        if (p == null || nbt == null) return;
        try {
            if (!nbt.hasKey(NBT_KEY)) return;
            State s = state(p);
            s.temp = clamp(nbt.getFloat(NBT_KEY));
            s.tier = tierOf(s.temp);
        } catch (Throwable t) {
            // 忽略
        }
    }
}
