package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.EntityPlayer;
import net.minecraft.World;

/**
 * ★★ 方块自发热 / 吸热系统（2026-10-05 用户拍板 ✓ 第一批）
 *
 * 【用户定稿的六条规则】
 *   ① 方块**自己会发热**、**自带明确半径**（例：火把 = 半径 2 ✓）
 *   ② 衰减 = **C 方案**：半径内**前 60% 全额**，最后一圈（外侧 40%）**线性衰减到 0** ✓
 *   ③ **同类之间不叠加**（含同种方块）⇒ 热源取**最大**、冷源取**最冷** ✓
 *      ★★ **热与冷相互抵消** ⇒ 两者**相加** ✓
 *         （用户 2026-10-05 原话：「同时挨着热源和冷源应该是相互抵消才对，而不是谁强谁存在」
 *           例：同时处在岩浆(+20)与冰(−6)范围内 ⇒ **+20 − 6 = +14** ✓）
 *   ④ **物品的暖独立一项**（暖手石等，见 {@link #itemHeat} ✓ **不受 ③ 限制** ✓）
 *   ⑤ **不做遮挡判定** ⇒ 隔着墙也算取暖 ✓（用户：省事，且很正常 ✓）
 *   ⑥ 冷源**不设门槛** ✓（用户原话："双重惩罚就双重" —— 反正后面有暖手石 ✓）
 *
 * 【本轮做的方块】火把 / 火 / 岩浆 / 雪层 / 雪块 / 冰 / 水 ✓
 *   ⚠️ 熔炉家族（6 材质 × idle/burning **两套 ID**）放**下一批** ✗
 *   ⚠️ 大熔炉（按热力池动态发热）也放下一批 ✗
 *
 * 【历史】上一代「体温系统」的 TemperatureRelief 接口已随体温系统一起删掉
 *   那个是上一代"体温系统"的玩家侧缓解接口 ✗（挂在已停用的 CMAmbientFeel 上 ✓）
 *   用户 2026-10-05 明确：**改成"方块自发热"** ⇒ 本类自己一张表 ✓ 不复活那套 ✓
 *
 * 【性能】场上最大半径 = 岩浆的 4 ⇒ 球内约 250 格 ✓ 每 10 tick 算一次 ✓ 可忽略 ✓
 * 【不存盘】体感是瞬时值 ⇒ 这里也只现算 + 缓存 ✓ 没有新存档字段 ✓
 */
public final class CMHeat {

    private CMHeat() {}

    /** 表项：{发热值 ℃（可以为负 = 吸热）, 半径（格）} */
    private static final java.util.HashMap<Integer, float[]> TABLE = new java.util.HashMap<Integer, float[]>();
    /** 方块 ID -> 中文名（报告 / 诊断用 ✓）*/
    private static final java.util.HashMap<Integer, String> NAMES = new java.util.HashMap<Integer, String>();
    /** 方块 ID -> ASCII 键名（日志用 ✓ 中文进日志会被毁成 U+FFFD ✗）*/
    private static final java.util.HashMap<Integer, String> KEYS = new java.util.HashMap<Integer, String>();

    private static boolean inited = false;
    private static int maxRadius = 1;

    // ---- 缓存：key = entityId，值 = {ticksExisted 的桶, 值, 名字} ----
    private static final java.util.HashMap<Integer, Object[]> CACHE = new java.util.HashMap<Integer, Object[]>();

    private static void reg(Block b, String key, String name, float heat, float radius) {
        if (b == null) return;
        float[] v = cfg(key, heat, radius);
        if (v[0] == 0.0F) return;                       // 配置成 0 = 关掉这一项 ✓
        TABLE.put(Integer.valueOf(b.blockID), v);
        NAMES.put(Integer.valueOf(b.blockID), name);
        KEYS.put(Integer.valueOf(b.blockID), key);
        if (v[1] > maxRadius) maxRadius = Math.round(v[1]);
    }

    /** 读配置 "值,半径" ✓（缺省用代码里的默认值 ✓）*/
    private static float[] cfg(String key, float dv, float dr) {
        try {
            String s = CMConfig.getString("heat." + key, null);
            if (s == null) return new float[]{dv, dr};
            String[] a = s.split(",");
            float v = Float.parseFloat(a[0].trim());
            float r = a.length > 1 ? Float.parseFloat(a[1].trim()) : dr;
            return new float[]{v, Math.max(0.0F, r)};
        } catch (Throwable t) {
            return new float[]{dv, dr};
        }
    }

