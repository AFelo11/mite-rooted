package net.dsh.createmite.campfire;

import net.minecraft.EntityPlayer;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * ★ 营火成型 / 拆除（2026-10-07 用户定稿 ✓ 3x3 版 ✓）
 *
 *   以**中心格**（原木那格 ✓）为参照：
 *     木板 木板 木板
 *     羊毛 原木 羊毛      ← 中心 = 原木 = **核心格** ✓
 *     木板 木板 木板
 *
 *   · 木板 = id 5（0~3 = **任意木板** ✓）
 *   · 羊毛 = id 35（0~15 = **任意羊毛** ✓）
 *   · 原木 = id 17（0~3 = **任意原木** ✓）
 *   ⇒ 成型后 9 格全换成我们的方块（中心 = 核心 ✓ 其余 8 格 = 占位 ✓）⇒ **挖掉不返还** ✓
 *   ⚠️ 成型 / 拆除都**必须延后一 tick**（当场改方块会和区块写回打架 ✗ 会留鬼影方块 ✓）
 */
public final class CampfireForm {

    private static final int ID_PLANKS = 5;
    private static final int ID_WOOL = 35;
    private static final int ID_LOG = 17;

    private static final java.util.List<int[]> PENDING = new java.util.ArrayList<int[]>();
    private static final java.util.List<World> PENDING_W = new java.util.ArrayList<World>();
    /** 待拆除：{中心x,中心y,中心z, 被挖x,被挖y,被挖z} */
    private static final java.util.List<int[]> DEMO = new java.util.ArrayList<int[]>();
    private static final java.util.List<World> DEMO_W = new java.util.ArrayList<World>();
    private static boolean DEMOLISHING = false;

    private CampfireForm() {}

    private static boolean id(World w, int x, int y, int z, int want) {
        try { return w.getBlockId(x, y, z) == want; } catch (Throwable t) { return false; }
    }

    /** (cx,cy,cz) = 中心（原木）那一格 ✓ 两种朝向都收 ✓ */
    public static boolean matches(World w, int cx, int cy, int cz) {
        if (!id(w, cx, cy, cz, ID_LOG)) return false;
        boolean okA = true, okB = true;
        for (int d = -1; d <= 1; d++) {
            okA = okA && id(w, cx + d, cy, cz - 1, ID_PLANKS) && id(w, cx + d, cy, cz + 1, ID_PLANKS);
            okB = okB && id(w, cx - 1, cy, cz + d, ID_PLANKS) && id(w, cx + 1, cy, cz + d, ID_PLANKS);
        }
        okA = okA && id(w, cx - 1, cy, cz, ID_WOOL) && id(w, cx + 1, cy, cz, ID_WOOL);
        okB = okB && id(w, cx, cy, cz - 1, ID_WOOL) && id(w, cx, cy, cz + 1, ID_WOOL);
        return okA || okB;
    }

