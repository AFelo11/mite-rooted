package net.dsh.createmite.kinetics.client;

import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * 3D **物品**模型的绘制工具（目前只有扳手）。
 *
 * 【和方块渲染的关系】几何、贴图、图层都复用 CreateModels（见 {@link CreateModels#WRENCH_ITEM}），
 * 差别只在"外面那层 GL 变换"：
 *   方块  → 由 TESR / RenderBlocks 摆到世界坐标
 *   物品  → 由 model json 的 display.{gui,firstperson_righthand,thirdperson_righthand} 摆
 *
 * 【display 变换的正确写法】必须严格按 Minecraft 的 TRSRTransformation 语义来：
 *     M = T · (Rz · Ry · Rx) · S
 * 注意两点，写反了就会"位置不对"：
 *   1. 顺序是 T 然后 R 然后 S —— 常见错误是写成 T·S·R；
 *   2. 欧拉角 XYZ 对应的旋转矩阵是 **Rz·Ry·Rx**，而 GL 是右乘，
 *      所以代码里要**先写 z、再写 y、最后写 x**（和字面顺序相反）。
 * 最后再平移 -0.5 把 0..1 的模型中心挪到原点 —— 这一步对应原版
 * ItemRenderer.renderItem 开头那句 GlStateManager.translate(-0.5F, -0.5F, -0.5F)，
 * 必须放在 display 变换**之后**。
 */
public final class ItemModelRender {

    /** Create 的扳手贴图：它是那个 3D 模型的 UV 展开表，不是 16x16 图标 */
    public static final ResourceLocation TEX_WRENCH = new ResourceLocation("textures/items/wrench.png");

    /** CreateModels.LAYER_NAMES 里 "wrench" 的下标 */
    private static final int LAYER_WRENCH = 14;

    private ItemModelRender() {}

    /**
     * 施加模型 json 里的一段 display 变换。
     *
     * @param tx,ty,tz 平移，单位是 1/16 格（json 原样）
     * @param rx,ry,rz 欧拉角，单位是度（json 原样，XYZ 序）
     * @param scale    等比缩放
     */
    public static void applyDisplay(float tx, float ty, float tz,
                                    float rx, float ry, float rz, float scale) {
        GL11.glTranslatef(tx / 16.0F, ty / 16.0F, tz / 16.0F);
        GL11.glRotatef(rz, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(ry, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rx, 1.0F, 0.0F, 0.0F);
        if (scale != 1.0F) GL11.glScalef(scale, scale, scale);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
    }

    /** 画扳手本体（调用方负责 bindTexture 和 GL 状态） */
    public static void drawWrench() {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        CreateModels.emitRawLayer(tess, CreateModels.WRENCH_ITEM, 0.0D, 0.0D, 0.0D, 1, 0.0F, LAYER_WRENCH);
        tess.draw();
    }
}
