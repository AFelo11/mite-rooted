package net.dsh.createmite.kinetics.client;

import net.minecraft.Icon;
import net.minecraft.Tessellator;

/**
 * 齿轮几何 —— 逐字段复刻 Create 的 `assets/create/models/block/cogwheel.json`。
 *
 * 该模型共 7 个元素（Axis / Gear / Gear2 / Gear3 / Gear4 / GearCaseInner / GearCaseOuter），
 * 用 3 张贴图：cogwheel.png(32×32)、cogwheel_axis.png(16×16)、axis_top.png(16×16)。
 *
 * 坐标系：局部坐标以"像素"为单位（1 格 = 16px），轴方向固定为 Y；
 * 调用方传入 axis(0=X,1=Y,2=Z) 把它摆到真实轴向（纯旋转，不做镜像）。
 *
 * 与 Minecraft 方块模型一致的三条约定：
 *   1) 面的顶点顺序满足 c0→c1 = 贴图 u 方向、c0→c3 = 贴图 v 方向；
 *   2) 元素 rotation 是绕 origin（本模型为方块中心）旋转；
 *   3) 面的 "rotation": 90 表示该面贴图相对面内 u/v 轴转 90°。
 */
public final class CogwheelGeometry {

    private CogwheelGeometry() {}

    /** 贴图层：齿轮 / 齿轮自带的轴身 / 轴端 / 传动轴轴身 / 传动轴端面 */
    public static final int LAYER_GEAR = 0;
    public static final int LAYER_AXIS_SIDE = 1;
    public static final int LAYER_AXIS_TOP = 2;
    public static final int LAYER_SHAFT_SIDE = 3;
    public static final int LAYER_SHAFT_TOP = 4;
    /** 贴图层总数 */
    public static final int LAYER_COUNT = 5;
    /**
     * 模型 UV 空间大小。
     *
     * 【重要】方块模型的逐面 UV 一律写在 0..16 的空间里，与实际贴图分辨率无关
     * （贴图是 32×32 时，UV=16 仍代表贴图整幅）。所以换算必须是 uv/16，
     * 不能除以贴图像素尺寸 —— 否则只会采样到贴图左上角一小块。
     * 本文件里的 UV 常量就是照抄 Create 模型的 0..16 原值。
     */
    private static final float MODEL_UV = 16F;

    private static final class Box {
        final String name;
        final float x0, y0, z0, x1, y1, z1;
        final float rotY;             // 绕方块中心的 Y 轴旋转（Create 的 ±45° 齿盘）
        final float[] uvTop;          // 上下面
        final float[] uvNS;           // 南北面(±Z)
        final float[] uvEW;           // 东西面(±X)
        final int rotTop;             // 上下面 UV 旋转（0 或 90）
        final int sideLayer;          // 四个侧面用哪张贴图
        final int topLayer;           // 上下面用哪张贴图

        Box(String name, float x0, float y0, float z0, float x1, float y1, float z1, float rotY,
            float[] uvTop, float[] uvNS, float[] uvEW, int rotTop, int sideLayer, int topLayer) {
            this.name = name;
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.rotY = rotY;
            this.uvTop = uvTop; this.uvNS = uvNS; this.uvEW = uvEW;
            this.rotTop = rotTop;
            this.sideLayer = sideLayer; this.topLayer = topLayer;
        }
    }

    // ---- cogwheel.json 里的逐面 UV（贴图像素矩形 [u0,v0,u1,v1]）----
    private static final float[] CASE_OUTER_SIDE = {0F, 4F, 4F, 6F};
    private static final float[] CASE_OUTER_TOP  = {0F, 0F, 4F, 4F};
    private static final float[] CASE_INNER_SIDE = {0F, 6F, 6F, 7.5F};
    private static final float[] CASE_INNER_TOP  = {4F, 0F, 10F, 6F};
    private static final float[] GEAR_NS  = {7F, 8F, 16F, 9.5F};   // Gear 的长边（南北面）
    private static final float[] GEAR_EW  = {5F, 8F, 6.5F, 9.5F};  // Gear 的短边（东西面）
    private static final float[] GEAR_TOP = {7F, 6F, 16F, 7.5F};
    private static final float[] AXIS_SIDE = {6F, 0F, 10F, 16F};
    private static final float[] AXIS_TOP  = {6F, 6F, 10F, 10F};
    // 传动轴（shaft.png / shaft_top.png）的 UV，与轴上完全同款，用于中心轮毂
    private static final float[] SHAFT_SIDE = {6F, 0F, 10F, 16F};
    private static final float[] SHAFT_TOP  = {6F, 6F, 10F, 10F};

