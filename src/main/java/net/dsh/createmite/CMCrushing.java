package net.dsh.createmite;

import net.minecraft.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 粉碎轮的**粉碎配方表**。
 *
 * <pre>
 *   铁矿石 -> 粉碎铁矿石   额外：25% 再出 1 个粉碎铁矿石 + 12% 圆石
 *   金矿石 -> 粉碎金矿石   额外：35% 再出 1 个粉碎金矿石 + 12% 圆石
 *   铜矿石 -> 粉碎铜矿石   额外：30% 再出 1 个粉碎铜矿石 + 12% 圆石
 *   锌矿石 -> 粉碎粗锌     额外：40% 再出 1 个粉碎粗锌   + 20% 圆石
 *   粗锌   -> 粉碎粗锌     额外：30% 再出 1 个粉碎粗锌
 * </pre>
 *
 * 【每一条额外产出都是**独立的一次判定**】中了就多吐一个（原版 Create 的"额外产出"就是这个意思）。
 *
 * 【怎么加配方】在下面 TABLE 里加一行，格式：
 * <pre>
 *   输入ID=输出ID:主额外概率:处理时间[:额外物品ID@概率,额外物品ID@概率...]
 * </pre>
 * 例：15=2364:0.25:250:4@0.12 = 铁矿石(15) → 粉碎铁矿石(2364)，25% 额外再出一个，
 * 250 tick，另外 12% 额外出一个圆石(4)。
 * 概率是 0~1 的小数（0 = 永不额外）。找不到配方的物品会被**原样吐回**，不会卡住也不会被吞掉。
 */
public final class CMCrushing {

    private CMCrushing() {}

    /** 一条粉碎配方 */
    public static final class Recipe {
        public final int outputId;
        /**
         * 处理时间（原版配方里的 processingTime 字段）。原版参考：铁矿石 250。
         * 粉碎轮每 tick 推进 max(1, 转速/8) 点，累计到这个数才算处理完一份。
         */
        public final int processingTime;
        /** 额外产出的物品 id（与 bonusChances 一一对应，可以有多条） */
        public final int[] bonusIds;
        /** 每条额外产出的概率（0~1） */
        public final float[] bonusChances;

        Recipe(int outputId, int processingTime, int[] bonusIds, float[] bonusChances) {
            this.outputId = outputId;
            this.processingTime = processingTime;
            this.bonusIds = bonusIds;
            this.bonusChances = bonusChances;
        }

        public ItemStack createOutput() {
            return new ItemStack(this.outputId, 1, 0);
        }
    }

    /** 配方表，格式见类注释 */
    private static String[] TABLE;

    private static final Map<Integer, Recipe> MAP = new HashMap<Integer, Recipe>();

    private static boolean initialised;

