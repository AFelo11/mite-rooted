package net.dsh.createmite.block;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.IBlockAccess;
import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.Material;

/**
 * 熔炉结构的「核心」方块：黑曜石熔炉核心 / 下界岩熔炉核心 / 圆石熔炉核心（2026-09-29 新增）。
 *
 * 【外观】用户提供的贴图就是**照着原版材质画的**：四个侧面 = 原版材质本体（逐像素照搬），
 * 只有顶面/底面正中多一个小太阳 —— 所以放进结构里看着就是一整块黑曜石/下界岩/圆石，
 * 只有顶底能看出是「核心」。因此这个方块**不需要 TESR**（几何就是标准整方块），
 * 走 MITE 原生的 getIcon 通路即可（和 MITE 自带的熔炉一样）✓。
 *
 * 【为什么不做成机器方块】它们不转、不进动力网络、这一阶段也不存任何状态 ✓ ——
 * 与 BlockCasing/BlockCMStorage 同样的理由。将来多方块成型逻辑要挂状态时再让它实现
 * ITileEntityProvider（方块 id 与外观都不用动 ✓）。
 *
 * 【数值全部对齐 MITE 自己的同材质方块】（见 .minecraft/MITE/reference 导出表）：
 * <pre>
 *   方块          材质            硬度   挖掘等级（MITE 参考导出的实测值）
 *   圆石           Material.stone     2.0    2  （= 铜·银·金镐及以上 ✓ 用户指定）
 *   下界岩(地狱岩) Material.netherrack 1.6   2  （= 铜·银·金镐及以上）
 *   黑曜石         Material.obsidian  2.4    3  （= 铁镐及以上）
 * </pre>
 * ★ 黑曜石的硬度**不是原版那个 50**：MITE 把黑曜石改成了 2.4（参考导出表 Block[49] = 240），
 *   照抄原版数值会让它变成 5000 点工具耐久才能挖掉 ✗。
 */
public class FurnaceCoreBlock extends Block implements net.minecraft.ITileEntityProvider {

    private final String texName;
    /** 顶/底（带小太阳那张）—— 构造期由 registerIcons 填上 */
    private Icon iconTop;
    /** 材质编号（见 FurnaceMultiblock.MATERIAL_*，与大熔炉贴图表的行顺序一致） */
    private final int materialId;

    public FurnaceCoreBlock(int blockID, String unlocalizedName, String textureName, Material material,
                            float hardness, float resistance, int minHarvestLevel, int materialId) {
        super(blockID, material, new BlockConstants());
        this.materialId = materialId;
        this.texName = textureName;
        this.setHardness(hardness);
        this.setResistance(resistance);
        this.setStepSound(Block.soundStoneFootstep);
        this.setMinHarvestLevel(minHarvestLevel);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName(textureName);       // = 侧面图，也是物品栏图标
        this.setCreativeTab(CreativeTabs.tabBlock);
    }

    /**
     * 注册两张图：侧面（= setTextureName 那张，MITE 默认通路已注册进 blockIcon ✓）与顶底。
     * 物品栏图标用的就是 blockIcon（侧面图）✓ —— 和用户素材包的说明一致。
     */
    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);
        this.iconTop = register.registerIcon(this.texName + "_top");
    }

    /** side 0 = 底面、1 = 顶面 → 小太阳那张；2~5 = 四个侧面 → 普通材质 */
    @Override
    public Icon getIcon(int side, int meta) {
        if ((side == 0 || side == 1) && this.iconTop != null) return this.iconTop;
        return this.blockIcon;
    }

    /** 世界渲染走的就是这条（RenderBlocks.getBlockIcon → getBlockTexture）✓ */
    @Override
    public Icon getBlockTexture(IBlockAccess access, int x, int y, int z, int side) {
        return this.getIcon(side, access != null ? access.getBlockMetadata(x, y, z) : 0);
    }

    /** 材质编号（0=圆石 1=黑曜石 2=地狱岩；顺序必须与 CreateModelsFurnaceBig 一致 ✓） */
    public int material() {
        return this.materialId;
    }

    // ===================== 多方块成型（2026-09-30）=====================

    /**
     * 核心必须挂方块实体：① 3x3x3 大模型要靠它当渲染锚点 ✓
     * ② 成型判定要"延迟到下一 tick"执行（onBlockAdded 那一刻还在 Chunk 写块内部 ✗）。
     * 状态本身不存在 TE 里（在 metadata 里 ✓），所以没有 NBT、也没有懒创建的风险 ✓。
     */
    @Override
    public net.minecraft.TileEntity createNewTileEntity(net.minecraft.World world) {
        return new net.dsh.createmite.furnace.FurnaceCoreTileEntity();
    }

    /**
     * 右键核心 = **手动叫一次自检**（顺带把核心的方块实体唤醒 ✓）。
     * 以后做专属 UI 时就从这个入口接管（现在先返回 false，不抢别的交互 ✓）。
     */
    @Override
    public boolean onBlockActivated(net.minecraft.World world, int x, int y, int z,
                                    net.minecraft.EntityPlayer player, net.minecraft.EnumFace face,
                                    float hitX, float hitY, float hitZ) {
        world.getBlockTileEntity(x, y, z);   // 客户端也把渲染锚点叫出来 ✓（没有 TE 就没有整机模型 ✗）
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
        net.dsh.createmite.furnace.FurnaceMultiblock.updateCore(world, x, y, z, 0, 0, 0);

        // ★ 2026-10-01：原来那个"右键核心手动切燃烧/待机外观"的**临时演示开关已整段删除** ✗
        //   （用户：「切换燃烧和未燃烧的模型操作不需要了」✓）
        //   整机外观现在**只跟真实工作状态走** ✓：炉子逻辑写核心 metadata 的 bit3 ⇒ 渲染器读同一格 ✓
        return false;
    }

    /** 放下来之后：通知周围 27 格里的核心（含自己）重新判定 ✓ */
    @Override
    public void onBlockAdded(net.minecraft.World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
    }

    /**
     * 破坏时：① 显式移除 TE（继承 Block 而不是 BlockContainer，不清会留下幽灵 TE ✗，
     * 这条是 BlockKineticBase 上踩过的真 bug）② 通知周围核心复核（结构缺一块要失型 ✓）。
     */
    @Override
    public void breakBlock(net.minecraft.World world, int x, int y, int z, int blockID, int meta) {
        // ★ 2026-10-01 用户 ①：**拆核心之前先把里面的东西全吐出来** ✗（不吐就等于吞了 ✓）
        net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof net.dsh.createmite.furnace.FurnaceCoreTileEntity) {
            ((net.dsh.createmite.furnace.FurnaceCoreTileEntity) te).spillContents();
        }
        world.removeBlockTileEntity(x, y, z);
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
        super.breakBlock(world, x, y, z, blockID, meta);
    }
}
