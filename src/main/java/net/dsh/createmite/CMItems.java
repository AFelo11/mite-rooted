package net.dsh.createmite;

import net.dsh.createmite.item.CMItem;
import net.dsh.createmite.item.CMIngot;
import net.dsh.createmite.item.ItemWrench;

/** 本模组的物品注册表 */
public final class CMItems {

    /** 物品构造参数 = itemID - 256（MITE/1.6.4 的 Item 构造器内部 +256） */
    private static final int BASE_ARG = 2100;

    public static CMItem rawZinc;         // 粗锌
    public static CMIngot ingotZinc;      // 锌锭
    public static CMIngot ingotBrass;     // 黄铜锭
    public static CMItem crushedRawZinc;  // 粉碎粗锌（石磨产物）
    public static ItemWrench wrench;      // 扳手（M2 批次 1）
    /** 安山合金（2026-09-28 新增）—— 原版 Create 的基础材料，传动轴/手摇曲柄都要它 */
    public static CMItem andesiteAlloy;
    /** 锌粒（2026-09-28 新增）—— 原版是"锌锭拆成 9 粒、9 粒拼回 1 锭" ✓ */
    public static CMItem zincNugget;
    /** 黄铜粒（2026-09-28 新增）—— 同上：1 黄铜锭 ↔ 9 黄铜粒 ✓ */
    public static CMItem brassNugget;
    /** 粉碎铁矿石（2026-09-28 新增）—— **只能由粉碎轮产出** ✓ */
    public static CMItem crushedIron;
    /** 粉碎金矿石（2026-09-28 新增）—— 同上 ✓ */
    public static CMItem crushedGold;
    /** 粉碎铜矿石（2026-09-28 新增）—— 同上 ✓ */
    public static CMItem crushedCopper;
    /** 粉碎银矿石（2026-09-28 新增）—— 同上 ✓ */
    public static CMItem crushedSilver;
    /** 粉碎秘银矿石（2026-09-28 新增）—— 同上 ✓ */
    public static CMItem crushedMithril;
    /** 粉碎艾德曼矿石（2026-09-28 新增）—— 同上 ✓ */
    public static CMItem crushedAdamantium;
    /**
     * 结构选择器（开发工具，2026-09-29 新增）—— 左键选 A 点、右键选 B 点，然后 /T <名称> 导出结构。
     * 贴图和木棍一样 ✓，**没有配方** ✓，只能从创造物品栏拿 ✓。
     */
    public static net.dsh.createmite.item.ItemStructureWand structureWand;
    /** 结构选择器的物品 id */
    public static final int ID_STRUCTURE_WAND = 2370;

    /** ★ 暖手石（2026-10-05 用户定稿 ✓）—— 可用两次、每次 3 分钟 +3℃ ✓ 不可叠加、有冷却 ✓ */
    public static net.dsh.createmite.item.ItemHandWarmer handWarmer;
    /** ★ 冷暖手石 —— 合成出来的原始形态 ✓ 放熔炉烤一下变暖手石 ✓ */
    public static net.dsh.createmite.item.ItemHandWarmer handWarmerCold;
    /** 物品 id：暖手石 2371 / 冷暖手石 2372 ✓ */
    public static final int ID_HAND_WARMER = 2371;
    public static final int ID_HAND_WARMER_COLD = 2372;

