package net.dsh.createmite;

import com.google.common.eventbus.Subscribe;
import net.dsh.createmite.command.HintCommand;
import net.dsh.createmite.command.PlayerModeCommand;
import net.dsh.createmite.command.TeleportCommand;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.dsh.createmite.kinetics.client.KineticRenderer;
import net.dsh.createmite.kinetics.tile.MillstoneTileEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.reload.event.BlockRegistryEvent;
import net.xiaoyu233.fml.reload.event.CommandRegisterEvent;
import net.xiaoyu233.fml.reload.event.ItemRegistryEvent;
import net.xiaoyu233.fml.reload.event.LanguageResourceReloadEvent;
import net.xiaoyu233.fml.reload.event.MITEEvents;
import net.xiaoyu233.fml.reload.event.RecipeRegistryEvent;
import net.xiaoyu233.fml.reload.event.TileEntityRegisterEvent;
import net.xiaoyu233.fml.reload.event.TileEntityRendererRegisterEvent;

import java.util.Map;

/**
 * Create × MITE R196 · 主入口
 *
 * 进度：
 *   Phase 1   —— 锌矿石（仅地下世界生成）、粗锌/锌锭/黄铜锭、熔炼与合成
 *   Phase 1.1 —— /P 游戏模式、/O 维度传送、作弊锁开关（独立模组）
 *   Phase M1  —— 动力内核（网络传播）+ 传动轴 / 齿轮 / 手摇曲柄 / 石磨
 */
public class CreateMite implements ModInitializer, ClientModInitializer {

    public static final String MOD_ID = "createmite";
    /** ★ 显示名 = MITE-扎根（fml.mod.json ✓）；日志前缀故意保持 ASCII ⇒ 日志好搜 ✓ */
    public static final String MOD_NAME = "MITE-Rooted";
    public static final String MOD_VERSION = "0.2.0";

    @Override
    public void onInitialize() {
        MITEEvents.MITE_EVENT_BUS.register(this);
        System.out.println("[" + MOD_NAME + "] 初始化 v" + MOD_VERSION);
        System.out.println("[" + MOD_NAME + "] 应力过载模式 = " + CMConfig.overstressMode()
                + "；手摇曲柄提供 " + CMConfig.getFloat("stress.capacity.hand_crank", 8.0F)
                + " 应力，石磨占用 " + CMConfig.getFloat("stress.impact.millstone", 4.0F)
                + "（改 config/createmite.properties）");
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[" + MOD_NAME + "] 客户端初始化完成");
        // 扳手贴图已确认（走 RenderItemMixin / ItemRendererMixin 画 3D 模型）——
        // 这里不再默认开诊断，免得每次启动都刷一屏贴图检查日志。
        // 需要时临时打开这一行即可：Diagnostics.scheduleTextureCheck();
    }

    @Subscribe
    public void onItemRegistry(ItemRegistryEvent event) {
        CMItems.register();
    }

    @Subscribe
    public void onBlockRegistry(BlockRegistryEvent event) {
        CMBlocks.register();
        // ★ 方块都注册完后再改堆叠上限（矿石 = 8）：MITE 自带矿石的物品形态这时也已就位 ✓
        CMItems.applyOreStackSizes();
        // ★ 必须挂在这里，不能挂在 onItemRegistry：实测事件顺序是**先物品、后方块**，
        //   挂在物品那边会拿到空的机器方块清单（日志里会看到"机器方块还没注册，跳过"）。
        ToolCompat.registerMachineToolEffectiveness();
    }

    @Subscribe
    public void onRecipeRegistry(RecipeRegistryEvent event) {
        CMRecipes.register(event);
    }

    // 删 MITE 自带面粉配方的工作交给 CraftingManagerMixin：
    // FML 的 RecipeModifyEvent 在这个类加载器组合下会抛 IllegalAccessError，详见那个 Mixin 的注释。

    @Subscribe
    public void onTileEntityRegister(TileEntityRegisterEvent event) {
        event.register(KineticTileEntity.class, "createmite_kinetic");
        event.register(MillstoneTileEntity.class, "createmite_millstone");
        event.register(net.dsh.createmite.kinetics.tile.CrushingWheelTileEntity.class, "createmite_crusher");
        // ★ 大熔炉核心（3x3x3 整机的渲染锚点 + 成型判定落脚点）。
        //   不注册的话**存盘时直接抛** "missing a mapping" ✗（实测踩到，写 chunk NBT 那一步炸）
        event.register(net.dsh.createmite.furnace.FurnaceCoreTileEntity.class, "createmite_furnace_core");
        // 五根金属传动杆（纯合成件，只有渲染锚点 ✓）
        event.register(net.dsh.createmite.shaft.MaterialShaftTileEntity.class, "createmite_material_shaft");
        event.register(net.dsh.createmite.campfire.TileCampfire.class, "createmite_campfire");
        event.register(net.dsh.createmite.campfire.TileCampfire.class, "createmite_campfire");
        System.out.println("[" + MOD_NAME + "] 方块实体已注册");
    }

