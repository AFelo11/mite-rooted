package net.dsh.createmite.kinetics.tile;

import net.dsh.createmite.CMConfig;
import net.dsh.createmite.CMCrushing;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.minecraft.AxisAlignedBB;
import net.minecraft.EntityItem;
import net.minecraft.ItemStack;
import net.minecraft.NBTTagCompound;
import net.minecraft.World;

import java.util.List;

/**
 * 粉碎轮的方块实体：**上方吸取 → 夹碎 → 下方吐出**。
 *
 * 【为什么不继承石磨】石磨的 updateEntity 里就是"磨粉"逻辑，
 * Java 又没有"跨过父类调祖父类"的写法，继承会把磨粉一起带进来。
 * 所以这里直接继承 {@link KineticTileEntity}，自己写一小段（吸取/产出那几招照石磨抄）。
 *
 * 【配对条件】同一轴上、隔着 1 格（或紧邻）有另一个粉碎轮。
 * 两个轮子都转起来才开始粉碎 —— 和 Create 的"双轮夹碎"一致。
 *
 * 【配方】见 CMCrushing。没配方的物品会被**原样吐回**，不会凭空消失。
 */
public class CrushingWheelTileEntity extends KineticTileEntity {

    /** 输入槽：一次能囤 **16 个**（需求设定），加工时一个一个消耗 */
    public ItemStack input;
    /** 产物**立刻从下方吐出**，不留在槽里（所以没有 output 槽） */
    public int progress;

    /** 配对成功后记下"两轮中间那一格"（缝隙）的坐标；没配对时全 0 */
    private int gapX;
    private int gapY;
    private int gapZ;
    private boolean hasGap;

    /** 输入槽上限 */
    public static final int INPUT_LIMIT = 16;

    public CrushingWheelTileEntity() {}

    public static int progressRequired() {
        return (int) CMConfig.getFloat("crusher.progress_required", 120.0F);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();                                  // 求解转速（网络 v2）
        World world = this.worldObj;
        if (world == null || world.isRemote) return;

        // ★ 必须**先配对**再吸取！
        //   配对时才会算出"缝隙那一格"的位置（gapX/gapY/gapZ），
        //   而吸取范围是以缝隙为中心的 —— 之前把配对放在下面，
        //   又被 "if (input == null) return;" 提前返回挡掉，导致 hasGap 永远是 false、
        //   吸取直接跳过（表现就是"完全不吸材料"）。
        this.findPartner();
        this.absorbFromAbove();
        if (this.input == null) return;

        // 没转速 → 停在这一格等，progress 原样保留
        if (Math.abs(this.speed) < 1.0F) return;
        if (!this.hasGap) return;                              // 没配对（或转向不配合）就不工作

        CMCrushing.Recipe recipe = CMCrushing.get(this.input);
        if (recipe == null) {
            this.progress = 0;
            this.spitBelow(this.input);                        // 没配方：原样还回去，不吞
            this.input = null;
            return;
        }

        // ★ 处理时间按**配方自己的 processingTime**（原版就是每个配方一个值，如铁矿石 250）。
        //   转速越高每 tick 推进越多 —— 保留"转速快就磨得快"的手感，但配方的相对快慢由表决定。
        this.progress += Math.max(1, (int) (Math.abs(this.speed) / 8.0F));
        if (this.progress < recipe.processingTime) return;

        // ★ 先留一份"被吃掉的这一个"的副本：额外产出要读它的**元数据**
        //   （下界金矿石要掉地狱岩，普通金矿石掉圆石 ✓）
        final net.minecraft.ItemStack consumedInput = new net.minecraft.ItemStack(
                this.input.itemID, 1, this.input.getItemSubtype());

        this.progress = 0;
        this.input.stackSize--;                                 // 一次只消耗 1 个（输入槽最多囤 16）
        if (this.input.stackSize <= 0) this.input = null;

        this.spitBelow(recipe.createOutput());
        // 额外产出：**每一条独立判定一次** ✓
        // （2026-09-28 起配方可以带两条：如"25% 再出一个粉碎铁矿石 + 12% 一个圆石"）
        //   ★ 要把**原料本身**传进去：下界金矿石的"石头类掉落"是地狱岩而不是圆石 ✓
        java.util.List<net.minecraft.ItemStack> bonuses =
                CMCrushing.rollBonuses(recipe, consumedInput, world.rand);
        for (int i = 0; i < bonuses.size(); i++) {
            this.spitBelow(bonuses.get(i));
        }
        world.playSoundEffect((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D,
                (double) this.zCoord + 0.5D, "random.pop", 0.35F, 1.5F);
    }

    /**
     * 旁边有没有**配对的**粉碎轮：两个轮子**轴平行**、沿垂直于轴的方向错开 1~2 格（面对面）。
     *
     * 【必须枚举四个方向】之前那版用了「方向编号」的猜法（dir==4 是 +X、dir==5 是 -X…），
     * 结果 -X 那条分支根本传不到，配对判定半个方向是瞎的。
     * 现在直接按轴列出垂直于轴的四个偏移量，正负都覆盖，不依赖任何编号约定。
     */
    private boolean hasPartner() {
        return this.findPartner() != null;
    }

    /**
     * 找配对的那个粉碎轮 —— **完全按原版条件**（资料 196534）：
     *   ① 二者**仅间隔 1 个方块**（也就是格距正好 2，中间那一格是缝隙）；
     *   ② **中间那一格未被阻挡**（必须是空气，否则料进不去）；
     *   ③ **轮面平行**（两者轴相同）；
     *   ④ **转向互相配合**（见 directionsMesh —— 不是单纯一正一反）。
     *
     * @return 配对成功的那个粉碎轮；不满足条件返回 null
     */
    private CrushingWheelTileEntity findPartner() {
        this.hasGap = false;
        World world = this.worldObj;
        if (world == null) return null;

        int axis = this.getBlockMetadata() & 3;
        int[][] offsets = axis == 0
                ? new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}
                : axis == 1
                        ? new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}
                        : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

