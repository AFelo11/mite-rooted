package net.dsh.createmite;

import net.dsh.createmite.block.ZincOreBlock;
import net.dsh.createmite.kinetics.block.BlockClutch;
import net.dsh.createmite.kinetics.block.BlockCogwheel;
import net.dsh.createmite.kinetics.block.BlockCrushingWheel;
import net.dsh.createmite.kinetics.block.BlockLargeWaterWheel;
import net.dsh.createmite.kinetics.block.BlockLargeWaterWheelPlaceholder;
import net.dsh.createmite.kinetics.block.BlockWaterWheel;
import net.dsh.createmite.kinetics.block.BlockGearbox;
import net.dsh.createmite.kinetics.block.BlockGearshift;
import net.dsh.createmite.kinetics.block.BlockHandCrank;
import net.dsh.createmite.kinetics.block.BlockLargeCogwheel;
import net.dsh.createmite.kinetics.block.BlockLargeCogwheelPlaceholder;
import net.dsh.createmite.kinetics.block.BlockMillstone;
import net.dsh.createmite.kinetics.block.BlockShaft;
import net.minecraft.Item;
import net.minecraft.ItemBlock;

/** 本模组的方块注册表 */
public final class CMBlocks {

    /**
     * 方块 ID 必须同时是一个空闲的"物品槽"：
     * MITE/1.6.4 中方块要能作为物品存在（ItemBlock），itemID 与 blockID 相同，
     * 而 MITE 自身已用到 0-255（方块）与 256+（物品），故这里取实测空闲的 ID。
     */
    public static final int ID_ZINC_ORE = 2300;
    public static final int ID_SHAFT = 2310;
    public static final int ID_COGWHEEL = 2311;
    public static final int ID_HAND_CRANK = 2312;
    public static final int ID_MILLSTONE = 2313;
    // ---- M2 批次 1：传动扩展 ----
    public static final int ID_LARGE_COGWHEEL = 2314;
    public static final int ID_GEARBOX = 2315;
    public static final int ID_CLUTCH = 2316;
    public static final int ID_GEARSHIFT = 2317;
    // ---- M2 批次 2：新的动力与加工 ----
    public static final int ID_CREATIVE_MOTOR = 2318;
    public static final int ID_WATER_WHEEL = 2319;
    public static final int ID_LARGE_WATER_WHEEL = 2320;
    public static final int ID_CRUSHING_WHEEL = 2321;
    /**
     * 大型水车的**占位方块**（3x3 平面里除主体以外那 8 格）。
     *
     * 【为什么单独占一个 id】它必须是**独立的方块**：MITE 里"占几格"就是"占几个方块 id 的格子"，
     * 没有"一个方块占多格"这种概念。它和主体共用同一片 3x3 平面，靠 metadata 的轴向互相对上号。
     * 2322 是紧挨着现有区段的下一个空闲 id（本模组已用 2300 / 2310-2321）。
     */
    public static final int ID_LARGE_WATER_WHEEL_PLACEHOLDER = 2322;
    /**
     * 大齿轮的**占位方块**（轮盘平面里、主体上下左右那 4 个正交邻居格）。
     *
     * 【为什么单独占一个 id】同大型水车那一条：MITE 里"占几格"就是"占几个方块 id 的格子"，
     * 没有"一个方块占多格"这种概念。它和主体共用同一片**垂直于自转轴的平面**，
     * 靠 metadata 的轴向互相对上号。
     * 2323 是紧挨着现有区段的下一个空闲 id（本模组已用 2300 / 2310-2322）。
     */
    public static final int ID_LARGE_COGWHEEL_PLACEHOLDER = 2323;

    // ---- M3：机壳系列（资料 mcmod 227807 安山机壳 / 227809 黄铜机壳）----
    /** 安山机壳；2325 是紧挨着占位方块的空闲 id（本模组已用 2300 / 2310-2323） */
    public static final int ID_ANDESITE_CASING = 2325;
    /** 黄铜机壳 */
    public static final int ID_BRASS_CASING = 2326;

    // ---- 封装箱（资料 396859/396860/857833/857843/857834/857844）----
    // 六个**独立方块**：安山/黄铜 × 传动杆/齿轮/大齿轮 ✓
    // 【为什么独立】bit3(值8) 不能可靠同步给客户端（日志实证：服务端写 13、客户端只读到 5 ✗），
    // 而 metadata 只有 bit0-2 可靠同步、轴已占 2 bit → 塞不下第二个标记 ✗。
    // 方块身份一定会同步 ✓，所以把信息放进"是哪个方块"里 ✓。
    public static final int ID_ANDESITE_ENCASED_SHAFT = 2327;
    public static final int ID_BRASS_ENCASED_SHAFT = 2328;
    public static final int ID_ANDESITE_ENCASED_COGWHEEL = 2329;
    public static final int ID_BRASS_ENCASED_COGWHEEL = 2330;
    public static final int ID_ANDESITE_ENCASED_LARGE_COGWHEEL = 2331;
    public static final int ID_BRASS_ENCASED_LARGE_COGWHEEL = 2332;

