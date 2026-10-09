package net.dsh.createmite.client.gui;

import net.minecraft.EntityPlayer;
import net.minecraft.FoodStats;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.IntegratedServer;
import net.minecraft.MITEConstant;
import net.minecraft.ResourceLocation;
import net.minecraft.ServerConfigurationManager;
import net.minecraft.ServerPlayer;
import net.minecraft.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * 「玩家面板」界面 —— **按 I 打开**。
 *
 * 【2026-10-09 第二轮（用户逐条要求）】
 *  1. 图标：**用原版图标**（把 jar 里的 icons.png 抠出来逐格核对过 ✓）
 *     心：空容器 (16,0) / 满 (52,0) / 半 (61,0)   —— 和原版 HUD 一致 ✓
 *     鸡腿：空 (16,27) / 满 (52,27) / 半 (61,27) —— 同一套 u 布局 ✓
 *  2. 各行的区分**不再给图标染色** ✗（染色会糊成一团 ✗）：
 *     饱食度 = 原版鸡腿（不描边）✓
 *     饱和度 = 原版鸡腿 ＋ **金色**描边 ✓
 *     肉类营养 = ＋ **棕红**描边 ✓ ／ 必需脂肪 = ＋ **暗黄**描边 ✓ ／ 植物营养 = ＋ **绿**描边 ✓
 *     描边做法：把同一张图标在 8 个方向各偏 1 像素铺一遍（剪影 ✓），再把图标本体盖上去 ⇒ 一圈 1 像素彩边 ✓
 *  3. 字体：**不要阴影** ✗（Gui.drawString / drawCenteredString 走的是 drawStringWithShadow ✗）
 *     ⇒ 一律直呼 FontRenderer.drawString(text, x, y, color, **false**) ✓ 居中自己算 ✓
 *
 * 【数据从哪来】
 *   · 血量 / 饱食 / 饱和：客户端本来就有 ✓（每帧现读 ⇒ 实时 ✓）
 *   · 三个营养值：只存在服务端 ⇒ 单机走"同 JVM 直读" IntegratedServer ✓（多人显示 "—" ✓）
 *   · 体感温度：{@link net.dsh.createmite.CMAmbientFeel#feel} ✓ 瞬时值、每帧读就是实时 ✓
 *   （2026-10-09：体温系统整体卸载 ⇒ 面板与工程里都只剩体感温度 ✓）
 */
public class GuiPlayerStatus extends GuiScreen {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    // ---------------- 面板几何 ----------------
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 252;
    private static final int PAD = 12;

    private static final int FIRST_ROW_Y = 36;
    private static final int ROW_PITCH = 22;
    private static final int ROWS = 6;

    // ---------------- 图标（icons.png 256x256）----------------
    private static final int SLOTS = 10;
    private static final int STEP = 10;
    private static final int ICON = 9;
    /** 空容器（心 / 鸡腿共用 ✓ 原版就是同一个 u ✓）*/
    private static final int U_EMPTY = 16;
    /** 满 / 半（心与鸡腿各自一行，u 相同 ✓）*/
    private static final int U_FULL = 52;
    private static final int U_HALF = 61;
    private static final int V_HEART = 0;
    private static final int V_SHANK = 27;

    // ---------------- 配色 ----------------
    private static final int C_TITLE = 0x404040;
    private static final int C_LABEL = 0x404040;
    private static final int C_VALUE = 0x303030;
    private static final int C_HEALTH = 0x9B1B1B;
    private static final int C_GOLD = 0x8A6D00;
    private static final int C_MEAT = 0x9B3A1A;
    private static final int C_FATS = 0x8A6A2A;
    private static final int C_PLANT = 0x2E6B2E;
    private static final int C_WARN = 0xB02000;
    private static final int C_HINT = 0x707070;
    private static final int C_SEP = 0xFF909090;
    private static final int C_STRIPE = 0x14FFFFFF;

    /** 描边颜色（RGBA ✓ 1 像素剪影 ✓）*/
    private static final float[] RING_GOLD = {1.00F, 0.82F, 0.18F, 1.0F};
    private static final float[] RING_MEAT = {0.65F, 0.27F, 0.10F, 1.0F};
    private static final float[] RING_FATS = {0.74F, 0.60F, 0.16F, 1.0F};
    private static final float[] RING_PLANT = {0.20F, 0.62F, 0.24F, 1.0F};

    private int panelX() { return (this.width - PANEL_W) / 2; }
    private int panelY() { return (this.height - PANEL_H) / 2; }

    @Override
    public void initGui() {
        this.buttonList.add(new GuiButton(0, panelX() + (PANEL_W - 200) / 2, panelY() + PANEL_H - 26,
                StatCollector.translateToLocal("gui.done")));
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == net.dsh.createmite.client.CMKeybinds.STATUS_KEY_CODE || keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) this.mc.displayGuiScreen(null);
    }

    // ================================================================== 绘制

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int x = panelX();
        int y = panelY();

        // ---- 面板本体：原版容器那套「浅灰 + 亮上左 / 暗下右」立体描边 ✓ ----
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        drawRect(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        drawRect(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        drawRect(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);

        cm$textCentered(StatCollector.translateToLocal("gui.createmite.status.title"),
                x + PANEL_W / 2, y + 10, C_TITLE);
        drawRect(x + PAD, y + 24, x + PANEL_W - PAD, y + 25, C_SEP);

        EntityPlayer player = this.mc.thePlayer;
        if (player == null) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        int labelX = x + PAD + 2;
        int iconX = x + 96;
        int valueRight = x + PANEL_W - PAD;

        // ---- 1) 血量（原版：空容器打底 + 满/半心叠上去 ✓ 上限 6~20 ⇒ 只画 3~10 颗 ✓）----
        // ★ 2026-10-09 修「面板 11.6 / 原版 12 不同步」：
        //   MITE 的血是 float，但**原版血条只画整颗/半颗** ⇒ 面板也统一按「整数 HP」口径显示 ✓
        //   （11.6 四舍五入 = 12 ⇒ 画 6 颗满心，和血条一模一样 ✓ 不再出现小数点对不上的情况 ✓）
        float hpMaxF = player.getHealthLimit();
        if (hpMaxF <= 0.0F) hpMaxF = player.getMaxHealth();
        // ★★ 2026-10-09 实证：MITE 血条用的是 **ceiling_float_int(getHealth())** ✗ 不是四舍五入
        //    （javap GuiIngame 里 48: getHealth → 51: MathHelper.ceiling_float_int ✓）
        //    ⇒ 面板必须同样向上取整：5.4 → 6 ✓ 11.6 → 12 ✓ 这才是血条上读到的那两个数 ✓
        int hpMax = Math.max(1, (int) Math.ceil(hpMaxF));
        int hp = (int) Math.ceil(player.getHealth());
        if (hp > hpMax) hp = hpMax;
        if (hp < 0) hp = 0;
        int rowY = y + FIRST_ROW_Y;
        cm$heartRow(labelX, rowY, iconX, valueRight, "gui.createmite.status.health", C_HEALTH,
                hp + " / " + hpMax, hp, hpMax, true);

        // ---- 2) 3) 饱食度（原版，无描边）/ 饱和度（金色描边）----
        rowY += ROW_PITCH;
        FoodStats food = player.getFoodStats();
        if (food != null) {
            cm$statRow(labelX, rowY, iconX, valueRight, "gui.createmite.status.nutrition", C_LABEL,
                    food.getNutrition() + " / " + food.getNutritionLimit(),
                    cm$fraction(food.getNutrition(), food.getNutritionLimit()), null, false);
            rowY += ROW_PITCH;
            cm$statRow(labelX, rowY, iconX, valueRight, "gui.createmite.status.satiation", C_GOLD,
                    food.getSatiation() + " / " + food.getSatiationLimit(),
                    cm$fraction(food.getSatiation(), food.getSatiationLimit()), RING_GOLD, true);
        }

        // ---- 4~6) 三个营养值（描边区分 ✓ 单机同 JVM 直读 ✓）----
        int[] nuts = cm$serverNutrients();
        int limit = MITEConstant.nutrient_limit;
        rowY += ROW_PITCH;
        cm$statRow(labelX, rowY, iconX, valueRight, "gui.createmite.status.protein",
                cm$malnourished(player, 0) ? C_WARN : C_MEAT, cm$nutrientText(nuts, 0, limit),
                cm$nutrientFraction(nuts, 0, limit), RING_MEAT, false);
        rowY += ROW_PITCH;
        // ★ 2026-10-09：脂肪这行把"档位"拼进标签（体脂充足/正常/偏低/告急 ✓）并按档位配色 ✓
        cm$statRowTxt(labelX, rowY, iconX, valueRight,
                StatCollector.translateToLocal("gui.createmite.status.fats")
                        + "（" + net.dsh.createmite.CMFats.tierName() + "）",
                net.dsh.createmite.CMFats.tierColor(), cm$nutrientText(nuts, 1, limit),
                cm$nutrientFraction(nuts, 1, limit), RING_FATS, true);
        rowY += ROW_PITCH;
        cm$statRow(labelX, rowY, iconX, valueRight, "gui.createmite.status.phytonutrients",
                cm$malnourished(player, 2) ? C_WARN : C_PLANT, cm$nutrientText(nuts, 2, limit),
                cm$nutrientFraction(nuts, 2, limit), RING_PLANT, false);

        // ---- 温度区：只有体感温度（纯数字 ✓ 实时 ✓）----
        int tempY = y + FIRST_ROW_Y + ROWS * ROW_PITCH + 8;
        drawRect(x + PAD, tempY - 5, x + PANEL_W - PAD, tempY - 4, C_SEP);
        cm$tempRow(labelX, valueRight, tempY + 12, "gui.createmite.status.feel_temperature",
                cm$feelTemperature(player));

        if (nuts == null) {
            cm$textCentered(StatCollector.translateToLocal("gui.createmite.status.singleplayer_only"),
                    x + PANEL_W / 2, y + PANEL_H - 42, C_HINT);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ================================================================== 行

    /** 血量行：原版画法 —— 空容器打底、满/半心叠上去 ✓ */
    private void cm$heartRow(int labelX, int rowY, int iconX, int valueRight, String key, int labelColor,
                             String value, int hp, int hpMax, boolean stripe) {
        cm$stripe(labelX - 4, rowY - 3, valueRight + 2, rowY + 15, stripe);
        cm$text(StatCollector.translateToLocal(key), labelX, rowY + 2, labelColor);
        cm$textRight(value, valueRight, rowY + 2, C_VALUE);
        int slots = (hpMax + 1) / 2;                    // 20 → 10 颗 ✓ 6 → 3 颗 ✓
        if (slots > SLOTS) slots = SLOTS;
        for (int i = 0; i < SLOTS; i++) {
            int x = iconX + i * STEP, y = rowY + 1;
            cm$icon(x, y, U_EMPTY, V_HEART, null);      // 空容器打底（原版画法 ✓）
            if (i < slots) {
                int v = hp - i * 2;                     // 整数口径：≥2 满心、=1 半心 ✓（与原版血条完全一致 ✓）
                if (v >= 2) cm$icon(x, y, U_FULL, V_HEART, null);
                else if (v == 1) cm$icon(x, y, U_HALF, V_HEART, null);
            }
        }
    }

    /** 通用「标签 + 鸡腿串 + 数值」行 ✓ ring = 满/半图标的 1 像素描边色（null = 不描边 ✓）*/
    private void cm$statRow(int labelX, int rowY, int iconX, int valueRight, String key, int labelColor,
                            String value, float fraction, float[] ring, boolean stripe) {
        cm$statRowTxt(labelX, rowY, iconX, valueRight, StatCollector.translateToLocal(key), labelColor,
                value, fraction, ring, stripe);
    }

    /** 同上，但标签文字已经翻译好（脂肪那行要拼档位 ✓）*/
    private void cm$statRowTxt(int labelX, int rowY, int iconX, int valueRight, String labelText, int labelColor,
                               String value, float fraction, float[] ring, boolean stripe) {
        cm$stripe(labelX - 4, rowY - 3, valueRight + 2, rowY + 15, stripe);
        cm$text(labelText, labelX, rowY + 2, labelColor);
        cm$textRight(value, valueRight, rowY + 2, C_VALUE);
        for (int i = 0; i < SLOTS; i++) {
            int x = iconX + i * STEP, y = rowY + 1;
            float slot = fraction * SLOTS - i;
            cm$icon(x, y, U_EMPTY, V_SHANK, null);
            if (slot >= 1.0F - 1.0E-4F) cm$iconRing(x, y, U_FULL, V_SHANK, ring);
            else if (slot >= 0.5F - 1.0E-4F) cm$iconRing(x, y, U_HALF, V_SHANK, ring);
        }
    }

    private void cm$tempRow(int labelX, int valueRight, int textY, String key, float celsius) {
        cm$text(StatCollector.translateToLocal(key), labelX, textY, C_LABEL);
        String s = Float.isNaN(celsius) ? "—" : cm$fmt(celsius) + " °C";
        cm$textRight(s, valueRight, textY, cm$tempColor(celsius));
    }

    // ================================================================== 小工具

    private void cm$stripe(int x1, int y1, int x2, int y2, boolean on) {
        if (on) drawRect(x1, y1, x2, y2, C_STRIPE);
    }

    /** ★ 无阴影文字（Gui.drawString 走的是带阴影那条 ✗ 用户要求干净不加阴影 ✓）*/
    private void cm$text(String text, int x, int y, int color) {
        this.fontRenderer.drawString(text, x, y, color, false);
    }

    private void cm$textRight(String text, int right, int y, int color) {
        this.fontRenderer.drawString(text, right - this.fontRenderer.getStringWidth(text), y, color, false);
    }

    private void cm$textCentered(String text, int centerX, int y, int color) {
        this.fontRenderer.drawString(text, centerX - this.fontRenderer.getStringWidth(text) / 2, y, color, false);
    }

    /** 画一个 9x9 图标（★ 必须显式设色 ✗ 否则会继承上一笔文字的深灰 ⇒ 图标发黑 ✗）*/
    private void cm$icon(int x, int y, int u, int v, float[] color) {
        this.mc.getTextureManager().bindTexture(ICONS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (color != null) GL11.glColor4f(color[0], color[1], color[2], color[3]);
        else GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(x, y, u, v, ICON, ICON);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 图标 + 一圈 1 像素描边（8 方向剪影 ✓ 原色图标盖在最上面 ✓）*/
    private void cm$iconRing(int x, int y, int u, int v, float[] ring) {
        if (ring == null) { cm$icon(x, y, u, v, null); return; }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                cm$icon(x + dx, y + dy, u, v, ring);
            }
        }
        cm$icon(x, y, u, v, null);
    }

    /** 体感温度：单机读同 JVM 的服务端玩家（数据最全 ✓）；拿不到退回本地玩家 ✓ */
    private float cm$feelTemperature(EntityPlayer fallback) {
        try {
            ServerPlayer sp = cm$serverPlayer();
            if (sp != null) return net.dsh.createmite.CMAmbientFeel.feel(sp);
        } catch (Throwable ignored) { }
        try { return net.dsh.createmite.CMAmbientFeel.feel(fallback); } catch (Throwable t) { return Float.NaN; }
    }

    private ServerPlayer cm$serverPlayer() {
        try {
            IntegratedServer server = this.mc.getIntegratedServer();
            if (server == null) return null;
            ServerConfigurationManager scm = server.getConfigurationManager();
            if (scm == null) return null;
            java.util.List list = scm.playerEntityList;
            if (list == null) return null;
            for (int i = 0; i < list.size(); i++) {
                Object o = list.get(i);
                if (o instanceof ServerPlayer) return (ServerPlayer) o;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** {protein, essential_fats, phytonutrients}；多人拿不到 → null ✓ */
    private int[] cm$serverNutrients() {
        ServerPlayer sp = cm$serverPlayer();
        if (sp == null) return null;
        try {
            return new int[]{sp.getProtein(), sp.getEssentialFats(), sp.getPhytonutrients()};
        } catch (Throwable t) { return null; }
    }

    private static boolean cm$malnourished(EntityPlayer player, int which) {
        try {
            net.minecraft.EntityClientPlayerMP cp = player.getAsEntityClientPlayerMP();
            if (cp == null) return false;
            if (which == 0) return cp.is_malnourished_in_protein;
            if (which == 1) return cp.is_malnourished_in_essential_fats;
            return cp.is_malnourished_in_phytonutrients;
        } catch (Throwable t) { return false; }
    }

    private static String cm$nutrientText(int[] nuts, int idx, int limit) {
        return nuts == null ? "—" : nuts[idx] + " / " + limit;
    }

    private static float cm$nutrientFraction(int[] nuts, int idx, int limit) {
        return nuts == null ? 0.0F : cm$fraction(nuts[idx], limit);
    }

    private static float cm$fraction(float current, float max) {
        if (max <= 0.0F) return 0.0F;
        float f = current / max;
        if (f < 0.0F) f = 0.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }

    private static String cm$fmt(float v) {
        if (Float.isNaN(v)) return "—";
        int scaled = Math.round(v * 10.0F);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    /** 体感温度的冷热配色（℃ ✓）*/
    private static int cm$tempColor(float c) {
        if (Float.isNaN(c)) return C_HINT;
        if (c <= 0.0F) return 0x2A5FA8;
        if (c < 10.0F) return 0x2E7BA6;
        if (c < 22.0F) return 0x2E6B2E;
        if (c < 32.0F) return 0xA87400;
        return 0xB02000;
    }
}
