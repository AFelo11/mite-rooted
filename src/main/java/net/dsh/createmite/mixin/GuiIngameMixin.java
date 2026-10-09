package net.dsh.createmite.mixin;

import net.dsh.createmite.CMConfig;
import net.minecraft.FoodStats;
import net.minecraft.GuiIngame;
import net.minecraft.Minecraft;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 用**金色鸡腿纹路**在食物条上叠出玩家的实时饱和度（照搬 AppleSkin 的 SaturationOverlay）。
 *
 * 【为什么需要它】反编译 MITE 画 HUD 的 GuiIngame.func_110327_a(II) 可以看到：
 * 它只调了 getNutrition() / getNutritionLimit()，**从来没调 getSatiation()**
 * —— MITE 的 HUD 只画"饥饿值"，**饱和度是看不见的**（只在食物提示里能看到）。
 * 这里把饱和度补画成 AppleSkin 那种金色覆盖。
 *
 * 【画法】AppleSkin 是拿**同一条鸡腿图标**再画一遍、染成金色半透明，从右往左铺，
 * 覆盖长度 = 饱和度 / 上限。这里完全一样（原版 icons.png 的满鸡腿在 u52..61 / v27..36）。
 *
 * 【位置】MITE 沿用了原版 width/2+91, height-39 的基准，但实测条被挪到了右上
 * （854x480 下 x≈557~610 / y≈402~412），所以留了两个可调偏移，对不上改 config 即可。
 */
@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final float U0 = 52.0F / 256.0F;
    private static final float U1 = 61.0F / 256.0F;
    private static final float V0 = 27.0F / 256.0F;
    private static final float V1 = 36.0F / 256.0F;
    private static final float UH = 70.0F / 256.0F;      // 半鸡腿右边界

    /** 画一条金色鸡腿（AppleSkin 的覆盖做法：同一条图标 + 金色半透明） */
    private static void cm$shank(Tessellator tess, int x, int y, float u0, float u1) {
        // ★ 每边放大 pad 像素 —— 画得比鸡腿本身大一圈，金色就从鸡腿边缘透出来，
        //   形成"包围一圈金色纹路"的效果（之前同尺寸画，被鸡腿盖得只剩边角）。
        // 独立一条 → 不需要放大，pad 默认 0（原尺寸画最清晰，不放大就不会糊）
        int pad = (int) CMConfig.getFloat("saturation.hud_pad", 0.0F);
        int x0 = x - pad;
        int y0 = y - pad;
        int s = SIZE + pad * 2;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x0, y0 + s, 0.0D, u0, V1);
        tess.addVertexWithUV(x0 + s, y0 + s, 0.0D, u1, V1);
        tess.addVertexWithUV(x0 + s, y0, 0.0D, u1, V0);
        tess.addVertexWithUV(x0, y0, 0.0D, u0, V0);
        tess.draw();
    }
    private static final int SLOTS = 10;
    private static final int STEP = 8;
    private static final int SIZE = 9;

    @Inject(method = "func_110327_a(II)V", at = @At("RETURN"))
    private void cm$drawSaturation(int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        FoodStats stats = mc.thePlayer.getFoodStats();
        if (stats == null) return;

        int saturation = stats.getSatiation();
        int limit = stats.getSatiationLimit();
        if (saturation <= 0 || limit <= 0) return;

        // AppleSkin 的细节：按格子数取整，**余数 ≥ 半格就多画一条半鸡腿**（原版 icons.png 半鸡腿在 u61..70）
        // 【条子有几格要按"每格 2 点"算】MITE 的饱食度上限随等级增加，
        // 等级 0 时上限 6 点 = **3 个鸡腿**，不是 10 个 —— 按固定 10 格算长度必然超。
        int nutrition = stats.getNutrition();
        int nutritionLimit = stats.getNutritionLimit();
        int totalSlots = Math.max(1, nutritionLimit / 2);            // 条子一共几格
        float barSlots = Math.max(0.0F, (float) nutrition / 2.0F);   // 已填满几格（= 当前饱食度）

        // 规则（照原版）：**饱和度不会超过玩家当前的饱食度** → 覆盖长度取两者较小值
        float slots = Math.min((float) totalSlots * saturation / (float) limit, barSlots);
        slots = Math.min(slots, (float) totalSlots);
        int fullSlots = (int) Math.floor(slots);   // 满格数（不足的零头按半格画）
        boolean halfSlot = (slots - fullSlots) >= 0.5F;
        if (fullSlots == 0 && !halfSlot) fullSlots = 1;      // 有一点点也画出来，免得"看不见"

        // 偏移默认 0：MITE 画条用的就是原版这条 width/2+91, height-39（反编译可见 bipush 91 / bipush 39）。
        // 之前误加了 +80/-35 的"实测值"，导致覆盖层整体偏右上、超出条子 —— 已改回 0。
        // ★ 改成**独立一条，画在饱食度条正上方**：不再试图和 MITE 的鸡腿图标对齐，
        //   所以既不会糊、也不会错位。默认抬高 11px（图标 9px + 2px 间隙）。
        int right = width / 2 + 91 + (int) CMConfig.getFloat("saturation.hud_offset_x", 0.0F);
        int y = height - 39 + (int) CMConfig.getFloat("saturation.hud_offset_y", -11.0F);

        // ★ 只画我们这一行，画完用 PopAttrib 把 GL 状态**原样还回去** ——
        //   之前直接改 glTexParameteri/Blend/Depth 会污染 MITE 后面画的 UI（血条/经验条/文字），
        //   表现就是整个 HUD 发糊。这是踩过的坑，别再动全局状态。
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 0.85F, 0.25F, 0.75F);   // 金色，透明度调高一点更醒目
        mc.getTextureManager().bindTexture(ICONS);

        Tessellator tess = Tessellator.instance;
        for (int i = 0; i < fullSlots; i++) {
            cm$shank(tess, right - SIZE - i * STEP, y, U0, U1);
        }
        if (halfSlot) {
            cm$shank(tess, right - SIZE - fullSlots * STEP, y, U1, UH);   // 半鸡腿
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopAttrib();     // ★ 原样还原 GL 状态，绝不把改动留给 MITE 后面的 UI
    }
}
