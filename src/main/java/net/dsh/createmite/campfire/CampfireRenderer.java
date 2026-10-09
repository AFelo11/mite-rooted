package net.dsh.createmite.campfire;

import net.minecraft.Block;
import net.minecraft.Tessellator;
import net.minecraft.TileEntity;
import net.minecraft.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 营火整机渲染器（TESR ✓ 只画核心那一格 = 整个 3x3 ✓）
 *
 *   · 几何 = 原版 block/campfire（template_campfire.json / campfire_off.json）✓ **逐面 UV 照抄** ✓
 *   · 原版 1 格模型 -> 3x3 结构：XZ 放大 3 倍（±1.5 格）、Y 保持 1 格高（= 用户定稿的 3*1*3 ✓）
 *   · 贴图直接 bind 文件（MITE 的 TESR 走不了方块图集 ✓ 照 FurnaceBigRenderer / KineticRenderer ✓）
 *   · fire(16x128=8 帧) / log_lit(16x64=4 帧) 是竖排帧动画 ⇒ 直接 bind 不会自己动
 *     ⇒ 这里按「世界时间 / frametime」自己切 V 窗口 ✓
 *   · 熄灭态：所有面都用 campfire_log ✓（= 原版 campfire_off.json ✓）
 *     燃烧态：朝火的那几面换成 campfire_log_lit（余烬 ✓）+ 两片 45° 火焰面 ✓
 */
public class CampfireRenderer extends TileEntitySpecialRenderer {

    private static final net.minecraft.ResourceLocation TEX_LOG =
            new net.minecraft.ResourceLocation("textures/blocks/campfire_log.png");
    private static final net.minecraft.ResourceLocation TEX_LOG_LIT =
            new net.minecraft.ResourceLocation("textures/blocks/campfire_log_lit.png");
    private static final net.minecraft.ResourceLocation TEX_FIRE =
            new net.minecraft.ResourceLocation("textures/blocks/campfire_fire.png");

    private static final int FIRE_FRAMES = 8;      // campfire_fire.png = 16x128
    private static final int FIRE_FRAMETIME = 2;   // .mcmeta
    private static final int EMBER_FRAMES = 4;     // campfire_log_lit.png = 16x64
    private static final int EMBER_FRAMETIME = 20; // .mcmeta

    /** 原版 16 像素 -> 方块：XZ 每像素 3/16 格（整模型 3 格宽 ✓）、Y 每像素 2/16 格（木头加厚 ✓ 用户 A 方案 ✓）*/
    private static final double SXZ = 3.0D / 16.0D;
    /** ★ 木头厚度总开关：2/16 ⇒ 原木 0.75 x 0.5 格（不再扁 ✓）架高的那两根顶到 0.875 格（仍在 1 格内 ✓）
     *  想再厚就改这里（3/16 = 完全等比 0.75 格厚，但架高的木头会高出一格 ✓）*/
    private static final double SY = 2.0D / 16.0D;
    /** 火面顶（格 ✓）：不跟着 SY 等比放大（否则火有 3 格高 ✗）*/
    private static final double FIRE_TOP = 1.5D;

    private static final int FULL_BRIGHT = 0x00F000F0;

    /** 火面绕 Y 转 45° 后的端点偏移（= bbmodel 里 [-24..24] 转 45° => ±24/√2 像素 => ±24/√2/3 原版像素 ✓）*/
    private static final double D = 24.0D / 1.4142135623730951D / 3.0D;

