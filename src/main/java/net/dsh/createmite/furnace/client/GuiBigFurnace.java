package net.dsh.createmite.furnace.client;

import net.dsh.createmite.furnace.BigFurnaceContainer;
import net.dsh.createmite.furnace.FurnaceCoreTileEntity;
import net.minecraft.GuiContainer;
import net.minecraft.Slot;

/**
 * 大熔炉的界面（第三版，2026-09-30 用户第三轮意见）
 *
 * 本轮改动：
 *   ① 材料 / 熔炼 / 产物 整体**上移** ✓
 *   ② 燃料槽下面那两个方块（火焰 + 燃烧条）**删掉** ✓（用户：不需要）
 *   ③ 槽位改成**原版那种浅灰凹槽**（外深灰 + 内浅灰 + 右下高光），不再是黑不溜秋 ✗
 *   ④ 熔炼进度改成原版那个 **→ 箭头** ✓
 *
 * 版式：左侧材料槽+燃料槽／中间「→」箭头／右侧产物槽＋火焰／底部玩家背包 ✓
 *   （★ 2026-10-01 用户「这两我不要了」⇒ 原来的「热值」「转速」两根表盘已删除 ✗）
 */
public class GuiBigFurnace extends GuiContainer {

    // 原版 GUI 的浅灰配色
    private static final int VANILLA_BG = 0xFFC6C6C6;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_HILITE = 0xFFFFFFFF;

    private final BigFurnaceContainer container;

    public GuiBigFurnace(BigFurnaceContainer container) {
        super(container);
        this.container = container;
        this.xSize = BigFurnaceContainer.GUI_W;
        this.ySize = BigFurnaceContainer.GUI_H;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = this.guiLeft;
        int y = this.guiTop;
        boolean brass = this.container.isBrass();

        drawRect(x, y, x + this.xSize, y + this.ySize, VANILLA_BG);

        // 机壳风格边框：安山=深灰、黄铜=金 ✓
        int edge = brass ? 0xFFC9A227 : 0xFF7A7A7A;
        int edgeDark = brass ? 0xFF8A6F1A : 0xFF565656;
        drawRect(x, y, x + this.xSize, y + 2, edge);
        drawRect(x, y + this.ySize - 2, x + this.xSize, y + this.ySize, edgeDark);
        drawRect(x, y, x + 2, y + this.ySize, edge);
        drawRect(x + this.xSize - 2, y, x + this.xSize, y + this.ySize, edgeDark);

        // ★ 槽位：原版那种浅灰凹槽（外 1px 深灰 + 内 16px 浅灰 + 右下 1px 高光）
        for (int i = 0; i < this.container.inventorySlots.size(); i++) {
            Slot slot = (Slot) this.container.inventorySlots.get(i);
            drawSlot(x + slot.xDisplayPosition, y + slot.yDisplayPosition);
        }

        // ★ 2026-10-01 用户：「这两我不要了」⇒ 左边那两根表盘（热值/转速）**整个删掉** ✗
        //   （热值池的玩法照旧在跑 ✓，只是不再往界面上画表盘 ✓）

        // ---- 火焰图标：燃烧时亮橙 + 内焰黄，不燃烧时整体暗灰 ✓ ----
        // ★ 2026-10-01 修用户报的 bug（关掉界面再打开、火焰是灭的 ✗）：
        //   改成读**客户端世界里核心方块的燃烧位** ✓ —— 随时读随时对，且与整机 3D 模型同源 ✓
        drawFlame(x + BigFurnaceContainer.FLAME[0], y + BigFurnaceContainer.FLAME[1],
                this.container.isBurningNow());

        // ---- 熔炼进度：原版那个「→」✓ ----
        int arrowW = this.container.cookArrowW();
        int arrowCx = this.container.cookArrowCenterX();
        drawCookArrow(x + arrowCx - arrowW / 2, y + this.container.cookArrowY(),
                arrowW, this.container.clientCook);
    }

    /** 原版风格的一格：外深灰描边 + 浅灰内底 + 右下白高光 ✓ */
    private void drawSlot(int sx, int sy) {
        drawRect(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_SHADOW);
        drawRect(sx, sy, sx + 16, sy + 16, SLOT_INNER);
        drawRect(sx + 16, sy, sx + 17, sy + 17, SLOT_HILITE);
        drawRect(sx, sy + 16, sx + 17, sy + 17, SLOT_HILITE);
    }

