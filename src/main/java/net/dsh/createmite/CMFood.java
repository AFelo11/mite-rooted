package net.dsh.createmite;

import net.minecraft.EntityPlayer;
import net.minecraft.Item;

/**
 * ★★ 食物 → 体感温度（2026-10-06 用户拍板 ✓ 第三套独立系统）
 *
 * 【★ 三套系统互相独立（用户明确 ✓）】
 *   ① 方块热源（火把/火/岩浆/冰雪水…）  = **环境系统** ✓ 同类取极值、热冷相加 ✓
 *   ② 暖手石                          = **物品系统** ✓ 自带冷却条 ✓
 *   ③ 本类（食物）                    = **食物系统** ✓ 就是这一套 ✓
 *   ⇒ 三者**相加**进体感 ✓（一碗热汤 ＋ 一块暖手石 ＋ 一盆火 = 三重叠加 ✓）
 *
 * 【★★ 食物之间的规则（用户 2026-10-06 原话）】
 *   「食物与食物无法叠加，但是如果喝了牛奶已经开了持续时间，在此期间再吃了牛肉汤，
 *     那么它应该是**直接按牛肉汤数值和时间重新计算**［**数值高的可以顶掉数值低的**，
 *     **降温食物则相反**］」
 *   ★★ 2026-10-06 二次更正（用户 ✓）——**分同号 / 异号两种情况** ✗✗：
 *      · **同号**（都是暖 或 都是冷）⇒ 取**更极端**的那个 ✓
 *          牛肉汤(+12) 顶掉 牛奶(+3) ✓ ／ 雪葩(−8) 顶掉 水碗(−2) ✓；同值 ⇒ **刷新时间** ✓；更弱 ⇒ 不动 ✓
 *      · **异号**（一暖一冷）⇒ **两者相加** ✓ 时间取**较长的那个** ✓
 *          用户原话：「牛奶+3 → 吃雪葩−8 ＝ 应该是 **3−8 = −5**，时间按谁长算谁的」
 *          （★ 第一版写成"绝对值大的赢"⇒ −8 ✗ 是错的 ✓ 已改 ✓）
 *   ⇒ 一句话：**同号比大小、异号相加、时间取长** ✓
 *
 * 【挂哪儿】反汇编实证：MITE 所有吃东西的路径最后都走
 *   ~~EntityPlayer.addFoodValue(Item)~~ ✓（ItemBowl / ItemFood / ItemBucketMilk / Potion / BlockCake 全调它 ✓）
 *   ⇒ 一个注入点覆盖全部食物 ✓，而且**拿得到"吃的是哪个 Item"** ✓（见 FoodValueMixin ✓）
 *   ⚠️ 吃完那一刻才生效 ✓（这个回调就是"吃完" ✓，不是咬第一口 ✓）
 *
 * 【不存盘】体感是瞬时值 ⇒ 这里也只记内存 + 世界总时刻 ✓
 */
public final class CMFood {

    private CMFood() {}

    /** 表项：{体感℃, 持续分钟} */
    private static final java.util.HashMap<Integer, float[]> TABLE = new java.util.HashMap<Integer, float[]>();
    private static final java.util.HashMap<Integer, String> NAMES = new java.util.HashMap<Integer, String>();
    private static final java.util.HashMap<Integer, String> KEYS = new java.util.HashMap<Integer, String>();
    private static boolean inited = false;

    /** 玩家当前的食物体感：entityId → {到期世界时刻, 体感值×100, 最后吃的物品id} ✓ 每人一份 ✓ */
    private static final java.util.HashMap<Integer, long[]> ACTIVE = new java.util.HashMap<Integer, long[]>();
    /** 显示名（合并过的话会是"牛奶碗+雪葩"这种 ✓）*/
    private static final java.util.HashMap<Integer, String> ACTIVE_NAME = new java.util.HashMap<Integer, String>();
    /** 同上，ASCII（日志用 ✓）*/
    private static final java.util.HashMap<Integer, String> ACTIVE_KEY = new java.util.HashMap<Integer, String>();

    private static void reg(int itemId, String key, String name, float temp, float minutes) {
        float[] v = cfg(key, temp, minutes);
        if (v[0] == 0.0F) return;
        TABLE.put(Integer.valueOf(itemId), v);
        NAMES.put(Integer.valueOf(itemId), name);
        KEYS.put(Integer.valueOf(itemId), key);
    }

    private static float[] cfg(String key, float dt, float dm) {
        try {
            String s = CMConfig.getString("food." + key, null);
            if (s == null) return new float[]{dt, dm};
            String[] a = s.split(",");
            float t = Float.parseFloat(a[0].trim());
            float m = a.length > 1 ? Float.parseFloat(a[1].trim()) : dm;
            return new float[]{t, Math.max(0.0F, m)};
        } catch (Throwable e) {
            return new float[]{dt, dm};
        }
    }