    /** 5 个元素：中间传动轴柱 + 4 条木纹齿条（Create 原模型的 2 个木纹轮毂元素已按需求移除） */
    public static final Box[] BOXES = {
        // 中间柱：使用"传动轴"材质（shaft.png / shaft_top.png）
        new Box("Axis", 6F, 0F, 6F, 10F, 16F, 10F, 0F,
                SHAFT_TOP, SHAFT_SIDE, SHAFT_SIDE, 0, LAYER_SHAFT_SIDE, LAYER_SHAFT_TOP),
        new Box("Gear", -1F, 6.5F, 6.5F, 17F, 9.5F, 9.5F, 0F,
                GEAR_TOP, GEAR_NS, GEAR_EW, 0, LAYER_GEAR, LAYER_GEAR),
        new Box("Gear2", -1F, 6.5F, 6.5F, 17F, 9.5F, 9.5F, 45F,
                GEAR_TOP, GEAR_NS, GEAR_EW, 0, LAYER_GEAR, LAYER_GEAR),
        new Box("Gear3", -1F, 6.5F, 6.5F, 17F, 9.5F, 9.5F, -45F,
                GEAR_TOP, GEAR_NS, GEAR_EW, 0, LAYER_GEAR, LAYER_GEAR),
        // Gear4 的南北/东西面 UV 与 Gear 相反，且上下面有 "rotation": 90
        new Box("Gear4", 6.5F, 6.5F, -1F, 9.5F, 9.5F, 17F, 0F,
                GEAR_TOP, GEAR_EW, GEAR_NS, 90, LAYER_GEAR, LAYER_GEAR),
        // 【按需求移除】GearCaseInner / GearCaseOuter（木纹轮毂）：
        // 这两个 Create 原模型的"轮毂"元素会把中间的轴柱包住，
        // 于是中心看起来是木纹在转（就是"多出来的材质"）。
        // 去掉之后，中心只剩 4×4 的传动轴柱（柱比 3px 的齿条粗，正好把交叉处盖住），
        // 转动时看到的就是传动轴材质。齿轮本体=8 条木纹齿条。
    };

    /**
     * 6 个面：{顶点0, 顶点1, 顶点2, 顶点3, UV组}。
     * 顶点索引 = xi*4 + yi*2 + zi（即 0=(x0,y0,z0) … 7=(x1,y1,z1)）。
     * UV组：0=上下面，1=南北面(±Z)，2=东西面(±X)。
     */
    private static final int[][] FACES = {
        {2, 6, 7, 3, 0},   // +Y
        {0, 4, 5, 1, 0},   // -Y
        {6, 2, 0, 4, 1},   // -Z
        {3, 7, 5, 1, 1},   // +Z
        {2, 3, 1, 0, 2},   // -X
        {7, 6, 4, 5, 2}    // +X
    };

