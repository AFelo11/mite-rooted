package net.dsh.createmite;

/**
 * 结构选择器的两个角点（开发用工具）。
 *
 * 【为什么可以用一个静态类】这个工具是用来**给 AI 看玩家搭了什么结构**的 ✓，
 * 只需要在**单机**里工作 ✓ —— 单机时客户端和服务端在同一个 JVM 里 ✓，
 * 所以客户端记的点（左键）服务端（/T 指令）能直接读到 ✓✓。
 * ⚠️ 多人游戏里这条不成立 ✗（那需要发包），这里是开发工具，不做那套 ✓。
 */
public final class CMSelection {

    public static boolean hasA = false;
    public static boolean hasB = false;
    public static int ax, ay, az;
    public static int bx, by, bz;
    public static int dimA = Integer.MIN_VALUE;
    public static int dimB = Integer.MIN_VALUE;

    private CMSelection() {}

    public static void setA(int x, int y, int z, int dim) {
        ax = x; ay = y; az = z; dimA = dim; hasA = true;
    }

    public static void setB(int x, int y, int z, int dim) {
        bx = x; by = y; bz = z; dimB = dim; hasB = true;
    }

    public static void clear() {
        hasA = false; hasB = false;
    }

    public static String describeA() {
        return hasA ? (ax + ", " + ay + ", " + az) : "（未选）";
    }

    public static String describeB() {
        return hasB ? (bx + ", " + by + ", " + bz) : "（未选）";
    }

    /** 两个点是否齐了、且同一维度 ✓ */
    public static boolean ready() {
        return hasA && hasB && dimA == dimB;
    }
}