    // ================= ★ 碗装 / 桶装饮料（2026-10-06 用户定稿 ✓）=================
    //   热水碗(2373) --放 5 分钟--> 温水碗(2374) ✓；冰水碗(2375) = 水碗＋雪球 ✓
    //   热牛奶碗(2376) ＋ 热奶桶 ×7(2377~2383) —— 全部由熔炉烧出来 ✓
    //   ★ 喝完**返还空容器**：碗 → 281（碗）／奶桶 → 325（铁桶）✓（MITE 的规矩 ✓）
    public static net.dsh.createmite.item.ItemBowlDrink hotWaterBowl;
    public static net.dsh.createmite.item.ItemBowlDrink warmWaterBowl;
    public static net.dsh.createmite.item.ItemBowlDrink iceWaterBowl;
    public static net.dsh.createmite.item.ItemBowlDrink hotMilkBowl;
    public static net.dsh.createmite.item.ItemBowlDrink[] hotMilkBucket = new net.dsh.createmite.item.ItemBowlDrink[7];
    public static final int ID_HOT_WATER_BOWL = 2373;
    public static final int ID_WARM_WATER_BOWL = 2374;
    public static final int ID_ICE_WATER_BOWL = 2375;
    public static final int ID_HOT_MILK_BOWL = 2376;
    public static final int ID_HOT_MILK_BUCKET_BASE = 2377;

    // ---- ★ 套餐 A：苹果派线 ＋ 巧克力奶线（2026-10-07 用户拍板 ✓）----
    //   ★ 用户裁定：**只要热苹果派 ＋ 热巧克力奶**（汤类不加热 ✓ 它们本来就是热的 ✓）
    //   ★ 用户还裁定：**苹果派允许回炉再加热** ✓（苹果派 → 熔炉 → 热苹果派 ✓ 循环 ✓）
    public static CMItem applePieRaw;                                  // 苹果派胚（生的 ✓ 不能吃 ✓）
    public static net.dsh.createmite.item.ItemCMFood applePie;
    public static net.dsh.createmite.item.ItemCMFood hotApplePie;      // 会凉成苹果派 ✓
    public static net.dsh.createmite.item.ItemCMFood chocolateMilkBowl;
    public static net.dsh.createmite.item.ItemCMFood hotChocolateMilkBowl;   // 会凉成巧克力奶 ✓
    public static final int ID_APPLE_PIE_RAW = 2384;
    /**
     * ⚠️ **过渡用**：run317 里苹果派胚误落在 **2640**（`newItemSafely` 的 id 要减 256 ✗）
     *   ⇒ 这个 id 上挂一个隐形迁移件（见 ItemLegacyItem ✓）⇒ 旧堆叠一进背包就自动变 2384 ✓
     *   ⇒ 等确认存档里没有旧堆叠了，**这几行连着 ItemLegacyItem.java 一起删** ✓
     */
    public static final int ID_LEGACY_APPLE_PIE_RAW = 2640;
    public static net.dsh.createmite.item.ItemLegacyItem legacyApplePieRaw;
    public static final int ID_APPLE_PIE = 2385;
    public static final int ID_HOT_APPLE_PIE = 2386;
    public static final int ID_CHOCOLATE_MILK_BOWL = 2387;
    public static final int ID_HOT_CHOCOLATE_MILK_BOWL = 2388;
    /** 配方要用的 MITE 原料 id（全部来自参考表 ✓）：巧克力 1186 ／ 牛奶碗 1166 ／ 苹果 260 ／ 糖 353 ／ 鸡蛋 344 ／ 面团 1185 ✓ */
    public static final int ID_CHOCOLATE = 1186;
    public static final int ID_MILK_BOWL = 1166;
    public static final int ID_APPLE = 260;
    public static final int ID_SUGAR = 353;
    public static final int ID_EGG = 344;
    public static final int ID_DOUGH = 1185;