    public static void onPartAdded(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) queue(w, x + dx, y, z + dz);
    }

    /** 由 CMHeat 的周期扫描顺手调（玩家附近 ±4 ✓）成型 / 拆除都在这里落地 ✓ */
    public static void tickNear(World w, EntityPlayer p) {
        if (w == null || w.isRemote || p == null) return;
        processPending(w);
        processDemolish(w);
        int px = (int) Math.floor(p.posX), py = (int) Math.floor(p.posY), pz = (int) Math.floor(p.posZ);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (matches(w, x, y, z)) queue(w, x, y, z);
                }
            }
        }
    }

    private static void queue(World w, int x, int y, int z) {
        if (!matches(w, x, y, z)) return;
        for (int i = 0; i < PENDING.size(); i++) {
            int[] c = PENDING.get(i);
            if (PENDING_W.get(i) == w && c[0] == x && c[1] == y && c[2] == z) return;
        }
        PENDING.add(new int[] { x, y, z });
        PENDING_W.add(w);
    }

    private static void processPending(World w) {
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            if (PENDING_W.get(i) != w) continue;
            int[] c = PENDING.remove(i);
            PENDING_W.remove(i);
            try { form(w, c[0], c[1], c[2]); } catch (Throwable t) { System.out.println("[CreateMITE][CAMPFIRE] 成型失败: " + t); }
        }
    }

    // ------------------------------------------------------------------ 拆除

    /**
     * ★ 挖掉任何一格 ⇒ 登记拆除（**下一 tick** 才真的清空 ✓）
     *   selfIsCore = 挖的是核心格（那中心就是它自己 ✓）
     *   selfIsCore = false 时到附近 ±2 找核心 ✓
     */
    public static void demolishLater(World w, int x, int y, int z, boolean selfIsCore) {
        if (w == null || w.isRemote || DEMOLISHING) return;
        if (BlockCampfire.unlit() == null) return;
        int cx = x, cy = y, cz = z;
        if (!selfIsCore) {
            int[] c = findCore(w, x, y, z);
            if (c != null) { cx = c[0]; cy = c[1]; cz = c[2]; }
        }
        for (int i = 0; i < DEMO.size(); i++) {
            int[] c = DEMO.get(i);
            if (DEMO_W.get(i) == w && c[0] == cx && c[1] == cy && c[2] == cz) return;
        }
        DEMO.add(new int[] { cx, cy, cz, x, y, z });
        DEMO_W.add(w);
        System.out.println("[CreateMITE][CAMPFIRE] 拆除登记 " + cx + "," + cy + "," + cz + "（下一 tick 清空 3x3 ✓）");
    }

    private static int[] findCore(World w, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int id = 0;
                    try { id = w.getBlockId(x + dx, y + dy, z + dz); } catch (Throwable t) { }
                    if (isCore(id)) return new int[] { x + dx, y + dy, z + dz };
                }
            }
        }
        return null;
    }

    private static boolean isCore(int id) {
        if (BlockCampfire.unlit() != null && id == BlockCampfire.unlit().blockID) return true;
        return BlockCampfire.lit() != null && id == BlockCampfire.lit().blockID;
    }

    private static boolean isCampfire(int id) {
        return isCore(id) || id == BlockCampfirePart.ID_PART;
    }

    public static void processDemolish(World w) {
        if (w == null || w.isRemote) return;
        for (int i = DEMO.size() - 1; i >= 0; i--) {
            if (DEMO_W.get(i) != w) continue;
            int[] c = DEMO.remove(i);
            DEMO_W.remove(i);
            DEMOLISHING = true;
            try {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) clear(w, c[0] + dx, c[1], c[2] + dz);
                }
                // 兜底：被挖那一格周围 ±2 里还剩下的营火方块一起清掉 ✓（中心不可考时也不会留鬼影 ✓）
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int id = 0;
                        try { id = w.getBlockId(c[3] + dx, c[4], c[5] + dz); } catch (Throwable t) { continue; }
                        if (isCampfire(id)) clear(w, c[3] + dx, c[4], c[5] + dz);
                    }
                }
                System.out.println("[CreateMITE][CAMPFIRE] demolished 3x3 at " + c[0] + "," + c[1] + "," + c[2] + "（不掉落 ✓）");
            } catch (Throwable t) {
                System.out.println("[CreateMITE][CAMPFIRE] 拆除失败: " + t);
            } finally {
                DEMOLISHING = false;
            }
        }
    }

    private static void clear(World w, int x, int y, int z) {
        int id = 0;
        try { id = w.getBlockId(x, y, z); } catch (Throwable t) { return; }
        if (id == 0) return;
        try { w.removeBlockTileEntity(x, y, z); } catch (Throwable t) { }
        try { CampfireClock.clear(w, x, y, z); } catch (Throwable t) { }
        try { w.setBlock(x, y, z, 0); } catch (Throwable t) { }
        try { w.markBlockForUpdate(x, y, z); } catch (Throwable t) { }
        try { w.notifyBlockChange(x, y, z, 0); } catch (Throwable t) { }
    }

    // ------------------------------------------------------------------ 成型

    private static void form(World w, int cx, int cy, int cz) {
        if (!matches(w, cx, cy, cz)) return;
        if (BlockCampfire.unlit() == null) return;
        // 中心 = 核心 ✓ 其余 8 格 = 占位 ✓（挖掉不返还 ✓ 用户定稿）
        set(w, cx, cy, cz, BlockCampfire.unlit().blockID);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(w, cx + dx, cy, cz + dz, BlockCampfirePart.ID_PART);
            }
        }
        try {
            TileEntity te = w.getBlockTileEntity(cx, cy, cz);
            if (te instanceof TileCampfire) {
                ((TileCampfire) te).setCorner(cx, cy, cz);
            } else {
                TileCampfire fresh = new TileCampfire();
                fresh.setCorner(cx, cy, cz);
                fresh.xCoord = cx; fresh.yCoord = cy; fresh.zCoord = cz;
                w.setBlockTileEntity(cx, cy, cz, fresh);
            }
        } catch (Throwable t) { System.out.println("[CreateMITE][CAMPFIRE] 挂 TE 失败: " + t); }
        System.out.println("[CreateMITE][CAMPFIRE] formed 3x3 at " + cx + "," + cy + "," + cz + "（中心=核心 ✓）");
    }

    private static void set(World w, int x, int y, int z, int blockID) {
        try { w.setBlock(x, y, z, blockID); } catch (Throwable t) { }
        try { w.markBlockForUpdate(x, y, z); } catch (Throwable t) { }
    }
}
