package net.dsh.createmite.shaft.client;

import net.dsh.createmite.block.BlockMaterialShaft;
import net.dsh.createmite.kinetics.client.CreateModels;
import net.dsh.createmite.shaft.MaterialShaftTileEntity;
import net.minecraft.Block;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import net.minecraft.TileEntity;
import net.minecraft.TileEntitySpecialRenderer;
import net.minecraft.World;
import org.lwjgl.opengl.GL11;

/**
 * 金属传动杆的渲染（TESR）—— 画的还是原版传动杆那份几何
 * （{@link CreateModels#SHAFT}：一个元素 (6,0,6)→(10,16,10) ✓），贴图换成该金属的纯色 ✓。
 *
 * 【为什么用"整张贴图 + emitRawLayer"而不是图集图标】
 *   物品栏那条路（RenderBlocksMixin.renderBlockAsItem）里**物品图集是绑着的** ✓，
 *   所以那里用图标没问题 ✓；但世界的 TESR 是**另一趟渲染** ✗ ——
 *   那会儿绑的是别的贴图 ✗，用图集 UV 会采到乱七八糟的东西（实测：柱子全黑 + 地上一片紫 ✓）。
 *   所以这里照 KineticRenderer 的做法：**逐层 bindTexture 自己的原图** + emitRawLayer ✓。
 *
 * 【光照】必须显式给这一批顶点设亮度 ✓ —— 沿用当时的 lightmap 会全黑 ✗
 *   （大熔炉整机那儿踩过同一个坑 ✓）。
 */
public class MaterialShaftRenderer extends TileEntitySpecialRenderer {

    /** 逐层的原图路径（0 = 侧面、1 = 顶底 ✓ 与 CreateModels.SHAFT 的层号一致） */
    private static final ResourceLocation[][] TEX = new ResourceLocation[5][2];

    private static ResourceLocation[] textures(BlockMaterialShaft shaft) {
        int idx = shaft.blockID - net.dsh.createmite.CMBlocks.ID_COPPER_SHAFT;   // 0..4
        if (idx < 0 || idx >= TEX.length) idx = 0;
        if (TEX[idx][0] == null) {
            String base = "textures/blocks/" + shaft.textureBase() + ".png";
            TEX[idx][0] = new ResourceLocation(base);
            TEX[idx][1] = new ResourceLocation("textures/blocks/" + shaft.textureBase() + "_top.png");
        }
        return TEX[idx];
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof MaterialShaftTileEntity)) return;
        World world = te.getWorldObj();
        if (world == null) return;
        Block block = Block.blocksList[world.getBlockId(te.xCoord, te.yCoord, te.zCoord)];
        if (!(block instanceof BlockMaterialShaft)) return;
        BlockMaterialShaft shaft = (BlockMaterialShaft) block;
        ResourceLocation[] tex = textures(shaft);

        int light = world.getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0);
        if (light == 0) light = 0xF000F0;      // 兜底全亮：宁可亮，不要黑 ✗

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        if (lighting) GL11.glDisable(GL11.GL_LIGHTING);
        if (cullFace) GL11.glDisable(GL11.GL_CULL_FACE);

        Tessellator tess = Tessellator.instance;
        for (int layer = 0; layer < 2; layer++) {
            if (tex[layer] == null) continue;
            this.bindTexture(tex[layer]);
            tess.startDrawingQuads();
            tess.setBrightness(light);
            tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            CreateModels.emitRawLayer(tess, CreateModels.SHAFT, 0.0D, 0.0D, 0.0D, 1, 0.0F, layer);
            tess.draw();
        }

        if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}
