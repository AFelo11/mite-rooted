package net.dsh.createmite.furnace;

import net.minecraft.EntityPlayer;
import net.minecraft.IInventory;
import net.minecraft.ItemStack;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 大熔炉核心的方块实体。
 *
 * 它管三件事：
 *   ① 把「结构变化」的判定**推迟到下一 tick** 执行 ✓
 *   ② 给 3x3x3 整机模型当渲染锚点（TESR 挂在这里 ✓）
 *   ③ 炉子的背包与数值：**材料槽 / 燃料槽 / 产物槽** + 热值 + 转速值（给 UI 用 ✓，2026-09-30 加）
 *   ④ ★ 2026-10-01：**真正的熔炼逻辑**（cm$smeltTick ✓，与 MITE 熔炉同一套配方/热值 ✓）
 *        + **燃烧态**（cm$setBurning → 核心 metadata bit3 ⇒ 整机模型与界面火焰自动切换 ✓）
 *   ⑤ ★★ 2026-10-01 用户 ①②③ 定稿：**热值池**（可叠加、可存住、上限按材质 ✓ 见 heatCurrent 的注释 ✓）
 *
 * 【槽位按机壳分档】黄铜机壳 = 8 材料 + 1 燃料 + 8 产物 ✓；安山机壳 = 4 材料 + 1 燃料 + 4 产物 ✓（用户指定 ✓）
 * 【为什么背包放核心】整机本体就是那 27 格，核心是唯一的控制器 ✓
 * —— 将来拆结构时把内容物吐出来也在这里做 ✓
 */
public class FurnaceCoreTileEntity extends TileEntity implements IInventory {

    /**
     * ★★ 2026-09-30 修用户报的 bug：**大熔炉离远了整机消失，底盘能直接透视看到矿洞** ✗
     *
     * 【根因】成型后那 27 格是**在方块渲染里被我们隐藏掉的** ✗（RenderBlocksMixin ✓），
     *   整机外观**全靠核心这一个 TESR 画** ✓；而原版 `TileEntity.getMaxRenderDistanceSquared()`
     *   只给 **4096 = 64 格** ✗，`TileEntityRenderer.renderTileEntity` 拿它当闸门 ✓
     *   → 距离一超，TESR 不画、方块又是隐形的 ⇒ **整台炉子变成空气，直接看穿到地底** ✗
     *
     * 【修法】大熔炉**一台机器只有一个方块实体** ✓（比一堆齿轮便宜得多 ✓），
     *   所以给它更大的范围：**1024 倍 = 2048 格** ✓ —— 相当于"只要这片区块还在视野里就一定画" ✓。
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return super.getMaxRenderDistanceSquared() * 1024.0D;
    }

    /** 黄铜机壳：8 + 1 + 8 */
    public static final int SLOTS_BRASS = 17;
    /** 安山机壳：4 + 1 + 4 */
    public static final int SLOTS_ANDESITE = 9;

    private static final int MAX_STACK = 64;

    private ItemStack[] slots = new ItemStack[SLOTS_ANDESITE];

    /** 当前机壳档位（0=安山 1=黄铜）—— 成型时由 FurnaceMultiblock 写入 ✓ */
    private int casing = 0;

    /**
     * ★★ 2026-10-01（用户 ②③ 定稿）：**热值池** —— 热值不再只是"当前燃料的档位"，
     *   而是**可以叠加、可以存住**的一份热量 ✓
     *
     *     · 燃料烧掉 ⇒ 燃烧值**加进池子**（木质100／木炭200／煤炭400／岩浆桶800／烈焰棒1000 ✓ 用户定稿 ✓）
     *     · 池子容量 = **HEAT_SCALE_MAX = 10000**（谁都一样 ✓ —— 界面上那个 0/10000 就是它 ✓）
     *     · **能烧什么级别**由炉子材质卡死 ✓：圆石 600（到铁）／黑曜石 800（到秘银）／下界岩 1000（到艾德曼）
     *       ⇒ **圆石大熔炉就算叠到 10000，也烧不了秘银/艾德曼** ✓ —— 用户第 ③ 条 ✓
     *     · 每烧出一份 ⇒ 扣掉"这份东西对应的燃烧值"✓（200/400/600/800/1000 ✓ 存住的被消耗 ✓）
     *   ⇒ 木头本来烧不了铁矿，但在这里**叠够 600** 就能烧 ✓ —— 用户第 ② 条 ✓
     */
    private int heatCurrent;      // 池子里现存的**燃烧值**（0..HEAT_SCALE_MAX）
    private int heatMax;          // 池子容量（= HEAT_SCALE_MAX = 10000 ✓）
    private int speedRpm;

    /** 燃料"烧"的计时器：每满 cm$fuelBurnTicks() 就把一份燃料的热力值加进池子 ✓ */
    private int fuelTick;

    /** 燃烧时长的计时器：每满 cm$ticksPerHeat() 就掉 1 点热力值 ✓（200 点 = 1 分钟 ✓） */
    private int burnTick;

    /**
     * ★ 2026-10-01：整机"真在烧"（服务端算 → 写核心 metadata 的 bit3 ⇒ 客户端渲染器读得到 ✓）
     *   驱动两处：整机自动切"燃烧中"模型 ✓ + 界面那条火焰 ✓
     */
    private boolean burning;