    // ---- 金属/合金块（2026-09-28 新增；**最低挖掘等级 = 秘银级 4** ✓）----
    /** 锌块（9 锌锭 ↔ 1 块）*/
    public static final int ID_ZINC_BLOCK = 2333;
    /** 黄铜块（9 黄铜锭 ↔ 1 块）*/
    public static final int ID_BRASS_BLOCK = 2334;
    /** 安山合金块（9 安山合金 ↔ 1 块）*/
    public static final int ID_ANDESITE_ALLOY_BLOCK = 2335;

    // ---- 多方块熔炉的六个结构方块（2026-09-29 用户提供模型/贴图）----
    // 三个「核心」（整方块，六图标通路，不需要 TESR）+ 三个「包裹传动杆」（自定义几何，走 TESR）
    // ★ 挖掘等级按材质对齐 MITE 自己的同类方块（.minecraft/MITE/reference/harvest_level.txt）：
    //     圆石 2 / 下界岩(地狱岩) 2 / 黑曜石 3  ✓ 用户指定「圆石需要至少铜·银·金镐」✓
    public static final int ID_OBSIDIAN_FURNACE_CORE = 2336;
    public static final int ID_NETHERRACK_FURNACE_CORE = 2337;
    public static final int ID_COBBLESTONE_FURNACE_CORE = 2338;
    public static final int ID_OBSIDIAN_WRAPPED_SHAFT = 2339;
    public static final int ID_NETHERRACK_WRAPPED_SHAFT = 2340;
    public static final int ID_COBBLESTONE_WRAPPED_SHAFT = 2341;

    // ---- 金属传动杆（纯合成件，2026-09-30 用户要求）----
    // 挖掘等级按材质（MITE 工具等级：木0 燧石1 铜·银·金2 铁3 秘银·钻石4）：
    //   铜/银/金 = 2 ✓、铁 = 3 ✓、秘银 = 4 ✓（= "秘银传动杆至少秘银镐/战锤" ✓）
    public static final int ID_COPPER_SHAFT = 2342;
    public static final int ID_SILVER_SHAFT = 2343;
    public static final int ID_GOLD_SHAFT = 2344;
    public static final int ID_IRON_SHAFT = 2345;
    public static final int ID_MITHRIL_SHAFT = 2346;

    public static net.dsh.createmite.campfire.BlockCampfire blockCampfire;
    public static net.dsh.createmite.campfire.BlockCampfire blockCampfireLit;
    public static net.dsh.createmite.campfire.BlockCampfirePart blockCampfirePart;

    /** 三个核心的挖掘等级（= MITE 同材质方块的等级）*/
    public static final int CORE_HARVEST_OBSIDIAN = 3;
    public static final int CORE_HARVEST_NETHERRACK = 2;
    public static final int CORE_HARVEST_COBBLESTONE = 2;

    public static ZincOreBlock oreZinc;

    public static BlockShaft blockShaft;
    public static BlockCogwheel blockCogwheel;
    public static BlockHandCrank blockHandCrank;
    public static BlockMillstone blockMillstone;
    public static BlockLargeCogwheel blockLargeCogwheel;
    public static BlockGearbox blockGearbox;
    public static BlockClutch blockClutch;
    public static BlockGearshift blockGearshift;
    public static BlockWaterWheel blockWaterWheel;
    public static BlockLargeWaterWheel blockLargeWaterWheel;
    /** 大型水车的隐形占位方块；注册失败时为 null（编队逻辑会自动停用，不会 NPE） */
    public static BlockLargeWaterWheelPlaceholder blockLargeWaterWheelPlaceholder;
    /** 大齿轮的隐形占位方块；注册失败时为 null（编队逻辑会自动停用，不会 NPE） */
    public static BlockLargeCogwheelPlaceholder blockLargeCogwheelPlaceholder;
    public static BlockCrushingWheel blockCrushingWheel;

    /** 机壳（装饰外壳，无方块实体、不进动力网络）*/
    public static net.dsh.createmite.kinetics.block.BlockCasing blockAndesiteCasing;
    public static net.dsh.createmite.kinetics.block.BlockCasing blockBrassCasing;

    /** 金属/合金块（锌块 / 黄铜块 / 安山合金块）—— 注册失败时为 null，配方会跳过 ✓ */
    public static net.dsh.createmite.kinetics.block.BlockCMStorage blockZincBlock;
    public static net.dsh.createmite.kinetics.block.BlockCMStorage blockBrassBlock;
    public static net.dsh.createmite.kinetics.block.BlockCMStorage blockAndesiteAlloyBlock;

