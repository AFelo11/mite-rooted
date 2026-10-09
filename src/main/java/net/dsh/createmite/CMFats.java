package net.dsh.createmite;

import net.minecraft.EntityPlayer;
import net.minecraft.MITEConstant;
import net.minecraft.ServerPlayer;
import net.minecraft.WorldServer;

/**
 * ★ 必需脂肪（essential fats）—— 2026-10-09 用户拍板启用。
 *
 * 【原版是什么样】MITE 里这个值只记不罚（没人读它 ✓ 见交接文档的调查 ✓），
 *   食物也只有"开关"（`Item.has_essential_fats`）而原版食物全都没打开 ✗。
 *
 * 【我们给它三档作用（用户 2026-10-09 定稿）】
 *   ≥ 75%   体感 +1.5℃（保温 ✓）＋ 移速 ×0.9（笨重 ✓）
 *   25~75%  中性：体感 0 ✓ 移速 ×1.0
 *   5~25%   体感 -1.5℃ ＋ 移速 ×1.1（轻快但怕冷 ✓）
 *   < 5%    体感 -3.0℃ ＋ 移速 ×1.3（又轻又快，但冬天很难熬 ✓）
 *   —— 一句话：**脂肪是"保温换灵活"的天平** ✓ 不是纯负面 ✓
 *
 * 【挨饿保护（第二条）】
 *   脂肪 > 65% 时，饿到 0 的**掉血间隔翻倍**（= 有储备 ✓）；
 *   同时脂肪会**快速消耗**：从当前值烧到 **50%** 为止（约 2.5 分钟烧完 ✓ 不会很快也不拖很久）
 *
 * 【数值来源】所有读数都走"同 JVM 直读服务端玩家"（和面板/体感一个套路 ✓）
 *   ⇒ 每 tick 由 {@link CMAmbientFeel#tick} 调 {@link #tick} 刷新缓存 ✓
 */
public final class CMFats {

    private CMFats() {}

    // ---- 档位阈值（比例 ✓）----
    public static final float T_HIGH = 0.75F;
    public static final float T_LOW  = 0.25F;
    public static final float T_CRIT = 0.05F;

    // ---- 体感修正（℃ ✓）----
    public static final float WARM_HIGH = 1.5F;
    public static final float WARM_LOW  = -1.5F;
    public static final float WARM_CRIT = -3.0F;

    // ---- 移速倍率（用户指定 ✓）----
    public static final float SPEED_HIGH = 0.9F;
    public static final float SPEED_MID  = 1.0F;
    public static final float SPEED_LOW  = 1.1F;
    public static final float SPEED_CRIT = 1.3F;

    // ---- 挨饿保护 ----
    public static final float STARVE_GATE  = 0.65F;   // 高于它才给"扛饿" ✓
    /** 挨饿掉血速率（原版的倍数 ✓ 用户 2026-10-09 定稿：0.35 ⇒ 约 2.86 倍时间才掉一点血 ✓）*/
    public static final float STARVE_RATE  = 0.35F;
    public static final float STARVE_FLOOR = 0.50F;   // 挨饿时脂肪最多烧到这个比例 ✓
    /** 烧脂肪速度：160000 x 15% = 24000；每 tick 烧 8 ⇒ 3000 tick = 2.5 分钟烧到 50% ✓ */
    public static final int STARVE_DRAIN_PER_TICK = 8;

    /** 本 tick 缓存（客户端/服务端同 JVM ✓ 移速 mixin 直接读它 ✓ 省得每帧查表 ✓）*/
    private static float cachedRatio = 0.5F;
    private static boolean shield = false;

    /** 脂肪比例（0~1 ✓）；拿不到就当中性 0.5 ✓ */
    public static float ratio() { return cachedRatio; }

    /** 挨饿保护是否生效（脂肪 > 65% ✓）*/
    public static boolean starveShield() { return shield; }

