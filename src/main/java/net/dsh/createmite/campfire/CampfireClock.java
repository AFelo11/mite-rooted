package net.dsh.createmite.campfire;

import net.minecraft.World;

/**
 * 营火燃烧计时（2026-10-07 加 ✓）
 *
 *   ⚠️ 熄灭 <-> 燃烧要换方块 id（亮度只能注册期定 ✗），而**换 id 会重建方块实体** ⇒
 *      把 burnUntil 只放在 TE 里会丢 ✗（实测：点火后立刻被自己判成烧完 ⇒ 反复点火都没火 ✗）
 *   ⇒ 计时表放这里（静态 ✓ 按「世界 + 坐标」查 ✓ 换 TE / 换 id 都不丢 ✓）
 *   NBT 里仍然存一份 ✓（读档时 updateEntity 会把它捡回表里 ✓）
 */
final class CampfireClock {

    private static final java.util.HashMap<String, long[]> M = new java.util.HashMap<String, long[]>();

    private CampfireClock() {}

    private static String k(World w, int x, int y, int z) {
        return System.identityHashCode(w) + ":" + x + "," + y + "," + z;
    }

    /** [0] = 烧到什么时候（世界时间 ✓） [1] = 已加木材数 ✓ */
    static void put(World w, int x, int y, int z, long until, int wood) {
        if (w == null) return;
        M.put(k(w, x, y, z), new long[] { until, wood });
    }

    static long[] get(World w, int x, int y, int z) {
        if (w == null) return null;
        return M.get(k(w, x, y, z));
    }

    static void clear(World w, int x, int y, int z) {
        if (w == null) return;
        M.remove(k(w, x, y, z));
    }
}