    /** 熔炉结构：三个核心（普通方块，见 FurnaceCoreBlock）*/
    public static net.dsh.createmite.block.FurnaceCoreBlock blockObsidianFurnaceCore;
    public static net.dsh.createmite.block.FurnaceCoreBlock blockNetherrackFurnaceCore;
    public static net.dsh.createmite.block.FurnaceCoreBlock blockCobblestoneFurnaceCore;

    /** 熔炉结构：三个包裹传动杆（机器方块，静止机壳 + 自转轴）*/
    public static net.dsh.createmite.kinetics.block.BlockWrappedShaft wrappedShaftObsidian;
    public static net.dsh.createmite.kinetics.block.BlockWrappedShaft wrappedShaftNetherrack;
    public static net.dsh.createmite.kinetics.block.BlockWrappedShaft wrappedShaftCobblestone;

    /** 五根金属传动杆（纯合成件 ✓） */
    public static net.dsh.createmite.block.BlockMaterialShaft shaftCopper;
    public static net.dsh.createmite.block.BlockMaterialShaft shaftSilver;
    public static net.dsh.createmite.block.BlockMaterialShaft shaftGold;
    public static net.dsh.createmite.block.BlockMaterialShaft shaftIron;
    public static net.dsh.createmite.block.BlockMaterialShaft shaftMithril;

    /** 六个封装箱（安山/黄铜 × 传动杆/齿轮/大齿轮）✓ */
    public static net.dsh.createmite.kinetics.block.BlockEncased encAndesiteShaft;
    public static net.dsh.createmite.kinetics.block.BlockEncased encBrassShaft;
    public static net.dsh.createmite.kinetics.block.BlockEncased encAndesiteCog;
    public static net.dsh.createmite.kinetics.block.BlockEncased encBrassCog;
    public static net.dsh.createmite.kinetics.block.BlockEncased encAndesiteLargeCog;
    public static net.dsh.createmite.kinetics.block.BlockEncased encBrassLargeCog;


    private CMBlocks() {}


    /**
     * ★ 篝火三个方块：核心（熄灭 2301）／核心（燃烧 2302）／占位 2303 ✓
     *   ⚠️ 只在**第一个 tick** 建（CMHeat 建发热表时调 ✓）—— 启动期建新方块会让 MITE 引导崩 ✗（实测 8 次 ✓）
     */
    public static void registerCampfire() {
        if (blockCampfire != null) return;
        try {
            blockCampfire = new net.dsh.createmite.campfire.BlockCampfire(
                    net.dsh.createmite.campfire.BlockCampfire.ID_CORE, false);
            blockCampfireLit = new net.dsh.createmite.campfire.BlockCampfire(
                    net.dsh.createmite.campfire.BlockCampfire.ID_CORE_LIT, true);
            blockCampfirePart = new net.dsh.createmite.campfire.BlockCampfirePart(
                    net.dsh.createmite.campfire.BlockCampfirePart.ID_PART);
            net.dsh.createmite.campfire.BlockCampfire.setInstances(blockCampfire, blockCampfireLit);
            // ★ 必须给物品形态 ✗ 否则一放方块就 `itemsList[id] is null` 崩 ✓（用户：不用进创造栏 ✓ 只求不崩 ✓）
            registerItemBlock(blockCampfire, 400.0F);
            registerItemBlock(blockCampfireLit, 400.0F);
            registerItemBlock(blockCampfirePart, 400.0F);
            // ★ 不给创造栏（用户要求：只能靠搭建获得 ✓）
            try { blockCampfire.setCreativeTab(null); blockCampfireLit.setCreativeTab(null); blockCampfirePart.setCreativeTab(null); } catch (Throwable ignored) { }
            System.out.println("[MITE] CAMPFIRE ok core=" + blockCampfire.blockID
                    + " coreLit=" + blockCampfireLit.blockID + " part=" + blockCampfirePart.blockID);
        } catch (Throwable t) {
            System.out.println("[MITE] CAMPFIRE 建方块失败: " + t);
        }
    }

