package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 封装箱（Encased）—— 被机壳包住的**传动杆 / 齿轮 / 大齿轮**。
 *
 * ============================ 为什么是**独立方块**，而不是"原方块 + 一个标记位" ============================
 * 资料（396859/396860 传动杆箱、857833/857843 齿轮箱、857834/857844 大齿轮箱）里，
 * 这**六个**（安山/黄铜 × 传动杆/齿轮/大齿轮）都是**各自独立的方块与物品** ✓。
 *
 * ✘ 我第一版做成了"原方块 + metadata 标记位（bit3=黄铜）"✗ —— 结果**实战失败**：
 *   取数日志实证 —— 服务端明明写对了（meta=13 = 轴1 + 封装位4 + 黄铜位8 ✓），
 *   但**客户端只读到 5**（丢了 bit3 ✓）→ 客户端永远按安山渲染 ✓。
 *   即 **bit3 不能可靠同步到客户端**（和当年"水车方向位"踩的是同一个坑 ✗）。
 * 而 metadata 只有 bit0-2 能可靠同步 ✓，轴要占 2 bit ✗ → **塞不下第二个标记** ✗。
 * 所以：**信息全部放进"方块身份"** ✓ —— 方块身份是一定会同步的 ✓✓，问题自然消失 ✓。
 *
 * ============================ 状态 ============================
 * metadata **只存自转轴（bit0-1）** ✓；"是哪种机构""哪种机壳"由**本类的字段/方块**决定 ✓。
 *
 * ============================ 获取方式（按资料）============================
 * · 生存不可合成 ✓（原版是创造模式物品，v0.4 起连创造物品栏都移除了 ✓）
 * · 用法：手持机壳右键**原方块** → 替换成对应的封装箱 ✓
 * · 解除：**扳手潜行右击** → 换回原方块 ✓
 * · **直接破坏只掉原方块** ✓（不掉机壳 ✓，资料原文）
 */
public class BlockEncased extends BlockKineticBase {

    /** 被封在里面的机构：0 = 传动杆、1 = 齿轮、2 = 大齿轮 */
    public static final int VARIANT_SHAFT = 0;
    public static final int VARIANT_COGWHEEL = 1;
    public static final int VARIANT_LARGE_COGWHEEL = 2;

    /**
     * **正在换壳**（封装/解除）时为 true ✓。
     *
     * 【为什么必须有】封装与解除都是"用 setBlock 换成另一个方块" ✓ ——
     * 而 setBlock **会先移除旧方块** ✓ → 触发 {@link #breakBlock} → 掉出里面的原方块 ✗
     * → 玩家每拆一次壳就白得一个传动杆/齿轮 ✗（用户实测："扳手卸载机壳时掉落原方块" ✓）。
     * 换壳期间把本位置 true，breakBlock 就不掉东西 ✓。
     */
    public static boolean swappingShell = false;

    private final int variant;
    private final boolean brass;

    public BlockEncased(int blockID, int variant, boolean brass) {
        super(blockID, Material.iron, machineConstants());
        this.variant = variant;
        this.brass = brass;
        this.applyMachineDefaults(name(variant, brass), "createmite_blank");

        // ★★ 2026-09-28 用户要求：**这六个封装箱不许出现在创造模式物品栏里** ✗
        //   applyMachineDefaults() 会给所有机器方块统一 setCreativeTab(tabBlock) ✓ ——
        //   于是安山/黄铜 × 传动杆/齿轮/大齿轮 这六个"空白方块"就一直躺在创造栏里 ✗
        //   （用户实测：「它们现在正常显示在了创造物品栏，但是它应该不显示才对」）。
        //   资料（396859/857833/857834）原文：它们是**创造模式物品**，v0.4 起**连创造物品栏都移除** ✓，
        //   获取方式只有"手持机壳右键原方块" ✓ → 这里把展示栏置空 ✓。
        //   【安全性】反汇编核实：Block.setCreativeTab(t) 就是 displayOnCreativeTab = t 一个字段赋值 ✓
        //   （remappedJar 1.6.4-MITE.jar-3.4.2：putfield Field displayOnCreativeTab）→ 传 null 安全 ✓。
        //   【副作用】启动日志会多出六行
        //     No creative tab for [2327] class ...BlockEncased —— 这是**预期的** ✓
        //     （两个占位方块 2322/2323 一直是这么干的 ✓），不是错误 ✗。
        this.setCreativeTab(null);
    }