    private static void ensureInit() {
        if (inited) return;
        inited = true;
        // ★ 只登记"真的会发热/吸热"的方块 ✓ 名单见交接文档的普查表 ✓
        reg(Block.torchWood, "torch",      "火把",   1.0F,  2.0F);
        reg(Block.fire,      "fire",       "火",    12.0F,  3.0F);
        reg(Block.lavaMoving,"lava",       "岩浆",  20.0F,  4.0F);
        reg(Block.lavaStill, "lava",       "岩浆",  20.0F,  4.0F);
        try {
            // (营火改到方块注册阶段建 ✓ 这里不再调)
            if (net.dsh.createmite.campfire.BlockCampfire.lit() != null) {
                reg(net.dsh.createmite.campfire.BlockCampfire.lit(), "campfire", "篝火", 12.0F, 12.0F);
            }
        } catch (Throwable ignored) { }
        try {
            // (营火改到方块注册阶段建 ✓ 这里不再调)
            if (net.dsh.createmite.campfire.BlockCampfire.lit() != null) {
                reg(net.dsh.createmite.campfire.BlockCampfire.lit(), "campfire", "篝火", 12.0F, 12.0F);
            }
        } catch (Throwable ignored) { }
        reg(Block.snow,      "snow",       "雪层",  -3.0F,  3.0F);
        reg(Block.blockSnow, "snow_block", "雪块",  -5.0F,  3.0F);
        reg(Block.ice,       "ice",        "冰",    -6.0F,  3.0F);
        reg(Block.waterMoving, "water",    "水",    -6.0F,  0.0F);   // 半径 0 = 只有自己那一格 ✓
        reg(Block.waterStill,  "water",    "水",    -6.0F,  0.0F);
        System.out.println("[MITE][HEAT] 方块发热表已建：" + TABLE.size() + " 项，最大半径 " + maxRadius);
    }

    /**
     * ★ C 方案衰减：半径内前 60% 全额，最后一圈线性衰减到 0 ✓
     *   （用户 2026-10-05 选定 ✓；heat.falloff = 0 可退回"一刀切" ✓）
     */
    private static float falloff(float v, float d, float r) {
        if (CMConfig.getFloat("heat.falloff", 1.0F) == 0.0F) return v;    // 一刀切 ✓
        if (r <= 0.0F) return v;
        float plateau = r * 0.6F;
        if (d <= plateau) return v;
        float k = (r - d) / (r - plateau);          // 1 → 0 ✓
        if (k < 0.0F) k = 0.0F;
        return v * k;
    }

    /** 玩家这一刻受到的**方块**热值（℃ ✓ 正 = 变暖、负 = 变冷 ✓）*/
    public static float blockHeat(EntityPlayer p) {
        if (CMConfig.getFloat("heat.enabled", 1.0F) == 0.0F) return 0.0F;   // 总开关 ✓
        return ((Number) cache(p)[1]).floatValue();
    }

    /** 起作用的是哪个方块（报告用 ✓ 中文 ✓ 没命中返回 null ✓）*/
    public static String sourceName(EntityPlayer p) {
        return (String) cache(p)[2];
    }

    /** ★ ASCII 键名（**日志用** ✓ 中文进日志会被毁成 U+FFFD、搜不到 ✗）*/
    public static String sourceKey(EntityPlayer p) {
        return (String) cache(p)[3];
    }

    // ================= ★ 物品那一项：暖手石（**独立在方块规则之外** ✓ 用户 2026-10-05 ✓）=================
    //  用户规格：**可用两次**，每次 **3 分钟 +3℃** ✓；**不可叠加**、**有冷却**（必须等上一次结束才能再用 ✓）
    //  状态就放这里（内存 ✓ 用**世界总时刻**判到期 ⇒ 重登/换维度都不会错乱 ✓）；
    //  剩余次数存在**物品自己的 NBT** 上（见 ItemHandWarmer ✓）
    //  ★★ 2026-10-05 深夜修正：**服务端和客户端各用一张表** ✗✗（这是"暖手石不能用"的真凶 ✓）
    //    单机里客户端与服务端**是同一个 JVM** ⇒ 共用一张表时，**客户端那次点击会先把表点着** ✗
    //    ⇒ 服务端紧接着就判成"还在热着"⇒ 直接 return ✗ ⇒ 次数不扣、体感也不加 ✓
    //    ⇒ 拆成两张：服务端只写 S 表、客户端只写 C 表 ⇒ **互不干扰** ✓
    private static final java.util.HashMap<Integer, long[]> ITEM_WARM_S = new java.util.HashMap<Integer, long[]>();
    private static final java.util.HashMap<Integer, long[]> ITEM_WARM_C = new java.util.HashMap<Integer, long[]>();

