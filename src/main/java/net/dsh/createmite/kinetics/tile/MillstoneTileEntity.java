package net.dsh.createmite.kinetics.tile;

import net.dsh.createmite.kinetics.KineticHelper;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.minecraft.AxisAlignedBB;
import net.minecraft.EntityItem;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.NBTTagCompound;

import java.util.List;

/**
 * 石磨的方块实体。
 *
 * == 玩法（2026-09-24 改版）==
 *  1. **只能被齿轮带动**：水平相邻、轴向同为竖直（Y）的齿轮才啮合（见 KineticHelper.connects）。
 *     传动轴、手摇曲柄都带不动它。想用手摇曲柄就用"曲柄 → 齿轮 → 石磨"这条链路。
 *  2. **投料只能靠丢，而且是秒吸**：物品进入石磨**正上方那一格**就被吃掉
 *     （不看原版的 delayBeforeCanPickup 保护期）。
 *     - **一次只收一个**：手上有货就不再收（`input` 恒为 1 个），磨完才收下一个；
 *     - 只吸"有研磨配方"的东西，其它物品留在原地（石磨不当垃圾桶）。
 *  3. **半途取不出来**：原料槽不对外暴露，空手右键只拿得走成品。
 *     断电就卡在里面，重新供能接着磨。
 *  4. **空手右键取成品**：产物堆在成品槽里，直接进背包，背包满了掉在**玩家脚下**
 *     （不是石磨头顶 —— 秒吸之下掉头顶会被自己立刻吃回去）。
 *  5. 成品槽满了就**停止吃料**，而且石磨**永远不会往外喷产物** ——
 *     秒吸之下，喷在头顶的东西会被自己立刻吃回去（砾石/圆石还会被反复研磨）。
 *
 * == 为什么要有"输入槽 + 输出槽"两个槽 ==
 * 原来磨完是直接把产物喷到地上的，那样既没法"攒着一次取"，也会让
 * 砾石/圆石这类"既是产物又是原料"的东西掉回石磨上方被反复研磨。
 */
public class MillstoneTileEntity extends KineticTileEntity {

    /** 正在磨的那一份（原料）——不对外暴露，所以磨到一半取不出来 */
    public ItemStack input;
    /** 当前这一份的研磨进度 */
    public int progress;
    /**
     * 已经磨好、等着被取走的成品（**多件、可混装**）。
     *
     * 【为什么从"单个 ItemStack"改成"一串"】2026-09-28 用户要求给研磨加**额外产出**：
     *   小麦一份可能同时出「面粉」和「小麦种子」两种东西 —— 原来的成品槽只认**一种**
     *   （canAcceptOutput 里种类不同就判满 ✗），种子根本没地方放，石磨会直接卡死不动 ✗。
     *   现在改成"**最多 8 件、可以混装**" ✓（用户原话："你可以修改输出槽至8个"）。
     */
    public final java.util.List<ItemStack> outputs = new java.util.ArrayList<ItemStack>();

    /** 成品槽上限：**8 件**（用户指定） */
    public static final int OUTPUT_LIMIT = 8;

    /** 上一次"吸进原料却没动力"提醒的世界 tick（10 秒冷却，免得刷屏） */
    private long lastNoPowerHint = Long.MIN_VALUE;

    /**
     * 磨完一份需要的进度点数（**默认值**，实际从 config/createmite.properties 读
     * 键 millstone.progress_required 覆盖，改完重启生效，不用重编译）。
     *
     * 【这个数是按"手摇曲柄摇几次出一份"倒推出来的】
     *   手摇曲柄：48 RPM，40 tick 线性衰减到 0。
     *   石磨每 tick 进度 += max(1, |speed| / 8)，
     *   于是**摇一次**累计约 Σ(k=39..1) max(1, floor(48*k/40/8)) ≈ **104 点**。
     *   所以：
     *     104 点 → 摇 1 次出一份
     *     400 点 → 摇 4 次出一份 ← 当前默认值
     *     800 点 → 摇 8 次出一份
     *   换算公式：**要摇几次 ≈ 这个数 ÷ 104**。
     *
     * 这个数对**所有**动力源生效：将来接水车之类的持续动力，
     * 出一份的时间 = 这个数 / (每 tick 进度)，48 RPM 下 400 点约 3.4 秒一份。
     */
    public static final int DEFAULT_PROGRESS_REQUIRED = 400;