    /** 最靠前那一格的熔炼进度（0..CM_COOK_TICKS）—— 界面那个「→」箭头用它 ✓ */
    private int cookProgress;

    /** 变化后延迟几 tick 再判定（那一刻还在 Chunk 写块的内部 ✗） */
    private static final int DELAY = 2;
    /** 兜底复检间隔（tick）：防"爆炸/活塞/世界编辑"这类没走 breakBlock 通知的路径 */
    private static final int IDLE_RECHECK = 100;

    private boolean dirty;
    private int lastDx, lastDy, lastDz;
    private int delay;
    private int idleTicks;

    // ===================== 判定（原有逻辑不变）=====================

    /** 由 FurnaceMultiblock.onChange 调用：附近有方块变了，记下来 */
    public void markChanged(int dx, int dy, int dz) {
        this.dirty = true;
        this.lastDx = dx;
        this.lastDy = dy;
        this.lastDz = dz;
        this.delay = DELAY;
    }

    /** 自检（不覆盖"最后一块落在哪"的朝向提示 ✓） */
    public void markSelfCheck() {
        this.dirty = true;
        this.delay = DELAY;
    }

    @Override
    public void updateEntity() {
        cm$smeltTick();          // ★ 2026-10-01：熔炼主循环（和 MITE 熔炉同一套配方与热值 ✓）
        World world = this.worldObj;
        if (world == null) return;
        if (world.isRemote) {
            // 客户端：只负责让渲染器注册上（和 KineticTileEntity 同一条路 ✓）
            net.dsh.createmite.kinetics.client.KineticRendererHook.ensureRegistered();
            return;                                    // 判定与写世界只在服务端做 ✓
        }

        if (this.dirty) {
            if (--this.delay > 0) return;
            this.dirty = false;
            FurnaceMultiblock.updateCore(world, this.xCoord, this.yCoord, this.zCoord,
                    this.lastDx, this.lastDy, this.lastDz);
            return;
        }

        this.idleTicks++;
        if (this.idleTicks >= IDLE_RECHECK) {
            this.idleTicks = 0;
            // 偏移给 (0,0,1)：只有"本来就该成型却还没成型"时才用得到，那时按默认南 ✓
            FurnaceMultiblock.updateCore(world, this.xCoord, this.yCoord, this.zCoord, 0, 0, 1);
        }
    }

    // ===================== 机壳档位 / 数值 =====================

    public int casing() {
        return this.casing;
    }

    public void setCasing(int casing) {
        if (this.casing == casing) return;
        this.casing = casing;
        if (this.slots.length != slotCountFor(casing)) {
            ItemStack[] grown = new ItemStack[slotCountFor(casing)];
            System.arraycopy(this.slots, 0, grown, 0, Math.min(this.slots.length, grown.length));
            this.slots = grown;      // 安山→黄铜是扩容 ✓（成型判定保证不会混用机壳 ✓）
        }
    }

    public static int slotCountFor(int casing) {
        return casing == FurnaceMultiblock.CASING_BRASS ? SLOTS_BRASS : SLOTS_ANDESITE;
    }

    /** 材料槽数量（安山 4 / 黄铜 8） */
    public int materialSlots() {
        return this.casing == FurnaceMultiblock.CASING_BRASS ? 8 : 4;
    }

    public int fuelSlot() {
        return this.materialSlots();
    }

    // ===================== 熔炼（2026-10-01 用户定稿）=====================
    private final int[] cm$cook = new int[16];
    private static final int CM_COOK_TICKS = 200;   // 每份 200 tick（和 MITE 熔炉一致 ✓）

    /**
     * 每 tick 的熔炼主循环 —— 配方与门槛**完全走 MITE 自己那套** ✓（2026-10-01 用户 ①②③ 定稿）
     *
     *   ① **燃烧值**刻度（用户 2026-10-01 定稿 ✓）：燃料给 100/200/400/800/1000 ✓；
     *      烧一份花 200/400/600/800/1000 ✓（木头档200 铜银金锌400 **铁600** 秘银800 艾德曼1000 ✓）
     *   ② 燃料烧掉 ⇒ 燃烧值**存进池子、可以叠加** ✓（池子容量 = `HEAT_SCALE_MAX` = **10000** ✓）
     *      材料槽**并行**烧、每份 200 tick ✓；门槛 = 池子燃烧值够不够 + 本炉材质够不够 ✓
     *      —— 不够 ⇒ **该格暂停、但进度保留** ✓（燃料补上来就接着烧 ✓）
     *   ③ **能烧什么级别**由炉子材质卡死 ✓：圆石 **600** ／ 黑曜石 **800** ／ 下界岩 **1000** ✓
     *      ⇒ 圆石大熔炉**叠到 10000 也烧不了秘银/艾德曼** ✓（用户举的那个例子 ✓）
     *   每烧出一份 ⇒ 从池子里扣掉它对应的燃烧值 ✓（200/400/600/800/1000 ✓）
     */
    private void cm$smeltTick() {
        if (this.worldObj == null || this.worldObj.isRemote) return;

        // ★ 没成型 = 这堆方块还不是一台炉子 ⇒ 不烧、不点燃烧态、池子清空 ✓
        //   （"成型"就是核心 metadata 的 bit0-2 非零，见 FurnaceMultiblock.isFormed ✓）
        if (!FurnaceMultiblock.isFormed(this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord))) {
            this.cm$setBurning(false);
            this.cookProgress = 0;
            this.heatCurrent = 0;
            this.fuelTick = 0;
            this.setValues(0, HEAT_SCALE_MAX, this.speedRpm);
            return;
        }