    public static void register() {
        registerCampfire();          // ★ 和其它方块同一时机 ✓（物品形态/贴图缝合都靠这一步 ✓）
        if (oreZinc != null) return;
        if (oreZinc != null) return;

        oreZinc = new ZincOreBlock(ID_ZINC_ORE);
        registerItemBlock(oreZinc, 250.0F);

        // ---- M1：动力三件套 + 石磨 ----
        blockShaft = new BlockShaft(ID_SHAFT);
        registerItemBlock(blockShaft, 406.0F);
        blockCogwheel = new BlockCogwheel(ID_COGWHEEL);
        registerItemBlock(blockCogwheel, 1800.0F);
        blockHandCrank = new BlockHandCrank(ID_HAND_CRANK);
        registerItemBlock(blockHandCrank, 1025.0F);
        blockMillstone = new BlockMillstone(ID_MILLSTONE);
        registerItemBlock(blockMillstone, 1800.0F);

        // ---- M2 批次 1 ----
        blockLargeCogwheel = new BlockLargeCogwheel(ID_LARGE_COGWHEEL);
        registerItemBlock(blockLargeCogwheel, 2700.0F);
        blockGearbox = new BlockGearbox(ID_GEARBOX);
        registerItemBlock(blockGearbox, 2800.0F);
        blockClutch = new BlockClutch(ID_CLUTCH);
        registerItemBlock(blockClutch, 2400.0F);
        blockGearshift = new BlockGearshift(ID_GEARSHIFT);
        registerItemBlock(blockGearshift, 2600.0F);

        // ---- M2 批次 2 ----
        blockWaterWheel = new BlockWaterWheel(ID_WATER_WHEEL);
        registerItemBlock(blockWaterWheel, 1200.0F);
        blockLargeWaterWheel = new BlockLargeWaterWheel(ID_LARGE_WATER_WHEEL);
        registerItemBlock(blockLargeWaterWheel, 2400.0F);
        blockCrushingWheel = new BlockCrushingWheel(ID_CRUSHING_WHEEL);
        registerItemBlock(blockCrushingWheel, 3200.0F);

        // ---- M3：机壳系列 ----
        // 贴图本工程已有（assets/minecraft/textures/blocks/andesite_casing.png、brass_casing.png）✓
        blockAndesiteCasing = new net.dsh.createmite.kinetics.block.BlockCasing(
                ID_ANDESITE_CASING, "andesite_casing", "andesite_casing");
        registerItemBlock(blockAndesiteCasing, 1200.0F);
        blockBrassCasing = new net.dsh.createmite.kinetics.block.BlockCasing(
                ID_BRASS_CASING, "brass_casing", "brass_casing");
        registerItemBlock(blockBrassCasing, 2000.0F);

        // ---- 金属/合金块（2026-09-28 新增；**最低挖掘等级 = 秘银级 4** ✓）----
        // 每个都带一道空闲检查：MITE 的 Block 构造器发现 id 被占会直接抛异常 →
        // 整个模组加载失败 ✗；这里只停用那一个块（留 null，配方会跳过）✓
        blockZincBlock = registerStorageBlock(ID_ZINC_BLOCK, "zinc_block", "zinc_block", 2500.0F);
        blockBrassBlock = registerStorageBlock(ID_BRASS_BLOCK, "brass_block", "brass_block", 3000.0F);
        blockAndesiteAlloyBlock = registerStorageBlock(ID_ANDESITE_ALLOY_BLOCK,
                "andesite_alloy_block", "andesite_alloy_block", 1200.0F);

        // ---- 多方块熔炉的六个结构方块（2026-09-29）----
        // 核心：整方块 + 六图标（侧面=原版材质、顶底=带小太阳），**不需要 TESR** ✓
        //   硬度取"同材质方块自己的硬度"（MITE 参考导出表 Block[4]=200/Block[87]=160/Block[49]=240，
        //   即 2.0 / 1.6 / 2.4）—— 注意黑曜石在 MITE 里是 2.4，**不是原版的 50** ✓
        blockObsidianFurnaceCore = registerFurnaceCore(ID_OBSIDIAN_FURNACE_CORE, "obsidian_furnace_core",
                "obsidian_core", net.minecraft.Material.obsidian, 2.4F, 2000.0F, CORE_HARVEST_OBSIDIAN,
                net.dsh.createmite.furnace.FurnaceMultiblock.MATERIAL_OBSIDIAN, 2400.0F);
        blockNetherrackFurnaceCore = registerFurnaceCore(ID_NETHERRACK_FURNACE_CORE, "netherrack_furnace_core",
                "netherrack_core", net.minecraft.Material.netherrack, 1.6F, 0.4F, CORE_HARVEST_NETHERRACK,
                net.dsh.createmite.furnace.FurnaceMultiblock.MATERIAL_NETHERRACK, 1200.0F);
        blockCobblestoneFurnaceCore = registerFurnaceCore(ID_COBBLESTONE_FURNACE_CORE, "cobblestone_furnace_core",
                "cobblestone_core", net.minecraft.Material.stone, 2.0F, 10.0F, CORE_HARVEST_COBBLESTONE,
                net.dsh.createmite.furnace.FurnaceMultiblock.MATERIAL_COBBLE, 1200.0F);

        // 包裹传动杆：自定义几何（4 片机壳 + 端盖 + 中间那根轴），机壳静止、轴自转 ✓
        //   硬度走"机器方块统一 3.0 = 每次挖掘扣 300 耐久"（BlockKineticBase）✓
        //   粒子贴图层 = 机壳那一层（31 黑曜石 / 32 下界岩 / 33 圆石）✓
        wrappedShaftObsidian = registerWrappedShaft(ID_OBSIDIAN_WRAPPED_SHAFT,
                net.dsh.createmite.kinetics.block.BlockWrappedShaft.KIND_OBSIDIAN,
                "obsidian_wrapped_shaft", net.minecraft.Material.obsidian, CORE_HARVEST_OBSIDIAN, 31, 3600.0F);
        wrappedShaftNetherrack = registerWrappedShaft(ID_NETHERRACK_WRAPPED_SHAFT,
                net.dsh.createmite.kinetics.block.BlockWrappedShaft.KIND_NETHERRACK,
                "netherrack_wrapped_shaft", net.minecraft.Material.netherrack, CORE_HARVEST_NETHERRACK, 32, 2400.0F);
        wrappedShaftCobblestone = registerWrappedShaft(ID_COBBLESTONE_WRAPPED_SHAFT,
                net.dsh.createmite.kinetics.block.BlockWrappedShaft.KIND_COBBLESTONE,
                "cobblestone_wrapped_shaft", net.minecraft.Material.stone, CORE_HARVEST_COBBLESTONE, 33, 2400.0F);

        // ---- 五根金属传动杆（纯合成件；只参与合成，不进动力网络 ✓）----
        // 硬度统一 3.0（机器方块规格 ✓）；挖掘等级按材质 ✓
        // ★★ 2026-09-30 修正②：**方块材质必须逐根写自己的金属** ✗（之前五根全写 Material.iron ✗）
        //   【为什么】MITE 的合成台等级闸门（RecipeHelper.addRecipe → IRecipe
        //     .setMaterialToCheckToolBenchHardnessAgainst）取的是**产物物品**的
        //     getHardestMetalMaterial()，而 ItemBlock 的材质是 Block.addItemBlockMaterials
        //     从**方块材质**抄过来的（javap 实证 ✓）→ 五根全写成 iron
        //     ⇒ 五条配方统统变成"至少铁工作台" ✗（用户实测："咋都是至少铁工作台" ✓）
        //   【改完的结果】闸门 = 该材质 durability（EnumEquipmentMaterial 实证：
        //     铜/银/金 = 4.0、铁 = 8.0、古代金属 = 16.0、秘银 = 64.0 ✓），
        //     工作台材质 durability 必须 ≥ 它 → 铜/银/金 = 至少铜工作台 ✓、铁 = 至少铁工作台 ✓、
        //     秘银 = 至少秘银工作台 ✓。
        //   ⚠️ 铜=银=金 的 durability 在 MITE 里**都是 4.0** ✗ → 三者分不出高低 ✗，
        //     这是 MITE 自己的规则（它家银/金工具同样只要"至少铜工作台" ✓），不是我们写错。
        shaftCopper = registerMaterialShaft(ID_COPPER_SHAFT, "copper_shaft",
                net.minecraft.Material.copper, 2, 3.0F, 2.0F, 1200.0F);
        shaftSilver = registerMaterialShaft(ID_SILVER_SHAFT, "silver_shaft",
                net.minecraft.Material.silver, 2, 3.0F, 2.0F, 1800.0F);
        shaftGold = registerMaterialShaft(ID_GOLD_SHAFT, "gold_shaft",
                net.minecraft.Material.gold, 2, 3.0F, 2.0F, 2400.0F);
        shaftIron = registerMaterialShaft(ID_IRON_SHAFT, "iron_shaft",
                net.minecraft.Material.iron, 3, 3.0F, 4.0F, 3000.0F);
        shaftMithril = registerMaterialShaft(ID_MITHRIL_SHAFT, "mithril_shaft",
                net.minecraft.Material.mithril, 4, 3.0F, 6.0F, 6000.0F);
        logShaftBenchGate();

        // ---- 六个封装箱 ----
        // 【不进创造栏】资料明说它们是创造模式物品、v0.4 起连创造栏都移除了 ✓，
        //   获取方式是"手持机壳右键原方块" ✓ —— 所以这里**不调 setCreativeTab** ✓。
        encAndesiteShaft = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_ANDESITE_ENCASED_SHAFT, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_SHAFT, false);
        registerItemBlock(encAndesiteShaft, 100000.0F);
        encBrassShaft = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_BRASS_ENCASED_SHAFT, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_SHAFT, true);
        registerItemBlock(encBrassShaft, 100000.0F);
        encAndesiteCog = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_ANDESITE_ENCASED_COGWHEEL, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_COGWHEEL, false);
        registerItemBlock(encAndesiteCog, 100000.0F);
        encBrassCog = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_BRASS_ENCASED_COGWHEEL, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_COGWHEEL, true);
        registerItemBlock(encBrassCog, 100000.0F);
        encAndesiteLargeCog = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_ANDESITE_ENCASED_LARGE_COGWHEEL, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_LARGE_COGWHEEL, false);
        registerItemBlock(encAndesiteLargeCog, 100000.0F);
        encBrassLargeCog = new net.dsh.createmite.kinetics.block.BlockEncased(
                ID_BRASS_ENCASED_LARGE_COGWHEEL, net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_LARGE_COGWHEEL, true);
        registerItemBlock(encBrassLargeCog, 100000.0F);

        // ---- 大型水车的占位方块（3x3 编队用） ----
        registerLargeWaterWheelPlaceholder();
        // ---- 大齿轮的占位方块（正十字挡位：主体 + 上下左右 4 格） ----
        registerLargeCogwheelPlaceholder();

        System.out.println("[MITE] 方块已注册: 锌矿石=" + ID_ZINC_ORE
                + " 传动轴=" + ID_SHAFT + " 齿轮=" + ID_COGWHEEL
                + " 手摇曲柄=" + ID_HAND_CRANK + " 石磨=" + ID_MILLSTONE
                + " 大齿轮=" + ID_LARGE_COGWHEEL + " 十字齿轮箱=" + ID_GEARBOX
                + " 离合器=" + ID_CLUTCH + " 反转齿轮箱=" + ID_GEARSHIFT
                + " 大型水车占位=" + ID_LARGE_WATER_WHEEL_PLACEHOLDER
                + (blockLargeWaterWheelPlaceholder != null ? "（已就绪）" : "（★ 注册失败，3x3 编队停用）")
                + " 大齿轮=" + ID_LARGE_COGWHEEL + "/大齿轮占位=" + ID_LARGE_COGWHEEL_PLACEHOLDER
                + (blockLargeCogwheelPlaceholder != null ? "（已就绪）" : "（★ 注册失败，正十字挡位停用）"));
        System.out.println("[MITE] 熔炉结构方块已注册: 核心=" + ID_OBSIDIAN_FURNACE_CORE + "/"
                + ID_NETHERRACK_FURNACE_CORE + "/" + ID_COBBLESTONE_FURNACE_CORE
                + " 包裹传动杆=" + ID_OBSIDIAN_WRAPPED_SHAFT + "/" + ID_NETHERRACK_WRAPPED_SHAFT
                + "/" + ID_COBBLESTONE_WRAPPED_SHAFT
                + " 挖掘等级: 黑曜石=" + CORE_HARVEST_OBSIDIAN + " 下界岩=" + CORE_HARVEST_NETHERRACK
                + " 圆石=" + CORE_HARVEST_COBBLESTONE
                + "（MITE 等级表: 木0 燧石1 铜银金2 铁3 秘银钻石4 艾德曼5）");
        // ★ 2026-09-29：把六个方块**实际生效**的挖掘等级读回来打一遍 ——
        //   MITE 参考导出只覆盖 id < 256，我们的方块查不到，只能自己打日志核对 ✓
        System.out.println("[MITE] 熔炉六件套挖掘等级实测: 黑曜石核心=" + levelOf(blockObsidianFurnaceCore)
                + " 下界岩核心=" + levelOf(blockNetherrackFurnaceCore)
                + " 圆石核心=" + levelOf(blockCobblestoneFurnaceCore)
                + " | 黑曜石传动杆=" + levelOf(wrappedShaftObsidian)
                + " 下界岩传动杆=" + levelOf(wrappedShaftNetherrack)
                + " 圆石传动杆=" + levelOf(wrappedShaftCobblestone)
                + "（只有镐类对这三种材质有效 → 只能镐子挖 ✓）");
    }

    /**
     * 注册大型水车的占位方块。
     *
     * 【为什么带一道空闲检查】MITE 的 {@code Block} 构造器发现 id 被占会直接抛
     * IllegalArgumentException，那会让**整个模组加载失败**。2322 是按"紧挨着现有区段"选的，
     * 万一将来被别的模组占了，这里只把 3x3 编队停掉（占位方块为 null，
     * LargeWaterWheelGroup 里每一处都有 null 判据），其余功能照常。
     */
    private static void registerLargeWaterWheelPlaceholder() {
        if (net.minecraft.Block.blocksList[ID_LARGE_WATER_WHEEL_PLACEHOLDER] != null
                || Item.itemsList[ID_LARGE_WATER_WHEEL_PLACEHOLDER] != null) {
            System.out.println("[MITE] ★ id " + ID_LARGE_WATER_WHEEL_PLACEHOLDER
                    + " 已被占用，大型水车的 3x3 占位格停用（其余功能不受影响）");
            return;
        }
        blockLargeWaterWheelPlaceholder = new BlockLargeWaterWheelPlaceholder(ID_LARGE_WATER_WHEEL_PLACEHOLDER);
        // 也给它一个物品形态：MITE 的数据导出 / 一致性校验是按"方块 ↔ 物品"成对扫的，
        // 缺一边会刷告警。难度给一个不可能达到的值，而且**不设创造模式物品栏**（构造器里没调
        // setCreativeTab → getCreativeTabToDisplayOn() 返回 null）→ 玩家正常途径拿不到它。
        registerItemBlock(blockLargeWaterWheelPlaceholder, 100000.0F);
    }

    /**
     * 注册大齿轮的占位方块（写法和大型水车那个完全同构，理由也一样）。
     *
     * 【为什么带一道空闲检查】MITE 的 {@code Block} 构造器发现 id 被占会直接抛
     * IllegalArgumentException，那会让**整个模组加载失败**。2323 是按"紧挨着现有区段"选的，
     * 万一将来被别的模组占了，这里只把正十字挡位停掉（占位方块为 null，
     * LargeCogwheelGroup 里每一处都有 null 判据），其余功能照常。
     */
    private static void registerLargeCogwheelPlaceholder() {
        if (net.minecraft.Block.blocksList[ID_LARGE_COGWHEEL_PLACEHOLDER] != null
                || Item.itemsList[ID_LARGE_COGWHEEL_PLACEHOLDER] != null) {
            System.out.println("[MITE] ★ id " + ID_LARGE_COGWHEEL_PLACEHOLDER
                    + " 已被占用，大齿轮的 4 个正交占位格停用（其余功能不受影响）");
            return;
        }
        blockLargeCogwheelPlaceholder = new BlockLargeCogwheelPlaceholder(ID_LARGE_COGWHEEL_PLACEHOLDER);
        // 也给它一个物品形态：MITE 的数据导出 / 一致性校验是按"方块 ↔ 物品"成对扫的，
        // 缺一边会刷告警。难度给一个不可能达到的值，而且**不设创造模式物品栏**（构造器里没调
        // setCreativeTab → getCreativeTabToDisplayOn() 返回 null）→ 玩家正常途径拿不到它。
        registerItemBlock(blockLargeCogwheelPlaceholder, 100000.0F);
    }

    /**
     * 注册一个"金属/合金块"（锌块 / 黄铜块 / 安山合金块），**自带 id 空闲检查** ✓。
     *
     * 被占用时只把这一块停用（返回 null，配方那边会跳过），不让整个模组加载失败 ✓。
     */
    private static net.dsh.createmite.kinetics.block.BlockCMStorage registerStorageBlock(
            int id, String unlocalizedName, String textureName, float difficulty) {
        if (net.minecraft.Block.blocksList[id] != null || Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 方块 id " + id + "（" + unlocalizedName
                    + "）已被占用 → 这一个块停用，其余照常");
            return null;
        }
        net.dsh.createmite.kinetics.block.BlockCMStorage b =
                new net.dsh.createmite.kinetics.block.BlockCMStorage(id, unlocalizedName, textureName);
        registerItemBlock(b, difficulty);
        return b;
    }

    /** 读一个方块实际生效的最低挖掘等级（只用于启动日志核对 ✓） */
    private static String levelOf(net.minecraft.Block b) {
        return b == null ? "未注册" : String.valueOf(b.getMinHarvestLevel(0));
    }

    /**
     * 注册一个熔炉「核心」方块（自带 id 空闲检查：被占用只停用这一个，不让整个模组加载失败 ✓）。
     */
    private static net.dsh.createmite.block.FurnaceCoreBlock registerFurnaceCore(
            int id, String unlocalizedName, String textureName, net.minecraft.Material material,
            float hardness, float resistance, int minHarvestLevel, int materialId, float difficulty) {
        if (net.minecraft.Block.blocksList[id] != null || Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 方块 id " + id + "（" + unlocalizedName
                    + "）已被占用 → 这一个块停用，其余照常");
            return null;
        }
        net.dsh.createmite.block.FurnaceCoreBlock b = new net.dsh.createmite.block.FurnaceCoreBlock(
                id, unlocalizedName, textureName, material, hardness, resistance, minHarvestLevel, materialId);
        registerItemBlock(b, difficulty);
        return b;
    }

    /** 注册一根「包裹传动杆」（同样自带 id 空闲检查 ✓） */
    private static net.dsh.createmite.kinetics.block.BlockWrappedShaft registerWrappedShaft(
            int id, int kind, String unlocalizedName, net.minecraft.Material material,
            int minHarvestLevel, int shellLayer, float difficulty) {
        if (net.minecraft.Block.blocksList[id] != null || Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 方块 id " + id + "（" + unlocalizedName
                    + "）已被占用 → 这一个块停用，其余照常");
            return null;
        }
        net.dsh.createmite.kinetics.block.BlockWrappedShaft b =
                new net.dsh.createmite.kinetics.block.BlockWrappedShaft(
                        id, kind, unlocalizedName, material, minHarvestLevel, shellLayer);
        registerItemBlock(b, difficulty);
        return b;
    }

    /**
     * 注册一根「金属传动杆」（自带 id 空闲检查 ✓）。
     *
     * 【material 这一项别乱写 ✗】它就是 MITE 的**工作台等级闸门**：
     *   ItemBlock 会从方块材质抄一份 → 配方要求"工作台材质 durability ≥ 它" ✓（见上面大段注释 ✓）
     */
    private static net.dsh.createmite.block.BlockMaterialShaft registerMaterialShaft(
            int id, String name, net.minecraft.Material material, int minHarvestLevel,
            float hardness, float resistance, float difficulty) {
        if (net.minecraft.Block.blocksList[id] != null || Item.itemsList[id] != null) {
            System.out.println("[MITE] ★ 方块 id " + id + "（" + name + "）已被占用 → 这一个块停用，其余照常");
            return null;
        }
        net.dsh.createmite.block.BlockMaterialShaft b = new net.dsh.createmite.block.BlockMaterialShaft(
                id, name, name, material, hardness, resistance, minHarvestLevel);
        registerItemBlock(b, difficulty);
        return b;
    }

    /**
     * 自检并把五根传动杆的「工作台等级闸门」打进启动日志 ✓（这样不用进游戏就能核对 ✓）。
     *
     * MITE 规则（javap 实证）：配方要求的材质 = **产物物品**的 getHardestMetalMaterial()，
     * 判定条件是「工作台材质的 Material.durability ≥ 要求材质的 durability」✓。
     */
    private static void logShaftBenchGate() {
        net.minecraft.Block[] shafts = {shaftCopper, shaftSilver, shaftGold, shaftIron, shaftMithril};
        String[] cn = {"铜", "银", "金", "铁", "秘银"};
        StringBuilder sb = new StringBuilder(
                "[MITE] 传动杆工作台闸门（工作台材质 durability ≥ 产物材质 durability）: ");
        for (int i = 0; i < shafts.length; i++) {
            net.minecraft.Block b = shafts[i];
            if (b == null) {
                sb.append(cn[i]).append("=未注册 ");
                continue;
            }
            Item it = Item.itemsList[b.blockID];
            net.minecraft.Material need = (it == null) ? null : it.getHardestMetalMaterial();
            if (need == null) {
                sb.append(cn[i]).append("=无闸门 ");
                continue;
            }
            float needD = durabilityOf(need);
            int minMeta = -1;
            for (int meta = 0; meta < 16; meta++) {
                net.minecraft.Material bench = net.minecraft.BlockWorkbench.getToolMaterial(meta);
                float benchD = (bench == null) ? -1.0F : durabilityOf(bench);
                if (benchD >= 0.0F && needD >= 0.0F && benchD >= needD) {
                    minMeta = meta;
                    break;
                }
            }
            // Material.name 也是 protected ✗ → 用公开的 getCapitalizedName() ✓
            sb.append(cn[i]).append("=").append(need.getCapitalizedName()).append("(").append(needD)
                    .append(")→最低工作台meta=").append(minMeta).append(" ");
        }
        System.out.println(sb.toString());
    }

    /**
     * 读 Material.durability —— 它是 **protected** ✗（javap 实证 ✓），MITE 没给 getter ✗，
     * 所以这里用反射读一份（**只用于启动自检日志** ✓，失败就返回 -1 不会影响游戏 ✓）。
     */
    private static float durabilityOf(net.minecraft.Material m) {
        try {
            java.lang.reflect.Field f = net.minecraft.Material.class.getDeclaredField("durability");
            f.setAccessible(true);
            return f.getFloat(m);
        } catch (Throwable t) {
            return -1.0F;
        }
    }

    /** 注册方块物品形态：参考数据导出、创造模式物品栏、精准采集掉落都依赖它 */
    private static void registerItemBlock(net.minecraft.Block block, float lowestCraftingDifficulty) {
        registerItemBlock(block, lowestCraftingDifficulty, null);
    }

    /**
     * 注册方块物品形态。
     * iconTextureName 非空时，背包里显示该贴图（而不是方块本体贴图）。
     */
    private static void registerItemBlock(net.minecraft.Block block, float lowestCraftingDifficulty,
                                          String iconTextureName) {
        ItemBlock itemBlock = iconTextureName == null
                ? new ItemBlock(block)
                : new net.dsh.createmite.item.CMItemBlock(block, iconTextureName);
        // 让 MITE 的合成难度体系认识我们的方块，避免数据一致性告警
        itemBlock.setLowestCraftingDifficultyToProduce(lowestCraftingDifficulty);
        Item.itemsList[block.blockID] = itemBlock;
    }
}