    /**
     * 磨一份要多少 tick —— **原版公式**：t = 80 / RPM 秒，即 ceil(1600 / RPM) ticks。
     * RPM 取当前转速（绝对值的下限 1，避免除零）。
     */
    public int requiredTicks() {
        float rpm = Math.max(1.0F, Math.abs(this.speed));
        return Math.max(1, (int) Math.ceil(1600.0D / (double) rpm));
    }

    /** 旧的配置写法保留给 HUD 显示用（现在按公式算，不再读配置） */
    public static int progressRequired() {
        return DEFAULT_PROGRESS_REQUIRED;
    }

    public MillstoneTileEntity() {}

    /** ★ 石磨只认齿轮：传动轴 / 手摇曲柄一律接不上 */
    @Override
    public boolean needsGearDrive() {
        return true;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();   // 求解转速
        if (this.worldObj == null || this.worldObj.isRemote) return;

        this.absorbDroppedItems();

        if (this.input == null) return;
        // 没动力 → 停在这一格等：**progress 原样保留**，重新供能后接着磨，不清零。
        if (Math.abs(this.speed) < 1.0F) return;
        if (this.isOutputBlocked()) return;                                   // 成品槽满 → 停止吃料

        // ★ 研磨耗时改用**原版公式**（资料 227794）：t = 80 / RPM 秒 = ceil(1600 / RPM) ticks。
        //   （资料还写了 ceil(100 × floor(16/RPM))，但那个在 RPM > 16 时 floor 变 0 会失效；
        //     两者在 RPM ≤ 16 完全一致，所以取连续的这一版。）
        //   32 RPM（手摇曲柄）→ 50 ticks = 2.5 秒 → **正好 5 次右击磨一份** ✓
        this.progress += 1;
        if (this.progress < requiredTicks()) return;

        // 先把**这一批产物（基础 + 额外产出）整套**算出来：只有成品槽真的收得下，才动原料。
        // 否则会出现"原料被吃掉、产物却没地方放"的丢东西 bug ✗。
        // 【为什么必须整套判】额外产出是随机骰子：只判基础产物的话，骰子中了却没地方放 → 会丢产物 ✗
        //   所以这里"装不下就停在这一 tick"，等玩家把成品取走再继续磨 ✓（进度保留、原料不消耗）✓
        java.util.List<ItemStack> batch = this.resultsFor(this.input, true);
        if (batch.isEmpty()) {
            this.progress = 0;
            this.input = null;
            return;
        }
        int need = 0;
        for (int i = 0; i < batch.size(); i++) need += batch.get(i).stackSize;
        if (!this.canAcceptOutput(need)) return;   // 成品槽装不下这一批 → 停在这，不消耗原料、不丢东西

        this.progress = 0;
        this.input.stackSize--;
        if (this.input.stackSize <= 0) this.input = null;
        for (int i = 0; i < batch.size(); i++) this.addOutput(batch.get(i));
        this.worldObj.playSoundEffect((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D,
                (double) this.zCoord + 0.5D, "random.pop", 0.35F, 1.5F);
    }

    /** 成品槽现在一共装了多少**件**（跨种类合计） */
    public int outputCount() {
        int n = 0;
        for (int i = 0; i < this.outputs.size(); i++) n += this.outputs.get(i).stackSize;
        return n;
    }

    /** 成品槽还吃得下这么多件吗（**同种叠加、异种也收**，只要总件数不超过 8） */
    private boolean canAcceptOutput(int count) {
        if (count <= 0) return true;
        return this.outputCount() + count <= OUTPUT_LIMIT;
    }

    /** 成品槽满了 —— 此时不再吃料（绝不往外吐产物） */
    private boolean isOutputBlocked() {
        if (this.outputCount() >= OUTPUT_LIMIT) return true;
        ItemStack probe = this.input;
        if (probe == null) return false;
        // 预判只看**必定产出**（不摇骰子，免得白白消耗随机数、也免得提前卡住）✓
        java.util.List<ItemStack> would = this.resultsFor(probe, false);
        int need = 0;
        for (int i = 0; i < would.size(); i++) need += would.get(i).stackSize;
        return !this.canAcceptOutput(need);
    }

    /**
     * 这一份原料磨完会出什么。
     *
     * · 基础产物来自 {@link KineticHelper#grind}（目前是 小麦 → 面粉）✓
     * · **额外产出**照原版 Create 的 milling 配方结构（一条必定结果 + 若干带概率的额外结果）✓
     *   小麦：**25% 再多出 1 个面粉** ＋ **50% 出 1 个小麦种子**（用户 2026-09-28 指定）✓
     *
     * @param roll false = 只算必定产出（用于"还吃得下吗"的预判，不动随机数）✓
     */
    private java.util.List<ItemStack> resultsFor(ItemStack in, boolean roll) {
        java.util.ArrayList<ItemStack> out = new java.util.ArrayList<ItemStack>();
        ItemStack base = KineticHelper.grind(in);
        if (base == null) return out;
        out.add(base);
        if (!roll || this.worldObj == null || this.worldObj.rand == null) return out;

        java.util.Random rnd = this.worldObj.rand;
        if (in.itemID == net.minecraft.Item.wheat.itemID) {
            // 小麦：25% 再多出 1 个面粉 + 50% 出 1 个小麦种子 ✓（用户指定）
            if (rnd.nextFloat() < 0.25F) out.add(new ItemStack(net.minecraft.Item.flour, 1, 0));
            if (rnd.nextFloat() < 0.50F) out.add(new ItemStack(net.minecraft.Item.seeds, 1, 0));
        } else if (in.itemID == net.minecraft.Item.bone.itemID) {
            // 骨头：**30% 额外多出 1 个骨粉** ✓（2026-09-28 用户指定；
            //   MITE 自带的"骨头 → 骨粉 ×3"合成配方已删，骨粉只能磨 ✓）
            if (rnd.nextFloat() < 0.30F) out.add(new ItemStack(net.minecraft.Item.dyePowder, 1, 15));
        }
        return out;
    }

    /**
     * 吸收石磨**正上方那一格**里的掉落物。
     *
     * 【两个刻意的限制】
     *  1. **一次只收一个**：`input` 恒为 1 个，手上有货就完全不再收（"收一个就满"）。
     *     磨完一个才收下一个，所以丢一整叠小麦也是"一个一个喂"。
     *  2. **秒吸**：不看原版的 delayBeforeCanPickup（那是给玩家捡东西用的 0.5 秒保护期）。
     *
     * 【范围为什么是"整格"而不是"贴着顶面的一小块"】
     * 试过把范围收窄到贴着磨盘顶面，结果物品经常正好擦着边落在地上、永远不被吸，
     * 反而更难用，所以退回整格。
     *
     * ⚠️ 「一次只收一个」有个副作用：原料卡在里面时（没动力磨、又取不出来），
     *    石磨就再也不吸了。这是刻意的（原料槽不对玩家暴露），**拆掉石磨能把原料拿回来**；
     *    空手右键石磨会报当前状态，能看出是不是卡着原料。
     */
    private void absorbDroppedItems() {
        if (this.input != null) return;          // ★ 收一个就满：手上还有货就不再收
        if (this.isOutputBlocked()) return;

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                (double) this.xCoord, (double) (this.yCoord + 1), (double) this.zCoord,
                (double) (this.xCoord + 1), (double) (this.yCoord + 2), (double) (this.zCoord + 1));
        List found = this.worldObj.getEntitiesWithinAABB(EntityItem.class, box);
        if (found == null || found.isEmpty()) return;

        for (int i = 0; i < found.size(); i++) {
            if (!(found.get(i) instanceof EntityItem)) continue;
            EntityItem entity = (EntityItem) found.get(i);

            ItemStack stack = entity.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;
            if (KineticHelper.grind(stack) == null) continue;   // 磨不了 → 不收

            // ★ 只拿一个：掉落物剩多少不影响，石磨这一格只装 1 个
            this.input = new ItemStack(stack.itemID, 1, stack.getItemSubtype());
            stack.stackSize--;
            if (stack.stackSize <= 0) entity.setDead();
            else entity.setEntityItemStack(stack);

            this.worldObj.playSoundEffect((double) this.xCoord + 0.5D, (double) this.yCoord + 1.1D,
                    (double) this.zCoord + 0.5D, "random.pop", 0.3F, 1.7F);
            this.hintIfNoPower();
            return;
        }
    }

