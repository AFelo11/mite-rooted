package net.dsh.createmite.client.gui;

import net.dsh.createmite.CMTemperature;
import net.minecraft.EntityPlayer;
import net.minecraft.FoodStats;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.IntegratedServer;
import net.minecraft.MITEConstant;
import net.minecraft.Minecraft;
import net.minecraft.ResourceLocation;
import net.minecraft.ServerConfigurationManager;
import net.minecraft.ServerPlayer;
import net.minecraft.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * 「玩家面板」界面 —— **按 I 打开**（2026-09-30 新增，用户逐条加的内容）。
 *
 * 【用户要求（按时间顺序）】
 *  1. 「先制作 I 键可以打开一个 UI 界面吧，UI 与原版一致即可……暂时先放一个血量和一个饱食度吧」
 *  2. 「这个键位不叫机械动力状态面板，它叫：**玩家面板**」
 *  3. 「血量不会随营养变动，增加**植物营养条**和**肉类营养条**显示」
 *  4. 「MITE 其实还有第三个营养值 essential_fats 必需脂肪加进去吧，在额外加一个温度条为后续做四季做准备」
 *  5. ★ 「**环境温度不对［我要的是体温！**环境温度比较特殊（后续会添加饰品系统，有饰品检测环境温度）
 *     检测到才会在 UI 内显示，**未佩戴饰品下显示环境温度为：未知**］。
 *     体温和环境温度的显示 UI 换成**体温计的形态**显示（在**左侧立起体温计**，实时变化温度）。
 *     特殊注意：**体温显示需要等待四季注入才正常**，环境温度检测也需要等四季与饰品系统打造完成」
 *   → 所以本版：左边两根**立式温度计**（体温 / 环境温度），数值来源全部收口到
 *     {@link CMTemperature} ✓ —— 四季 / 饰品没做好之前，两根都显示「未知」✓（这是**预期状态** ✓）
 *
 * 【纯客户端、不开容器】血量/饱食度/饱和度在客户端本来就有 ✓，不需要服务端同步 ✗，
 *   所以**不占窗口号、不发 Packet100OpenWindow** ✗ —— 和大熔炉 UI（GuiBigFurnace）
 *   那条"客户端→服务端转发开窗"的路子完全不同，别搞混 ✓
 *
 * 【★ 三个营养值为什么特殊 ✗】
 *   javap 实证：`protein / essential_fats / phytonutrients` **只存在服务端** `ServerPlayer` 上
 *   （各自 [0, 160000]，每 tick 各 -1、创造模式不衰减 ✓），服务端只把"**是否营养不良**"的
 *   3 个 bit 同步给客户端 ✗ —— **数值本身客户端拿不到** ✗。
 *   → 走**同一个 JVM 直读**（和「结构选择器」一模一样的招 ✓），**零发包** ✓；
 *     多人（专用服务端）拿不到 → 那三行显示 "—" ✓（以后要做联机再补自己的包 ✓）。
 *
 * 【血量上限】`getHealthLimit()` = 按**经验等级**算 ✗不是营养：`clamp(6 + (level/5)*2, 6, 20)` ✓（用户纠正过 ✓）
 */
public class GuiPlayerStatus extends GuiScreen {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 246;

    /** 一排 10 格、每格 8 像素（原版 HUD 的排法 ✓） */
    private static final int SLOTS = 10;
    private static final int STEP = 8;
    private static final int ROW_W = SLOTS * STEP;

    /** 右侧数据区：每一块「标签 + 条」的行距 ✓ */
    private static final int ROW_PITCH = 28;
    private static final int FIRST_ROW_Y = 26;
    /** 右侧数据区的左边界（左边那条留给温度计 ✓） */
    private static final int RIGHT_X = 150;

    // 温度计几何（左侧两根 ✓）
    // 细了之后两根挨近一点，不然中间空得太开 ✓（左右位置不算"长度"，可以调 ✓）
    private static final int THERMO1_CX = 40;
    private static final int THERMO2_CX = 96;
    private static final int THERMO_TOP = 40;
    private static final int THERMO_BULB = 180;