    /** 这一侧该用哪张表 ✓（客户端 / 服务端各一张 ✓）*/
    private static java.util.HashMap<Integer, long[]> itemMap(EntityPlayer p) {
        return (p.worldObj != null && p.worldObj.isRemote) ? ITEM_WARM_C : ITEM_WARM_S;
    }

    /** 现在有没有"物品"在发热 ✓（暖手石 ✓ **本侧**的状态 ✓ 用做冷却判定 ✓）*/
    public static boolean isItemWarmActive(EntityPlayer p) {
        return itemWarmUntil(p) > 0L && p != null && p.worldObj != null
                && p.worldObj.getTotalWorldTime() < itemWarmUntil(p);
    }

    /**
     * 这次"物品发热"什么时候结束（**世界总时刻** ✓；没有就 0 ✓）
     *   给冷却条用 ✓（ItemHandWarmer.tickCooldownBar ✓）
     *   ⚠️ 是**玩家级**的：不管手里是哪一块暖手石，冷却都是同一个 ✓（用户要求 ✓）
     */
    public static long itemWarmUntil(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 0L;
        long now = p.worldObj.getTotalWorldTime();
        long[] e = itemMap(p).get(Integer.valueOf(p.entityId));
        if (e != null) {
            if (now < e[0]) return e[0];
            itemMap(p).remove(Integer.valueOf(p.entityId));
        }
        // ★★ 2026-10-05 深夜③：**客户端读服务端那张表** ✗✗（冷却条必须这样才画得出来 ✓）
        //   反汇编实证：Item.onItemRightClick 的调用者只有 EntityPlayer（**服务端那趟** ✓）
        //   ⇒ 客户端**从来不会**跑我们的回调 ⇒ 客户端那张表永远是空的 ✗
        //   ⇒ 单机里两者是**同一个 JVM** ⇒ 客户端直接读服务端那张表 ✓（不用发包 ✓）
        //   ⚠️ 只在客户端做这个回退 ✓ 服务端一律只认自己那张 ✗（否则又会被互相挡 ✓）
        if (p.worldObj.isRemote) {
            long[] s = ITEM_WARM_S.get(Integer.valueOf(p.entityId));
            if (s != null && now < s[0]) return s[0];
        }
        // ★ 兜底：手上那块石头自己的 NBT 说还在热 ⇒ 认它 ✓（重登/换维度后也成立 ✓）
        try {
            net.minecraft.ItemStack held = p.getHeldItemStack();
            if (held != null && held.getItem() instanceof net.dsh.createmite.item.ItemHandWarmer) {
                long until = net.dsh.createmite.item.ItemHandWarmer.warmUntil(held);
                if (now < until) {
                    itemMap(p).put(Integer.valueOf(p.entityId),
                            new long[]{until, (long) (net.dsh.createmite.item.ItemHandWarmer.heat() * 100.0F)});
                    return until;
                }
            }
        } catch (Throwable ignored) { }
        return 0L;
    }

    /** 开始一次物品发热 ✓（**只写本侧那张表** ✓ 两侧数值完全一样 ✓）*/
    public static void startItemWarm(EntityPlayer p, int ticks, float heat) {
        if (p == null || p.worldObj == null) return;
        itemMap(p).put(Integer.valueOf(p.entityId),
                new long[]{p.worldObj.getTotalWorldTime() + ticks, (long) (heat * 100.0F)});
    }

    /**
     * 物品那一项（℃ ✓）—— **独立加在方块那一项之上** ✓
     *   （方块之间才"同类取最强、异类相加"✗；物品是自己的一个加项 ✓ 用户 2026-10-05 明确 ✓）
     */
    public static float itemHeat(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 0.0F;
        long now = p.worldObj.getTotalWorldTime();
        java.util.HashMap<Integer, long[]> map = itemMap(p);
        long[] e = map.get(Integer.valueOf(p.entityId));
        if (e != null) {
            if (now < e[0]) return e[1] / 100.0F;
            map.remove(Integer.valueOf(p.entityId));       // 到点了 ✓
        }
        // ★ 兜底：手里那块石头**自己的 NBT** 说还在热 ⇒ 重新起一份 ✓
        //   （换维度、重登之后 entityId 变了也不会丢 ✓ 而且服务端的 NBT 是权威的 ✓）
        try {
            net.minecraft.ItemStack held = p.getHeldItemStack();
            if (held != null && held.getItem() instanceof net.dsh.createmite.item.ItemHandWarmer) {
                long until = net.dsh.createmite.item.ItemHandWarmer.warmUntil(held);
                if (now < until) {
                    map.put(Integer.valueOf(p.entityId),
                            new long[]{until, (long) (net.dsh.createmite.item.ItemHandWarmer.heat() * 100.0F)});
                    return net.dsh.createmite.item.ItemHandWarmer.heat();
                }
            }
        } catch (Throwable ignored) { }
        return 0.0F;
    }