    /** 第一次用到时才建表 —— 因为表里要引用 CMItems/CMBlocks 的字段，构造顺序不能早于注册 */
    private static void init() {
        if (initialised) return;
        initialised = true;

        // ★ "石头类掉落"用 BONUS_STONE(-1) 占位：普通矿给圆石、**下界变种给地狱岩** ✓
        //   （MITE：Block[14] 金矿石 meta 的 bit2 = 下界金矿石 ✓，用户实测指出它该掉地狱岩 ✓）
        final String STONE = String.valueOf(BONUS_STONE);
        TABLE = new String[]{
                // ---- 铁 / 金 / 铜 矿石（2026-09-28 新增）----
                net.minecraft.Block.oreIron.blockID + "=" + CMItems.crushedIron.itemID
                        + ":0.25:250:" + STONE + "@0.12",
                net.minecraft.Block.oreGold.blockID + "=" + CMItems.crushedGold.itemID
                        + ":0.35:250:" + STONE + "@0.12",
                net.minecraft.Block.oreCopper.blockID + "=" + CMItems.crushedCopper.itemID
                        + ":0.30:250:" + STONE + "@0.12",
                // ---- 银 / 秘银 / 艾德曼（2026-09-28 新增）----
                // 银矿石：与铜矿石**同款**（用户指定）：30% 额外 + 12% 圆石
                net.minecraft.Block.oreSilver.blockID + "=" + CMItems.crushedSilver.itemID
                        + ":0.30:250:" + STONE + "@0.12",
                // 秘银矿石：**15% 额外 + 35% 圆石**（用户指定）
                net.minecraft.Block.oreMithril.blockID + "=" + CMItems.crushedMithril.itemID
                        + ":0.15:250:" + STONE + "@0.35",
                // 艾德曼矿石：**10% 额外 + 50% 圆石**（用户指定）
                net.minecraft.Block.oreAdamantium.blockID + "=" + CMItems.crushedAdamantium.itemID
                        + ":0.10:250:" + STONE + "@0.50",
                // ---- 锌（2026-09-28 用户调整概率）----
                // 锌矿石 -> 粉碎粗锌：40% 额外再出 1 个 + 20% 圆石
                CMBlocks.oreZinc.blockID + "=" + CMItems.crushedRawZinc.itemID
                        + ":0.40:250:" + STONE + "@0.20",
                // 粗锌 -> 粉碎粗锌：30% 额外（已经粗加工过所以更快，125）
                // 注意：MITE 里物品 id 字段叫 itemID（不是原版的 shiftedIndex）
                CMItems.rawZinc.itemID + "=" + CMItems.crushedRawZinc.itemID + ":0.30:125",
        };
        for (String row : TABLE) {
            int eq = row.indexOf('=');
            if (eq <= 0) continue;
            try {
                int in = Integer.parseInt(row.substring(0, eq).trim());
                String[] parts = row.substring(eq + 1).split(":");
                int out = Integer.parseInt(parts[0].trim());
                float chance = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 0.0F;
                int time = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 120;

                // 第 4 段（可选）：额外产出列表 "id@概率,id@概率"
                int[] bonusIds = new int[0];
                float[] bonusChances = new float[0];
                if (parts.length > 3 && parts[3].trim().length() > 0) {
                    String[] extra = parts[3].trim().split(",");
                    bonusIds = new int[extra.length];
                    bonusChances = new float[extra.length];
                    for (int i = 0; i < extra.length; i++) {
                        int at = extra[i].indexOf('@');
                        if (at <= 0) {
                            bonusIds[i] = Integer.parseInt(extra[i].trim());
                            bonusChances[i] = 0.0F;
                            continue;
                        }
                        bonusIds[i] = Integer.parseInt(extra[i].substring(0, at).trim());
                        bonusChances[i] = Float.parseFloat(extra[i].substring(at + 1).trim());
                    }
                }
                // 兼容"输入=输出:概率:时间"这种老写法：那个概率表示**同一个产物**再多出一个 ✓
                if (chance > 0.0F) {
                    int[] ids = new int[bonusIds.length + 1];
                    float[] cs = new float[bonusChances.length + 1];
                    System.arraycopy(bonusIds, 0, ids, 0, bonusIds.length);
                    System.arraycopy(bonusChances, 0, cs, 0, bonusChances.length);
                    ids[ids.length - 1] = out;
                    cs[cs.length - 1] = chance;
                    bonusIds = ids;
                    bonusChances = cs;
                }
                MAP.put(in, new Recipe(out, time, bonusIds, bonusChances));
            } catch (Throwable t) {
                System.out.println("[MITE] 粉碎配方解析失败: " + row + " (" + t + ")");
            }
        }
        System.out.println("[MITE] 粉碎配方 " + MAP.size() + " 条");
    }

    /** 有没有这个物品的配方（没配方就不该吸进去） */
    public static boolean hasRecipe(ItemStack in) {
        init();
        return in != null && MAP.containsKey(in.itemID);
    }

    public static Recipe get(ItemStack in) {
        init();
        return in == null ? null : MAP.get(in.itemID);
    }

    /** 额外产出里的"石头类"占位：圆石 / 地狱岩，按矿石是不是**下界变种**决定 ✓ */
    public static final int BONUS_STONE = -1;

    /**
     * "石头类掉落"到底给什么 ✓
     *
     * MITE 的矿石带"变种位"（参考数据 block_metadata.txt）：
     *   Block[14] 金矿石：{0=Gold Ore Stone, **2=Gold Ore Netherrack**, bit1 = 是由实体放置的}
     * → 所以**下界金矿石要掉地狱岩**（用户实测指出），普通金矿石/其它矿掉圆石 ✓
     * （判据用 bit2：meta 2 和 meta 3 都算下界变种 ✓）
     */
    private static int stoneDropFor(ItemStack input) {
        if (input != null && input.itemID == net.minecraft.Block.oreGold.blockID
                && (input.getItemSubtype() & 2) != 0) {
            return net.minecraft.Block.netherrack.blockID;   // 地狱岩
        }
        return net.minecraft.Block.cobblestone.blockID;      // 圆石
    }

    /**
     * 掷这一份的全部额外产出（每条独立判定一次）✓
     *
     * @param input  被粉碎的那一份原料（**要看它的元数据** —— 下界金矿石掉地狱岩 ✓）
     * @param random 传世界自己的随机源，保证服务端可复现、不额外 new Random
     * @return 这次额外多出来的东西（可能为空列表，但**不会是 null** ✓）
     */
    public static List<ItemStack> rollBonuses(Recipe recipe, ItemStack input, Random random) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (recipe == null || random == null) return out;
        for (int i = 0; i < recipe.bonusIds.length; i++) {
            float chance = i < recipe.bonusChances.length ? recipe.bonusChances[i] : 0.0F;
            if (chance <= 0.0F) continue;
            if (random.nextFloat() < chance) {
                int id = recipe.bonusIds[i] == BONUS_STONE ? stoneDropFor(input) : recipe.bonusIds[i];
                out.add(new ItemStack(id, 1, 0));
            }
        }
        return out;
    }
}