        int cap = HEAT_SCALE_MAX;      // ★ 池子容量：谁都一样 10000 ✓（"能烧什么级别"另由 heatCeiling 管 ✓）
        if (this.heatCurrent > cap) this.heatCurrent = cap;   // 换材质 / 老存档兜底 ✓

        // ---- ① 燃料：把热值"存"进池子（用户 ②：热值可以叠加 ✓）----
        boolean fueling = false;
        if (this.heatCurrent < cap) {
            ItemStack fuel = this.getStackInSlot(this.fuelSlot());
            if (fuel != null) {
                int h = cm$fuelHeat(fuel);      // ★ 100/200/400/800/1000 ✓（用户定稿 ✓）
                if (h < 0) h = 0;
                if (h > 0) {
                    if (++this.fuelTick >= cm$fuelBurnTicks()) {
                        this.fuelTick = 0;
                        ItemStack burned = fuel.copy();       // 留个副本：要拿它的"容器物"（岩浆桶 → 空桶 ✓）
                        burned.stackSize = 1;
                        this.decrStackSize(this.fuelSlot(), 1);
                        this.heatCurrent += h;
                        if (this.heatCurrent > cap) this.heatCurrent = cap;   // 超出上限的部分浪费掉（和 MITE 熔炉一样 ✓）
                        this.cm$returnContainer(burned);      // ★ 岩浆桶烧完要还一个空桶 ✓（不然桶也没了 ✗）
                    }
                    fueling = true;              // 有燃料正在烧 ⇒ 火焰亮 ✓
                }
            } else {
                this.fuelTick = 0;
            }
        } else {
            this.fuelTick = 0;                   // ★ 池子满了就停烧 ⇒ 一份燃料都不浪费 ✓
        }

        // ---- ②③ 熔炼：够不够热，交给 MITE 自己的表判 ✓ ----
        int mats = this.materialSlots();
        boolean smelting = false;
        int cook = 0;
        int done = 0;

        // ★★ 用户 ②：**并行上限**（没动力 1 ／ 安山+动力 2 ／ 黄铜+动力 4 ✓）
        int parallel = this.parallelLimit();
        int active = 0;
        this.speedRpm = (int) this.kineticSpeed();          // 顺手把真实转速记下来（将来"越快烧越快"用得上 ✓）

        // ★ 顺序：**烧到一半的排前面** ✗ 否则超过上限的那几格明明快好了却在干等 ✓
        //   （mats 最多 8 ⇒ 插排足够了，不用搞排序库 ✓）
        int[] order = new int[mats];
        for (int k = 0; k < mats; k++) order[k] = k;
        for (int a = 1; a < mats; a++) {
            int key = order[a];
            int kv = this.cm$cook[key];
            int b = a - 1;
            while (b >= 0 && this.cm$cook[order[b]] < kv) { order[b + 1] = order[b]; b--; }
            order[b + 1] = key;
        }

        for (int oi = 0; oi < mats && oi < this.cm$cook.length; oi++) {
            int i = order[oi];
            ItemStack in = this.getStackInSlot(i);
            if (in == null) { this.cm$cook[i] = 0; continue; }
            // 压根不是能烧的东西 ⇒ 清零 ✓（和"暂时不够热"要区分开 ✗）
            if (!net.minecraft.FurnaceRecipes.smelting().doesSmeltingRecipeExistFor(in)) {
                this.cm$cook[i] = 0; continue;
            }
            // ★ 门槛（用户 ①：**热力值达到界限才能烧相应的矿物** ✓）：
            //   ① 本炉材质**最多只能烧到某一档** ⇒ 超出的那一格永远不动 ✓
            //      （圆石炉最多烧铁 ✓ 就算热力值 800 也不行 ✓）
            //   ② 池子里的热力值**没到这道矿的界限** ⇒ 暂停、**进度保留** ✓
            int need = cm$heatRequired(in.itemID);
            if (need > this.heatCeiling()) continue;
            if (this.heatCurrent < need) continue;
            // 交给 MITE 那张表时要用**它的档位**（1~4 ✓），不是我们的燃烧值 ✗
            ItemStack result = net.minecraft.FurnaceRecipes.smelting().getSmeltingResult(
                    in, net.minecraft.TileEntityFurnace.getHeatLevelRequired(in.itemID));
            if (result == null) continue;
            // ★ 2026-10-01 用户：「改成可以输出至其他槽」⇒ 不再钉死在"自己那一格产物槽"上 ✗
            if (this.cm$findOutputSlot(i, result) < 0) continue;   // 所有产物槽都装不下 ⇒ 这一格暂停 ✓
            // ★★ 用户 ②：**并行上限**（没动力 1 个 ／ 安山 2 ／ 黄铜 4 ✓）
            if (active >= parallel) continue;                      // 超出上限的格本轮不动、**进度保留** ✓
            smelting = true;
            active++;
            if (++this.cm$cook[i] >= CM_COOK_TICKS) {
                this.cm$cook[i] = 0;
                int outSlot = this.cm$findOutputSlot(i, result);   // 出锅时**再找一次**（这 200 tick 里可能被拿走过 ✓）
                if (outSlot >= 0) {
                    ItemStack out = this.getStackInSlot(outSlot);
                    this.decrStackSize(i, 1);
                    if (out == null) this.setInventorySlotContents(outSlot, result.copy());
                    else out.stackSize += result.stackSize;
                    done++;
                }
            }
            if (this.cm$cook[i] > cook) cook = this.cm$cook[i];   // 界面箭头取"最靠前的那一格" ✓
        }

