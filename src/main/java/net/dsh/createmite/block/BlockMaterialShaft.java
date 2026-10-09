package net.dsh.createmite.block;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 金属传动杆（铜 / 银 / 金 / 铁 / 秘银）——**纯合成件**，2026-09-30 新增。
 *
 * 【用途】只参与合成，**不进动力网络、不自转、不参与任何传动** ✓（用户明确要求 ✓）
 *  所以它刻意**不继承 BlockKineticBase** ✗ —— 继承它会被 KineticHelper 当成元件收进网络 ✗。
 *
 * 【渲染】几何就是那根 4x4x16 的方柱（Blockbench 工程：模型\<材质>-传动杆 ✓）：
 *   方块本体贴图设成 createmite_blank（全透明）→ 世界里不画那个整方块 ✓，
 *   真正的柱体由 MaterialShaftRenderer（TESR）画 ✓ —— 和传动杆箱同一套路 ✓。
 *
 * 【手感】硬度/抗性按"同材质方块"给，**挖掘等级按材质**（MITE 工具等级：木0 燧石1 铜·银·金2 铁3 秘银·钻石4 ✓）：
 *   铜/银/金 = 2 ✓、铁 = 3 ✓、秘银 = 4 ✓（= 用户要的"秘银传动杆至少秘银镐/战锤" ✓）
 *   工具类别闸门复用 ToolCompat（镐类 = 镐 + 战锤 ✓），三刀都在（客户端进度/创造模式/服务端判定 ✓）。
 *
 * 【★★ material 这一项兼职"工作台等级闸门" ✗ 别乱传 ✗】2026-09-30 修正②：
 *   ItemBlock 会把**方块材质**抄成物品材质（Block.addItemBlockMaterials ✓），MITE 的
 *   RecipeHelper.addRecipe 又拿产物物品的 getHardestMetalMaterial() 去设
 *   "本配方要求的工作台材质" ✓，判定是「工作台材质 durability ≥ 它」✓ ——
 *   所以：方块材质写什么，合成这根传动杆就至少需要什么等级的工作台 ✓
 *   （之前五根全传 Material.iron ✗ → 五条配方统统"至少铁工作台" ✗，用户实测发现 ✓）
 */
public class BlockMaterialShaft extends Block implements net.minecraft.ITileEntityProvider {

    private final String texName;
    private Icon iconSide;
    private Icon iconTop;

    public BlockMaterialShaft(int blockID, String unlocalizedName, String textureName, Material material,
                              float hardness, float resistance, int minHarvestLevel) {
        // ★★ 必须 setNeverHidesAdjacentFaces() ✗否则"透视"：
        //   方块本体是全透明的（createmite_blank ✓），但默认常量会让**邻居把贴着它的面剔掉** ✗
        //   → 从缝里直接看到虚空/天空（用户实测："透视了" + 地上一片蓝 ✓）
        //   动力元件（BlockKineticBase.machineConstants）用的就是这一条 ✓，照抄 ✓
        super(blockID, material, new BlockConstants().setNeverHidesAdjacentFaces());
        this.texName = textureName;
        this.setHardness(hardness);
        this.setResistance(resistance);
        this.setStepSound(Block.soundMetalFootstep);
        this.setMinHarvestLevel(minHarvestLevel);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName("createmite_blank");   // 世界里不画整方块（交给 TESR ✓）
        this.setCreativeTab(CreativeTabs.tabBlock);
        // 那一格只占中间 4x4 的柱子 ✓（碰撞箱跟着柱子走，不至于撞空气 ✓）
        this.setBlockBoundsForCurrentThread(6.0F / 16.0F, 0.0F, 6.0F / 16.0F, 10.0F / 16.0F, 1.0F, 10.0F / 16.0F);
    }

    @Override
    public void registerIcons(IconRegister register) {
        // ★ 必须调 super ✗：它才会把 setTextureName("createmite_blank") 那张注册成 blockIcon ✓
        //   漏了这一句 → blockIcon 为 null → 世界里的方块渲染成"缺贴图"的紫黑格 ✗（用户实测截图 ✓）
        super.registerIcons(register);
        this.iconSide = register.registerIcon(this.texName);
        this.iconTop = register.registerIcon(this.texName + "_top");
    }

    /** 贴图基名（TESR 用它拼原图路径 ✓） */
    public String textureBase() {
        return this.texName;
    }

    /** 侧面图标（给物品栏 3D 渲染用 ✓） */
    public Icon iconSide() {
        return this.iconSide;
    }

    /** 顶/底图标（给 TESR 用 ✓） */
    public Icon iconTop() {
        return this.iconTop;
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new net.dsh.createmite.shaft.MaterialShaftTileEntity();
    }

    /** 破坏时显式移除方块实体（继承 Block 而不是 BlockContainer，不清会留幽灵 TE ✗） */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        world.removeBlockTileEntity(x, y, z);
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    // 注意：MITE 的 Block **没有** isOpaqueCube() ✗（javap 实证：只有 renderAsNormalBlock /
    // isOpaqueStandardFormCube 等 final 方法）→ 这里不加这个覆盖 ✓。
    // 世界里的"隐形整方块"和原版传动杆同一套做法：贴图用 createmite_blank ✓，柱体交给 TESR ✓。
}
