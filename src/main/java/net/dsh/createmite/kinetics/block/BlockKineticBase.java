package net.dsh.createmite.kinetics.block;

import net.dsh.createmite.kinetics.KineticHelper;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.EffectRenderer;
import net.minecraft.Entity;
import net.minecraft.EntityDiggingFX;
import net.minecraft.EntityLivingBase;
import net.minecraft.EnumFace;
import net.minecraft.Minecraft;
import net.dsh.createmite.kinetics.client.CreateModels;
import net.minecraft.IBlockAccess;
import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 动力方块基类：方块实体 + 轴向元数据 + 按轴向变化的范围。
 *
 * 【重要】贴图一律走 MITE 的默认路径（setTextureName → 默认 registerIcons → blockIcon），
 * 也就是和"锌矿石"完全相同的机制；不要在这里重写 registerIcons/getIcon/getBlockTexture
 * ——实测那样写会导致方块与物品栏贴图异常（原因未完全定位，先用这条已验证可行的路径）。
 */
public abstract class BlockKineticBase extends Block implements net.minecraft.ITileEntityProvider {

    protected BlockKineticBase(int blockID, Material material, BlockConstants constants) {
        super(blockID, material, constants);
    }

    /**
     * ★ 关键：必须声明"不是标准整方块"。
     *
     * MITE 的 Block 构造器会遍历 16 个 metadata 调用本方法，据此算出 is_always_standard_form_cube；
     * 而 Block.renderAsNormalBlock() 就等于该标志。若返回 true（默认值），
     * 渲染器会走整方块快路径、**忽略我们设置的 setBlockBounds**，
     * 于是传动轴/齿轮/曲柄/石磨全被画成 1x1x1 大方块 —— 世界里和物品栏里看起来都是"材质错误"。
     * MITE 自己的 BlockSlab / BlockStairs 也是通过重写本方法来表明自己不是整方块。
     */
    @Override
    public boolean isStandardFormCube(boolean[] array, int metadata) {
        if (array != null && metadata >= 0 && metadata < array.length) {
            array[metadata] = false;
        }
        return false;
    }

    /**
     * ★ MITE 会校验方块元数据：{@code Block.isValidMetadata(int)} 默认**只认 0**，
     * 其余一律走 {@code reportInvalidMetadata} 刷错误日志：
     * <pre>Block: invalid metadata value of 1 for Block[2315]</pre>
     * 我们的机器用元数据存：轴向（bit0-1）、朝向符号（bit2）、**机壳封装位（bit2/bit3）**，
     * 所以合法范围是 **0..15（4 bit 全部）**。
     *
     * ★★ 2026-09-27 修正：这里以前写的是 {@code metadata < 8} ✗ ——
     *   而"黄铜机壳封装"用的是 **bit3（值 8）** → metadata 一到 8 就被判定为**非法** ✓，
     *   MITE 把它**抹回 0** ✗ → 表现就是用户实测的"**闪一帧黄铜外壳、随后变回安山**"✓、
     *   以及"齿轮/大齿轮封装后始终显示安山"✓。**不要改回去** ✗。
     */
    @Override
    public boolean isValidMetadata(int metadata) {
        return metadata >= 0 && metadata < 16;
    }