    @Subscribe
    public void onTileEntityRendererRegister(TileEntityRendererRegisterEvent event) {
        KineticRenderer renderer = new KineticRenderer();
        event.register(KineticTileEntity.class, renderer);
        event.register(MillstoneTileEntity.class, renderer);
        event.register(net.dsh.createmite.kinetics.tile.CrushingWheelTileEntity.class, renderer);
        // ★ 3x3x3 大熔炉整机（挂在核心的方块实体上）。
        //   【为什么必须挂在这里】实测这个事件**是**会触发的（日志里有"动力渲染器已注册"✓），
        //   而 KineticRendererHook 那条反射路看到"表里已经有 KineticRenderer"就直接 return ✗
        //   —— 之前把大熔炉渲染器加在它后面，结果**从来没被注册过** ✗（"整机透明"的真凶 ✓）。
        event.register(net.dsh.createmite.furnace.FurnaceCoreTileEntity.class,
                new net.dsh.createmite.kinetics.client.FurnaceBigRenderer());
        System.out.println("[" + MOD_NAME + "] 动力渲染器已注册");
    }

    @Subscribe
    public void onCommandRegister(CommandRegisterEvent event) {
        event.register(new PlayerModeCommand());
        event.register(new TeleportCommand());
        event.register(new HintCommand());
        event.register(new net.dsh.createmite.command.FacingCommand());
        // ★ 结构选择器配套：/T <名称> → 把选区的方块结构导出成文本（给 AI 看玩家搭了什么）
        event.register(new net.dsh.createmite.command.StructureCommand());
        // ★ 2026-09-30：四季系统的 /se 指令（用户指定；**不碰原版看天数的指令** ✗ ✓）
        event.register(new net.dsh.createmite.command.SeasonCommand());
        // ★ 2026-09-30 用户要求的两条工具指令：
        //   /S <倍速> = 时间流速（1 正常 / 4 四倍速 ✓）
        //   /Y 1|2|3  = 天气：下雨 / 雷暴雨 / 晴天 ✓
        event.register(new net.dsh.createmite.command.TimeSpeedCommand());
        event.register(new net.dsh.createmite.command.WeatherCommand());
        System.out.println("[CreateMITE] command registered: /F (facing)");
        System.out.println("[" + MOD_NAME + "] 指令已注册: /P (游戏模式), /O (维度传送), /cmhint (提示开关), /cmf (朝向信息), /T (导出结构)");
        System.out.println("[CreateMITE] 提示开关 = 键位 V（按键设置里的名称：机械动力提示，默认开启）");
    }

