package net.dsh.createmite.furnace;

import net.minecraft.Container;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.Slot;

/**
 * 大熔炉的界面容器（材料槽 / 燃料槽 / 产物槽 + 玩家背包）。
 *
 * == 版式（2026-09-30 用户第二轮意见后重排）==
 * <pre>
 *   ★ 2026-10-01 用户「这两我不要了」⇒ **左上的「热值」「转速」两根表盘已整块删除** ✗
 *     （机器区因此整体左移 30px ✓；热值池的玩法本身不受影响 ✓）
 *   左：材料槽（黄铜 4x2 = 8 / 安山 2x2 = 4）+ 单独一个燃料槽在下方
 *   中：熔炼进度「→」箭头（燃烧中亮 / 停下来变暗）
 *   右：产物槽（黄铜 2x4 = 8 / 安山 2x2 = 4）+ 右下火焰图标
 *   底部：玩家背包
 * </pre>
 * 【两种机壳同一块画布】200 x 222 ✓（用户："两种一样大，槽位少的摆宽松"）
 * 【数值同步】热值 / 转速 / 燃烧状态 / 熔炼进度走原版进度条通道（Container.updateProgressBar ✓ → Packet105 ✓）
 */
public class BigFurnaceContainer extends Container {

    public static final int GUI_W = 200;
    public static final int GUI_H = 222;
    /** 玩家背包第一行（140 / 158 / 176 / 198）*/
    public static final int PLAYER_INV_Y = 140;

    // ★ 2026-10-01 用户：「这两我不要了」⇒ **「热值」「转速」两根表盘整个删掉** ✗
    //   （heatCurrent/heatMax/speedRpm 这几个值本身**留着** ✓ —— 热值池的玩法还在用 ✓，只是不再画出来 ✓）

    // ---- 熔炼进度：与原版一样用「→」箭头（用户 2026-09-30 指定 ✓）----
    //   位置不再写死：按"材料槽右边缘 ~ 产物槽左边缘"这段空隙**撑满并居中** ✓
    //   （用户：箭头拉长、居中于材料槽与产物槽之间，两种机壳都要 ✓）
    public static final int COOK_ARROW_H = 16;

    /** 箭头中心 X（空隙正中）*/
    public int cookArrowCenterX() {
        int matRight = (this.brass ? BRASS_MAT_X[3] : AND_MAT_X[1]) + 18;
        int prodLeft = BRASS_PROD_X[0];
        return (matRight + prodLeft) / 2;
    }

    /** 箭头总宽（撑满空隙，至少 20）*/
    public int cookArrowW() {
        int matRight = (this.brass ? BRASS_MAT_X[3] : AND_MAT_X[1]) + 18;
        int prodLeft = BRASS_PROD_X[0];
        return Math.max(20, prodLeft - matRight - 4);
    }

    /** 箭头 Y（与两行材料槽的中线对齐 ✓）*/
    public int cookArrowY() {
        return MAT_Y[0] + 8;
    }

    // ---- 槽位（整体上移 ✓ 用户：材料/熔炼/产物拉上去一些）----
    public static final int[] MAT_Y = {36, 54};
    // ★ 2026-10-01：表盘拆掉后左边空出 44px，材料/燃料整列**左移 30** 补上 ✓
    //   （产物列与火焰不动 ⇒ 中间的「→」箭头自动变长 ✓ 算箭头用的是这两组 X ✓）
    public static final int[] BRASS_MAT_X = {16, 34, 52, 70};
    public static final int[] AND_MAT_X = {16, 34};     // 紧凑：与黄铜左列对齐 ✓（用户 2026-09-30）
    public static final int[] BRASS_FUEL = {16, 92};
    public static final int[] AND_FUEL = {16, 92};      // 与黄铜同一个位置 ✓（用户指定）

    // ---- 火焰图标（用户 2026-09-30：放右下空白处；燃烧时亮、不燃烧时暗 ✓）----
    public static final int[] FLAME = {162, 112};
    public static final int FLAME_W = 14;
    public static final int FLAME_H = 16;
    public static final int[] BRASS_PROD_X = {150, 168};
    public static final int[] BRASS_PROD_Y = {36, 54, 72, 90};
    public static final int[] AND_PROD_X = {150, 168};  // 与黄铜右列对齐 ✓
    public static final int[] AND_PROD_Y = {36, 54};    // 紧凑两行 ✓

    /** 进度条通道（走 Packet105 ✓）*/
    public static final int BAR_HEAT = 0;
    public static final int BAR_HEAT_MAX = 1;
    public static final int BAR_SPEED = 2;
    public static final int BAR_COOK = 5;         // 熔炼进度（0..100）—— 画成原版那个「→」✓

    private final FurnaceCoreTileEntity core;
    private final int materialSlots;
    private final boolean brass;

    private int lastHeat = -1, lastHeatMax = -1, lastSpeed = -1, lastCook = -1;

