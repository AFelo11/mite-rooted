package net.dsh.createmite.kinetics.client;

import net.minecraft.Icon;
import net.minecraft.Tessellator;

/**
 * 传动轴 / 手摇曲柄 / 石磨的几何（方块局部 0..1 坐标）。
 *
 * 世界（TESR）与背包（RenderBlocksMixin 接管 renderBlockAsItem）共用同一份，
 * 保证"世界里"和"物品栏里"是同一个模型。
 *
 * UV：这些部件的贴图就是整张贴在盒子上，所以 u/v 直接乘图标区间即可
 * （TESR 传整张图 → uMin=0,uScale=1）。
 */
public final class KineticGeometry {

    private KineticGeometry() {}

    /** Minecraft 经典逐面明暗 */
    private static final float[] FACE_SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.6F, 0.6F};

    // ---- 传动轴：两片十字 ----
    public static void emitShaft(Tessellator tess, double ox, double oy, double oz,
                                 float uMin, float vMin, float uScale, float vScale) {
        box(tess, ox, oy, oz, 0.06F, 0.0F, 0.43F, 0.94F, 1.0F, 0.57F, uMin, vMin, uScale, vScale);
        box(tess, ox, oy, oz, 0.43F, 0.0F, 0.06F, 0.57F, 1.0F, 0.94F, uMin, vMin, uScale, vScale);
    }

    // ---- 手摇曲柄：摇臂 + 握把 ----
    public static void emitCrank(Tessellator tess, double ox, double oy, double oz,
                                 float uMin, float vMin, float uScale, float vScale) {
        box(tess, ox, oy, oz, 0.08F, 0.44F, 0.43F, 0.92F, 0.56F, 0.57F, uMin, vMin, uScale, vScale);
        box(tess, ox, oy, oz, 0.80F, 0.40F, 0.40F, 0.92F, 0.60F, 0.60F, uMin, vMin, uScale, vScale);
    }

    // ---- 石磨：磨盘 + 两根辐条 ----
    public static void emitMillstone(Tessellator tess, double ox, double oy, double oz,
                                     float uMin, float vMin, float uScale, float vScale) {
        box(tess, ox, oy, oz, 0.08F, 0.80F, 0.08F, 0.92F, 0.94F, 0.92F, uMin, vMin, uScale, vScale);
        box(tess, ox, oy, oz, 0.00F, 0.80F, 0.42F, 0.14F, 0.94F, 0.58F, uMin, vMin, uScale, vScale);
        box(tess, ox, oy, oz, 0.86F, 0.80F, 0.42F, 1.00F, 0.94F, 0.58F, uMin, vMin, uScale, vScale);
    }

    /** 贴图集图标版：把整张图映射到图标的 UV 区间 */
    public static void emitShaftIcon(Tessellator tess, double ox, double oy, double oz, Icon icon) {
        emitShaft(tess, ox, oy, oz, icon.getMinU(), icon.getMinV(),
                icon.getMaxU() - icon.getMinU(), icon.getMaxV() - icon.getMinV());
    }

    public static void emitCrankIcon(Tessellator tess, double ox, double oy, double oz, Icon icon) {
        emitCrank(tess, ox, oy, oz, icon.getMinU(), icon.getMinV(),
                icon.getMaxU() - icon.getMinU(), icon.getMaxV() - icon.getMinV());
    }

    public static void emitMillstoneIcon(Tessellator tess, double ox, double oy, double oz, Icon icon) {
        emitMillstone(tess, ox, oy, oz, icon.getMinU(), icon.getMinV(),
                icon.getMaxU() - icon.getMinU(), icon.getMaxV() - icon.getMinV());
    }

    /** 带逐面明暗与法线的盒子；UV 为整张图（0..1）映射到 [uMin..uMin+uScale] */
    private static void box(Tessellator tess, double ox, double oy, double oz,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float uMin, float vMin, float uScale, float vScale) {
        float fu0 = uMin;
        float fv0 = vMin;
        float fu1 = uMin + uScale;
        float fv1 = vMin + vScale;
        // {顶点0..3, 面索引}
        float[][] faces = {
            {x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1},   // +Y
            {x1, y0, z1, x1, y0, z0, x0, y0, z0, x0, y0, z1},   // -Y
            {x0, y1, z0, x0, y0, z0, x1, y0, z0, x1, y1, z0},   // -Z
            {x1, y1, z1, x1, y0, z1, x0, y0, z1, x0, y1, z1},   // +Z
            {x0, y1, z1, x0, y0, z1, x0, y0, z0, x0, y1, z0},   // -X
            {x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1}    // +X
        };
        float[][] normals = {{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
        double[][] uvs = {{fu0, fv0}, {fu0, fv1}, {fu1, fv1}, {fu1, fv0}};
        for (int f = 0; f < 6; f++) {
            tess.setNormal(normals[f][0], normals[f][1], normals[f][2]);
            float s = FACE_SHADE[f];
            tess.setColorOpaque_F(s, s, s);
            float[] v = faces[f];
            for (int i = 0; i < 4; i++) {
                tess.addVertexWithUV(ox + v[i * 3], oy + v[i * 3 + 1], oz + v[i * 3 + 2],
                        uvs[i][0], uvs[i][1]);
            }
        }
    }
}
