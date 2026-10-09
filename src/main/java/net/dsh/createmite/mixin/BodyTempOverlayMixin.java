package net.dsh.createmite.mixin;

import net.dsh.createmite.CMAmbientFeel;
import net.minecraft.GuiIngame;
import net.minecraft.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 体温的屏幕边框（2026-10-01 用户定稿 ✓）
 *
 * 冷 = 浅蓝 → 深蓝；热 = 浅红 → 深红 ✓
 *   · **只描边、不覆盖画面** ✓（不然看不清东西 ✗）
 *   · 两级信息叠一个通道：基础浓度（过冷/过热 vs 极端档 ✓）
 *     + **同一档待满 5 分钟越深** ✓（用户的想法 ✓ 比进度环直观 ✓）
 *   · 透明度上限 **0.35** ✓（MITE 本来就暗 ✗ 太抢眼会烦 ✓）
 *
 * 画法：由外向内叠 N 条带子，每条 alpha 递减 ⇒ 一条"渐变"边框 ✓
 *   （不用 Tessellator 顶点色 ✗ —— 那种在 MITE 上踩过坑 ✓ 叠矩形最稳 ✓）
 */
@Mixin(GuiIngame.class)
public abstract class BodyTempOverlayMixin {

    /** 每条边的带子数（越多越平滑 ✓ 12 条足够 ✓）*/
    private static final int BANDS = 12;
    /** 带子总厚度（GUI 像素 ✓）*/
    private static final int THICK = 16;      // ★ 用户反馈"圈还能小一点" ⇒ 26 → 16 ✓
    /** 透明度上限（用户建议 <= 0.35 ✓）*/
    private static final float ALPHA_MAX = 0.35F;

    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void cm$bodyTempBorder(float partialTicks, boolean hasScreen, int mouseX, int mouseY, CallbackInfo ci) {
        float[] info = CMAmbientFeel.borderInfo();   // ★ 改读体感温度 ✓（旧体温系统已停用 ✗）
        if (info == null) return;
        float strength = info[0];
        if (strength <= 0.01F) return;
        boolean cold = info[1] > 0.0F;

        net.minecraft.Minecraft mc = net.minecraft.Minecraft.getMinecraft();
        if (mc == null) return;
        ScaledResolution sr = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        // 冷：蓝（RGB 90,150,255）；热：红（RGB 255,90,70）✓
        int r = cold ? 90 : 255;
        int g = cold ? 150 : 90;
        int b = cold ? 255 : 70;

        int bandH = Math.max(1, THICK / BANDS);
        for (int i = 0; i < BANDS; i++) {
            float f = (BANDS - i) / (float) BANDS;          // 外圈最浓 ✓
            int a = (int) (255.0F * ALPHA_MAX * strength * f);
            if (a <= 3) continue;
            if (a > 255) a = 255;
            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int d = i * bandH;                              // 离屏幕边缘的距离 ✓
            net.minecraft.Gui.drawRect(0, d, w, d + bandH, col);                // 上
            net.minecraft.Gui.drawRect(0, h - d - bandH, w, h - d, col);        // 下
            net.minecraft.Gui.drawRect(d, 0, d + bandH, h, col);                // 左
            net.minecraft.Gui.drawRect(w - d - bandH, 0, w - d, h, col);        // 右
        }
    }
}
