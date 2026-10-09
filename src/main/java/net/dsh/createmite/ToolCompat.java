package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.Item;
import net.minecraft.ItemTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Create 机器方块的**挖掘工具规则**。
 *
 * 【MITE 的判定长什么样】{@code ItemTool.isEffectiveAgainstBlock(block, meta)}：
 * <pre>
 *   return (materials_effective_against.contains(block.blockMaterial)
 *        || blocks_effective_against.contains(block))
 *       && this.getMaterialHarvestLevel() >= block.getMinHarvestLevel(meta);
 * </pre>
 * 两个条件都要满足：
 *   1. **工具类别**：靠"材质/方块在不在工具的有效列表里"来判定。镐的有效材质表里有 iron，
 *      斧的只有 cactus/clay/glass/hardened_clay/ice/pumpkin/wood —— **斧默认挖不动我们的方块**，
 *      所以这里要把机器方块显式加进斧的 blocks_effective_against。
 *   2. **挖掘等级**：{@code getMaterialHarvestLevel() >= block.getMinHarvestLevel(meta)}，
 *      是**直接比较、不减一**（见 BlockKineticBase.MIN_HARVEST_LEVEL = 3 → 铁进、铜银金出）。
 *
 * 工具等级表（{@code getMaterialHarvestLevel}：金属取材质等级，非金属减 1）：
 * <pre>
 *   木 0 | 燧石·石·锈铁 1 | 铜·银·金 2 | 铁·远古金属 3 | 秘银·钻石 4 | 艾德曼 5
 * </pre>
 *
 * 顺带把结果打进日志，省得再为了确认规则反复进游戏。
 */
public final class ToolCompat {

    /**
     * 默认允许挖掘机器方块的工具类别。
     *
     * 全部可选值见启动日志的 {@code [CMTOOL]} 行（MITE R196 共 115 件 ItemTool）：
     * pickaxe 镐 / axe 斧 / war_hammer 战锤 / hatchet 手斧 / battle_axe 战斧 / mattock 鹤嘴锄 /
     * shovel 锹 / scythe 镰刀 / hoe 锄 / sword 剑 / dagger 匕首 / knife 小刀 / shears 剪刀 /
     * club 棍棒 / cudgel 短棍
     *
     * 想改不用改代码：config/createmite.properties 里加一行
     * <pre>mining.allowed_tool_types=pickaxe,axe,war_hammer,hatchet,mattock,battle_axe</pre>
     */
    private static final String DEFAULT_ALLOWED_TYPES =
            "pickaxe,axe,war_hammer,hatchet,mattock,battle_axe";

    private ToolCompat() {}