    /**
     * 逐面明暗（Minecraft 经典取值：上 1.0 / 下 0.5 / 南北 0.8 / 东西 0.6）。
     *
     * TESR 里光照是关掉的，如果所有面都用纯白，模型会显得很平、
     * 只剩贴图花纹抢眼；按面乘上明暗才有立体感（Create 也是这个观感）。
     * 背包/手持那条路径本身有标准物品光照，传 shaded=false 避免二次压暗。
     */
    private static final float[] FACE_SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.6F, 0.6F};

    /** 各面的外法线（局部坐标） */
    private static final float[][] NORMALS = {
        {0F, 1F, 0F}, {0F, -1F, 0F}, {0F, 0F, -1F}, {0F, 0F, 1F}, {-1F, 0F, 0F}, {1F, 0F, 0F}
    };

    /**
     * 图集版：用于背包/手持渲染（3 张贴图都在方块图集里）。
     * 调用方负责 startDrawingQuads()/draw()。
     */
    public static void emit(Tessellator tess, double ox, double oy, double oz, int axis,
                            Icon gearIcon, Icon axisSideIcon, Icon axisTopIcon,
                            Icon shaftSideIcon, Icon shaftTopIcon) {
        Icon[] icons = {gearIcon, axisSideIcon, axisTopIcon, shaftSideIcon, shaftTopIcon};
        float[] uMin = new float[LAYER_COUNT];
        float[] vMin = new float[LAYER_COUNT];
        float[] uScale = new float[LAYER_COUNT];
        float[] vScale = new float[LAYER_COUNT];
        boolean[] enabled = new boolean[LAYER_COUNT];
        for (int l = 0; l < LAYER_COUNT; l++) {
            Icon icon = icons[l];
            if (icon == null) continue;      // 该层贴图不可用就跳过（而不是画成黑块）
            enabled[l] = true;
            uMin[l] = icon.getMinU();
            vMin[l] = icon.getMinV();
            uScale[l] = icon.getMaxU() - icon.getMinU();
            vScale[l] = icon.getMaxV() - icon.getMinV();
        }
        emitAll(tess, ox, oy, oz, axis, uMin, vMin, uScale, vScale, enabled, true);
    }

    /**
     * TESR 版：绑定原始 png（整张图）时使用，一次只画一个贴图层。
     * 调用方负责 startDrawingQuads()/draw()，并在调用前 bindTexture 对应贴图。
     */
    public static void emitRawLayer(Tessellator tess, double ox, double oy, double oz, int axis, int layer, boolean shaded) {
        float[] uMin = new float[LAYER_COUNT];
        float[] vMin = new float[LAYER_COUNT];
        float[] uScale = {1F, 1F, 1F, 1F, 1F};
        float[] vScale = {1F, 1F, 1F, 1F, 1F};
        boolean[] enabled = new boolean[LAYER_COUNT];
        enabled[layer] = true;
        emitAll(tess, ox, oy, oz, axis, uMin, vMin, uScale, vScale, enabled, shaded);
    }

    private static void emitAll(Tessellator tess, double ox, double oy, double oz, int axis,
                                float[] uMin, float[] vMin, float[] uScale, float[] vScale,
                                boolean[] enabled, boolean shaded) {
        for (int i = 0; i < BOXES.length; i++) {
            emitBox(tess, BOXES[i], ox, oy, oz, axis, uMin, vMin, uScale, vScale, enabled, shaded);
        }
    }

    private static void emitBox(Tessellator tess, Box b, double ox, double oy, double oz, int axis,
                                float[] uMin, float[] vMin, float[] uScale, float[] vScale,
                                boolean[] enabled, boolean shaded) {
        // 8 个角点（像素 → 0..1）
        float[][] corners = new float[8][3];
        int idx = 0;
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    corners[idx][0] = (xi == 0 ? b.x0 : b.x1) / 16F;
                    corners[idx][1] = (yi == 0 ? b.y0 : b.y1) / 16F;
                    corners[idx][2] = (zi == 0 ? b.z0 : b.z1) / 16F;
                    idx++;
                }
            }
        }

        for (int f = 0; f < FACES.length; f++) {
            int[] face = FACES[f];
            int group = face[4];
            int layer = (group == 0) ? b.topLayer : b.sideLayer;
            if (!enabled[layer]) continue;
            float[] uv = (group == 0) ? b.uvTop : (group == 1 ? b.uvNS : b.uvEW);
            if (uv == null) continue;

            double fu0 = uMin[layer] + (uv[0] / MODEL_UV) * uScale[layer];
            double fv0 = vMin[layer] + (uv[1] / MODEL_UV) * vScale[layer];
            double fu1 = uMin[layer] + (uv[2] / MODEL_UV) * uScale[layer];
            double fv1 = vMin[layer] + (uv[3] / MODEL_UV) * vScale[layer];

            // 顶点 i 的 UV：0=(u0,v0) 1=(u1,v0) 2=(u1,v1) 3=(u0,v1)
            double[][] uvs = {{fu0, fv0}, {fu1, fv0}, {fu1, fv1}, {fu0, fv1}};
            if (group == 0 && b.rotTop == 90) {
                // "rotation": 90 —— 把 UV 沿面内旋转 90°（面 u 轴改为对应贴图 -v，面 v 轴对应贴图 +u）
                uvs = new double[][]{uvs[3], uvs[0], uvs[1], uvs[2]};
            }

            float[] normal = rotateVector(NORMALS[f], b.rotY, axis);
            tess.setNormal(normal[0], normal[1], normal[2]);
            float shade = shaded ? FACE_SHADE[f] : 1.0F;
            tess.setColorOpaque_F(shade, shade, shade);

            for (int v = 0; v < 4; v++) {
                double[] p = localToWorld(corners[face[v]], b.rotY, axis);
                tess.addVertexWithUV(ox + p[0], oy + p[1], oz + p[2], uvs[v][0], uvs[v][1]);
            }
        }
    }

    /** 点：先绕方块中心（0.5,0.5）做 Y 轴旋转，再按轴向摆正 */
    private static double[] localToWorld(float[] p, float rotYDeg, int axis) {
        double x = p[0];
        double y = p[1];
        double z = p[2];

        if (rotYDeg != 0F) {
            // Create 的 origin 是 [8,8,8]，即方块中心
            double dx = x - 0.5D;
            double dz = z - 0.5D;
            double r = Math.toRadians(rotYDeg);
            double cos = Math.cos(r);
            double sin = Math.sin(r);
            x = 0.5D + dx * cos - dz * sin;
            z = 0.5D + dx * sin + dz * cos;
        }
        if (axis == 0) {
            // 局部 Y → X：(x,y,z) → (y, 1-x, z)
            return new double[]{y, 1.0D - x, z};
        } else if (axis == 2) {
            // 局部 Y → Z：(x,y,z) → (x, 1-z, y)
            return new double[]{x, 1.0D - z, y};
        }
        return new double[]{x, y, z};
    }

    /** 向量：与 localToWorld 相同的旋转，但不做平移 */
    private static float[] rotateVector(float[] v, float rotYDeg, int axis) {
        double x = v[0];
        double y = v[1];
        double z = v[2];

        if (rotYDeg != 0F) {
            double r = Math.toRadians(rotYDeg);
            double cos = Math.cos(r);
            double sin = Math.sin(r);
            double nx = x * cos - z * sin;
            double nz = x * sin + z * cos;
            x = nx;
            z = nz;
        }
        if (axis == 0) {
            return new float[]{(float) y, (float) -x, (float) z};
        } else if (axis == 2) {
            return new float[]{(float) x, (float) -z, (float) y};
        }
        return new float[]{(float) x, (float) y, (float) z};
    }
}
