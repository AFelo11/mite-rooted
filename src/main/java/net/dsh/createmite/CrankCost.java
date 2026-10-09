package net.dsh.createmite;

import net.minecraft.EntityPlayer;
import net.minecraft.FoodStats;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 手摇曲柄的食物消耗 —— **直接扣饱和度**，不再经过 MITE 的饥饿累积系统。
 *
 * 【为什么换掉 addHungerServerSide】
 * 那条路的换算比例没法确知（实测"每次右键掉 1 点"和预期的 10 倍差距对不上，
 * 而且不同饱食状态下还会变），导致"每 5 次 0.5"这种精确要求根本调不准。
 *
 * 【现在怎么算】MITE 的饱和度是 **int**，而我们要的是 0.5 / 5 次 = 0.1 / 次，
 * 所以按玩家各自记一个**小数欠账**，攒满 1 点才真正 setSatiation(-1)：
 * <pre>10 次右键 = 1.0 点 → 5 次正好 0.5 点 ✓</pre>
 *
 * 用 WeakHashMap 按玩家存欠账，玩家下线自动回收，不需要往 FoodStats 上挂字段。
 */
public final class CrankCost {

    private static final Map<Object, float[]> PENDING = new WeakHashMap<Object, float[]>();

    private CrankCost() {}

    /** 摇一次曲柄的消耗；amount = 每次扣多少点饱和度（默认 0.1 = 每 5 次 0.5） */
    public static void consume(EntityPlayer player, float amount) {
        if (player == null || amount <= 0.0F) return;
        FoodStats stats = player.getFoodStats();
        if (stats == null) return;

        float[] box = PENDING.get(player);
        if (box == null) {
            box = new float[]{0.0F};
            PENDING.put(player, box);
        }
        box[0] += amount;

        while (box[0] >= 1.0F) {
            box[0] -= 1.0F;
            int current = stats.getSatiation();
            if (current <= 0) {
                box[0] = 0.0F;          // 见底了，欠账清零，不做负反馈
                break;
            }
            stats.setSatiation(current - 1, true);
        }
    }
}