    // icons.png 里的 u/v（像素，256x256）
    private static final int U_EMPTY = 16;
    private static final int U_FULL = 52;
    private static final int U_HALF = 61;
    private static final int V_HEART = 0;
    private static final int V_SHANK = 27;

    // 浅灰底(#C6C6C6)上的深色字
    private static final int C_TITLE = 0x404040;
    private static final int C_LABEL = 0x404040;
    private static final int C_HEALTH = 0x8B1A1A;
    private static final int C_GOLD = 0x8A6D00;
    private static final int C_MEAT = 0x8B3A1A;
    private static final int C_FATS = 0x8A6A2A;
    private static final int C_PLANT = 0x2E6B2E;
    private static final int C_WARN = 0xB02000;
    private static final int C_HINT = 0x707070;
    private static final int C_TEMP_TEXT = 0x1A4A6B;

    // 温度计配色
    private static final int GLASS_OUT = 0xFF555555;
    private static final int GLASS_IN = 0xFFE8E8E8;

    /** 金色的饱和度覆盖色（AppleSkin 那套 ✓） */
    private static final float[] TINT_GOLD = {1.00F, 0.85F, 0.25F, 0.78F};
    /** 肉类营养（蛋白质）：棕红 */
    private static final float[] TINT_MEAT = {0.95F, 0.45F, 0.30F, 0.80F};
    /** 必需脂肪：暗黄 */
    private static final float[] TINT_FATS = {0.95F, 0.80F, 0.35F, 0.80F};
    /** 植物营养（植物营养素）：绿 */
    private static final float[] TINT_PLANT = {0.35F, 0.90F, 0.40F, 0.80F};

    /** 温度计水银柱的**平滑跟随**（每帧一次 ✓ → "实时变化"看起来是流动的 ✓） */
    private float dispBody = Float.NaN;
    private float dispAmbient = Float.NaN;

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void initGui() {
        this.buttonList.add(new GuiButton(0, panelX() + (PANEL_W - 200) / 2, panelY() + PANEL_H - 26,
                StatCollector.translateToLocal("gui.done")));
    }

    /** 像原版背包那样**不暂停游戏** ✓ */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** I 再按一次也能关（原版背包用 E 关，是同一个做法 ✓） */
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
        if (button.id == 0) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int x = panelX();
        int y = panelY();

        // ---- 面板：原版容器那种「浅灰底 + 亮上左 / 暗下右」的立体描边 ✓ ----
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        drawRect(x, y, x + PANEL_W, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + PANEL_H, 0xFFFFFFFF);
        drawRect(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, 0xFF555555);
        drawRect(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, 0xFF555555);

        drawCenteredString(this.fontRenderer,
                StatCollector.translateToLocal("gui.createmite.status.title"),
                x + PANEL_W / 2, y + 8, C_TITLE);

        EntityPlayer player = this.mc.thePlayer;
        if (player == null) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        // ================= 左侧：两根立式温度计 =================
        // ★ 2026-10-01：两根**都已经是真值**了 ✓（用户：「现在就正常在 I 键 UI 正常显示就行了」✓）
        //   ① 体温   ← CMBodyTemp（36.4 基准 + 季节环境温度 + 昼夜温差 + 水/岩浆/雨雪 ✓）
        //   ② 环境温度 ← CMAmbient（季节温度表 + 昼夜温差，℃ ✓）
        //   （等饰品系统做好，把 CMTemperature 里两个 panel.*_needs_accessory 配置改成 1 即可回到"没戴 = 未知" ✓）
        dispBody = cm$approach(dispBody, CMTemperature.bodyTemperature());
        dispAmbient = cm$approach(dispAmbient, CMTemperature.ambientTemperature(this.mc));

        // 体温左边两条参考线 = **危险线**（低于 34.5 = 过冷档、高于 38 = 高烧档 ✓）
        cm$thermometer(x + THERMO1_CX, y + THERMO_TOP, y + THERMO_BULB, dispBody,
                CMTemperature.BODY_MIN, CMTemperature.BODY_MAX,
                new float[]{net.dsh.createmite.CMBodyTemp.T1, net.dsh.createmite.CMBodyTemp.T5},
                "gui.createmite.status.body_temperature", 1);