    @Subscribe
    public void onLanguageReload(LanguageResourceReloadEvent event) {
        Map translation = event.getTranslation();
        if (translation == null) return;
        String lang = event.getLanguageKey();
        boolean english = lang != null && lang.toLowerCase().startsWith("en");

        translation.put("tile.oreZinc.name", english ? "Zinc Ore" : "锌矿石");
        translation.put("tile.campfire.name", english ? "Campfire" : "营火");
        translation.put("tile.campfireLit.name", english ? "Lit Campfire" : "燃烧的营火");
        translation.put("tile.campfirePart.name", english ? "Campfire" : "营火");
        translation.put("tile.campfire.name", english ? "Campfire" : "篝火");
        translation.put("tile.campfireLit.name", english ? "Lit Campfire" : "燃烧的篝火");
        translation.put("item.rawZinc.name", english ? "Raw Zinc" : "粗锌");
        translation.put("item.handWarmer.name", english ? "Hand Warmer" : "暖手石");
        translation.put("item.hotWaterBowl.name", english ? "Bowl of Hot Water" : "热水碗");
        translation.put("item.warmWaterBowl.name", english ? "Bowl of Warm Water" : "温水碗");
        translation.put("item.iceWaterBowl.name", english ? "Bowl of Ice Water" : "冰水碗");
        translation.put("item.hotMilkBowl.name", english ? "Bowl of Hot Milk" : "热牛奶碗");
        // ---- ★ 套餐 A：苹果派线 ＋ 巧克力奶线（2026-10-07 用户拍板 ✓）----
        translation.put("item.applePieRaw.name", english ? "Unbaked Apple Pie" : "苹果派胚");
        translation.put("item.applePieRawLegacy.name", english ? "Unbaked Apple Pie (old)" : "苹果派胚（旧）");
        translation.put("item.applePie.name", english ? "Apple Pie" : "苹果派");
        translation.put("item.hotApplePie.name", english ? "Hot Apple Pie" : "热苹果派");
        translation.put("item.chocolateMilkBowl.name", english ? "Bowl of Chocolate Milk" : "巧克力奶");
        translation.put("item.hotChocolateMilkBowl.name", english ? "Bowl of Hot Chocolate Milk" : "热巧克力奶");
        String[] mtEn = {"Copper", "Silver", "Gold", "Iron", "Mithril", "Adamantium", "Ancient Metal"};
        String[] mtCn = {"铜", "银", "金", "铁", "秘银", "艾德曼", "古代金属"};
        String[] mtKey = {"Copper", "Silver", "Gold", "Iron", "Mithril", "Adamantium", "AncientMetal"};
        for (int i = 0; i < mtKey.length; i++) {
            translation.put("item.hotMilkBucket" + mtKey[i] + ".name",
                    english ? ("Bucket of Hot " + mtEn[i] + " Milk") : (mtCn[i] + "热奶桶"));
        }
        translation.put("item.handWarmerCold.name", english ? "Cold Hand Warmer" : "冷暖手石");
        translation.put("item.ingotZinc.name", english ? "Zinc Ingot" : "锌锭");
        translation.put("item.ingotBrass.name", english ? "Brass Ingot" : "黄铜锭");
        translation.put("item.crushedRawZinc.name", english ? "Crushed Raw Zinc" : "粉碎粗锌");

        translation.put("tile.copper_shaft.name", english ? "Copper Shaft" : "铜-传动杆");
        translation.put("tile.silver_shaft.name", english ? "Silver Shaft" : "银-传动杆");
        translation.put("tile.gold_shaft.name", english ? "Gold Shaft" : "金-传动杆");
        translation.put("tile.iron_shaft.name", english ? "Iron Shaft" : "铁-传动杆");
        translation.put("tile.mithril_shaft.name", english ? "Mithril Shaft" : "秘银-传动杆");

        translation.put("tile.shaft.name", english ? "Shaft" : "传动轴");
        translation.put("tile.cogwheel.name", english ? "Cogwheel" : "齿轮");
        translation.put("tile.hand_crank.name", english ? "Hand Crank" : "手摇曲柄");
        translation.put("tile.millstone.name", english ? "Millstone" : "石磨");
        translation.put("tile.andesite_casing.name", english ? "Andesite Casing" : "安山机壳");
        translation.put("tile.brass_casing.name", english ? "Brass Casing" : "黄铜机壳");
        translation.put("tile.large_cogwheel.name", english ? "Large Cogwheel" : "大齿轮");
        translation.put("tile.gearbox.name", english ? "Gearbox" : "十字齿轮箱");
        // M2 批次 2 —— 名字必须和 BlockKineticBase.applyMachineDefaults 里的 unlocalizedName 一致
        translation.put("tile.water_wheel.name", english ? "Water Wheel" : "水车");
        translation.put("tile.large_water_wheel.name", english ? "Large Water Wheel" : "大型水车");
        // 大型水车的隐形占位方块（3x3 编队用）—— 玩家正常拿不到，但 MITE 的参考数据会按方块名去查
        translation.put("tile.large_water_wheel_placeholder.name",
                english ? "Large Water Wheel (part)" : "大型水车（占位）");
        // 大齿轮的隐形占位方块（正十字挡位用）—— 同上，玩家正常拿不到，但参考数据会按方块名去查
        translation.put("tile.large_cogwheel_placeholder.name",
                english ? "Large Cogwheel (part)" : "大齿轮（占位）");
        translation.put("tile.crushing_wheel.name", english ? "Crushing Wheel" : "粉碎轮");
        translation.put("tile.clutch.name", english ? "Clutch" : "离合器");
        translation.put("tile.gearshift.name", english ? "Gearshift" : "反转齿轮箱");
        translation.put("item.wrench.name", english ? "Wrench" : "扳手");
        translation.put("item.andesiteAlloy.name", english ? "Andesite Alloy" : "安山合金");
        translation.put("item.zincNugget.name", english ? "Zinc Nugget" : "锌粒");
        translation.put("item.brassNugget.name", english ? "Brass Nugget" : "黄铜粒");
        // 三种"粉碎矿石"（只能由粉碎轮产出）
        translation.put("item.crushedIron.name", english ? "Crushed Raw Iron" : "粉碎铁矿石");
        translation.put("item.crushedGold.name", english ? "Crushed Raw Gold" : "粉碎金矿石");
        translation.put("item.crushedCopper.name", english ? "Crushed Raw Copper" : "粉碎铜矿石");
        translation.put("item.crushedSilver.name", english ? "Crushed Raw Silver" : "粉碎银矿石");
        translation.put("item.crushedMithril.name", english ? "Crushed Raw Mithril" : "粉碎秘银矿石");
        translation.put("item.crushedAdamantium.name", english ? "Crushed Raw Adamantium" : "粉碎艾德曼矿石");
        // 结构选择器（开发工具）：贴图是木棍 ✓，无配方、创造栏拿 ✓
        translation.put("item.structure_wand.name", english ? "Structure Selector" : "结构选择器");
        // 金属 / 合金块（2026-09-28 新增；最低挖掘等级 = 秘银级）
        translation.put("tile.zinc_block.name", english ? "Block of Zinc" : "锌块");
        translation.put("tile.brass_block.name", english ? "Block of Brass" : "黄铜块");
        translation.put("tile.andesite_alloy_block.name", english ? "Block of Andesite Alloy" : "安山合金块");
        // 多方块熔炉的六个结构方块（2026-09-29）
        // ★ 名字必须和 CMBlocks 里 setUnlocalizedName 的名字一致 ✓
        //   （MITE 自己的「黑曜石熔炉/地狱岩熔炉」是单方块熔炉 id 222-225，
        //     我们的是多方块结构的构件，所以名字统一带「核心 / 传动杆」后缀区分 ✓）
        translation.put("tile.obsidian_furnace_core.name", english ? "Obsidian Furnace Core" : "黑曜石熔炉核心");
        // ★ 2026-09-29 用户要求改名：中文名按 **MITE 自己的叫法「地狱岩」** ✓
        //   （MITE 参考导出里 Block[87] 就叫「地狱岩」，我们原来写的「下界岩」不是游戏里的名字 ✗）
        translation.put("tile.netherrack_furnace_core.name", english ? "Netherrack Furnace Core" : "地狱岩熔炉核心");
        translation.put("tile.cobblestone_furnace_core.name", english ? "Cobblestone Furnace Core" : "圆石熔炉核心");
        translation.put("tile.obsidian_wrapped_shaft.name", english ? "Obsidian Cased Shaft" : "黑曜石熔炉传动杆");
        translation.put("tile.netherrack_wrapped_shaft.name", english ? "Netherrack Cased Shaft" : "地狱岩熔炉传动杆");
        translation.put("tile.cobblestone_wrapped_shaft.name", english ? "Cobblestone Cased Shaft" : "圆石熔炉传动杆");
        // 按键绑定显示名（按键设置菜单里那一行）—— 用户指定：机械动力提示 ✓
        translation.put("key.createmite.hints", english ? "Create: Hints" : "机械动力提示");
        // ★ 2026-09-30 新增：I 键界面（GuiPlayerStatus）—— 用户定名：**玩家面板** ✓
        //   （用户原话：「这个键位不叫机械动力状态面板，它叫：玩家面板」）
        //   ⚠️ 语言**键名** key.createmite.status 保持不变 ✗ —— options.txt 里按键是按键名+键码存的，
        //     改键名会把玩家已经设好的按键弄丢 ✗；只改显示文字 ✓
        translation.put("key.createmite.status", english ? "Player Panel" : "玩家面板");
        translation.put("gui.createmite.status.title", english ? "Player Panel" : "玩家面板");
        translation.put("gui.createmite.status.health", english ? "Health" : "血量");
        translation.put("gui.createmite.status.nutrition", english ? "Hunger" : "饱食度");
        translation.put("gui.createmite.status.satiation", english ? "Saturation" : "饱和度");
        // ★ 2026-09-30 追加：两条营养条（数值在服务端 ServerPlayer 上，单机同 JVM 直读 ✓）
        translation.put("gui.createmite.status.protein", english ? "Protein (meat)" : "肉类营养");
        translation.put("gui.createmite.status.fats", english ? "Essential Fats" : "必需脂肪");
        translation.put("gui.createmite.status.phytonutrients", english ? "Phytonutrients (plant)" : "植物营养");
        // 两根温度计（2026-09-30 用户改规格：UI 换成体温计形态 ✓）
        //   体温 = 等四季系统注入；环境温度 = 要戴测温饰品，未佩戴显示"未知" ✓
        translation.put("gui.createmite.status.body_temperature", english ? "Body" : "体温");
        translation.put("gui.createmite.status.ambient_temperature", english ? "Ambient" : "环境温度");
        translation.put("gui.createmite.status.unknown", english ? "Unknown" : "未知");
        translation.put("gui.createmite.status.singleplayer_only",
                english ? "Nutrient values are only readable in singleplayer" : "营养数值目前只在单人世界读得到");
    }
}