    /** 这个工具类别允不允许挖机器方块（大小写不敏感，逗号分隔） */
    public static boolean isAllowedToolType(String type) {
        if (type == null) return false;
        String allowed = CMConfig.getString("mining.allowed_tool_types", DEFAULT_ALLOWED_TYPES);
        for (String s : allowed.split(",")) {
            if (s.trim().equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    /**
     * 这把工具能不能挖机器方块：**必须是镐类或斧类，且等级 >= 3（铁起步）**。
     *
     * 用于 ServerPlayer.getDamageVsBlock 那道闸（BlockKineticBase.MIN_HARVEST_LEVEL 的说明里有等级表）。
     */
    public static boolean canMineMachine(net.minecraft.ItemStack stack) {
        if (stack == null) return false;
        net.minecraft.Item item = stack.getItem();
        if (!(item instanceof ItemTool)) return false;
        ItemTool tool = (ItemTool) item;
        if (!isAllowedToolType(tool.getToolType())) return false;
        return tool.getMaterialHarvestLevel() >= net.dsh.createmite.kinetics.block.BlockKineticBase.MIN_HARVEST_LEVEL;
    }

    /**
     * 这一族方块的挖掘闸：**按方块自己的最低挖掘等级**判，而不是写死"铁起步" ✓（2026-09-29 新增）。
     *
     * 【为什么要泛化】熔炉结构的「包裹传动杆」是机器方块（同一个基类），但用户指定的挖掘等级是
     * **按材质走**：圆石/下界岩 = 2（铜·银·金镐就能挖）、黑曜石 = 3（铁起步）。
     * 老写法用常量 {@link net.dsh.createmite.kinetics.block.BlockKineticBase#MIN_HARVEST_LEVEL}（= 3），
     * 会把这三根包壳传动杆一起抬到"铁起步" ✗。
     * 改成读 getMinHarvestLevel(meta) 之后：
     *   · 原有的机器方块最低等级本来就是 3 → **行为一字不变** ✓
     *   · 包裹传动杆按材质生效 ✓
     */
    public static boolean canMineBlock(net.minecraft.ItemStack stack, Block block, int meta) {
        if (stack == null || block == null) return false;
        net.minecraft.Item item = stack.getItem();
        if (!(item instanceof ItemTool)) return false;
        ItemTool tool = (ItemTool) item;
        if (isFurnaceStructureBlock(block)) {
            // ★★ 熔炉六件套：**完全按 MITE 自己的规矩**（用户 2026-09-29 明确：
            //   「战锤在 MITE 中也属于镐类工具，它应该也能挖」+「战锤类也分挖掘等级，
            //     不要搞成铜战锤也能挖黑曜石」）✓
            //   isEffectiveAgainstBlock 一条就把这两件事都管住了：
            //     ① 类别：工具的有效材质表里得有 stone/obsidian/netherrack ——
            //        启动诊断实测**只有 pickaxe 和 war_hammer** 有 ✓（斧/战斧/手斧/鹤嘴锄都没有 ✗）
            //     ② 等级：getMaterialHarvestLevel() >= 方块自己的 getMinHarvestLevel(meta) ——
            //        铜战锤 lvl2 < 黑曜石 3 → 挖不动 ✓；铁战锤 lvl3 >= 3 → 能挖 ✓
            //   所以这里**不再限定工具类别**（限定反而会把战锤误伤 ✗），也不查白名单 ✓
            boolean ok = tool.isEffectiveAgainstBlock(block, meta);
            if (!ok) {
                // 拒绝时留一条日志：用户实机测试全靠它核对（每次点击一条 ✓）
                System.out.println("[CreateMITE][镐子闸] 拒绝 " + tool.getToolType() + "/"
                        + tool.getToolMaterialName() + "/lvl" + tool.getMaterialHarvestLevel()
                        + " 挖 " + block.getUnlocalizedName()
                        + "（六件套 = 镐类(镐+战锤) 且 等级 >= 材质等级 ✓）");
            }
            return ok;
        }
        // 机器方块：老规矩（白名单类别 + 真正有效 + 该方块自己的等级 ✓）
        if (!isAllowedToolType(tool.getToolType())) return false;
        // ★★ 2026-09-29 追加：**还必须是"真正对这个方块有效"的工具** ✓
        //   只判"白名单 + 等级"是不够的：白名单里有斧类/战锤，`getDamageVsBlock` 那道闸
        //   是**独立于 MITE 自己的工具判定**的，于是拿着斧子也能把方块挖穿 ✗
        //   （用户实测：熔炉传动杆用斧子能挖 —— 与"MITE 原版材料"不符 ✗）。
        //   isEffectiveAgainstBlock = MITE 自己的那条规矩：
        //      (materials_effective_against.contains(block.blockMaterial)
        //       || blocks_effective_against.contains(block))
        //      && getMaterialHarvestLevel() >= block.getMinHarvestLevel(meta)
        //   → 机器方块：白名单类别都已在 registerMachineToolEffectiveness 里登记过 → 行为不变 ✓
        //   → 熔炉六件套：只有镐类的材质表里有 stone/obsidian/netherrack（javap 实测：
        //      ItemPickaxe 是**唯一**含这三种材质的工具类）→ 于是"只能镐子挖" ✓ 与材质严格对应 ✓
        return tool.isEffectiveAgainstBlock(block, meta);
    }

    /**
     * 熔炉六件套（3 核心 + 3 包裹传动杆）—— 判定**完全交回 MITE 自己**（见 canMineBlock）✓
     *
     * 用户 2026-09-29 两轮口径：
     *   ①「新添加的 6 个方块它们应该都只能使用镐子挖掘且严格与 MITE 原版材料对应」
     *   ②「**战锤在 MITE 中也属于镐类工具，它应该也能挖**，但是记住战锤类也是分挖掘等级的！
     *      不要给我搞成铜战锤也能挖黑曜石了！」
     *
     * 【MITE 的"镐类"到底有哪几种】启动诊断实测（见 logStrictPickaxeDiagnostics）：
     *   有效材质表里含 stone / obsidian / netherrack 的工具类**只有两个**：
     *       pickaxe（镐）✓ 和 war_hammer（战锤）✓
     *   斧 axe / 战斧 battle_axe / 手斧 hatchet / 鹤嘴锄 mattock / 锹 shovel … **都没有** ✗
     *   → 所以"只按 MITE 的材质表判"就自动等于"镐 + 战锤" ✓，不用再手写类别名单 ✓
     *     （手写名单反而会把战锤误伤 ✗ —— 我上一版就是这么错的 ✗）
     *
     * 【本方法只判"是不是这六个方块"】工具那一侧交给 isEffectiveAgainstBlock ✓
     */
    public static boolean isFurnaceStructureBlock(Block block) {
        return block instanceof net.dsh.createmite.block.FurnaceCoreBlock
                || block instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft
                // ★ 2026-09-30：五根金属传动杆也走同一道闸 ✓
                //   （镐类 = 镐 + 战锤 ✓、等级按方块自己的 getMinHarvestLevel ✓
                //     → 秘银传动杆(4) 只有秘银及以上的镐/战锤挖得动 ✓ 用户指定 ✓）
                || block instanceof net.dsh.createmite.block.BlockMaterialShaft;
    }

    /** MITE 的镐类 = **镐 + 战锤**（实测有效材质表含 stone/obsidian/netherrack 的只有这两类 ✓） */
    public static boolean isPickaxeClass(String type) {
        if (type == null) return false;
        return type.equalsIgnoreCase("pickaxe") || type.equalsIgnoreCase("war_hammer");
    }

    /**
     * 启动时把"六件套上到底哪些工具算有效"打出来 —— 这是**唯一**能核对的证据来源。
     *
     * 【为什么不能查 MITE 的参考导出】harvest_level.txt 等只覆盖 **id < 256** ✗，
     * 我们的方块是 2300+，一个都不会出现在里面 ✓（实测确认）✓
     */
    public static void logStrictPickaxeDiagnostics() {
        Block[] six = {
                CMBlocks.blockCobblestoneFurnaceCore, CMBlocks.blockNetherrackFurnaceCore,
                CMBlocks.blockObsidianFurnaceCore, CMBlocks.wrappedShaftCobblestone,
                CMBlocks.wrappedShaftNetherrack, CMBlocks.wrappedShaftObsidian,
        };
        StringBuilder picks = new StringBuilder();
        StringBuilder wrong = new StringBuilder();
        int wrongCount = 0, pickCount = 0;
        for (int id = 0; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (!(item instanceof ItemTool)) continue;
            ItemTool tool = (ItemTool) item;
            boolean any = false;
            for (Block b : six) {
                if (b != null && tool.isEffectiveAgainstBlock(b, 0)) { any = true; break; }
            }
            if (!any) continue;
            String tag = tool.getToolType() + "/" + tool.getToolMaterialName()
                    + "/lvl" + tool.getMaterialHarvestLevel();
            if (isPickaxeClass(tool.getToolType())) { pickCount++; if (picks.length() < 600) picks.append(tag).append("  "); }
            else { wrongCount++; if (wrong.length() < 600) wrong.append(tag).append("  "); }
        }
        System.out.println("[CreateMITE][镐子闸] 六件套上有效的**镐类**（镐+战锤，" + pickCount + " 件）: " + picks);
        System.out.println("[CreateMITE][镐子闸] 六件套上**非镐类**却有效的工具: "
                + (wrongCount == 0 ? "无 ✓（等于只能镐/战锤挖 ✓）" : wrongCount + " 件 ✗ → " + wrong));
    }

    /** 机器方块清单（顺序无所谓，只用来做"有效性"登记） */
    public static Block[] machineBlocks() {
        Block[] all = {
                CMBlocks.blockShaft,
                CMBlocks.blockCogwheel,
                CMBlocks.blockLargeCogwheel,
                CMBlocks.blockHandCrank,
                CMBlocks.blockMillstone,
                CMBlocks.blockGearbox,
                CMBlocks.blockClutch,
                CMBlocks.blockGearshift,
        };
        // ★★ 2026-09-29：熔炉结构的「包裹传动杆」**故意不登记在这里** ✓
        //   【为什么】登记进来 = 斧类/战锤等也会对它"有效" → 拿斧子就能挖 ✗。
        //   用户要求：这六个方块**只能镐子挖，且严格与 MITE 原版材料对应** ✓。
        //   javap 实测：stone / obsidian / netherrack 三种材质**只出现在 ItemPickaxe**
        //   的有效材质表里 → 不登记时，只有镐类挖得动 ✓（和 MITE 的圆石/黑曜石/地狱岩一模一样 ✓）
        List<Block> list = new ArrayList<Block>();
        for (Block b : all) {
            if (b != null) list.add(b);
        }
        return list.toArray(new Block[list.size()]);
    }

    /**
     * 让**镐类和斧类**都能作用于机器方块。
     *
     * 必须在方块和物品都注册完之后调用（这里挂在 ItemRegistryEvent 上）。
     * 镐一般本来就有效（机器方块用的是 Material.iron），但一并登记不会有副作用，
     * 而且万一以后有方块换了材质也不会漏。
     */
    public static void registerMachineToolEffectiveness() {
        Block[] machines = machineBlocks();
        if (machines.length == 0) {
            System.out.println("[CreateMITE][工具] 机器方块还没注册，跳过");
            return;
        }

        int touched = 0;
        for (int id = 0; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (!(item instanceof ItemTool)) continue;
            ItemTool tool = (ItemTool) item;

            String type = tool.getToolType();
            if (!isAllowedToolType(type)) continue;

            boolean before = tool.isEffectiveAgainstBlock(machines[0], 0);
            // ★ 白名单里的类别都要显式登记：镐/战锤的材质表里本来有 iron，
            //   斧/手斧/战斧/鹤嘴锄没有，不登记就会"等级够了却挖不出掉落"。
            tool.addBlocksEffectiveAgainst(machines);
            boolean after = tool.isEffectiveAgainstBlock(machines[0], 0);
            if (!before && after) touched++;

            System.out.println("[CMTOOL] id=" + id
                    + " | class=" + tool.getClass().getSimpleName()
                    + " | type=" + type
                    + " | mat=" + tool.getToolMaterialName()
                    + " | lvl=" + tool.getMaterialHarvestLevel()
                    + " | before=" + before
                    + " | after=" + after);
        }
        logStrictPickaxeDiagnostics();
        System.out.println("[CreateMITE][工具] 机器方块工具规则就绪：白名单 = "
                + CMConfig.getString("mining.allowed_tool_types", DEFAULT_ALLOWED_TYPES)
                + "，本次新登记 " + touched + " 件；闸门 = 白名单类别 **且** MITE 自己的 isEffectiveAgainstBlock ✓"
                + "；等级读的是**每个方块自己的** getMinHarvestLevel(meta)：机器方块 = "
                + net.dsh.createmite.kinetics.block.BlockKineticBase.MIN_HARVEST_LEVEL
                + "（铁起步），熔炉六件套 = 按材质 3/2/2，且**不登记进机器方块名单** → 只有镐类挖得动 ✓");
    }
}