    /** 模型用到的图集图标（顺序 = CreateModels.LAYER_NAMES），世界/背包两条渲染路径共用 */
    private final Icon[] modelIcons = new Icon[CreateModels.LAYER_COUNT];

    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);
        for (int i = 0; i < CreateModels.LAYER_COUNT; i++) {
            this.modelIcons[i] = register.registerIcon(CreateModels.LAYER_NAMES[i]);
        }
    }

    public Icon[] getModelIcons() {
        return this.modelIcons;
    }

    /**
     * ★★★ **崩溃的根因修复就在这里**（完整链路见 KineticTileEntity.hasWorld 上面那一大段）。
     *
     * 【以前为什么错】这里原来是 {@code return new KineticTileEntity();} ——
     * 完全丢掉了 Chunk 递进来的 world 参数。平时看不出问题，因为"非扫描期"那条创建路径
     * （World.setBlockTileEntity → Chunk.setChunkBlockTileEntity）会替我们调一次 setWorldObj；
     * 但**方块实体 tick 期间**（World.updateEntities 把 scanningTileEntities 置 true 的那一段，
     * 也正是我们的动力网络在到处找邻居的时候）那条路径**不会**调 setWorldObj ✗，
     * 于是 World.getBlockTileEntity 会把一个 worldObj==null 的半成品交给动力网络，
     * 一句 isGearbox() → getBlockType() → worldObj.getBlockId(...) → NPE → 崩游戏。
     *
     * 【现在】把 world 当场绑上：不管哪条路径、不管谁先问，这个元件都是"有世界"的，
     * 网络可以正常把它当成一个真元件用（它本来就是）✓
     */
    @Override
    public TileEntity createNewTileEntity(World world) {
        return KineticTileEntity.withWorld(new KineticTileEntity(), world);
    }

    /**
     * 这台机器**自带齿轮**吗？默认 false。
     * 资料 330130：石磨这类元件自带齿轮，所以同样适用"大齿轮带动它 → 加速一倍"的规则。
     */
    /**
     * 手持**机壳**右键 → 封装 / 解除（资料 227807/227809 + 396859/857833/857834）✓
     *
     * 【为什么放在基类】原来只写在 BlockShaft 里 ✗ → 齿轮和大齿轮压根没有入口，
     * 右键完全没反应（用户实测 ✗）。原版只有**传动杆 / 齿轮 / 大齿轮**这三种能装壳 ✓，
     * 所以这里用方块类型白名单限定，别的机器右键照旧没反应 ✓。
     */
    @Override
    public boolean onBlockActivated(net.minecraft.World world, int x, int y, int z,
                                    net.minecraft.EntityPlayer player, net.minecraft.EnumFace face,
                                    float hitX, float hitY, float hitZ) {
        boolean encasable = this == net.dsh.createmite.CMBlocks.blockShaft
                || this == net.dsh.createmite.CMBlocks.blockCogwheel
                || this == net.dsh.createmite.CMBlocks.blockLargeCogwheel;
        if (!encasable) return false;

        net.minecraft.Item held = player.getHeldItem();     // MITE 返回 Item（不是 ItemStack）✓
        if (held == null) return false;
        int id = held.itemID;
        boolean andesite = id == net.dsh.createmite.CMBlocks.ID_ANDESITE_CASING;
        boolean brass = id == net.dsh.createmite.CMBlocks.ID_BRASS_CASING;
        if (!andesite && !brass) return false;

        net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!(te instanceof net.dsh.createmite.kinetics.KineticTileEntity)) return false;
        net.dsh.createmite.kinetics.KineticTileEntity kinetic =
                (net.dsh.createmite.kinetics.KineticTileEntity) te;

        // ★★ 2026-09-27 改版：封装 = **把这个方块换成对应的"封装箱"** ✓
        //   （资料：六个封装箱是**独立方块** ✓；也正因为要独立方块，才能绕开
        //    "bit3 不能同步到客户端"那个死结 ✓ —— 方块身份一定会同步 ✓）
        net.dsh.createmite.kinetics.block.BlockEncased target =
                net.dsh.createmite.kinetics.block.BlockEncased.encasedFor(this, brass);
        if (target == null) return false;

        if (!world.isRemote) {
            int axis = kinetic.liveMeta() & 3;                 // 保留自转轴 ✓
            // MITE 的写法（javap 核对过）：setBlock(x,y,z,id,meta,flag)，flag=2 会同步给客户端 ✓
            // ★ 换壳期间置 swappingShell，避免 setBlock 移除旧方块时触发"掉原方块" ✗
            net.dsh.createmite.kinetics.block.BlockEncased.swappingShell = true;
            try {
                world.setBlock(x, y, z, target.blockID, axis, 2);
            } finally {
                net.dsh.createmite.kinetics.block.BlockEncased.swappingShell = false;
            }
            world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                    "tile.piston.out", 0.4F, 1.6F);
            System.out.println("[CreateMITE][ENCASE] server -> " + target.getUnlocalizedName()
                    + " @ " + x + "," + y + "," + z + " axis=" + axis + " brass=" + brass);
            // ★ 2026-09-28：**所有**机械动力的聊天栏提示都归 CMHints 管（键位 V 开关）✓
            if (net.dsh.createmite.CMHints.enabled(player)) {
                player.sendChatToPlayer(net.minecraft.ChatMessageComponent.createFromText(
                        brass ? "§6已用黄铜机壳封装（不消耗机壳）" : "§b已用安山机壳封装（不消耗机壳）"));
            }
        }
        return true;
    }

    public boolean hasBuiltInCog() {
        return false;
    }

    @Override
    public int getMetadataForPlacement(World world, int x, int y, int z, ItemStack stack,
                                       Entity entity, EnumFace face, float hitX, float hitY, float hitZ) {
        return KineticHelper.axisFromFace(face);
    }

    /**
     * ★ 破坏方块时必须**显式移除方块实体**。
     *
     * 【这是一个真 bug 的修复，不能再删】
     * MC 1.6.4 里"方块被破坏 → 移除它的 TE"这段代码写在 {@code BlockContainer.breakBlock} 里
     * （BlockContainer 调 World.removeBlockTileEntity）。而我们的方块继承的是 Block、
     * 只实现了 ITileEntityProvider（Block.hasTileEntity() 恰好就是 instanceof ITileEntityProvider，
     * 所以"有没有 TE"这一半是对的），**但没有 BlockContainer 那段清理**。
     *
     * 后果（实测踩到）：方块挖掉了，TE 却还留在 chunkTileEntityMap 里继续 tick：
     *   - 石磨：**挖掉了还一直在吸物品**（吸入区不消失）；
     *   - 更糟的是在同一格重新放方块时，Chunk 会**复用**那个残留 TE
     *     （setBlockIDWithMetadata 里 getChunkBlockTileEntity 非空就复用，只清一下缓存），
     *     于是"新放的机器"继承了旧的原料/成品/进度；
     *   - 残留的轴/齿轮还会以幽灵成员的身份留在动力网络里，一直算转速和应力。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        world.removeBlockTileEntity(x, y, z);
        // 挖掘粒子不在这里喷：breakBlock 只在服务端有保证，
        // 客户端真正的破坏粒子入口是 EffectRenderer.addBlockDestroyEffects（见 EffectRendererMixin）。
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /**
     * 方块被破坏时喷碎屑粒子。
     *
     * 【为什么不是"自动就有"】MITE 的 addBlockDestroyEffects 是拿**方块的图集图标**当粒子贴图的，
     * 而我们的方块图标是 createmite_blank（一张全透明图，专门用来让原版那趟整方块渲染"看不见"），
     * 于是粒子喷出来了但是**全透明**——表现就是"挖方块没有粒子"。
     *
     * 解决办法不是去改方块图标（那会让世界里真的多出一个不透明大方块），
     * 而是自己 new EntityDiggingFX 并 setParticleIcon(模型自己的图集图标)：
     * 粒子用的是 Create 那张贴图，跟方块本体一致。
     *
     * 调这里的是 Block.breakBlock —— **两边都会执行**（TE 清理那条修复就是靠这一点），
     * 所以用 world.isRemote 把粒子限制在客户端。
     */
    protected void spawnBreakParticles(World world, int x, int y, int z, int meta) {
        Icon icon = this.particleIcon();
        if (icon == null) return;
        EffectRenderer renderer = Minecraft.getMinecraft().effectRenderer;
        if (renderer == null) return;

        java.util.Random random = world.rand;
        for (int i = 0; i < 24; i++) {
            double px = x + random.nextDouble();
            double py = y + random.nextDouble();
            double pz = z + random.nextDouble();
            EntityDiggingFX fx = new EntityDiggingFX(world, px, py, pz,
                    px - ((double) x + 0.5D), py - ((double) y + 0.5D), pz - ((double) z + 0.5D),
                    this, meta);
            fx.setParticleIcon(icon);   // ★ 覆盖掉构造器里按方块图标取的那张（那是全透明的）
            renderer.addEffect(fx);
        }
    }

    /**
     * 粒子用第几层贴图。默认第 0 层（axis，金属色）；
     * 外壳是安山岩机壳的方块（齿轮箱/离合器/反转齿轮箱/手摇曲柄）覆写成 13，石磨覆写成 5。
     */
    protected int particleLayer() {
        return 0;
    }

    public Icon particleIcon() {
        int layer = this.particleLayer();
        if (layer >= 0 && layer < this.modelIcons.length && this.modelIcons[layer] != null) {
            return this.modelIcons[layer];
        }
        for (Icon icon : this.modelIcons) {
            if (icon != null) return icon;
        }
        return null;
    }

    @Override
    public void setBlockBoundsBasedOnStateAndNeighbors(IBlockAccess world, int x, int y, int z) {
        this.setBoundsForAxis(world.getBlockMetadata(x, y, z) & 3);
    }

    /**
     * 扳手能不能旋转它的轴向。
     *
     * 默认可以；**轴向被锁死的方块要覆写为 false**（例：石磨永远是竖直 Y，
     * 因为"只能被齿轮带动"这条规则依赖它的轴是竖直的）。
     */
    public boolean isWrenchRotatable() {
        return true;
    }

    /** 默认像 Create 的轴：中间一根 4x4 像素的杆（6..10 px == 0.375..0.625） */
    protected void setBoundsForAxis(int axis) {
        if (axis == 1) {
            this.setBlockBoundsForCurrentThread(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);
        } else if (axis == 0) {
            this.setBlockBoundsForCurrentThread(0.0D, 0.375D, 0.375D, 1.0D, 0.625D, 0.625D);
        } else {
            this.setBlockBoundsForCurrentThread(0.375D, 0.375D, 0.0D, 0.625D, 0.625D, 1.0D);
        }
    }

    @Override
    public boolean isPortable(World world, EntityLivingBase entity, int x, int y, int z) {
        return true;
    }

    protected static BlockConstants machineConstants() {
        return new BlockConstants().setNeverHidesAdjacentFaces();
    }

    // ===== Create 机器的统一规格（所有机器方块都必须走 applyMachineDefaults） =====

    /**
     * 挖掉一个方块消耗的工具耐久。
     *
     * 【数值是怎么来的】MITE 的 {@code ItemTool.getToolDecayFromBreakingBlock}：
     * <pre>
     *   decay = max(1, max((int)(hardness * 100 * getBaseDecayRateForBreakingBlock(block)),
     *                      (int)(100 * rate / 20)))
     * </pre>
     * 而 {@code ItemPickaxe/ItemAxe.getBaseDecayRateForBreakingBlock} 都返回 <b>1.0</b>，
     * 所以 **decay = 硬度 × 100**：想要 300 就是硬度 3.0。
     * 改这个数只要改 hardness 一处，别去动工具。
     */
    public static final int TOOL_DECAY_PER_BLOCK = 300;

    /** 由 TOOL_DECAY_PER_BLOCK 反推的硬度（3.0F）。改硬度会让挖掘时间一起变，这是预期的。 */
    public static final float HARDNESS_PER_BLOCK = TOOL_DECAY_PER_BLOCK / 100.0F;

    /**
     * 最低挖掘等级。
     *
     * 【数值是怎么来的】MITE 的工具等级 = {@code ItemTool.getMaterialHarvestLevel()}：
     * 金属材料直接用 {@code Material.min_harvest_level}，非金属要减 1。实测这张表：
     * <pre>
     *   wood 0 | flint/stone/rust 2→1 | 铜·银·金 2 | 铁·远古金属 3 | 秘银·钻石 4 | 艾德曼 5
     * </pre>
     * 所以取 <b>3</b>：铁及以上的镐/斧能挖，**铜、银、金一律挖不动**。
     */
    public static final int MIN_HARVEST_LEVEL = 3;

    /** 与锌矿石同一条路径：只调 setTextureName，让 MITE 默认 registerIcons 去注册 blockIcon */
    protected void applyMachineDefaults(String unlocalizedName, String textureName) {
        this.setHardness(HARDNESS_PER_BLOCK);          // ★ 3.0F = 每次挖掘扣 300 耐久
        this.setMinHarvestLevel(MIN_HARVEST_LEVEL);    // ★ 3 = 铁起步
        this.setStepSound(Block.soundMetalFootstep);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName(textureName);
        this.setCreativeTab(CreativeTabs.tabBlock);
    }
}