    /**
     * ★ 2026-10-01 修用户报的 bug：**关掉炉子界面再打开，热力值和时长显示 0** ✗
     *
     * 【根因】进度条包是"**变了才发**"✗ ⇒ 重新打开界面时，如果这几条数值**一直没变**，
     *   客户端那个新容器就永远收不到初值 ⇒ 一直显示 0 ✗
     *   ——用户描述完全对得上：「只要添加一下燃烧物就又变了」✓（一加燃料，数值变了才补上 ✓）
     *
     * 【修法（两道保险 ✓）】
     *   ① 玩家一挂上容器，**立刻把四条数值全推一遍** ✓（原版 ContainerMerchant 就是这招 ✓）
     *   ② 容器刚建好的**头 2 秒**（40 tick）里，无视"变了才发"，每 tick 全推 ✓
     *      —— 防止有些路径根本不走 addCraftingToCrafters ✗（那时 ① 兜不住 ✓）
     */
    private int cm$forceSync = 40;

    @Override
    public void addCraftingToCrafters(net.minecraft.ICrafting crafter) {
        super.addCraftingToCrafters(crafter);
        this.lastHeat = -1; this.lastHeatMax = -1; this.lastSpeed = -1; this.lastCook = -1;
        this.cm$push(crafter, this.core.heatCurrent(), this.core.heatMax(),
                this.core.speedRpm(), this.core.cookPercent());
    }

    /** 把这四条数值推给一个 crafter ✓（变了才发 ✓；force=true 时无视比较全推 ✓）*/
    private void cm$push(net.minecraft.ICrafting c, int heat, int heatMax, int speed, int cook) {
        boolean force = this.cm$forceSync > 0;
        if (force || heat != this.lastHeat) { c.sendProgressBarUpdate(this, BAR_HEAT, heat); this.lastHeat = heat; }
        if (force || heatMax != this.lastHeatMax) { c.sendProgressBarUpdate(this, BAR_HEAT_MAX, heatMax); this.lastHeatMax = heatMax; }
        if (force || speed != this.lastSpeed) { c.sendProgressBarUpdate(this, BAR_SPEED, speed); this.lastSpeed = speed; }
        if (force || cook != this.lastCook) { c.sendProgressBarUpdate(this, BAR_COOK, cook); this.lastCook = cook; }
    }

    public BigFurnaceContainer(EntityPlayer player, FurnaceCoreTileEntity core) {
        this(player, core, core.casing());
    }

    /**
     * ★ 必须让两边用**同一个机壳档位**建槽位表 ✗否则崩溃：
     *   服务端按它自己的核心建（黄铜 = 8+8+1+36 = 53 格 ✓），
     *   客户端那边核心方块实体的 casing 字段**是没同步过的**（默认为 0 ✗）→ 只建出 45 格 ✗
     *   → 服务端发来的 Packet104（53 个物品）打到 45 格的容器上直接 IndexOutOfBounds ✗
     *   （实测崩溃：Index 45 out of bounds for length 45 ✓）
     * 所以机壳档位跟着开窗包一起发过来，客户端照它建 ✓
     */
    public BigFurnaceContainer(EntityPlayer player, FurnaceCoreTileEntity core, int casing) {
        super(player);
        this.core = core;
        core.setCasing(casing);                       // 客户端顺手补上（GUI 画边框风格也用它 ✓）
        this.brass = casing == FurnaceMultiblock.CASING_BRASS;
        this.materialSlots = brass ? 8 : 4;

        int index = 0;
        // 材料槽
        if (brass) {
            for (int row = 0; row < 2; row++)
                for (int col = 0; col < 4; col++)
                    this.addSlotToContainer(new MachineSlot(core, index++, BRASS_MAT_X[col], MAT_Y[row]));
        } else {
            for (int row = 0; row < 2; row++)
                for (int col = 0; col < 2; col++)
                    this.addSlotToContainer(new MachineSlot(core, index++, AND_MAT_X[col], MAT_Y[row]));
        }
        // 燃料槽（单独一个 ✓）
        int[] fuel = brass ? BRASS_FUEL : AND_FUEL;
        this.addSlotToContainer(new MachineSlot(core, core.fuelSlot(), fuel[0], fuel[1]));
        // 产物槽
        // ★★ 2026-10-01 修用户报的 bug「我的产物呢！哪去了」：**这里必须把 index 归零** ✗
        //   原来材料槽和产物槽**共用同一个 index** 且中间没重置 ⇒ 安山炉材料槽用掉 0~3 之后，
        //   产物槽从 productStart()+4 = **9** 开始建（真实产物槽是 5~8）⇒ 界面上那 4 格指向
        //   **根本不存在的槽位**（永远空 ✓），而东西全躺在你看不见的 5~8 里 ✗✗
        //   （黄铜同理：8 个材料槽用完 index=8 ⇒ 产物从 17 开始，真实是 9~16 ✗）
        //   教训：**槽位索引宁可重新算一遍，也不要跨段复用计数器** ✗
        index = 0;
        if (brass) {
            for (int row = 0; row < 4; row++)
                for (int col = 0; col < 2; col++)
                    this.addSlotToContainer(new ProductSlot(core, core.productStart() + index++, BRASS_PROD_X[col], BRASS_PROD_Y[row]));
        } else {
            for (int row = 0; row < 2; row++)
                for (int col = 0; col < 2; col++)
                    this.addSlotToContainer(new ProductSlot(core, core.productStart() + index++, AND_PROD_X[col], AND_PROD_Y[row]));
        }
        // 玩家背包
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
    }