    /**
     * 面表：{面, x0,y0,z0, x1,y1,z1, u1,v1,u2,v2, 贴图走向}
     *   面：0=北(-z) 1=南(+z) 2=西(-x) 3=东(+x) 4=上(+y) 5=下(-y) ✓
     *   走向：0 = u 沿「左下->右下」 1 = u 沿「左下->左上」 2 = 同 0 但 v 上下翻转（侧面用 ✓）
     *   坐标 / UV 全是原版 16 像素制 ✓
     */
    private static final double[][] F_LOG = new double[][] {
        // 原木一（x 1..5，z 0..16，躺地 ✓）
        { 0,  1,0, 0,  5,4,16,   0,4, 4,8,   2 },
        { 1,  1,0, 0,  5,4,16,   0,4, 4,8,   2 },
        { 2,  1,0, 0,  5,4,16,   0,0,16,4,   2 },
        { 4,  1,0, 0,  5,4,16,   0,0,16,4,   1 },
        { 5,  1,0, 0,  5,4,16,   0,0,16,4,   1 },
        // 原木二（x 0..16，z 11..15，架高 ✓）
        { 2,  0,3,11, 16,7,15,   0,4, 4,8,   2 },
        { 3,  0,3,11, 16,7,15,   0,4, 4,8,   2 },
        { 4,  0,3,11, 16,7,15,   0,0,16,4,   0 },
        // 原木三（x 11..15，z 0..16，躺地 ✓）
        { 0, 11,0, 0, 15,4,16,   0,4, 4,8,   2 },
        { 1, 11,0, 0, 15,4,16,   0,4, 4,8,   2 },
        { 3, 11,0, 0, 15,4,16,   0,0,16,4,   2 },
        { 4, 11,0, 0, 15,4,16,   0,0,16,4,   1 },
        { 5, 11,0, 0, 15,4,16,   0,0,16,4,   1 },
        // 原木四（x 0..16，z 1..5，架高 ✓）
        { 2,  0,3, 1, 16,7, 5,   0,4, 4,8,   2 },
        { 3,  0,3, 1, 16,7, 5,   0,4, 4,8,   2 },
        { 4,  0,3, 1, 16,7, 5,   0,0,16,4,   0 },
        // 中间灰烬条（x 5..11，y 0..1 ✓）
        { 0,  5,0, 0, 11,1,16,   0,15, 6,16, 2 },
        { 1,  5,0, 0, 11,1,16,  10,15,16,16, 2 },
        { 5,  5,0, 0, 11,1,16,   0,8,16,14,  1 },
    };

    /** 燃烧态的「余烬面」（原版 #lit_log 那几面 ✓ 熄灭时会被并进 F_LOG ✓）*/
    private static final double[][] F_EMBER = new double[][] {
        { 3,  1,0, 0,  5,4,16,   0,1,16,5,   2 },
        { 0,  0,3,11, 16,7,15,   0,0,16,4,   2 },
        { 1,  0,3,11, 16,7,15,   0,0,16,4,   2 },
        { 5,  0,3,11, 16,7,15,   0,4,16,8,   0 },
        { 2, 11,0, 0, 15,4,16,   0,1,16,5,   2 },
        { 0,  0,3, 1, 16,7, 5,   0,0,16,4,   2 },
        { 1,  0,3, 1, 16,7, 5,   0,0,16,4,   2 },
        { 5,  0,3, 1, 16,7, 5,   0,4,16,8,   0 },
        { 4,  5,0, 0, 11,1,16,   0,8,16,14,  1 },
    };

    private static final double[] SHADE = new double[] { 0.8D, 0.8D, 0.6D, 0.6D, 1.0D, 0.5D };

    @Override
    public void renderTileEntityAt(TileEntity te, double px, double py, double pz, float partial) {
        if (!(te instanceof TileCampfire)) return;
        net.minecraft.Minecraft mc = net.minecraft.Minecraft.getMinecraft();
        net.minecraft.World world = mc == null ? null : mc.theWorld;
        if (world == null) return;
        Block b = null;
        try { b = Block.blocksList[world.getBlockId(te.xCoord, te.yCoord, te.zCoord)]; } catch (Throwable ignored) { }
        if (!(b instanceof BlockCampfire)) return;
        boolean lit = ((BlockCampfire) b).isLit();
        int bright = FULL_BRIGHT;
        try { bright = world.getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0); } catch (Throwable ignored) { }
        long ticks = 0L;
        try { ticks = world.getTotalWorldTime(); } catch (Throwable ignored) { }

        boolean cull0 = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GL11.glPushMatrix();
        GL11.glTranslated(px + 0.5D, py, pz + 0.5D);   // 3x3 以核心格中心为原点 ✓
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator t = Tessellator.instance;

        // ---- 木头（原木贴图 ✓）----
        bind(TEX_LOG);
        t.startDrawingQuads();
        t.setBrightness(bright);
        emit(t, F_LOG, false, 0, 1);
        if (!lit) emit(t, F_EMBER, false, 0, 1);       // 熄灭态：余烬面也用原木 ✓
        t.draw();