    /**
     * 吸进原料、但石磨根本没在转时，给最近的玩家一句提示。
     *
     * 为什么需要它：石磨的转子藏在壳子里，**从外面看不出它到底转没转**；
     * 而"齿轮没啮合 / 没接动力"这种失败是**完全静默**的 ——
     * 玩家只会觉得"我丢进去了，但什么都不发生"，然后开始怀疑是 bug。
     *
     * 10 秒冷却，避免刷屏；用 /M 0 可以整体关掉。
     */
    private void hintIfNoPower() {
        if (Math.abs(this.speed) >= 1.0F) return;
        long now = this.worldObj.getTotalWorldTime();
        if (this.lastNoPowerHint != Long.MIN_VALUE && now - this.lastNoPowerHint < 200L) return;

        List players = this.worldObj.getEntitiesWithinAABB(EntityPlayer.class,
                AxisAlignedBB.getBoundingBox(
                        (double) this.xCoord - 8.0D, (double) this.yCoord - 8.0D, (double) this.zCoord - 8.0D,
                        (double) this.xCoord + 9.0D, (double) this.yCoord + 9.0D, (double) this.zCoord + 9.0D));
        if (players == null || players.isEmpty()) return;
        this.lastNoPowerHint = now;

        for (int i = 0; i < players.size(); i++) {
            if (!(players.get(i) instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) players.get(i);
            if (!net.dsh.createmite.CMHints.enabled(p)) continue;
            p.addChatMessage("§7[石磨] §e吸进原料了，但石磨没有转§7 — 它只能被"
                    + "「水平相邻、轴向也为竖直」的齿轮带动（传动轴 / 手摇曲柄直接接上来是无效的）。"
                    + " 空手右键石磨可以查看状态，关掉本提示：§f按 V 键（机械动力提示开关）");
        }
    }

    /**
     * 把一份产物并进成品槽。
     *
     * 调用前必须先用 canAcceptOutput 确认放得下 —— 石磨**永远不会往外吐产物**，
     * 因为改成"秒吸"之后，吐在头顶上的东西会立刻被自己吸回去（砾石/圆石还会被反复研磨）。
     */
    private void addOutput(ItemStack out) {
        if (out == null || out.stackSize <= 0) return;
        // 同种就叠加，异种就再占一格 ✓（上限由 canAcceptOutput 统一把关）
        for (int i = 0; i < this.outputs.size(); i++) {
            ItemStack s = this.outputs.get(i);
            if (s.itemID == out.itemID && s.getItemSubtype() == out.getItemSubtype()) {
                s.stackSize += out.stackSize;
                return;
            }
        }
        this.outputs.add(out.copy());
    }

    /**
     * 空手右键：把成品交给玩家。
     *
     * @return 是否真的取到了东西（没东西可取就返回 false，让别的交互有机会处理这次右键）
     */
    public boolean ejectOutput(EntityPlayer player) {
        if (this.outputs.isEmpty()) return false;

        // 成品槽现在可能装着**好几种**东西（面粉 + 种子）→ 一件一件交出去 ✓
        for (int i = 0; i < this.outputs.size(); i++) {
            // 进背包；背包满了会自动掉在**玩家所在位置**（不是石磨头顶 ——
            // 石磨现在是秒吸，掉在头顶会被自己立刻吃回去）
            player.inventory.addItemStackToInventoryOrDropIt(this.outputs.get(i).copy());
        }
        this.outputs.clear();
        player.inventory.onInventoryChanged();

        this.worldObj.playSoundEffect((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D,
                (double) this.zCoord + 0.5D, "random.pop", 0.4F, 1.3F);
        return true;
    }

    /**
     * 把石磨当前状态报到玩家的聊天栏。
     *
     * 石磨没有 GUI，而且有两类失败是**全静默**的：
     *   - 齿轮没啮合（比如把齿轮贴在石磨侧面、或者用了传动轴/曲柄直连）→ 转速 0；
     *   - 应力过载 → 整张网络停转。
     * 这两种情况从外面看都是"什么都没发生"，光靠等是等不出来的。所以给一个能问的地方：
     * **空手右键石磨**，有成品就给成品，没有成品就报状态。
     */
    public void reportStatus(EntityPlayer player) {
        float spd = Math.abs(this.speed);
        StringBuilder sb = new StringBuilder("§7[石磨] §f转速 ");
        sb.append(String.format("%.1f", spd)).append(" RPM");

        if (this.input != null) {
            sb.append("；原料 ").append(this.input.getDisplayName())
              .append("（进度 ").append(this.progress).append('/').append(progressRequired()).append("）");
        } else {
            sb.append("；原料 空");
        }
        if (!this.outputs.isEmpty()) {
            sb.append("；成品 ");
            for (int i = 0; i < this.outputs.size(); i++) {
                ItemStack s = this.outputs.get(i);
                if (i > 0) sb.append(" + ");
                sb.append(s.getDisplayName()).append(" x").append(s.stackSize);
            }
            sb.append("（").append(this.outputCount()).append('/').append(OUTPUT_LIMIT).append("）");
        }
        if (spd < 1.0F && this.input != null) {
            sb.append(this.overStressed
                    ? "  §c→ 应力过载，整张网络停转了（拆掉多余的机器，或调 config/createmite.properties）"
                    : "  §c→ 没有动力：石磨只认“水平相邻、轴向也为竖直”的齿轮；传动轴和手摇曲柄直接接上来是转不动的");
        }
        player.addChatMessage(sb.toString());
    }

    /**
     * 把物品吐在石磨位置（**只用在"拆掉石磨"这一条路上**，那时石磨已经不存在了，
     * 所以不存在被自己秒吸回去的问题）。
     *
     * delayBeforeCanPickup = 10 是给**玩家**捡东西用的原版保护期（约 0.5 秒），
     * 石磨自己不看这个值。
     */
    public void dropStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || this.worldObj == null) return;
        EntityItem entity = new EntityItem(this.worldObj,
                (double) this.xCoord + 0.5D, (double) this.yCoord + 1.0D, (double) this.zCoord + 0.5D, stack);
        entity.delayBeforeCanPickup = 10;
        entity.motionY = 0.1D;
        this.worldObj.spawnEntityInWorld(entity);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("cm_progress", this.progress);
        if (this.input != null) {
            NBTTagCompound tag = new NBTTagCompound();
            this.input.writeToNBT(tag);
            nbt.setCompoundTag("cm_input", tag);
        }
        // 成品槽：用"计数 + cm_output_0/1/2..."存（最多 8 件）——
        // 比 NBTTagList 少一层 API 名字的风险 ✓，条目少、可读性好 ✓
        nbt.setInteger("cm_output_count", this.outputs.size());
        for (int i = 0; i < this.outputs.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            this.outputs.get(i).writeToNBT(tag);
            nbt.setCompoundTag("cm_output_" + i, tag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.progress = nbt.getIntegerWithDefault("cm_progress", 0);
        if (nbt.hasKey("cm_input")) {
            this.input = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("cm_input"));
        }
        if (nbt.hasKey("cm_output")) {
            // 成品槽（新格式）—— 顺便把老的"单件 cm_output"迁移过来 ✓
        this.outputs.clear();
        int outCount = nbt.hasKey("cm_output_count") ? nbt.getInteger("cm_output_count") : 0;
        for (int i = 0; i < outCount; i++) {
            String key = "cm_output_" + i;
            if (!nbt.hasKey(key)) continue;
            ItemStack s = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(key));
            if (s != null && s.stackSize > 0) this.outputs.add(s);
        }
        if (this.outputs.isEmpty() && nbt.hasKey("cm_output")) {
            ItemStack old = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("cm_output"));
            if (old != null && old.stackSize > 0) this.outputs.add(old);
        }
        }
    }
}