    private static void ensureInit() {
        if (inited) return;
        inited = true;
        // ---- MITE 自带的 15 种碗装食物（id 来自 MITE 参考表 food_value.txt ✓）----
        reg(282,  "mushroom_stew",   "蘑菇煲",     7.0F, 3.0F);
        reg(1188, "beef_soup",       "牛肉汤",    12.0F, 5.0F);
        reg(1189, "chicken_soup",    "鸡肉汤",    10.0F, 4.0F);
        reg(1190, "vegetable_soup",  "蔬菜汤",     9.0F, 4.0F);
        reg(1215, "cream_mush_soup", "奶油蘑菇汤", 8.0F, 4.0F);
        reg(1216, "cream_veg_soup",  "奶油蔬菜汤",10.0F, 4.0F);
        reg(1217, "pumpkin_soup",    "南瓜汤",     6.0F, 3.0F);
        reg(1223, "mashed_potato",   "土豆泥",     8.0F, 3.0F);
        reg(1226, "porridge",        "蓝莓粥",     5.0F, 3.0F);
        reg(1227, "cereal",          "麦片粥",     6.0F, 3.0F);
        reg(1209, "salad",           "沙拉",       0.0F, 0.0F);
        // ---- 奶：一桶更顶、一小碗温和 ✓ ----
        reg(1166, "milk_bowl",       "牛奶碗",     2.0F, 2.0F);
        reg(335,  "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1160, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1161, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1162, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1163, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1164, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        reg(1165, "milk_bucket",     "牛奶桶",     3.0F, 2.0F);
        // ---- ★ 新增：碗装/桶装饮料（2026-10-06 用户定稿 ✓ 我们自己的物品 ✓）----
        reg(CMItems.ID_HOT_WATER_BOWL,  "hot_water_bowl",  "热水碗",    6.0F, 3.0F);
        reg(CMItems.ID_WARM_WATER_BOWL, "warm_water_bowl", "温水碗",    1.0F, 2.0F);
        reg(CMItems.ID_ICE_WATER_BOWL,  "ice_water_bowl",  "冰水碗",   -4.0F, 3.0F);
        reg(CMItems.ID_HOT_MILK_BOWL,   "hot_milk_bowl",   "热牛奶碗",  5.0F, 3.0F);
        for (int i = 0; i < CMItems.ID_EMPTY_BUCKETS.length; i++) {
            reg(CMItems.ID_HOT_MILK_BUCKET_BASE + i, "hot_milk_bucket", "热奶桶", 6.0F, 3.0F);
        }
        // ---- ★ 套餐 A：苹果派线 ＋ 巧克力奶线（2026-10-07 用户拍板 ✓）----
        //   热苹果派 / 热巧克力奶 = 刚出炉的版本 ✓ 放 5 分钟自己凉回常温版（CMCool ✓）
        reg(CMItems.ID_APPLE_PIE,               "apple_pie",               "苹果派",     7.0F, 4.0F);
        reg(CMItems.ID_HOT_APPLE_PIE,           "hot_apple_pie",           "热苹果派",  11.0F, 4.0F);
        reg(CMItems.ID_CHOCOLATE_MILK_BOWL,     "chocolate_milk_bowl",     "巧克力奶",   4.0F, 4.0F);
        reg(CMItems.ID_HOT_CHOCOLATE_MILK_BOWL, "hot_chocolate_milk_bowl", "热巧克力奶", 9.0F, 4.0F);
        //   ⚠️ 苹果派胚**不登记** ✗ —— 它是生的中间产物 ✓ 不能吃 ✓（isEatable 也没开 ✓）
        // ---- 冷食 ----
        reg(1167, "water_bowl",      "水碗",      -2.0F, 2.0F);
        reg(1206, "ice_cream",       "冰激凌",    -6.0F, 3.0F);
        reg(1224, "sorbet",          "雪葩",      -8.0F, 3.0F);
        System.out.println("[CreateMITE][FOOD] 食物体感表已建：" + TABLE.size() + " 项");
    }

    /**
     * ★ 吃完那一刻调用（FoodValueMixin ✓）
     *   规则：**绝对值大的赢** ✓ 同值刷新时间 ✓ 更弱的忽略 ✓
     */
    public static void onEaten(EntityPlayer p, Item item) {
        if (p == null || item == null || p.worldObj == null) return;
        ensureInit();
        if (CMConfig.getFloat("food.enabled", 1.0F) == 0.0F) return;
        float[] e = TABLE.get(Integer.valueOf(item.itemID));
        if (e == null || e[1] <= 0.0F) return;                       // 没登记 / 时长为 0 ⇒ 不管 ✓
        long now = p.worldObj.getTotalWorldTime();
        long newUntil = now + (long) (e[1] * 60.0F * 20.0F);
        Integer k = Integer.valueOf(p.entityId);
        String name = NAMES.get(Integer.valueOf(item.itemID));
        String key = KEYS.get(Integer.valueOf(item.itemID));
        long[] cur = ACTIVE.get(k);

        if (cur != null && now < cur[0]) {
            float curVal = cur[1] / 100.0F;
            float newVal = e[0];
            boolean sameSign = (curVal >= 0.0F && newVal >= 0.0F) || (curVal <= 0.0F && newVal <= 0.0F);
            if (sameSign) {
                // ---- 同号：比大小 ⇒ 更极端才顶得掉 ✓ 同值刷新时间 ✓ 更弱无事发生 ✓ ----
                if (Math.abs(newVal) < Math.abs(curVal)) return;
                ACTIVE.put(k, new long[]{newUntil, (long) (newVal * 100.0F), (long) item.itemID});
                ACTIVE_NAME.put(k, name);
                ACTIVE_KEY.put(k, key);
            } else {
                // ---- ★★ 异号：**相加** ✓ 时间取较长的那个 ✓（用户 2026-10-06 更正 ✓）----
                float sum = curVal + newVal;                          // 例：+3 + (−8) = −5 ✓
                long remain = cur[0] - now;
                long useUntil = Math.max(newUntil, now + remain);      // 谁长算谁的 ✓
                ACTIVE.put(k, new long[]{useUntil, (long) (sum * 100.0F), (long) item.itemID});
                String curName = ACTIVE_NAME.get(k);
                String curKey = ACTIVE_KEY.get(k);
                ACTIVE_NAME.put(k, (curName == null ? "?" : curName) + "+" + name);
                ACTIVE_KEY.put(k, (curKey == null ? "?" : curKey) + "+" + key);
                System.out.println("[CreateMITE][FOOD] merge(opposite) " + curVal + " + " + newVal
                        + " = " + sum + " until=" + useUntil + " now=" + now);
            }
        } else {
            // ---- 当前没有效果 ⇒ 直接起一份 ✓ ----
            ACTIVE.put(k, new long[]{newUntil, (long) (e[0] * 100.0F), (long) item.itemID});
            ACTIVE_NAME.put(k, name);
            ACTIVE_KEY.put(k, key);
        }
        System.out.println("[CreateMITE][FOOD] ate id=" + item.itemID + " temp=" + e[0]
                + " min=" + e[1] + " now=" + now);
    }

    /** 食物这一项（℃ ✓）—— 独立加在 环境/物品 两项之上 ✓ */
    public static float foodHeat(EntityPlayer p) {
        long u = foodUntil(p);
        if (u <= 0L || p == null || p.worldObj == null) return 0.0F;
        if (p.worldObj.getTotalWorldTime() >= u) return 0.0F;
        long[] e = ACTIVE.get(Integer.valueOf(p.entityId));
        return e == null ? 0.0F : e[1] / 100.0F;
    }

    /** 到期时刻（0 = 没有 ✓）—— 诊断 / 以后做 HUD 用 ✓ */
    public static long foodUntil(EntityPlayer p) {
        if (p == null || p.worldObj == null) return 0L;
        ensureInit();
        long[] e = ACTIVE.get(Integer.valueOf(p.entityId));
        if (e == null) return 0L;
        if (p.worldObj.getTotalWorldTime() >= e[0]) {
            ACTIVE.remove(Integer.valueOf(p.entityId));
            return 0L;
        }
        return e[0];
    }

    /** ASCII 名字（日志用 ✓ 中文进日志会被毁成 U+FFFD ✗）*/
    public static String activeKey(EntityPlayer p) {
        if (p == null || p.worldObj == null) return "NONE";
        long[] e = ACTIVE.get(Integer.valueOf(p.entityId));
        if (e == null || p.worldObj.getTotalWorldTime() >= e[0]) return "NONE";
        String k = ACTIVE_KEY.get(Integer.valueOf(p.entityId));
        return k == null ? "HIT" : k;
    }

    /** 中文名（报告用 ✓）*/
    public static String activeName(EntityPlayer p) {
        if (p == null || p.worldObj == null) return null;
        long[] e = ACTIVE.get(Integer.valueOf(p.entityId));
        if (e == null || p.worldObj.getTotalWorldTime() >= e[0]) return null;
        return ACTIVE_NAME.get(Integer.valueOf(p.entityId));
    }
}
