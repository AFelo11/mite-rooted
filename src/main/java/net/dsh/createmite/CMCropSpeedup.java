package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.World;

import java.util.ArrayList;

/**
 * ★★ `/S <倍速>` 也加速**作物**（2026-10-01 用户要求 ✓）
 *
 * 【为什么需要它】`/S` 原来只推进 `totalWorldTime`（**日历** ✓），
 *   而作物的生长靠 **方块的随机 tick** ✗ —— 两者是两套时钟 ✗✗
 *   ⇒ 开 `/S 10` 时"日子 10 倍速地过、作物按原速长" ⇒ 看上去作物纹丝不动 ✗
 *     （用户实测原话：「很明显现在没有加速效果」✓）
 *
 * 【怎么补】MITE 的作物每 **~1365 tick** 才轮到一次随机 tick ✓（原版随机 tick 频率 ✓）
 *   ⇒ 倍速 S 时，每株作物需要**额外** `(S-1)/1365` 次随机 tick / tick ✓
 *   ⇒ 这个量**非常小** ✓（100 株作物 + 10 倍速 ≈ 每 tick 0.66 次 ✓）⇒ 性能无忧 ✓
 *
 * 【实现】不逐格扫描（太贵 ✗）：
 *   ① 每 100 tick（5 秒）扫一次**玩家周围 24 格**内的作物，缓存坐标 ✓
 *   ② 每 tick 按"作物数 × (倍速-1) / 1365"的期望，随机挑几株补一次 updateTick ✓
 *   ⇒ 统计上每个作物拿到的额外随机 tick 次数 = 它应该拿到的次数的 (S-1) 倍 ✓
 *
 * ⚠️ 只补**作物**（BlockGrowingPlant ✓）✗ —— 不碰树苗/甘蔗/仙人掌那批（用户要求它们不受影响 ✓）
 */
public final class CMCropSpeedup {

    private CMCropSpeedup() {}

    /** 扫描间隔（tick ✓ 5 秒）*/
    private static final int SCAN_INTERVAL = 100;
    /** 扫描半径（格 ✓ 玩家周围）*/
    private static final int RADIUS = 24;
    /** 每 tick 最多补几次（性能上限 ✓）*/
    private static final int MAX_PER_TICK = 64;
    /** 原版随机 tick 频率：每格每 1365 tick 一次 ✓ */
    private static final float RANDOM_TICK_PERIOD = 1365.0F;

    private static final ArrayList<int[]> CROPS = new ArrayList<int[]>();
    private static int scanTimer = 0;
    /** 小数累积（用"千分之一"为单位存 ✓ 免得浮点误差吃掉小数 ✓）*/
    private static long carry = 0L;

    public static void tick(World world) {
        if (world == null || world.isRemote) return;
        float mul = CMTimeSpeed.multiplier();
        if (mul <= 1.0F) {
            if (!CROPS.isEmpty()) CROPS.clear();
            scanTimer = 0;
            carry = 0L;
            return;
        }
        if (--scanTimer <= 0) {
            scanTimer = SCAN_INTERVAL;
            rescan(world);
        }
        if (CROPS.isEmpty()) return;

        // 期望额外次数 = 作物数 × (倍速-1) / 1365 ✓（乘 1000 存成整数累积 ✓）
        carry += (long) (CROPS.size() * (mul - 1.0F) * 1000.0F / RANDOM_TICK_PERIOD);
        int n = (int) (carry / 1000L);
        carry -= n * 1000L;
        if (n <= 0) return;
        if (n > MAX_PER_TICK) n = MAX_PER_TICK;

        for (int i = 0; i < n; i++) {
            int[] c = CROPS.get(world.rand.nextInt(CROPS.size()));
            Block b = Block.blocksList[world.getBlockId(c[0], c[1], c[2])];
            if (b == null) continue;
            try {
                b.updateTick(world, c[0], c[1], c[2], world.rand);   // 就是随机 tick 那一下 ✓
            } catch (Throwable ignored) { }
        }
    }

    /** 扫玩家周围的作物（只扫一遍，存坐标 ✓）*/
    private static void rescan(World world) {
        CROPS.clear();
        java.util.List players = world.playerEntities;
        if (players == null) return;
        for (int p = 0; p < players.size(); p++) {
            Object o = players.get(p);
            if (!(o instanceof net.minecraft.EntityPlayer)) continue;
            net.minecraft.EntityPlayer ep = (net.minecraft.EntityPlayer) o;
            int px = (int) Math.floor(ep.posX);
            int py = (int) Math.floor(ep.posY);
            int pz = (int) Math.floor(ep.posZ);
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    for (int dy = -8; dy <= 8; dy++) {
                        int x = px + dx, y = py + dy, z = pz + dz;
                        if (y < 0 || y > 255) continue;
                        Block b = Block.blocksList[world.getBlockId(x, y, z)];
                        if (b instanceof net.minecraft.BlockGrowingPlant) {   // 只收作物 ✓
                            CROPS.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
    }
}