    /** 内部名称（同时用于贴图查找与翻译键 ✓）*/
    public static String name(int variant, boolean brass) {
        String c = brass ? "brass" : "andesite";
        if (variant == VARIANT_COGWHEEL) return c + "_encased_cogwheel";
        if (variant == VARIANT_LARGE_COGWHEEL) return c + "_encased_large_cogwheel";
        return c + "_encased_shaft";
    }

    /** 被封在里面的**原方块**（破坏封装箱时掉它 ✓ —— 资料："直接破坏只会掉落传动杆本身"）*/
    public static net.minecraft.Block baseBlockFor(int variant) {
        if (variant == VARIANT_COGWHEEL) return net.dsh.createmite.CMBlocks.blockCogwheel;
        if (variant == VARIANT_LARGE_COGWHEEL) return net.dsh.createmite.CMBlocks.blockLargeCogwheel;
        return net.dsh.createmite.CMBlocks.blockShaft;
    }

    /** 原方块 -> 对应的封装箱（用手里的机壳种类决定安山/黄铜 ✓）；不是可封装的方块就返回 null */
    public static BlockEncased encasedFor(net.minecraft.Block base, boolean brass) {
        if (base == net.dsh.createmite.CMBlocks.blockShaft) {
            return brass ? net.dsh.createmite.CMBlocks.encBrassShaft : net.dsh.createmite.CMBlocks.encAndesiteShaft;
        }
        if (base == net.dsh.createmite.CMBlocks.blockCogwheel) {
            return brass ? net.dsh.createmite.CMBlocks.encBrassCog : net.dsh.createmite.CMBlocks.encAndesiteCog;
        }
        if (base == net.dsh.createmite.CMBlocks.blockLargeCogwheel) {
            return brass ? net.dsh.createmite.CMBlocks.encBrassLargeCog : net.dsh.createmite.CMBlocks.encAndesiteLargeCog;
        }
        return null;
    }

    /**
     * 破坏封装箱 → **只掉里面的原方块** ✓（资料 396859/857833/857834 原文：
     * "直接将其破坏只会掉落传动杆/齿轮本身" ✓）—— 不掉机壳 ✓。
     */
    @Override
    public void breakBlock(net.minecraft.World world, int x, int y, int z, int blockID, int meta) {
        if (!world.isRemote && !swappingShell) {   // ★ 换壳期间不掉落 ✓
            net.minecraft.Block base = baseBlockFor(this.variant);
            if (base != null) {
                // ★★ 必须**显式补上 block 字段** ✗→✓
                //   breakBlock 被调用时这一格**已经是空气**了 ✗ → new BlockBreakInfo(world,x,y,z)
                //   里的 block 是 **null** ✓ → 掉落阶段调 Block.canBeCarried() 直接 NPE 崩游戏 ✓
                //   实测崩溃：Cannot invoke "Block.canBeCarried()" because "info.block" is null ✓
                //   BlockBreakInfo.block / block_id 都是 public 字段 ✓，补上即可 ✓。
                net.minecraft.BlockBreakInfo info = new net.minecraft.BlockBreakInfo(world, x, y, z);
                info.block = base;
                info.block_id = base.blockID;
                base.dropBlockAsItself(info);   // 掉的是**里面的原方块** ✓（资料：不掉机壳 ✓）
            }
        }
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    public int variant() { return this.variant; }
    public boolean isBrass() { return this.brass; }

    /** 挖掘粒子的贴图层：传动杆沿用轴那一层，齿轮沿用齿轮层 ✓ */
    @Override
    protected int particleLayer() {
        return this.variant == VARIANT_SHAFT ? 0 : 13;
    }

    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }
}