    /** 体感温度修正（℃ ✓）—— 由 CMAmbientFeel 相加 ✓ */
    public static float insulation() {
        float r = cachedRatio;
        if (r >= T_HIGH) return WARM_HIGH;
        if (r < T_CRIT)  return WARM_CRIT;
        if (r < T_LOW)   return WARM_LOW;
        return 0.0F;
    }

    /** 移速倍率（用户指定的四档 ✓）*/
    public static float speedFactor() {
        float r = cachedRatio;
        if (r >= T_HIGH) return SPEED_HIGH;
        if (r < T_CRIT)  return SPEED_CRIT;
        if (r < T_LOW)   return SPEED_LOW;
        return SPEED_MID;
    }

    /** 档位名（面板/日志用 ✓）*/
    public static String tierName() {
        float r = cachedRatio;
        if (r >= T_HIGH) return "体脂充足";
        if (r < T_CRIT)  return "体脂告急";
        if (r < T_LOW)   return "体脂偏低";
        return "正常";
    }

    /** 档位颜色（面板标签用 ✓）*/
    public static int tierColor() {
        float r = cachedRatio;
        if (r >= T_HIGH) return 0x8A6D00;   // 充足：金
        if (r < T_CRIT)  return 0xB02000;   // 告急：红
        if (r < T_LOW)   return 0xA85A00;   // 偏低：橙
        return 0x8A6A2A;                    // 正常：暗黄（原色）
    }

    // ------------------------------------------------------------------ 每 tick