        // 环境温度左边一条参考线 = **0 ℃（结冰线）** ✓ 现在是摄氏度刻度 ✓，1 位小数 ✓
        cm$thermometer(x + THERMO2_CX, y + THERMO_TOP, y + THERMO_BULB, dispAmbient,
                CMTemperature.AMBIENT_MIN, CMTemperature.AMBIENT_MAX,
                new float[]{0.0F},
                "gui.createmite.status.ambient_temperature", 1);

        // ================= 右侧：六行数据 =================
        int rowY = y + FIRST_ROW_Y;

        // 1) 血量（上限按经验等级 ✓）
        float hpMax = player.getHealthLimit();
        if (hpMax <= 0.0F) hpMax = player.getMaxHealth();
        float hp = player.getHealth();
        rowY = cm$iconRow(x, rowY, "gui.createmite.status.health", C_HEALTH,
                cm$fmt(hp) + " / " + cm$fmt(hpMax), cm$fraction(hp, hpMax), V_HEART, null);

        // 2) 饱食度 / 3) 饱和度
        FoodStats food = player.getFoodStats();
        if (food != null) {
            int nutrition = food.getNutrition();
            int nutritionLimit = food.getNutritionLimit();
            rowY = cm$iconRow(x, rowY, "gui.createmite.status.nutrition", C_LABEL,
                    nutrition + " / " + nutritionLimit, cm$fraction(nutrition, nutritionLimit),
                    V_SHANK, null);

            int satiation = food.getSatiation();
            int satiationLimit = food.getSatiationLimit();
            rowY = cm$iconRow(x, rowY, "gui.createmite.status.satiation", C_GOLD,
                    satiation + " / " + satiationLimit, cm$fraction(satiation, satiationLimit),
                    V_SHANK, TINT_GOLD);
        }

        // 4~6) 三个营养值（服务端 ServerPlayer → 单机同 JVM 直读 ✓）
        int[] nuts = cm$serverNutrients();
        int limit = MITEConstant.nutrient_limit;

        rowY = cm$iconRow(x, rowY, "gui.createmite.status.protein",
                cm$malnourished(player, 0) ? C_WARN : C_MEAT,
                cm$nutrientText(nuts, 0, limit), cm$nutrientFraction(nuts, 0, limit),
                V_SHANK, TINT_MEAT);

        rowY = cm$iconRow(x, rowY, "gui.createmite.status.fats",
                cm$malnourished(player, 1) ? C_WARN : C_FATS,
                cm$nutrientText(nuts, 1, limit), cm$nutrientFraction(nuts, 1, limit),
                V_SHANK, TINT_FATS);

        rowY = cm$iconRow(x, rowY, "gui.createmite.status.phytonutrients",
                cm$malnourished(player, 2) ? C_WARN : C_PLANT,
                cm$nutrientText(nuts, 2, limit), cm$nutrientFraction(nuts, 2, limit),
                V_SHANK, TINT_PLANT);

