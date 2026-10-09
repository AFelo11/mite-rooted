package net.dsh.createmite.kinetics.client;

import net.dsh.createmite.block.FurnaceCoreBlock;
import net.dsh.createmite.furnace.FurnaceCoreTileEntity;
import net.dsh.createmite.furnace.FurnaceMultiblock;
import net.minecraft.Block;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import net.minecraft.TileEntity;
import net.minecraft.TileEntitySpecialRenderer;
import net.minecraft.World;
import org.lwjgl.opengl.GL11;

/**
 * 3x3x3 大熔炉整机的渲染（挂在核心那一格的方块实体上，2026-09-30）。
 *
 * == 画在哪儿 ==
 *   模型是 0..48 像素 = **3 格**，中心在 24 像素 = 中间那格的正中心。
 *   所以把原点平移到 (核心 - 1) 的角上，几何正好铺满整个 3x3x3 ✓。
 *   另外 26 格方块的本体由 RenderBlocksMixin 直接**不画**了（见那边的注入）✓。
 *
 * == 朝向 ==
 *   几何**在生成期就转好了**（CreateModelsFurnaceBig 里 4 份朝向），这里不再用 glRotatef ✓。
 *   原因：整机是一次 Tessellator 批次画完的，GL 矩阵在顶点真正提交时早就复位了 ✗
 *   （这条坑在创造马达那儿踩过一次）。
 *
 * == 光照 ==
 *   和其它机器一样：关掉 GL_LIGHTING、用逐面明暗（CreateModels 的 FACE_SHADE ✓），
 *   再按世界亮度给整个批次设一次 brightness ✓ —— 不设的话洞里会是全亮的 ✗。
 */
public class FurnaceBigRenderer extends TileEntitySpecialRenderer {

    /** 12 种组合 × 11 张贴图（第一次渲染时建好） */
    private static ResourceLocation[][] tex;
    /** 第一次真正画出来时打一行日志（排查"到底有没有被调用"用 ✓） */
    private static boolean loggedOnce = false;
    /** 第一次取光照时打一行（排查"为什么黑"用 ✓） */
    private static boolean loggedLight = false;

    private static ResourceLocation[][] textures() {
        if (tex == null) {
            String[][] names = CreateModelsFurnaceBig.VARIANT_TEXTURES;
            ResourceLocation[][] out = new ResourceLocation[names.length][];
            for (int v = 0; v < names.length; v++) {
                out[v] = new ResourceLocation[names[v].length];
                for (int k = 0; k < names[v].length; k++) {
                    out[v][k] = new ResourceLocation("textures/blocks/" + names[v][k] + ".png");
                }
            }
            tex = out;
        }
        return tex;
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof FurnaceCoreTileEntity)) return;
        World world = te.getWorldObj();   // TileEntity.worldObj 是 protected，走 getter ✓
        if (world == null) return;
        int cx = te.xCoord, cy = te.yCoord, cz = te.zCoord;

        int meta = world.getBlockMetadata(cx, cy, cz) & FurnaceMultiblock.META_MASK;
        if (meta == 0) return;                       // 没成型 -> 画普通方块（由 RenderBlocks 那条路）
        Block self = Block.blocksList[world.getBlockId(cx, cy, cz)];
        if (!(self instanceof FurnaceCoreBlock)) return;

        int material = ((FurnaceCoreBlock) self).material();
        Block above = Block.blocksList[world.getBlockId(cx, cy + 1, cz)];
        int casing = FurnaceMultiblock.casingTypeOf(above);
        if (casing < 0) casing = FurnaceMultiblock.CASING_ANDESITE;
        boolean burning = FurnaceMultiblock.isBurning(world, cx, cy, cz);   // 核心 meta bit3 = 真工作状态 ✓

        int variant = CreateModelsFurnaceBig.variantIndex(material, casing, burning);
        CreateModels.El[] els = CreateModelsFurnaceBig.forFront(meta - 1);
        ResourceLocation[] set = textures()[variant];
        if (set == null) return;

        if (!loggedOnce) {
            loggedOnce = true;
            System.out.println("[MITE] 大熔炉整机渲染器开始工作 @ " + cx + "," + cy + "," + cz
                    + " meta=" + meta + " 材质=" + material + " 机壳=" + casing + " 组合=" + variant);
        }

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        GL11.glTranslated(x - 1.0D, y - 1.0D, z - 1.0D);   // 原点 = 核心那一格 -1 的角
        if (lighting) GL11.glDisable(GL11.GL_LIGHTING);
        if (cullFace) GL11.glDisable(GL11.GL_CULL_FACE);

        // ★★ 光照：**必须显式给**，不能沿用当时的 lightmap。
        //   实测两种错法各踩过一次：
        //     ① 什么都不设 → 沿用世界渲染此刻的 lightmap → 整机**全黑** ✗（用户截图实证）
        //     ② 用 core.getMixedBrightnessForBlock(...) → 也是黑的 ✗
        //   所以这里直接用 World.getLightBrightnessForSkyBlocks（打包格式 = (天光<<20)|(块光<<4)），
        //   并且**兜底全亮**：查出来是 0 就按 15/15 走 —— 宁可亮着，也不要黑 ✗。
        int rawLight = world.getLightBrightnessForSkyBlocks(cx, cy, cz, 0);
        int light = (rawLight != 0) ? rawLight : 0xF000F0;
        if (!loggedLight) {
            loggedLight = true;
            System.out.println("[MITE] 大熔炉光照取值: raw=0x" + Integer.toHexString(rawLight)
                    + " -> 用 0x" + Integer.toHexString(light));
        }

        Tessellator tess = Tessellator.instance;
        for (int layer = 0; layer < set.length; layer++) {
            this.bindTexture(set[layer]);
            tess.startDrawingQuads();
            tess.setBrightness(light);
            tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            CreateModels.emitRawLayer(tess, els, 0.0D, 0.0D, 0.0D, 1, 0.0F, layer);
            tess.draw();
        }

        if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}