    /** 每 heat.interval（默认 10）tick 只算一次 ✓ 客户端每帧要问 HUD 也不怕 ✓ */
    private static Object[] cache(EntityPlayer p) {
        ensureInit();
        if (p == null || p.worldObj == null) return new Object[]{Integer.valueOf(0), Float.valueOf(0.0F), null, null};
        int interval = Math.max(1, (int) CMConfig.getFloat("heat.interval", 10.0F));
        int bucket = p.ticksExisted / interval;
        Object[] c = CACHE.get(Integer.valueOf(p.entityId));
        if (c != null && ((Integer) c[0]).intValue() == bucket) return c;
        Object[] n = scan(p, bucket);
        CACHE.put(Integer.valueOf(p.entityId), n);
        return n;
    }

    /** 真的扫一遍：以玩家为准的球内，只看表里登记过的方块 ✓ */
    private static Object[] scan(EntityPlayer p, int bucket) {
        float best = 0.0F;
        float hot = 0.0F, cold = 0.0F;              // 热的一堆取最大、冷的一堆取最冷 ✓
        String hotName = null, coldName = null;
        String hotKey = null, coldKey = null;
        try {
            World w = p.worldObj;
            int px = (int) Math.floor(p.posX);
            int py = (int) Math.floor(p.posY);
            int pz = (int) Math.floor(p.posZ);
            // ★ 营火成型检测（2026-10-07 ✓ 就挂在这里 ✓ 之前那次改没生效 ⇒ 一直没成型 ✗）
            try { net.dsh.createmite.campfire.CampfireForm.tickNear(w, p); } catch (Throwable ignored) { }
            int R = maxRadius;
            for (int dx = -R; dx <= R; dx++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int dz = -R; dz <= R; dz++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > R * R) continue;
                        int id = w.getBlockId(px + dx, py + dy, pz + dz);
                        if (id <= 0) continue;
                        float[] e = TABLE.get(Integer.valueOf(id));
                        if (e == null) continue;
                        float d = (float) Math.sqrt((double) d2);
                        if (d > e[1]) continue;
                        float v = falloff(e[0], d, e[1]);
                        // ★★ 规则③（2026-10-05 用户更正 ✓）：
                        //   ① **同类之间不叠加** ⇒ 热源里取**最大**、冷源里取**最冷** ✓
                        //   ② **热与冷相互抵消** ⇒ 最后**相加** ✓
                        //      例：同时处在岩浆(+20)与冰(−6)范围内 ⇒ **+20 − 6 = +14** ✓（用户原话 ✓）
                        if (v > 0.0F) {
                            if (v > hot) {
                                hot = v;
                                hotName = NAMES.get(Integer.valueOf(id));
                                hotKey = KEYS.get(Integer.valueOf(id));
                            }
                        } else if (v < 0.0F) {
                            if (v < cold) {                 // 越负越冷 ⇒ 取最冷 ✓
                                cold = v;
                                coldName = NAMES.get(Integer.valueOf(id));
                                coldKey = KEYS.get(Integer.valueOf(id));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            hot = 0.0F;
            cold = 0.0F;
            hotName = null;
            coldName = null;
            hotKey = null;
            coldKey = null;
        }
        // ★ 热 + 冷 = 净效果（相互抵消 ✓）；两个都有就把名字拼起来（例：岩浆+冰 ✓）
        float total = hot + cold;
        String name = null, key = null;
        if (hotName != null && coldName != null) {
            name = hotName + "+" + coldName;
            key = hotKey + "+" + coldKey;
        } else if (hotName != null) {
            name = hotName; key = hotKey;
        } else if (coldName != null) {
            name = coldName; key = coldKey;
        }
        return new Object[]{Integer.valueOf(bucket), Float.valueOf(total), name, key};
    }
}