        if (nuts == null) {
            // 多人（专用服务端）时营养数值拿不到 —— 说清楚，免得以为坏了 ✓
            drawCenteredString(this.fontRenderer,
                    StatCollector.translateToLocal("gui.createmite.status.singleplayer_only"),
                    x + PANEL_W / 2, y + PANEL_H - 32, C_HINT);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ------------------------------------------------------------------
    // 温度计
    // ------------------------------------------------------------------

    /**
     * 立式温度计：底部圆球 + 竖直玻璃管 + 水银柱 ✓
     *
     * @param cx       中心 x
     * @param tubeTop  玻璃管顶
     * @param bulbCy   底部圆球中心
     * @param value    当前温度；**NaN = 未知**（管子留空 + 文字写"未知" ✓）
     * @param min/max  量程（体温 30~42 ℃ ✓；环境 -20~45 ℃ ✓，都在 CMTemperature 里 ✓）
     * @param marks    左侧要不要画参考刻度线（体温画 34.5/38 两条危险线 ✓；环境画 0 ℃ ✓）；null = 不画
     * @param digits   数值保留几位小数（两根都是 1 位 ✓）
     */
    private void cm$thermometer(int cx, int tubeTop, int bulbCy, float value,
                                float min, float max, float[] marks,
                                String labelKey, int digits) {
        boolean unknown = Float.isNaN(value);
        // ★ 2026-09-30 用户："两个温度显示有点太大了，可以缩小一些**但是长度不变**"
        //   → 只把"粗细"缩小（管径 6→4、圆球半径 7→5、刻度 3px→2px ✓），
        //     管子**长度一个像素都没动**（THERMO_TOP / THERMO_BULB 保持原值 ✓）
        int halfTube = 2;
        int bulbR = 5;

        // --- 玻璃外壳 ---
        cm$disc(cx, bulbCy, bulbR + 1, GLASS_OUT);
        drawRect(cx - halfTube - 1, tubeTop - 1, cx + halfTube + 1, bulbCy + 1, GLASS_OUT);
        // --- 管内底色 ---
        cm$disc(cx, bulbCy, bulbR - 1, GLASS_IN);
        drawRect(cx - halfTube, tubeTop, cx + halfTube, bulbCy, GLASS_IN);

        int innerTop = tubeTop + 2;

        if (!unknown) {
            float f = cm$fraction(value - min, max - min);
            int color = 0xFF000000 | cm$tempColor(value, min, max);
            int innerBottom = bulbCy - 1;
            int len = Math.round((innerBottom - innerTop) * f);
            cm$disc(cx, bulbCy, bulbR - 2, color);
            if (len > 0) {
                drawRect(cx - halfTube + 1, innerBottom - len, cx + halfTube - 1, innerBottom, color);
            }
        }

        // --- 右侧刻度（5 格）---
        for (int i = 0; i <= 4; i++) {
            int ty = bulbCy - Math.round((bulbCy - tubeTop) * (i / 4.0F));
            drawRect(cx + halfTube + 2, ty, cx + halfTube + 4, ty + 1, GLASS_OUT);
        }
        // --- 左侧参考线（体温 = 正常带；环境 = 结冰线 ✓）---
        if (marks != null) {
            for (int i = 0; i < marks.length; i++) {
                int my = bulbCy - Math.round((bulbCy - tubeTop)
                        * cm$fraction(marks[i] - min, max - min));
                drawRect(cx - halfTube - 4, my, cx - halfTube - 2, my + 1, 0xFF2E6B2E);
            }
        }

        // --- 标签（管子上面）+ 数值（球下面）---
        drawCenteredString(this.fontRenderer, StatCollector.translateToLocal(labelKey),
                cx, tubeTop - 14, C_LABEL);
        String text;
        if (unknown) {
            text = StatCollector.translateToLocal("gui.createmite.status.unknown");
        } else if (digits == 1) {
            text = String.format("%.1f", value);
        } else {
            text = String.format("%.2f", value);
        }
        // 球半径从 7 缩到 5 → 数值往上提 2 像素，免得离球太远 ✓
        drawCenteredString(this.fontRenderer, text, cx, bulbCy + 10,
                unknown ? C_HINT : C_TEMP_TEXT);
    }

    /** 画一个实心圆（没有画圆的 API，就用一行行 drawRect 拼 ✓） */
    private void cm$disc(int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) (r * r - dy * dy));
            drawRect(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 温度 → 颜色（冷蓝 → 青 → 绿 → 橙 → 红 ✓） */
    private static int cm$tempColor(float value, float min, float max) {
        float f = cm$fraction(value - min, max - min);
        if (f < 0.22F) return 0x3D7DD8;      // 冷
        if (f < 0.45F) return 0x49B6C8;
        if (f < 0.68F) return 0x54B85A;      // 适中
        if (f < 0.86F) return 0xD9A03A;      // 热
        return 0xD0503C;                     // 很热
    }

    /** 平滑跟随（每帧一次）—— NaN 表示"未知"，直接留空 ✓ */
    private static float cm$approach(float current, float target) {
        if (Float.isNaN(target)) return Float.NaN;
        if (Float.isNaN(current)) return target;         // 第一次：直接到位 ✓
        return current + (target - current) * 0.12F;     // 之后慢慢跟上 → 肉眼看是流动的 ✓
    }

    // ------------------------------------------------------------------
    // 数据
    // ------------------------------------------------------------------

    /**
     * 从**同 JVM 的集成服务端**读三个营养值（单机 ✓）。
     *
     * @return {protein, essential_fats, phytonutrients}；拿不到（多人 / 没进世界）返回 **null** ✓
     */
    private int[] cm$serverNutrients() {
        IntegratedServer server = this.mc.getIntegratedServer();
        if (server == null) return null;                      // 专用服务端：客户端没有这三个数值 ✗
        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) return null;
        java.util.List list = scm.playerEntityList;
        if (list == null) return null;
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer) o;
                return new int[]{sp.getProtein(), sp.getEssentialFats(), sp.getPhytonutrients()};
            }
        }
        return null;
    }

    /** 客户端也有"是否营养不良"那三个 boolean（服务端同步过来的 ✓，多人一样有效 ✓） */
    private static boolean cm$malnourished(EntityPlayer player, int which) {
        try {
            net.minecraft.EntityClientPlayerMP cp = player.getAsEntityClientPlayerMP();
            if (cp == null) return false;
            if (which == 0) return cp.is_malnourished_in_protein;
            if (which == 1) return cp.is_malnourished_in_essential_fats;
            return cp.is_malnourished_in_phytonutrients;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String cm$nutrientText(int[] nuts, int idx, int limit) {
        if (nuts == null) return "—";
        return nuts[idx] + " / " + limit;
    }

    private static float cm$nutrientFraction(int[] nuts, int idx, int limit) {
        if (nuts == null) return 0.0F;
        return cm$fraction(nuts[idx], limit);
    }

    // ------------------------------------------------------------------
    // 通用绘制
    // ------------------------------------------------------------------

    /** 一行「标签 + 数值」+ 一排图标，返回下一行的 y ✓ */
    private int cm$iconRow(int panelLeft, int rowY, String langKey, int valueColor,
                           String value, float fraction, int v, float[] tint) {
        int leftX = panelLeft + RIGHT_X;
        drawString(this.fontRenderer, StatCollector.translateToLocal(langKey), leftX, rowY, C_LABEL);
        int vx = panelLeft + PANEL_W - 12 - this.fontRenderer.getStringWidth(value);
        drawString(this.fontRenderer, value, vx, rowY, valueColor);
        int barX = leftX + ((PANEL_W - RIGHT_X - 12) - ROW_W) / 2;
        cm$bar(barX, rowY + 12, fraction, v, tint);
        return rowY + ROW_PITCH;
    }

    /**
     * 一排 10 格图标。
     * @param fraction 0..1 的填充比例（调用方先除好 ✓）
     * @param tint     null = 原色；否则 RGBA 染色 ✓
     */
    private void cm$bar(int x, int y, float fraction, int v, float[] tint) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICONS);
        if (tint != null) {
            GL11.glColor4f(tint[0], tint[1], tint[2], tint[3]);
        }
        float per = 1.0F / SLOTS;
        for (int i = 0; i < SLOTS; i++) {
            int ix = x + i * STEP;
            cm$icon(ix, y, U_EMPTY, v);
            float filled = fraction - i * per;
            if (filled >= per) {
                cm$icon(ix, y, U_FULL, v);
            } else if (filled > 0.0F) {
                cm$icon(ix, y, U_HALF, v);
            }
        }
        if (tint != null) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);   // ★ 一定要还原，否则后面全被染色 ✗
        }
    }

    /** 画一颗 9x9 图标（调用前必须已经 bindTexture ✓） */
    private void cm$icon(int x, int y, int u, int v) {
        drawTexturedModalRect(x, y, u, v, 9, 9);
    }

    /** 比例（0..1，超界夹住 ✓） */
    private static float cm$fraction(float current, float max) {
        if (max <= 0.0F) return 0.0F;
        float f = current / max;
        if (f < 0.0F) return 0.0F;
        if (f > 1.0F) return 1.0F;
        return f;
    }

    /** 整数就不带小数点，否则留一位（MITE 的血量可能是小数 ✓） */
    private static String cm$fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 0.05F) {
            return String.valueOf(Math.round(v));
        }
        return String.format("%.1f", v);
    }
}