        // ---- ★★ 燃烧时长：**每 200 点热力值 = 1 分钟**（1200 tick ⇒ 每 6 tick 掉 1 点 ✓）----
        //   热力值就是"还能烧多久"：满池 10000 = 50 分钟 ✓
        //   ★ 2026-10-01 用户改口径：**空转也掉** ✓（上一版是"只在真的在烧时才掉" ✗ 已按用户要求改掉 ✓）
        //      —— 也就是"炉子点着了就在烧"，不管有没有东西在炼 ✓
        if (this.heatCurrent > 0 && ++this.burnTick >= cm$ticksPerHeat()) {
            this.burnTick = 0;
            this.heatCurrent -= 1;
            if (this.heatCurrent < 0) this.heatCurrent = 0;
        }

        this.setValues(this.heatCurrent, cap, this.speedRpm);
        this.cookProgress = cook;
        // ★ 2026-10-01：**"池子里还有热值"也算燃烧态** ✓
        //   ① 用户报的「关掉界面再打开，火焰是灭的」根治：状态不再依赖"当前这一瞬间有没有在烧" ✗
        //      （原来池子一装满 + 暂时没料 ⇒ 瞬间判定为不烧 ⇒ 关界面时正好卡在这个瞬间就显示灭 ✗）
        //   ② 池子里存着热值 = 炉子里确实有火气 ✓ 显示"燃烧中"名正言顺 ✓
        this.cm$setBurning(fueling || smelting || this.heatCurrent > 0);