        if (lit) {
            // ---- 余烬面（朝火那几面 ✓ 4 帧自己切 ✓）----
            int ef = (int) ((ticks / EMBER_FRAMETIME) % EMBER_FRAMES);
            bind(TEX_LOG_LIT);
            t.startDrawingQuads();
            t.setBrightness(bright);
            emit(t, F_EMBER, true, ef, EMBER_FRAMES);
            t.draw();
            // ---- 火焰两片 45° 面（双面 ✓ 满亮 ✓ 8 帧自己切 ✓）----
            int ff = (int) ((ticks / FIRE_FRAMETIME) % FIRE_FRAMES);
            bind(TEX_FIRE);
            t.startDrawingQuads();
            t.setBrightness(FULL_BRIGHT);
            t.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            double fy0 = 1.0D * SY;     // 火面底 = 原版 1 像素 ✓（跟着木头缩放 ✓）
            double fy1 = FIRE_TOP;
            fireQuad(t, new double[] { 8-D,fy0,8+D, 8+D,fy0,8-D, 8+D,fy1,8-D, 8-D,fy1,8+D }, ff);
            fireQuad(t, new double[] { 8+D,fy0,8+D, 8-D,fy0,8-D, 8-D,fy1,8-D, 8+D,fy1,8+D }, ff);
            t.draw();
        }
        if (light0) GL11.glEnable(GL11.GL_LIGHTING);
        if (cull0) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private void bind(net.minecraft.ResourceLocation rl) {
        try { this.bindTexture(rl); } catch (Throwable ignored) { }
    }

    /** 逐面铺一张面表 ✓ */
    private static void emit(Tessellator t, double[][] tbl, boolean frameWindow, int frame, int frames) {
        for (int i = 0; i < tbl.length; i++) {
            double[] e = tbl[i];
            int face = (int) e[0];
            double[] v = verts(face, e[1], e[2], e[3], e[4], e[5], e[6]);
            double u1 = e[7] / 16.0D, v1 = e[8] / 16.0D, u2 = e[9] / 16.0D, v2 = e[10] / 16.0D;
            if (u1 > u2) { double s = u1; u1 = u2; u2 = s; }
            if (v1 > v2) { double s = v1; v1 = v2; v2 = s; }
            if (frameWindow) { v1 = (frame + v1) / frames; v2 = (frame + v2) / frames; }
            double sh = SHADE[face];
            t.setColorOpaque_F((float) sh, (float) sh, (float) sh);
            int mode = (int) e[11];
            double[] uv;
            if (mode == 1) uv = new double[] { u1,v1, u1,v2, u2,v2, u2,v1 };
            else if (mode == 2) uv = new double[] { u1,v2, u2,v2, u2,v1, u1,v1 };
            else uv = new double[] { u1,v1, u2,v1, u2,v2, u1,v2 };
            for (int k = 0; k < 4; k++) {
                t.addVertexWithUV(cx(v[k * 3]), cy(v[k * 3 + 1]), cz(v[k * 3 + 2]), uv[k * 2], uv[k * 2 + 1]);
            }
        }
    }

    /** 火焰面：两片对角线 ✓ 双面画（正反各一遍 ✓）y 直接给「格」✓（自己一套比例 ✓）*/
    private static void fireQuad(Tessellator t, double[] p, int frame) {
        t.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        double v0 = (double) frame / FIRE_FRAMES, v1 = (double) (frame + 1) / FIRE_FRAMES;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                t.addVertexWithUV(cx(p[0]), p[1], cz(p[2]), 0.0D, v1);
                t.addVertexWithUV(cx(p[3]), p[4], cz(p[5]), 1.0D, v1);
                t.addVertexWithUV(cx(p[6]), p[7], cz(p[8]), 1.0D, v0);
                t.addVertexWithUV(cx(p[9]), p[10], cz(p[11]), 0.0D, v0);
            } else {
                t.addVertexWithUV(cx(p[9]), p[10], cz(p[11]), 0.0D, v0);
                t.addVertexWithUV(cx(p[6]), p[7], cz(p[8]), 1.0D, v0);
                t.addVertexWithUV(cx(p[3]), p[4], cz(p[5]), 1.0D, v1);
                t.addVertexWithUV(cx(p[0]), p[1], cz(p[2]), 0.0D, v1);
            }
        }
    }

    private static double cx(double v) { return (v - 8.0D) * SXZ; }
    private static double cy(double v) { return v * SY; }
    private static double cz(double v) { return (v - 8.0D) * SXZ; }

    /** 一个长方体的 6 个面各自的 4 个顶点（原版 16 像素制 ✓ 逆时针 = 朝外 ✓）*/
    private static double[] verts(int face, double x0, double y0, double z0, double x1, double y1, double z1) {
        switch (face) {
            case 0:  return new double[] { x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0 };
            case 1:  return new double[] { x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1 };
            case 2:  return new double[] { x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1 };
            case 3:  return new double[] { x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0 };
            case 4:  return new double[] { x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1 };
            default: return new double[] { x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0 };
        }
    }
}