    /** MITE 的空容器 id：碗只有一种 281 ✓ */
    public static final int ID_EMPTY_BOWL = 281;
    /** ★ MITE 的**水碗**（id 1167 ✓）—— 两级降温链的终点：温水碗放 3 分钟变它 ✓（用户 2026-10-07 补充 ✓）*/
    public static final int ID_WATER_BOWL = 1167;
    /**
     * ★★ 空桶**每种材质各有一个** ✗（用户 2026-10-06 提醒 ✓ 差点只做铁桶 ✓）
     *   铁 325 ／ 铜 1142 ／ 银 1143 ／ 金 1144 ／ 秘银 1145 ／ 艾德曼 1146 ／ 古代金属 1147 ✓
     *   （id 来自 MITE 参考表 item_material.txt ✓：325 铁桶 / 1142 铜桶 / … / 1147 古代金属桶 ✓）
     */
    public static final int[] ID_EMPTY_BUCKETS = {1142, 1143, 1144, 325, 1145, 1146, 1147};   // 与下面的 metals 顺序一一对应 ✓
    /** 对应的**牛奶桶** id（熔炼的输入 ✓）：铜 1160 ／ 银 1161 ／ 金 1162 ／ 铁 335 ／ 秘银 1163 ／ 艾德曼 1164 ／ 古代金属 1165 ✓ */
    public static final int[] ID_MILK_BUCKETS = {1160, 1161, 1162, 335, 1163, 1164, 1165};

    private CMItems() {}

    /** ★ 建一个碗/桶饮料 ✓（带 id 空闲检查 ✓ 被占用就跳过、不让模组加载失败 ✓）*/
    private static net.dsh.createmite.item.ItemBowlDrink newDrink(
            int id, String unloc, String texture, net.minecraft.Material mat,
            int emptyId, int becomesId, int coolMinutes) {
        if (id <= 0 || id >= net.minecraft.Item.itemsList.length) return null;
        if (net.minecraft.Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 物品 id " + id + "（" + unloc + "）已被占用 → 这一件停用");
            return null;
        }
        return new net.dsh.createmite.item.ItemBowlDrink(
                id - 256, unloc, texture, mat, emptyId, becomesId, coolMinutes);
    }

    /** ★ 建一个「食物/饮品」（通用类 ✓ 带 id 空闲检查 ✓）*/
    private static net.dsh.createmite.item.ItemCMFood newFood(
            int id, String unloc, String texture, net.minecraft.Material mat, float difficulty,
            net.minecraft.EnumItemInUseAction action, int emptyId, int becomesId, int coolMinutes) {
        if (id <= 0 || id >= net.minecraft.Item.itemsList.length) return null;
        if (net.minecraft.Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 物品 id " + id + "（" + unloc + "）已被占用 → 这一件停用");
            return null;
        }
        return new net.dsh.createmite.item.ItemCMFood(
                id - 256, unloc, texture, mat, difficulty, action, emptyId, becomesId, coolMinutes);
    }