        for (int i = 0; i < offsets.length; i++) {
            int ox = offsets[i][0];
            int oy = offsets[i][1];
            int oz = offsets[i][2];

            // ② 中间那一格必须是空的（空气），否则料下不去
            int gapId = world.getBlockId(this.xCoord + ox, this.yCoord + oy, this.zCoord + oz);
            if (gapId != 0) continue;

            // ① 正好隔 1 格 → 对面在 2 格外
            int tx = this.xCoord + ox * 2;
            int ty = this.yCoord + oy * 2;
            int tz = this.zCoord + oz * 2;
            net.minecraft.TileEntity te = world.getBlockTileEntity(tx, ty, tz);
            if (!(te instanceof CrushingWheelTileEntity)) continue;
            CrushingWheelTileEntity p = (CrushingWheelTileEntity) te;
            // ★ 防御：MITE 在"方块实体 tick 期间"懒创建出来的 TE 可能还没挂进世界
            //   （见 KineticTileEntity.hasWorld 的说明）。这种半成品读 metadata 会 NPE，
            //   而且它也不该被当成一个"配对好的粉碎轮" —— 直接跳过。
            if (!p.hasWorld()) continue;

            // ③ 轮面平行 = 两者轴相同
            if ((p.getBlockMetadata() & 3) != axis) continue;

            // ★ 缝隙位置**只依赖几何**（正好隔 1 格 + 中间空 + 轴平行）——
            //   转向咬合是"能不能加工"的条件，不该影响"能不能吸料"。
            //   （上一版把 hasGap 放在转向判定之后，方向没配好就完全不吸 ✗）
            this.gapX = this.xCoord + ox;
            this.gapY = this.yCoord + oy;
            this.gapZ = this.zCoord + oz;
            this.hasGap = true;

            // ④ 转向要"朝中间咬合"才算真正的加工配对
            if (!this.directionsMesh(p, ox, oy, oz)) return null;

            return p;
        }
        return null;
    }

    /**
     * 两轮转向是否**朝中间咬合** —— 原版要求"旋转方向必须相互配合"，不是单纯一正一反。
     *
     * 【怎么判定】把两轮在**缝隙那一侧**的轮面速度算出来，要求它们**都朝下**
     * （料从上方进、被往下拽 ✓）。轮面速度 = ω × r：
     *   - 本轮的缝隙侧在 +d 方向 → v_self = speed · (轴 × d) 的 Y 分量
     *   - 对面的缝隙侧在 −d 方向 → v_other = −speed' · (轴 × d) 的 Y 分量
     * 所以"两个都朝下"就等价于：**速度符号相反，且谁的符号是哪个由 (轴 × d) 的 Y 分量决定** ——
     * 这正是"左轮往右转、右轮往左转"的含义 ✓
     *
     * 竖直轴（轮面水平）时缝隙侧轮面速度是水平的，不存在"往下拽"，此时只要求两轮转向相反。
     */
    private boolean directionsMesh(CrushingWheelTileEntity p, int dx, int dy, int dz) {
        int axis = this.getBlockMetadata() & 3;

        double crossY;
        if (axis == 0) {
            crossY = -dz;          // (1,0,0) × d 的 Y 分量
        } else if (axis == 1) {
            crossY = 0.0D;         // (0,1,0) × d 的 Y 分量恒为 0
        } else {
            crossY = dx;           // (0,0,1) × d 的 Y 分量
        }

        if (Math.abs(crossY) < 1.0E-6D) {
            return this.speed * p.speed < 0.0F;      // 竖直轴：只要转向相反
        }

        float vSelf = (float) ((double) this.speed * crossY);
        float vOther = (float) ((double) -p.speed * crossY);
        return vSelf < 0.0F && vOther < 0.0F;        // 两个都朝下 = 把料往下拽
    }

    /** 从**上方那一格**吸一个掉落物（和石磨同一套：一次只吸一个，吸满就停） */
    private void absorbFromAbove() {
        World world = this.worldObj;
        if (world == null || !this.hasGap) return;
        if (this.input != null && this.input.stackSize >= INPUT_LIMIT) return;   // 装满 16 个就停

        // ★ 进料口 = **两轮中间那一格（缝隙）的上方**，不是轮子本体上方
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                this.gapX, this.gapY + 1, this.gapZ,
                this.gapX + 1, this.gapY + 2, this.gapZ + 1);
        List list = world.getEntitiesWithinAABB(EntityItem.class, box);
        if (list == null || list.isEmpty()) return;

        EntityItem item = (EntityItem) list.get(0);
        ItemStack stack = item.getEntityItem();
        if (stack == null) return;
        if (!CMCrushing.hasRecipe(stack)) return;               // 没配方的不吸，免得吞掉玩家丢的东西

        // 同种物品就往输入槽里叠，一直叠到 16 个
        if (this.input != null
                && (this.input.itemID != stack.itemID || this.input.getItemSubtype() != stack.getItemSubtype())) {
            return;                                             // 槽里是别的东西 → 不混装
        }
        int room = INPUT_LIMIT - (this.input == null ? 0 : this.input.stackSize);
        int take = Math.min(room, stack.stackSize);
        if (take <= 0) return;
        if (this.input == null) {
            this.input = new ItemStack(stack.itemID, take, stack.getItemSubtype());
        } else {
            this.input.stackSize += take;
        }
        stack.stackSize -= take;
        if (stack.stackSize <= 0) item.setDead();
    }

    /** 从**下方**吐出一个物品实体 */
    private void spitBelow(ItemStack stack) {
        World world = this.worldObj;
        if (world == null || stack == null) return;
        // ★ 出料口 = **两轮中间那一格（缝隙）的下方**
        double ox = (this.hasGap ? this.gapX : this.xCoord) + 0.5D;
        double oy = (this.hasGap ? this.gapY : this.yCoord) - 0.3D;
        double oz = (this.hasGap ? this.gapZ : this.zCoord) + 0.5D;
        EntityItem item = new EntityItem(world, ox, oy, oz, stack);
        item.motionX = 0.0D;
        item.motionY = -0.1D;
        item.motionZ = 0.0D;
        world.spawnEntityInWorld(item);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("cm_progress", this.progress);
        if (this.input != null) {
            NBTTagCompound t = new NBTTagCompound();
            this.input.writeToNBT(t);
            nbt.setCompoundTag("cm_input", t);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.progress = nbt.getIntegerWithDefault("cm_progress", 0);
        if (nbt.hasKey("cm_input")) this.input = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("cm_input"));

    }
}