    /**
     * 每 tick 由体感系统调一次（只在服务端 ✓）。
     * 做两件事：① 刷新缓存比例 ② 挨饿时把脂肪烧到 50% ✓
     */
    public static void tick(ServerPlayer sp) {
        if (sp == null) return;
        if (!foodsDone) registerFoods();     // 首次 tick 才做（物品/方块都注册完了 ✓）
        try {
            int limit = MITEConstant.nutrient_limit;
            int fats = sp.getEssentialFats();
            float r = limit <= 0 ? 0.5F : (float) fats / (float) limit;
            if (r < 0.0F) r = 0.0F;
            if (r > 1.0F) r = 1.0F;
            cachedRatio = r;
            shield = r > STARVE_GATE;

            // 挨饿（饥饿值 0）⇒ 烧脂肪，烧到 50% 为止 ✓
            if (sp.getFoodStats() != null && sp.getFoodStats().getHunger() <= 0.0F && r > STARVE_FLOOR) {
                int next = fats - STARVE_DRAIN_PER_TICK;
                int floor = (int) (limit * STARVE_FLOOR);
                if (next < floor) next = floor;
                sp.setEssentialFats(next);
            }
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ 食物来源

    /**
     * 给"含脂食物"打开 MITE 原版的脂肪开关（2026-10-09 ✓）。
     *
     *   原版所有食物的 has_essential_fats 都是 false ⇒ 吃什么都补不了脂肪 ✓
     *   这里用反射把挑出来的食物打开（**不改数值公式** ✓ 仍按原版：营养值 x 8000 ✓）
     *
     *   选的原则（用户授权我定 ✓ 不多不少）：
     *     · 打开：奶类（奶桶全材质 / 一碗牛奶 / 奶酪）、甜点（蛋糕 / 南瓜派 / 巧克力 / 冰淇淋）、猪肉（肥肉代表）
     *     · 不开：牛羊肉 / 鸡 / 鱼 / 蛋 / 腐肉（瘦肉 ✓）、全部果蔬谷物与汤 ✓
     *   ⇒ 想吃出脂肪得靠奶制品、甜点和猪肉 ⇒ 有取舍，也不至于补不上 ✓
     */
    private static final int[] FATTY_FOODS = {
            335,                                    // 装满牛奶的铁桶
            1160, 1161, 1162, 1163, 1164, 1165,     // 六种金属奶桶
            1166,                                   // 一碗牛奶
            1183,                                   // 奶酪
            92,                                     // 蛋糕
            400,                                    // 南瓜派
            1186,                                   // 巧克力
            1206,                                   // 冰淇淋
            319, 320                                // 生猪排 / 熟猪排
    };

    private static boolean foodsDone = false;

    /** 给单件食物设三个养分开关（我们自己的食物 ✓）*/
    private static int cm$flag(int itemID, boolean fats, boolean protein, boolean phyto) {
        return cm$flag(itemID, fats, protein, phyto, -1);
    }

    /** nutrition >= 0 时顺带设营养值（碗类饮料原本没设 ⇒ 只开开关也拿不到脂肪 ✗）*/
    private static int cm$flag(int itemID, boolean fats, boolean protein, boolean phyto, int nutrition) {
        try {
            net.minecraft.Item it = net.minecraft.Item.itemsList[itemID];
            if (it instanceof net.dsh.createmite.item.CMItem) {
                net.dsh.createmite.item.CMItem ci = (net.dsh.createmite.item.CMItem) it;
                ci.setHasEssentialFats(fats).setHasProtein(protein).setHasPhytonutrients(phyto);
                if (nutrition >= 0) ci.setNutrition(nutrition);
                return 1;
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** 由 CMFats.tick 首次调用（那时物品一定注册好了 ✓）*/
    public static void registerFoods() {
        if (foodsDone) return;
        foodsDone = true;
        try {
            java.lang.reflect.Field f = net.minecraft.Item.class.getDeclaredField("has_essential_fats");
            f.setAccessible(true);
            int n = 0;
            for (int i = 0; i < FATTY_FOODS.length; i++) {
                net.minecraft.Item it = net.minecraft.Item.itemsList[FATTY_FOODS[i]];
                if (it == null) continue;
                f.setBoolean(it, true);
                n++;
            }
            // ---- 我们自己的食物（直接设 ✓ 不用反射 ✓）----
            //   热牛奶碗 / 热奶桶 x7 / 巧克力奶系 = 脂肪 + 蛋白质 ✓（奶制品 ✓）
            //   苹果派三态 = 脂肪 + 植物营养 ✓（面团+苹果+糖+蛋 ✓）
            // 热牛奶碗 / 热奶桶 x7：营养值补成 4（和原版牛奶桶一致 ✓）⇒ 脂肪/蛋白质各 +32000 ✓
            n += cm$flag(2376, true, true, false, 4);      // 热牛奶碗
            for (int id = 2377; id <= 2383; id++) n += cm$flag(id, true, true, false, 4);   // 热奶桶 x7
            n += cm$flag(2384, true, false, true);      // 苹果派胚
            n += cm$flag(2385, true, false, true);      // 苹果派
            n += cm$flag(2386, true, false, true);      // 热苹果派
            n += cm$flag(2387, true, true, false);      // 巧克力奶
            n += cm$flag(2388, true, true, false);      // 热巧克力奶
            System.out.println("[MITE][体脂] 已给 " + n + " 种含脂食物打开脂肪开关（奶类/甜点/猪肉 + 我们自己的热奶与派 ✓）");
        } catch (Throwable t) {
            System.out.println("[MITE][体脂] 食物开关设置失败（不影响其它功能）: " + t);
        }
    }

    /** 给"同 JVM"用的服务端玩家查找（单机 ✓；多人拿不到 → null ✓）*/
    public static ServerPlayer serverPlayerOf(EntityPlayer p) {
        if (p instanceof ServerPlayer) return (ServerPlayer) p;
        try {
            net.minecraft.server.MinecraftServer s = net.minecraft.server.MinecraftServer.getServer();
            if (s == null || p == null || p.worldObj == null) return null;
            WorldServer w = s.worldServerForDimension(p.worldObj.provider.dimensionId);
            if (w == null || w.playerEntities == null) return null;
            for (int i = 0; i < w.playerEntities.size(); i++) {
                Object o = w.playerEntities.get(i);
                if (o instanceof ServerPlayer) return (ServerPlayer) o;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