    /** 带 id 空闲检查的建物品 ✓（被占用就返回 null 并打日志，绝不让整个模组加载失败） */
    private static CMItem newItemSafely(int id, net.minecraft.Material material,
                                        String unlocalizedName, String textureName, float difficulty) {
        if (net.minecraft.Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 物品 id " + id + "（" + unlocalizedName
                    + "）已被占用 → 这一件停用，其余照常");
            return null;
        }
        return new CMItem(id, material, unlocalizedName, textureName, difficulty);
    }

    /**
     * **把矿石的堆叠上限改成 8**（用户 2026-09-28 要求）✓
     *
     * 覆盖：**MITE 自带的全部矿石** + 本模组的**锌矿石**；另外**粗锌**（物品）也是 8 ✓
     * （用户原话："更改锌矿石的最大堆叠数为8，更改粗锌的最大堆叠数为8（MITE中自带矿物都堆叠数也改为8）"）
     *
     * 【为什么两处都要设】`Item.getItemStackLimit()` 读的是 **Item 的 maxStackSize** ✓
     * （javap 实证：MITE 没改这个逻辑）；而 MITE 又给 `Block` 加了 `setMaxStackSize` ✓
     * → 方块和它的 ItemBlock 都设一遍最稳 ✓。
     *
     * 【矿石清单来自 MITE 自己的参考数据】block_constants.txt：
     *   14 金矿石（meta bit2 = 下界金矿石）／15 铁矿石／16 煤矿石／21 青金石矿石／56 钻石矿石／
     *   73+74 红石矿石（未点亮/点亮）／129 绿宝石矿石／153 下界石英矿石／200 铜矿石／
     *   201 银矿石／202 秘银矿石／203 艾德曼矿石 ✓
     */
    public static void applyOreStackSizes() {
        final int[] oreBlockIds = {
                net.minecraft.Block.oreGold.blockID,
                net.minecraft.Block.oreIron.blockID,
                net.minecraft.Block.oreCoal.blockID,
                net.minecraft.Block.oreLapis.blockID,
                net.minecraft.Block.oreDiamond.blockID,
                net.minecraft.Block.oreRedstone.blockID,        // 未点亮态（点亮态是另一个 id，见下）
                net.minecraft.Block.oreEmerald.blockID,
                net.minecraft.Block.oreNetherQuartz.blockID,
                net.minecraft.Block.oreCopper.blockID,
                net.minecraft.Block.oreSilver.blockID,
                net.minecraft.Block.oreMithril.blockID,
                net.minecraft.Block.oreAdamantium.blockID,
                net.dsh.createmite.CMBlocks.ID_ZINC_ORE,        // 我们的锌矿石 ✓
        };
        int done = 0;
        for (int i = 0; i < oreBlockIds.length; i++) {
            int id = oreBlockIds[i];
            net.minecraft.Block b = net.minecraft.Block.blocksList[id];
            if (b != null) {
                b.setMaxStackSize(ORE_STACK_SIZE);
                done++;
            }
            net.minecraft.Item it = net.minecraft.Item.itemsList[id];
            if (it != null) it.setMaxStackSize(ORE_STACK_SIZE);
        }
        // 红石矿石的"点亮态"是**另一个方块 id**（74）——它没有物品形态，但方块上限也一起设 ✓
        net.minecraft.Block lit = net.minecraft.Block.oreRedstoneGlowing;
        if (lit != null) lit.setMaxStackSize(ORE_STACK_SIZE);

        if (rawZinc != null) rawZinc.setMaxStackSize(ORE_STACK_SIZE);   // 粗锌也是 8 ✓

        System.out.println("[MITE] 矿石/粗锌堆叠上限已设为 " + ORE_STACK_SIZE
                + "（MITE 自带 " + done + " 种 + 锌矿石 + 粗锌）");
    }

    /** 矿石与粗锌的堆叠上限（用户指定）✓ */
    public static final int ORE_STACK_SIZE = 8;

    /** **粉碎矿物的堆叠上限 = 16**（用户指定）✓ */
    public static final int CRUSHED_STACK_SIZE = 16;

    /**
     * 把所有"粉碎矿物"的堆叠上限设成 16 ✓
     * （粗锌的粉碎产物 + 铁/金/铜/银/秘银/艾德曼 矿石的粉碎产物 ✓）
     */
    public static void applyCrushedStackSize() {
        CMItem[] crushed = { crushedRawZinc, crushedIron, crushedGold, crushedCopper,
                crushedSilver, crushedMithril, crushedAdamantium };
        int n = 0;
        for (int i = 0; i < crushed.length; i++) {
            if (crushed[i] != null) {
                crushed[i].setMaxStackSize(CRUSHED_STACK_SIZE);
                n++;
            }
        }
        System.out.println("[MITE] 粉碎矿物堆叠上限已设为 " + CRUSHED_STACK_SIZE + "（" + n + " 种）");
    }

    private static String idOrNone(CMItem item) {
        return item != null ? String.valueOf(item.itemID) : "（★未注册）";
    }

    public static void register() {
        if (rawZinc != null) return;
        CMMaterials.register();

        rawZinc = new CMItem(BASE_ARG, CMMaterials.zinc, "rawZinc", "raw_zinc", 100.0F);
        ingotZinc = new CMIngot(BASE_ARG + 1, CMMaterials.zinc, "ingotZinc", 300.0F);
        ingotBrass = new CMIngot(BASE_ARG + 2, CMMaterials.brass, "ingotBrass", 400.0F);
        crushedRawZinc = new CMItem(BASE_ARG + 3, CMMaterials.zinc, "crushedRawZinc", "crushed_raw_zinc", 80.0F);
        wrench = new ItemWrench(BASE_ARG + 4);

        // ★ 2026-09-28：安山合金（id 2361 = BASE_ARG + 5）。
        //   为什么要有"空闲检查"：MITE 的 Item 构造器发现 id 被占会直接抛异常 →
        //   **整个模组加载失败** ✗。这里只把这一件物品停掉（andesiteAlloy 保持 null），
        //   依赖它的配方在 CMRecipes 里会跳过并打日志，其余内容照常 ✓。
        int alloyId = BASE_ARG + 5;
        if (net.minecraft.Item.itemsList[alloyId] != null) {
            System.out.println("[MITE] ★ 物品 id " + alloyId
                    + " 已被占用，安山合金停用（依赖它的传动轴/手摇曲柄配方会被跳过）");
        } else {
            andesiteAlloy = new CMItem(alloyId, net.minecraft.Material.iron,
                    "andesiteAlloy", "andesite_alloy", 150.0F);
        }

        // ★ 2026-09-28：锌粒（id 2362 = BASE_ARG + 6）。原版配方**原样还原**：
        //   锌锭 ×1 --无序--> 锌粒 ×9 ；锌粒 ×9（3×3）--> 锌锭 ×1 ✓
        int nuggetId = BASE_ARG + 6;
        if (net.minecraft.Item.itemsList[nuggetId] != null) {
            System.out.println("[MITE] ★ 物品 id " + nuggetId
                    + " 已被占用，锌粒停用（锌粒/拼回锌锭两条配方会被跳过）");
        } else {
            zincNugget = new CMItem(nuggetId, CMMaterials.zinc, "zincNugget", "zinc_nugget", 30.0F);
        }

        // ★ 2026-09-28：黄铜粒（id 2363 = BASE_ARG + 7），同样带 id 空闲检查 ✓
        int brassNuggetId = BASE_ARG + 7;
        if (net.minecraft.Item.itemsList[brassNuggetId] != null) {
            System.out.println("[MITE] ★ 物品 id " + brassNuggetId
                    + " 已被占用，黄铜粒停用（相关两条配方会被跳过）");
        } else {
            brassNugget = new CMItem(brassNuggetId, CMMaterials.brass, "brassNugget", "brass_nugget", 40.0F);
        }

        // ★ 2026-09-28：三种"粉碎矿石"（id 2364/2365/2366 = BASE_ARG + 8/9/10）
        //   —— 它们**只能由粉碎轮产出** ✓（没有任何合成配方），所以只登记物品本身 ✓
        crushedIron = newItemSafely(BASE_ARG + 8, net.minecraft.Material.iron,
                "crushedIron", "crushed_raw_iron", 90.0F);
        crushedGold = newItemSafely(BASE_ARG + 9, net.minecraft.Material.gold,
                "crushedGold", "crushed_raw_gold", 120.0F);
        crushedCopper = newItemSafely(BASE_ARG + 10, net.minecraft.Material.copper,
                "crushedCopper", "crushed_raw_copper", 90.0F);
        crushedSilver = newItemSafely(BASE_ARG + 11, net.minecraft.Material.silver,
                "crushedSilver", "crushed_raw_silver", 90.0F);
        crushedMithril = newItemSafely(BASE_ARG + 12, net.minecraft.Material.mithril,
                "crushedMithril", "crushed_raw_mithril", 150.0F);
        // ---- 结构选择器（开发工具；物品 id 2370 → 构造参数 = 2370 - 256 ✓）----
        if (net.minecraft.Item.itemsList[ID_STRUCTURE_WAND] != null) {
            System.out.println("[MITE] ★ 物品 id " + ID_STRUCTURE_WAND
                    + "（结构选择器）已被占用 → 这一件停用，其余照常");
        } else {
            structureWand = new net.dsh.createmite.item.ItemStructureWand(
                    ID_STRUCTURE_WAND - 256, "structure_wand");
            System.out.println("[MITE] 结构选择器已注册: id=" + ID_STRUCTURE_WAND
                    + "（左键=A，右键=B，/T <名称> 导出；无配方，创造栏拿）");
        }

        crushedAdamantium = newItemSafely(BASE_ARG + 13, net.minecraft.Material.adamantium,
                "crushedAdamantium", "crushed_raw_adamantium", 200.0F);

        // ---- ★ 暖手石 / 冷暖手石（2026-10-05 用户定稿 ✓）----
        //   合成：空圆石空 ／ 圆石空圆石 ／ 空圆石空 ⇒ 出 2 个冷暖手石 ✓（所有工作台 ✓）
        //   冷暖手石 --熔炉烤--> 暖手石 ✓；暖手石可用两次、每次 3 分钟 +3℃ ✓ 用完变回冷暖手石 ✓
        if (net.minecraft.Item.itemsList[ID_HAND_WARMER] != null) {
            System.out.println("[MITE] ★ 物品 id " + ID_HAND_WARMER + "（暖手石）已被占用 → 这一件停用");
        } else {
            handWarmer = new net.dsh.createmite.item.ItemHandWarmer(
                    ID_HAND_WARMER - 256, true, "handWarmer", "hand_warmer");
            // ★ 用"耐久条"当冷却条（用户要跟末影珍珠一样 ✓）：上限 = 180 秒 + 1
            //   +1 是**故意的** ✗ ⇒ 条永远不会满到 damage==max ⇒ 不会被当成损坏 ✓
            handWarmer.setMaxDamage(net.dsh.createmite.item.ItemHandWarmer.barMax());
        }
        if (net.minecraft.Item.itemsList[ID_HAND_WARMER_COLD] != null) {
            System.out.println("[MITE] ★ 物品 id " + ID_HAND_WARMER_COLD + "（冷暖手石）已被占用 → 这一件停用");
        } else {
            handWarmerCold = new net.dsh.createmite.item.ItemHandWarmer(
                    ID_HAND_WARMER_COLD - 256, false, "handWarmerCold", "hand_warmer_cold");
        }

        // ---- ★ 碗装 / 桶装饮料（2026-10-06 用户定稿 ✓）----
        //   ★★ 两级降温链（用户 2026-10-07 补充后半段 ✓）：
        //      热水碗 --5 分钟--> 温水碗 --3 分钟--> **水碗(1167)** ✓
        //      两级都用**耐久条**当倒计时 ✓；分钟数走配置（drink.hot_water_minutes / drink.warm_water_minutes ✓）
        int hotMin = Math.max(1, (int) net.dsh.createmite.CMConfig.getFloat("drink.hot_water_minutes", 5.0F));
        int warmMin = Math.max(1, (int) net.dsh.createmite.CMConfig.getFloat("drink.warm_water_minutes", 3.0F));
        hotWaterBowl = newDrink(ID_HOT_WATER_BOWL, "hotWaterBowl", "hot_water_bowl",
                net.minecraft.Material.water, ID_EMPTY_BOWL, ID_WARM_WATER_BOWL, hotMin);   // ★ 5 分钟后变温水 ✓
        warmWaterBowl = newDrink(ID_WARM_WATER_BOWL, "warmWaterBowl", "warm_water_bowl",
                net.minecraft.Material.water, ID_EMPTY_BOWL, ID_WATER_BOWL, warmMin);      // ★ 3 分钟后变水碗 ✓
        iceWaterBowl = newDrink(ID_ICE_WATER_BOWL, "iceWaterBowl", "ice_water_bowl",
                net.minecraft.Material.water, ID_EMPTY_BOWL, 0, 0);
        hotMilkBowl = newDrink(ID_HOT_MILK_BOWL, "hotMilkBowl", "hot_milk_bowl",
                net.minecraft.Material.milk, ID_EMPTY_BOWL, 0, 0);
        String[] metals = {"Copper", "Silver", "Gold", "Iron", "Mithril", "Adamantium", "AncientMetal"};
        String[] metalTex = {"copper", "silver", "gold", "iron", "mithril", "adamantium", "ancient_metal"};
        for (int i = 0; i < metals.length; i++) {
            // ★ 每个热奶桶**返还自己材质的空桶** ✓（铜→铜桶 ✓ 不是铁桶 ✗ 用户特意提醒过 ✓）
            hotMilkBucket[i] = newDrink(ID_HOT_MILK_BUCKET_BASE + i,
                    "hotMilkBucket" + metals[i], "hot_milk_bucket_" + metalTex[i],
                    net.minecraft.Material.milk, ID_EMPTY_BUCKETS[i], 0, 0);
        }
        // ASCII 标签行（中文在日志里是花的，只有 ASCII 搜得到）
        System.out.println("[MITE] COOL_CHAIN hot=" + hotMin + "min -> warm="
                + warmMin + "min -> water_bowl(" + ID_WATER_BOWL + ")");
        System.out.println("[MITE] 饮料已注册：热水碗=" + idOrNone(hotWaterBowl)
                + " 温水碗=" + idOrNone(warmWaterBowl) + " 冰水碗=" + idOrNone(iceWaterBowl)
                + " 热牛奶碗=" + idOrNone(hotMilkBowl)
                + " 热奶桶=" + idOrNone(hotMilkBucket[0]) + "~" + idOrNone(hotMilkBucket[6]));

        // ---- ★ 套餐 A：苹果派线 ＋ 巧克力奶线（2026-10-07 ✓）----
        //   苹果派胚 --熔炉--> 热苹果派 --放 5 分钟--> 苹果派 --熔炉--> 热苹果派 ✓（用户批准回炉 ✓）
        //   巧克力 ＋ 牛奶碗 --合成--> 巧克力奶 --熔炉--> 热巧克力奶 --放 5 分钟--> 巧克力奶 ✓
        int hotFoodMin = Math.max(1, (int) net.dsh.createmite.CMConfig.getFloat("hot_food.minutes", 5.0F));
        //   ⚠️ ★★ 坑（2026-10-07 run317 实测撞到 ✗）：newItemSafely 的 id 参数是**"itemID − 256"** ✓
        //      （vanilla 1.6.4 Item(int,String) 自己会 +256 ✓ 而 newDrink/newFood 内部已经减过了 ✓）
        //      第一次写成了 ID_APPLE_PIE_RAW（2384）⇒ 实际落到 **2640** ✗ 日志 PIE_CHAIN 一眼看出来 ✓
        applePieRaw = newItemSafely(ID_APPLE_PIE_RAW - 256, net.minecraft.Material.pie,
                "applePieRaw", "apple_pie_raw", 60.0F);                       // ★ 生的：不能吃 ✓ 只是中间产物 ✓
        applePie = newFood(ID_APPLE_PIE, "applePie", "apple_pie", net.minecraft.Material.pie, 135.0F,
                net.minecraft.EnumItemInUseAction.EAT, 0, 0, 0);               // ★ 常温 +7/4 ✓（难度照南瓜派的 135 ✓）
        if (applePie != null) {
            applePie.setFoodValue(9, 5, true, false, true);                    // (饱食, 营养, 蛋白, 脂肪, 植物营养) ✓ 照南瓜派的路子 ✓
        }
        hotApplePie = newFood(ID_HOT_APPLE_PIE, "hotApplePie", "hot_apple_pie", net.minecraft.Material.pie, 135.0F,
                net.minecraft.EnumItemInUseAction.EAT, 0, ID_APPLE_PIE, hotFoodMin);   // ★ 刚出炉：+11/4 ✓ 凉了变苹果派 ✓
        if (hotApplePie != null) {
            hotApplePie.setFoodValue(9, 5, true, false, true);
        }
        chocolateMilkBowl = newFood(ID_CHOCOLATE_MILK_BOWL, "chocolateMilkBowl", "chocolate_milk_bowl",
                net.minecraft.Material.milk, 50.0F,
                net.minecraft.EnumItemInUseAction.DRINK, ID_EMPTY_BOWL, 0, 0);  // ★ 常温 +4/4 ✓ 喝完返还碗 ✓
        if (chocolateMilkBowl != null) {
            chocolateMilkBowl.setFoodValue(1, 3, true, false, false);
        }
        hotChocolateMilkBowl = newFood(ID_HOT_CHOCOLATE_MILK_BOWL, "hotChocolateMilkBowl", "hot_chocolate_milk_bowl",
                net.minecraft.Material.milk, 75.0F,
                net.minecraft.EnumItemInUseAction.DRINK, ID_EMPTY_BOWL, ID_CHOCOLATE_MILK_BOWL, hotFoodMin);  // ★ +9/4 ✓
        if (hotChocolateMilkBowl != null) {
            hotChocolateMilkBowl.setFoodValue(1, 3, true, false, false);
        }
        // ★ 过渡迁移件（旧 id 2640 → 新 2384 ✓ 用完可删 ✓）
        if (net.minecraft.Item.itemsList[ID_LEGACY_APPLE_PIE_RAW] == null) {
            legacyApplePieRaw = new net.dsh.createmite.item.ItemLegacyItem(
                    ID_LEGACY_APPLE_PIE_RAW - 256, "applePieRawLegacy", "apple_pie_raw",
                    net.minecraft.Material.pie, ID_APPLE_PIE_RAW);
        }
        // ★ ASCII 标签行（日志里中文是花的 ✗ 只有 ASCII 搜得到 ✓）
        System.out.println("[MITE] PIE_CHAIN raw=" + idOrNone(applePieRaw) + " hot=" + idOrNone(hotApplePie)
                + " cold=" + idOrNone(applePie) + " | CHOC_MILK cold=" + idOrNone(chocolateMilkBowl)
                + " hot=" + idOrNone(hotChocolateMilkBowl) + " | hot_food_min=" + hotFoodMin);

        // ★ 所有"粉碎矿物"堆叠上限 = **16**（用户 2026-09-28 要求）✓
        applyCrushedStackSize();

        System.out.println("[MITE] 物品已注册: 粗锌=" + rawZinc.itemID
                + " 锌锭=" + ingotZinc.itemID + " 黄铜锭=" + ingotBrass.itemID
                + " 粉碎粗锌=" + crushedRawZinc.itemID
                + " 扳手=" + wrench.itemID
                + " 安山合金=" + (andesiteAlloy != null ? String.valueOf(andesiteAlloy.itemID) : "（★ 未注册）")
                + " 锌粒=" + (zincNugget != null ? String.valueOf(zincNugget.itemID) : "（★ 未注册）")
                + " 黄铜粒=" + (brassNugget != null ? String.valueOf(brassNugget.itemID) : "（★ 未注册）")
                + " 粉碎铁/金/铜/银/秘银/艾德曼矿石=" + idOrNone(crushedIron) + "/" + idOrNone(crushedGold)
                + "/" + idOrNone(crushedCopper) + "/" + idOrNone(crushedSilver)
                + "/" + idOrNone(crushedMithril) + "/" + idOrNone(crushedAdamantium)
                + " 暖手石=" + (handWarmer != null ? String.valueOf(handWarmer.itemID) : "（★ 未注册）")
                + " 冷暖手石=" + (handWarmerCold != null ? String.valueOf(handWarmerCold.itemID) : "（★ 未注册）"));
    }
}