    /**
     * 火焰图标：用几个矩形拼出火苗形状（上窄下宽 + 内焰）✓
     * 燃烧时 = 橙底 + 黄内焰；不燃烧时 = 整体暗灰轮廓 ✓（用户要的"亮/暗"就是这条 ✓）
     */
    private void drawFlame(int fx, int fy, boolean burning) {
        int outer = burning ? 0xFFFF8C1A : 0xFF6E6E6E;
        int inner = burning ? 0xFFFFE066 : 0xFF8A8A8A;
        // 底座
        drawRect(fx, fy + 10, fx + 14, fy + 16, outer);
        // 中段（稍窄）
        drawRect(fx + 2, fy + 5, fx + 12, fy + 11, outer);
        // 上段（尖）
        drawRect(fx + 5, fy + 1, fx + 9, fy + 6, outer);
        // 内焰
        drawRect(fx + 5, fy + 8, fx + 9, fy + 14, inner);
        drawRect(fx + 6, fy + 4, fx + 8, fy + 9, inner);
    }

    /** 画原版熔炼那个「→」：一条横杠 + 三角箭头，按进度从左往右点亮 ✓ */
    private void drawCookArrow(int ax, int ay, int w, int percent) {
        int h = BigFurnaceContainer.COOK_ARROW_H;
        int barH = 6;
        int barY = ay + (h - barH) / 2;
        int headW = 8;
        int barW = w - headW;

        // 底：深灰箭头轮廓
        drawRect(ax, barY, ax + barW, barY + barH, 0xFF5A5A5A);
        for (int i = 0; i < headW; i++) {
            int half = (headW - i) * 2;              // 三角：越靠右越窄
            drawRect(ax + barW + i, ay + (h - half) / 2, ax + barW + i + 1, ay + (h + half) / 2, 0xFF5A5A5A);
        }

        // 进度：白色（原版就是白箭头 ✓）
        int p = Math.max(0, Math.min(100, percent));
        int litBar = barW * Math.min(p, 70) / 70;
        if (litBar > 0) drawRect(ax, barY, ax + litBar, barY + barH, 0xFFE8E8E8);
        if (p > 70) {
            int litHead = headW * (p - 70) / 30;
            for (int i = 0; i < litHead; i++) {
                int half = (headW - i) * 2;
                drawRect(ax + barW + i, ay + (h - half) / 2, ax + barW + i + 1, ay + (h + half) / 2, 0xFFE8E8E8);
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // ★ 标题不画 ✓（用户：左上角那行字不需要）
        // ★ 2026-10-01 用户：「这两我不要了」⇒ 「热值」「转速」两行字跟着表盘一起删掉 ✗
        // ★ 燃料槽旁边：热力值（0~10000 ✓ 用户指定）
        int qx = BigFurnaceContainer.BRASS_FUEL[0] + 20;
        int qy = BigFurnaceContainer.BRASS_FUEL[1] + 5;
        this.fontRenderer.drawString(this.container.clientHeat + "/"
                + FurnaceCoreTileEntity.HEAT_SCALE_MAX, qx, qy, 0x404040);
        // ★ 新增：**燃烧时长**（每 200 点热力值 = 1 分钟 ✓ 显示成 分:秒 ✓ 用户本轮要求）
        int secLeft = FurnaceCoreTileEntity.burnSecondsFor(this.container.clientHeat);
        String burnText = (secLeft / 60) + ":" + (secLeft % 60 < 10 ? "0" : "") + (secLeft % 60);
        this.fontRenderer.drawString("燃烧 " + burnText, qx, qy + 9, 0x606060);

        int labelY = BigFurnaceContainer.MAT_Y[0] - 10;
        this.fontRenderer.drawString("材料", BigFurnaceContainer.BRASS_MAT_X[0], labelY, 0x505050);
        this.fontRenderer.drawString("燃料", BigFurnaceContainer.BRASS_FUEL[0], BigFurnaceContainer.BRASS_FUEL[1] - 10, 0x505050);
        this.fontRenderer.drawString("产物", BigFurnaceContainer.BRASS_PROD_X[0], labelY, 0x505050);
        // ★ "熔炼"字样不画 ✓（用户不需要）
    }
}