    /**
     * 产物槽：**只出不进** ✓（用户 2026-10-01：「不能往输出槽内放置物品」）
     *
     * ⚠️ 光靠 IInventory.isItemValidForSlot 不够 ✗ —— 管"手上拿着的东西能不能放进这一格"的
     *   其实是 **Slot.isItemValid()** ✓（前者是给漏斗/自动化那条路看的 ✓），
     *   而 MITE 的 Slot.isItemValid 默认**恒 true** ✗ ⇒ 必须自己覆写成 false ✓
     * （shift 搬运那条路另外用 mergeItemStack 的范围挡住，见 transferStackInSlot ✓）
     */
    /**
     * 材料槽 / 燃料槽：**把"能不能放"接到方块实体自己的 isItemValidForSlot 上** ✓
     *
     * 【为什么要这个类】1.6.4 的 Slot.isItemValid() 默认并不去看 IInventory.isItemValidForSlot() ✗
     *   （后者本来是给漏斗/自动化那条路用的 ✓）⇒ 光在方块实体里写规则，**手上的鼠标点击照样能放进去** ✗
     *   ⚠️ Slot.slotIndex 是 **private** ✗ ⇒ 索引只能自己在构造时存一份 ✓
     */
    public static class MachineSlot extends Slot {
        private final int cm$index;
        public MachineSlot(net.minecraft.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
            this.cm$index = index;
        }
        @Override
        public boolean isItemValid(ItemStack stack) {
            return this.inventory.isItemValidForSlot(this.cm$index, stack);
        }
    }

    public static class ProductSlot extends Slot {
        public ProductSlot(net.minecraft.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }
        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;                  // ★ 永远不收 ✗
        }
    }

    /**
     * 界面用的"真在烧" —— ★ 直接读**客户端世界里核心方块的燃烧位（metadata bit3）** ✓
     *
     * 【为什么不再用进度条包】那条包是"变了才发"✗ ⇒ 关掉界面再打开时，新容器的 crafters 刚挂上，
     *   用户实测就是「炉子在烧、但火焰是灭的」✗。
     *   metadata 是**世界状态**，随时读随时对 ✓，而且和整机 3D 模型用的是**同一个来源** ✓ 天然一致 ✓
     */
    public boolean isBurningNow() {
        return FurnaceMultiblock.isBurning(this.core.getWorldObj(),
                this.core.xCoord, this.core.yCoord, this.core.zCoord);
    }

    public FurnaceCoreTileEntity core() { return this.core; }
    public boolean isBrass() { return this.brass; }
    public int materialSlots() { return this.materialSlots; }
    /** 机器自己的槽位区间 [0, lastMachineSlot) */
    public int lastMachineSlot() { return this.materialSlots * 2 + 1; }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.core.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int heat = this.core.heatCurrent();
        int heatMax = this.core.heatMax();
        int speed = this.core.speedRpm();
        int cook = this.core.cookPercent();             // ★ 熔炼进度：接上真值（0..100 ✓）

        for (int i = 0; i < this.crafters.size(); i++) {
            Object o = this.crafters.get(i);
            if (!(o instanceof net.minecraft.ICrafting)) continue;
            this.cm$push((net.minecraft.ICrafting) o, heat, heatMax, speed, cook);
        }
        if (this.cm$forceSync > 0) this.cm$forceSync--;      // ★ 开窗头 2 秒全推 ✓
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == BAR_HEAT) this.clientHeat = value;
        else if (id == BAR_HEAT_MAX) this.clientHeatMax = value;
        else if (id == BAR_SPEED) this.clientSpeed = value;
        else if (id == BAR_COOK) this.clientCook = value;
    }

    public int clientHeat;
    public int clientHeatMax;
    public int clientSpeed;
    public int clientCook;

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        result = stack.copy();

        int machineEnd = this.lastMachineSlot();
        if (index < machineEnd) {
            if (!this.mergeItemStack(stack, machineEnd, this.inventorySlots.size(), true)) return null;
        } else {
            // ★ 玩家背包 → 机器：**按类型分流** ✓（产物槽绝不许被塞东西 ✗ 用户 2026-10-01）
            //   燃料 → 燃料槽 ✓；其余 → 材料槽 ✓（材料槽自己还会再挡一道"必须有配方" ✓）
            boolean isFuel = stack.getItem() != null && stack.getItem().getHeatLevel(stack) > 0;
            boolean ok = isFuel
                    ? this.mergeItemStack(stack, this.core.fuelSlot(), this.core.fuelSlot() + 1, false)
                    : this.mergeItemStack(stack, 0, this.core.fuelSlot(), false);
            if (!ok) return null;
        }

        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return result;
    }
}