        // ---- 临时诊断（2026-10-01）：每 10 秒一行，一眼看出"到底烧没烧" ✓
        //   （界面那两根表盘拆掉之后，这是唯一的取数通道 ✓ —— 排查完可以删 ✗）
        if ((this.worldObj.getTotalWorldTime() % 200L) == 0L) {
            int filled = 0;
            for (int k = 0; k < mats; k++) if (this.getStackInSlot(k) != null) filled++;
            ItemStack f = this.getStackInSlot(this.fuelSlot());
            ItemStack p0 = this.getStackInSlot(this.productStart());
            System.out.println("[CreateMITE][大熔炉] 燃烧值=" + this.heatCurrent + "/" + cap
                    + " 本炉上限=" + this.heatCeiling()
                    + " 材料=" + filled + "/" + mats
                    + " 燃料=" + (f == null ? "空" : (f.stackSize + "个"))
                    + " 在烧=" + (fueling ? 1 : 0) + " 熔炼=" + (smelting ? 1 : 0)
                    + " 燃烧位=" + (FurnaceMultiblock.isBurning(this.worldObj, this.xCoord, this.yCoord, this.zCoord) ? 1 : 0)
                    + " 可烧=" + (burnSecondsFor(this.heatCurrent) / 60) + "分" + (burnSecondsFor(this.heatCurrent) % 60) + "秒"
                    + " 动力=" + (int) this.kineticSpeed() + "RPM 并行=" + parallel + " 在烧格数=" + active
                    + " 完成=" + done + " 进度=" + cook + "/" + CM_COOK_TICKS
                    + " 产物槽0=" + (p0 == null ? "空" : (p0.itemID + "x" + p0.stackSize))
                    + " 槽位表=" + this.slots.length + " 产物起始=" + this.productStart());
        }
    }

    /**
     * 燃料烧掉之后，把它的"容器物"还回来 ✓
     *   MITE 里**岩浆桶 = 热值 3** ✓，烧完本该剩一个空桶 —— 直接 decrStackSize 会把桶一起吞掉 ✗
     *   顺序：① 燃料槽空出来了就放回去（最常见 ✓）→ ② 找个空的产物槽 → ③ 掉在炉子顶上 ✓
     */
    private void cm$returnContainer(ItemStack burned) {
        if (burned == null || burned.getItem() == null) return;
        if (!burned.getItem().hasContainerItem()) return;
        net.minecraft.Item c = burned.getItem().getContainerItem();
        if (c == null) return;
        ItemStack back = new ItemStack(c, 1);
        int fs = this.fuelSlot();
        if (this.getStackInSlot(fs) == null) { this.setInventorySlotContents(fs, back); return; }
        for (int i = this.productStart(); i < this.slots.length; i++) {
            if (this.getStackInSlot(i) == null) { this.setInventorySlotContents(i, back); return; }
        }
        // （World.dropItem 在本工程里没用过，就不冒险了：宁可留在槽里也不吞 ✗）
    }

    /** 每份燃料烧多久（tick）才把热值存进池子 —— 可配置 ✓（默认 20 tick = 1 秒） */
    private static int cm$fuelBurnTicks() {
        int v = (int) net.dsh.createmite.CMConfig.getFloat("furnace.fuel_burn_ticks", 20.0F);
        return v < 1 ? 1 : v;
    }

    /**
     * 找一个**能装下这份产物**的产物槽 ✓（用户 2026-10-01：「改成可以输出至其他槽，不要第一个满了就停」）
     *
     *   顺序：**先看"本该属于它的那一格"**（产物槽 i ✓ 单槽运行时仍然整齐 ✓）
     *        → 再顺着往后找 **同种产物且还有空间**的槽（堆叠 ✓）
     *        → 再找**空槽** ✓
     *        → 都没有 ⇒ 返回 -1（这一格暂停 ✓ 但**不停机** ✓，其余槽照烧 ✓）
     *   ⚠️ 出锅时要**再找一次** ✗ —— 这 200 tick 里玩家可能把产物拿走了 ✓
     */
    private int cm$findOutputSlot(int preferred, ItemStack result) {
        int start = this.productStart();
        int count = this.slots.length - start;
        if (count <= 0 || result == null) return -1;
        int first = start + (preferred < count ? preferred : count - 1);
        for (int k = 0; k < count; k++) {                       // ① 同种、还有空间
            int s = start + ((first - start + k) % count);
            ItemStack cur = this.getStackInSlot(s);
            if (cur != null && cur.itemID == result.itemID
                    && cur.stackSize + result.stackSize <= cur.getMaxStackSize()) return s;
        }
        for (int k = 0; k < count; k++) {                       // ② 空槽
            int s = start + ((first - start + k) % count);
            if (this.getStackInSlot(s) == null) return s;
        }
        return -1;
    }

    /**
     * ★★ 燃烧时长（用户本轮新增）：「每 200 点热力值，熔炉就可以燃烧 1 分钟」✓
     *
     *   1 分钟 = 1200 tick ⇒ 200 点 / 1200 tick ⇒ **每 6 tick 掉 1 点** ✓
     *   配置项 `furnace.burn_heat_per_minute`（默认 200 ✓）—— 调大 = 更耐烧 ✓ / 调小 = 更费 ✓
     *   ⚠️ 只在**真的在烧**（有格在熔炼）时才掉 ✗ —— 空转不掉，走开也不漏 ✓
     */
    private static int cm$ticksPerHeat() {
        int perMin = (int) net.dsh.createmite.CMConfig.getFloat("furnace.burn_heat_per_minute", 200.0F);
        if (perMin < 1) perMin = 1;
        int t = 1200 / perMin;          // 20 tick/秒 × 60 秒 = 1200 ✓
        return t < 1 ? 1 : t;
    }

    /** 这些热力值还能烧多少**秒** ✓（界面显示"分:秒"用它 ✓） */
    public static int burnSecondsFor(int heat) {
        if (heat <= 0) return 0;
        return heat * cm$ticksPerHeat() / 20;      // 20 tick = 1 秒 ✓
    }

    // ===================== ★★ 用户 ②：动力 → 并行上限 =====================

    /**
     * ★★ 2026-10-01 用户 ②：「特殊机制」——
     *   ［材料单次最多烧出一次成品］
     *   ［黄铜大熔炉**接上动力**单次最多同时烧 **4** 个物品。安山大熔炉接上动力单次最多同时烧 **2** 个］
     *
     * 【怎么理解】每个材料槽**一次只出一份**成品 ✓（本来就是这样 ✓）；
     *   而**同时**能开火几个槽，看**有没有接动力** ✓：
     *     · 没动力 ⇒ **1**（和普通熔炉一样，一次一份 ✓ 这就是"单次最多烧出一次成品"✓）
     *     · 安山机壳 + 动力 ⇒ **2**
     *     · 黄铜机壳 + 动力 ⇒ **4**
     *   （机壳档位 = casing：安山 0 ／ 黄铜 1 ✓）
     */
    public int parallelLimit() {
        if (!this.hasKineticPower()) return 1;
        return this.casing == FurnaceMultiblock.CASING_BRASS ? 4 : 2;
    }

    /**
     * 整机**有没有接上动力在转** ✓
     *
     * 【在哪读】整机 27 格里本来就有**包裹传动杆**（结构件 ✓ 同时也是动力方块 ✓）
     *   ⇒ 拿那一格的方块实体读 KineticTileEntity.speed（**public** ✓）：
     *      非 0 = 有动力在转 ✓；没有包裹传动杆 / 它没转 = 没动力 ✓
     * （转速顺手存进 speedRpm ✓ 将来要做"转速越快烧越快"就有现成的值 ✓）
     */
    public boolean hasKineticPower() {
        return this.kineticSpeed() != 0.0F;
    }

    /** 整机里包裹传动杆的转速（RPM ✓ 没接动力就是 0 ✓） */
    public float kineticSpeed() {
        World w = this.worldObj;
        if (w == null) return 0.0F;
        for (int i = 0; i < FurnaceMultiblock.OFF_DX.length; i++) {
            int x = this.xCoord + FurnaceMultiblock.OFF_DX[i];
            int y = this.yCoord + FurnaceMultiblock.OFF_DY[i];
            int z = this.zCoord + FurnaceMultiblock.OFF_DZ[i];
            net.minecraft.Block b = net.minecraft.Block.blocksList[w.getBlockId(x, y, z)];
            if (!(b instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft)) continue;
            net.minecraft.TileEntity te = w.getBlockTileEntity(x, y, z);
            if (te instanceof net.dsh.createmite.kinetics.KineticTileEntity) {
                float s = ((net.dsh.createmite.kinetics.KineticTileEntity) te).speed;
                if (s != 0.0F) return s;
            }
        }
        return 0.0F;
    }

    /**
     * ★ 本炉材质**最多能烧到多少燃烧值**（用户 ③：圆石炉就算叠满也只能烧到铁 ✓）
     *   圆石 = **600**（到铁为止 ✓）／ 黑曜石 = **800**（到秘银 ✓）／ 下界岩 = **1000**（到艾德曼 ✓）
     * ⚠️ 卡的是"**能烧什么级别**" ✗，**不是"能存多少"** ✓（池子容量是 HEAT_SCALE_MAX = 10000 ✓）
     */
    public int heatCeiling() {
        return FurnaceMultiblock.heatCeilingOf(this.materialOfSelf());
    }

    /**
     * ★★ 一份燃料能存进池子的**燃烧值**（用户 2026-10-01 定稿 ✓）
     *     木质燃烧物 **100** ／ 木炭 **200** ／ 煤炭 **400** ／ 岩浆桶 **800** ／ 烈焰棒 **1000**
     *
     * 【怎么认】先看 MITE 自己的档（Item.getHeatLevel ✓ 1~4 ✓）：
     *   4 = 烈焰棒 ✓ ／ 3 = 岩浆桶 ✓ ／ 2 = 煤炭 ✓ ／ 1 = 木质**或木炭** ✗
     * ⚠️ **木炭在 MITE 里也是 1 档** ✗（ItemCoal.getHeatLevel 字节码：subtype==1 ⇒ 1，否则 ⇒ 2 ✓）
     *   ⇒ 只能单独认它：**"煤炭那件物品 + subtype 1" = 木炭** ✓（javap 实证 ✓）
     */
    private static int cm$fuelHeat(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        int t = stack.getItem().getHeatLevel(stack);
        if (t >= 4) return 1000;                     // 烈焰棒 ✓
        if (t == 3) return 800;                      // 岩浆桶 ✓
        if (t == 2) return 400;                      // 煤炭 ✓
        if (t == 1) {
            if (net.minecraft.Item.coal != null && stack.itemID == net.minecraft.Item.coal.itemID
                    && stack.getItemSubtype() == 1) return 200;   // ★ 木炭 ✓
            return 100;                              // 木质燃烧物 ✓
        }
        return 0;
    }

    /**
     * ★★ 用户 2026-10-01 ①：**"热力值达到某个界限，才能烧相应的矿物"** ✓
     *
     *     **200** = 木头那一档（MITE 默认 1 档 ✓ 沙子/食物这类 ✓）
     *     **400** = **一档矿**：金 ／ 银 ／ 铜 ／ 锌 ✓
     *     **600** = **二档矿**：铁 ✓ ← ★ 用户单独给它一档（MITE 自己把铁和铜银金算一档 ✗）
     *     **800** = **三档矿**：秘银 ✓
     *    **1000** = **四档矿**：艾德曼 ✓
     *
     * ⚠️ 这**不是"烧一份花多少"** ✗ —— 它是**门槛**：池子里的热力值到了才烧得动 ✓
     *   （"烧多久"另外由燃烧时长管 ✓：每 200 点热力值 = 1 分钟 ✓ 用户本轮新增 ✓）
     * ⚠️ 也**不能**拿去调 MITE 的 FurnaceRecipes ✗ —— 那张表要的是 1~4 档 ✓（见 cm$smeltTick ✓）
     */
    private static int cm$heatRequired(int itemId) {
        if (cm$isIron(itemId)) return 600;                                   // ★ 铁：特例 ✓
        int t = net.minecraft.TileEntityFurnace.getHeatLevelRequired(itemId);
        if (t <= 1) return 200;
        if (t == 2) return 400;
        if (t == 3) return 800;
        return 1000;
    }

    /** 铁那一族（铁矿 + 我们的粉碎铁 ✓）—— 用户把铁单独定成 600 ✓ */
    private static boolean cm$isIron(int itemId) {
        if (net.minecraft.Block.oreIron != null && itemId == net.minecraft.Block.oreIron.blockID) return true;
        return net.dsh.createmite.CMItems.crushedIron != null
                && itemId == net.dsh.createmite.CMItems.crushedIron.itemID;
    }

    /** 读自己那格方块的材质编号（渲染器也是这么取的 ✓） */
    private int materialOfSelf() {
        World w = this.worldObj;
        if (w == null) return FurnaceMultiblock.MATERIAL_COBBLE;
        net.minecraft.Block b = net.minecraft.Block.blocksList[w.getBlockId(this.xCoord, this.yCoord, this.zCoord)];
        if (b instanceof net.dsh.createmite.block.FurnaceCoreBlock) {
            return ((net.dsh.createmite.block.FurnaceCoreBlock) b).material();
        }
        return FurnaceMultiblock.MATERIAL_COBBLE;
    }

    public int productStart() {
        return this.materialSlots() + 1;
    }

    public int heatCurrent() { return this.heatCurrent; }
    public int heatMax() { return this.heatMax; }
    public int speedRpm() { return this.speedRpm; }

    /** 整机现在真在烧吗（服务端权威值 ✓；客户端那边看核心 metadata 的 bit3 ✓） */
    public boolean isBurningNow() { return this.burning; }

    /** 熔炼进度百分比 0..100（界面那个「→」箭头用 ✓） */
    public int cookPercent() {
        int p = this.cookProgress * 100 / CM_COOK_TICKS;
        return p < 0 ? 0 : (p > 100 ? 100 : p);
    }

    /**
     * 燃烧态翻转 → 写核心 metadata 的 bit3（**只在该位翻转那一刻写一次**，不是每 tick ✗）。
     *   ① 客户端渲染器 FurnaceBigRenderer 读这一位 ⇒ 整机自动切"燃烧中"模型 ✓
     *   ② 界面那条火焰走原版进度条通道（BigFurnaceContainer.BAR_BURNING ✓），读的也是它 ✓
     * ⚠️ 写的时候**只动 bit3、其余位原样保留** ✗（bit0-2 是"成型 + 正面"✓）
     */
    private void cm$setBurning(boolean on) {
        if (this.burning == on) return;
        this.burning = on;
        World w = this.worldObj;
        if (w == null || w.isRemote) return;
        int meta = w.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
        int want = on ? (meta | FurnaceMultiblock.BURNING_BIT)
                      : (meta & ~FurnaceMultiblock.BURNING_BIT);
        if (want != meta) {
            w.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, want, 3);
        }
    }

    /**
     * ★ 2026-10-01 用户 ①：热值一共 **4 档**，对应 MITE 自带的热力值 ✓
     *   木头/木炭 = 1 ／ 煤炭 = 2 ／ 岩浆桶 = 3 ／ 烈焰棒 = 4
     *   —— 这里**直接用 MITE 自己的常量**（javap 实证：HEAT_LEVEL_WOOD_AND_CHARCOAL=1 /
     *   _COAL=2 / _LAVA=3 / _BLAZE_ROD=4 ✓），不再手写数字 ✓
     * ⚠️ 它只是"**MITE 那边燃料的最大档位**"（拿它去查 MITE 的表 ✓）；
     *   **我们的刻度是燃烧值**（0~10000 ✓），换算见 cm$fuelHeat / cm$smeltCost ✗ 别混用 ✗
     */
    public static final int HEAT_TIERS = net.minecraft.TileEntityFurnace.HEAT_LEVEL_BLAZE_ROD;

    /**
     * ★★ 2026-10-01 用户最终定稿：那个数字改成 **0 / 10000** ✓
     *   —— 这是**池子的容量**（所有大熔炉都一样 ✓）：燃烧值可以一路叠到 10000 存着 ✓
     *   ⚠️ 它和"**能烧什么级别**"是两件事 ✗：
     *     容量   = HEAT_SCALE_MAX = **10000**（谁都一样 ✓）
     *     级别上限 = heatCeiling() = 圆石 **600** ／ 黑曜石 **800** ／ 下界岩 **1000** ✓（用户 ③ ✓）
     */
    public static final int HEAT_SCALE_MAX = 10000;

    public void setValues(int heatCurrent, int cap, int speedRpm) {
        this.heatMax = cap < 1 ? 1 : cap;
        this.heatCurrent = heatCurrent < 0 ? 0 : (heatCurrent > this.heatMax ? this.heatMax : heatCurrent);
        this.speedRpm = speedRpm;
    }

    /**
     * ★★ 2026-10-01 用户 ①：**大熔炉结构被破坏时，里面的东西全掉出来** ✓
     *   · 材料槽 / 燃料槽 / 产物槽 **全掉** ✓
     *   · **热力值不返还** ✗（用户明确说了不返还 ✓ —— 直接清零 ✓）
     *   · 掉完把槽位清空 ⇒ 重新拼起来不会"诈尸" ✓
     *
     * 什么时候调（两处 ✓）：
     *   ① 核心方块被挖掉 —— FurnaceCoreBlock.breakBlock ✓
     *   ② 结构散了（比如拆掉一块机壳）—— FurnaceMultiblock.updateCore 判到失型 ✓
     * ⚠️ 只在服务端跑 ✓（两个调用方本来就都在服务端 ✓）
     */
    public void spillContents() {
        World w = this.worldObj;
        if (w == null || w.isRemote) return;
        boolean any = false;
        for (int i = 0; i < this.slots.length; i++) {
            ItemStack s = this.slots[i];
            if (s == null) continue;
            this.slots[i] = null;
            any = true;
            // 从整机正上方丢出来（核心在正中 ⇒ +2 正好在机器顶上 ✓），带一点随机散布免得叠成一条线 ✓
            double ox = 0.5D + (w.rand.nextDouble() - 0.5D) * 0.6D;
            double oz = 0.5D + (w.rand.nextDouble() - 0.5D) * 0.6D;
            w.spawnEntityInWorld(new net.minecraft.EntityItem(
                    w, this.xCoord + ox, this.yCoord + 2.0D, this.zCoord + oz, s));
        }
        for (int i = 0; i < this.cm$cook.length; i++) this.cm$cook[i] = 0;
        this.cookProgress = 0;
        this.heatCurrent = 0;                    // ★ 热力值**不返还** ✗（用户指定 ✓）
        this.cm$setBurning(false);
        if (any) this.onInventoryChanged();
    }

    // ===================== IInventory =====================

    @Override
    public int getSizeInventory() {
        return this.slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return (i >= 0 && i < this.slots.length) ? this.slots[i] : null;
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (i < 0 || i >= this.slots.length || this.slots[i] == null) return null;
        ItemStack stack = this.slots[i];
        if (stack.stackSize <= n) {
            this.slots[i] = null;
            this.onInventoryChanged();
            return stack;
        }
        ItemStack split = stack.splitStack(n);
        if (stack.stackSize <= 0) this.slots[i] = null;
        this.onInventoryChanged();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        if (i < 0 || i >= this.slots.length) return null;
        ItemStack stack = this.slots[i];
        this.slots[i] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack stack) {
        if (i < 0 || i >= this.slots.length) return;
        this.slots[i] = stack;
        if (stack != null && stack.stackSize > this.getInventoryStackLimit()) {
            stack.stackSize = this.getInventoryStackLimit();
        }
        this.onInventoryChanged();
    }

    // getCustomNameOrUnlocalized / hasCustomName 由 TileEntity 自己 final 实现（不能再覆盖 ✗），
    // 界面标题用 BigFurnaceUi 里传的那个字符串 ✓

    @Override
    public int getInventoryStackLimit() {
        return MAX_STACK;
    }

    @Override
    public void onInventoryChanged() {
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.worldObj != null
                && this.worldObj.getBlockTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
                && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openChest() {}

    @Override
    public void closeChest() {}

    /**
     * ★ 2026-10-01 用户：「燃料槽和材料槽也改成**只能放对应物品**」✓
     *
     *   材料槽 ⇒ **必须真的有熔炼配方** ✓（用 MITE 自己的 doesSmeltingRecipeExistFor ✓，
     *             它就是一句 smeltingList.get(itemID) != null ✓ —— 纯配方表查询 ✓，最准 ✓）
     *   燃料槽 ⇒ **必须真的是燃料** ✓（Item.getHeatLevel(stack) > 0 ✓ —— 这正好就是那 1~4 档的定义 ✓：
     *             木头/木炭 1、煤炭 2、岩浆桶 3、烈焰棒 4 ✓；其它东西一律 0 ⇒ 收不进来 ✓）
     *   产物槽 ⇒ **一律不收** ✓（只出不进 ✓）
     */
    @Override
    public boolean isItemValidForSlot(int i, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (i >= this.productStart()) return false;                       // 产物槽：只出不进 ✓
        if (i == this.fuelSlot()) {                                       // 燃料槽：只收燃料 ✓
            return stack.getItem().getHeatLevel(stack) > 0;
        }
        return net.minecraft.FurnaceRecipes.smelting().doesSmeltingRecipeExistFor(stack);   // 材料槽：只收能烧的 ✓
    }

    @Override
    public void destroyInventory() {
        for (int i = 0; i < this.slots.length; i++) this.slots[i] = null;
    }

    // ===================== NBT =====================

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.casing = tag.getInteger("Casing");
        int count = slotCountFor(this.casing);
        if (this.slots.length != count) this.slots = new ItemStack[count];
        NBTTagList list = tag.getTagList("Items");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = (NBTTagCompound) list.tagAt(i);
            int slot = item.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.slots.length) {
                this.slots[slot] = ItemStack.loadItemStackFromNBT(item);
            }
        }
        // ★ 2026-10-01：热值现在是一份**存住的池子**（用户 ②）⇒ 存盘 ✓
        this.heatCurrent = tag.getInteger("HeatStored");
        if (this.heatCurrent <= 0) this.heatCurrent = tag.getInteger("HeatCur");   // 老存档兼容 ✓
        // ★ 刻度前后换过两轮（4 档 → 1000 → 10000）⇒ 老存档读出来一律夹到合法范围 ✓，
        //   反正池子很快就会被燃料重新填上 ✓（不做花哨的换算 ✗）
        if (this.heatCurrent < 0) this.heatCurrent = 0;
        if (this.heatCurrent > HEAT_SCALE_MAX) this.heatCurrent = HEAT_SCALE_MAX;
        this.heatMax = tag.getInteger("HeatMax");
        this.speedRpm = tag.getInteger("Speed");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Casing", this.casing);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.slots.length; i++) {
            if (this.slots[i] == null) continue;
            NBTTagCompound item = new NBTTagCompound();
            item.setByte("Slot", (byte) i);
            this.slots[i].writeToNBT(item);
            list.appendTag(item);
        }
        tag.setTag("Items", list);
        tag.setInteger("HeatStored", this.heatCurrent);   // ★ 池子里的热值要存住 ✓
        tag.setInteger("HeatMax", this.heatMax);
        tag.setInteger("Speed", this.speedRpm);
    }
}
