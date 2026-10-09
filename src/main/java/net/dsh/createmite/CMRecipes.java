package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.FurnaceRecipes;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.xiaoyu233.fml.reload.event.RecipeRegistryEvent;

/** 熔炼与合成 */
public final class CMRecipes {

    private CMRecipes() {}

    public static void register(RecipeRegistryEvent event) {
        if (CMItems.rawZinc == null) return;

        // ================= ★ 碗装 / 桶装饮料（2026-10-06 用户定稿 ✓）=================
        //   ① **热水碗** = 水碗(1167) 进熔炉 ✓
        //   ② **冰水碗** = 水碗 ＋ 雪球（**无序**合成 ✓ 用户指定 ✓）
        //   ③ **热牛奶碗** = 牛奶碗(1166) 进熔炉 ✓
        //   ④ **热奶桶 ×7** = 各自材质的牛奶桶 进熔炉 ✓（铁 335 ／ 铜 1160 ／ 银 1161 ／ 金 1162
        //        秘银 1163 ／ 艾德曼 1164 ／ 古代金属 1165 ✓）
        //   ★ 熔炼热值：**故意不写进 FurnaceHeatMixin** ✗ ⇒ 走 MITE 默认热值 1
        //     ⇒ **砂岩/黏土/硬化黏土熔炉也能烧** ✓（和暖手石一个路子 ✓）
        //   ⚠️ **温水没有配方** ✓ —— 两级降温链自动出来的（见 ItemBowlDrink.onUpdate ✓）：
        //      热水碗 --5 分钟--> **温水碗** --3 分钟--> **水碗(1167)** ✓（用户 2026-10-07 补充后半段 ✓）
        if (CMItems.hotWaterBowl != null && net.minecraft.Item.itemsList[CMItems.ID_EMPTY_BOWL] != null) {
            FurnaceRecipes.smelting().addSmelting(1167, new ItemStack(CMItems.hotWaterBowl, 1));   // 水碗 → 热水碗 ✓
            System.out.println("[CreateMITE] 热水碗熔炼已注册：水碗 → 热水碗（任意熔炉 ✓）");
        }
        if (CMItems.hotMilkBowl != null && net.minecraft.Item.itemsList[1166] != null) {
            FurnaceRecipes.smelting().addSmelting(1166, new ItemStack(CMItems.hotMilkBowl, 1));    // 牛奶碗 → 热牛奶碗 ✓
            System.out.println("[CreateMITE] 热牛奶碗熔炼已注册：牛奶碗 → 热牛奶碗 ✓");
        }
        for (int i = 0; i < CMItems.ID_MILK_BUCKETS.length; i++) {
            if (CMItems.hotMilkBucket[i] == null) continue;
            FurnaceRecipes.smelting().addSmelting(CMItems.ID_MILK_BUCKETS[i],
                    new ItemStack(CMItems.hotMilkBucket[i], 1));                                   // 奶桶 → 热奶桶 ✓
        }
        System.out.println("[CreateMITE] 热奶桶熔炼已注册：7 种材质的奶桶 → 对应热奶桶 ✓");
        if (CMItems.iceWaterBowl != null) {
            event.registerShapelessRecipe(new ItemStack(CMItems.iceWaterBowl, 1), false,
                    new ItemStack(net.minecraft.Item.itemsList[1167], 1, 0),                       // 水碗 ✓
                    new ItemStack(net.minecraft.Item.snowball, 1, 0));                             // 雪球 ✓
            System.out.println("[CreateMITE] 冰水碗配方已注册：水碗 + 雪球（无序）→ 冰水碗 ✓");
        }

        // ================= ★ 套餐 A：苹果派线 ＋ 巧克力奶线（2026-10-07 用户拍板 ✓）=================
        //   ★ 用户裁定①：**汤类不加热** ✗（它们本来就是热的 ✓）⇒ 只做热苹果派 ＋ 热巧克力奶 ✓
        //   ★ 用户裁定②：**苹果派允许回炉再加热** ✓ ⇒ 苹果派 → 熔炉 → 热苹果派 ✓（循环 ✓）
        //   链条：
        //     面团(1185) ＋ 苹果(260) ＋ 糖(353) ＋ 鸡蛋(344)  --无序--> **苹果派胚**
        //     苹果派胚 --熔炉--> **热苹果派** --放 5 分钟--> **苹果派** --熔炉--> 热苹果派 ✓
        //     巧克力(1186) ＋ 牛奶碗(1166)  --无序--> **巧克力奶** --熔炉--> **热巧克力奶**
        //     热巧克力奶 --放 5 分钟--> 巧克力奶 ✓
        //   ★ 熔炼同样**不写进 FurnaceHeatMixin** ✗ ⇒ 任何熔炉都能烤 ✓（和饮料一致 ✓）
        if (CMItems.applePieRaw != null) {
            event.registerShapelessRecipe(new ItemStack(CMItems.applePieRaw, 1), false,
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_DOUGH], 1, 0),      // 面团 ✓
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_APPLE], 1, 0),      // 苹果 ✓
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_SUGAR], 1, 0),      // 糖 ✓
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_EGG], 1, 0));       // 鸡蛋 ✓
            System.out.println("[CreateMITE] 苹果派胚配方已注册：面团 + 苹果 + 糖 + 鸡蛋（无序）✓");
        }
        if (CMItems.hotApplePie != null && CMItems.applePieRaw != null) {
            FurnaceRecipes.smelting().addSmelting(CMItems.applePieRaw.itemID,
                    new ItemStack(CMItems.hotApplePie, 1));                                   // 苹果派胚 → 热苹果派 ✓
        }
        if (CMItems.hotApplePie != null && CMItems.applePie != null) {
            FurnaceRecipes.smelting().addSmelting(CMItems.applePie.itemID,
                    new ItemStack(CMItems.hotApplePie, 1));                                   // ★ 苹果派 → 回炉 → 热苹果派 ✓（用户批准 ✓）
            System.out.println("[CreateMITE] 苹果派熔炼已注册：胚 → 热苹果派，苹果派 → 回炉 → 热苹果派 ✓");
        }
        if (CMItems.chocolateMilkBowl != null) {
            event.registerShapelessRecipe(new ItemStack(CMItems.chocolateMilkBowl, 1), false,
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_CHOCOLATE], 1, 0),   // 巧克力 ✓
                    new ItemStack(net.minecraft.Item.itemsList[CMItems.ID_MILK_BOWL], 1, 0));  // 牛奶碗 ✓
            System.out.println("[CreateMITE] 巧克力奶配方已注册：巧克力 + 牛奶碗（无序）✓");
        }
        if (CMItems.hotChocolateMilkBowl != null && CMItems.chocolateMilkBowl != null) {
            FurnaceRecipes.smelting().addSmelting(CMItems.chocolateMilkBowl.itemID,
                    new ItemStack(CMItems.hotChocolateMilkBowl, 1));                          // 巧克力奶 → 热巧克力奶 ✓
            System.out.println("[CreateMITE] 热巧克力奶熔炼已注册：巧克力奶 → 热巧克力奶 ✓");
        }

        // ---- 锌/黄铜 ----
        FurnaceRecipes.smelting().addSmelting(CMItems.rawZinc.itemID, new ItemStack(CMItems.ingotZinc, 1));
        FurnaceRecipes.smelting().addSmelting(CMItems.crushedRawZinc.itemID, new ItemStack(CMItems.ingotZinc, 1));

        // ★ 2026-09-28：**粉碎矿物 → 对应锭** ✓（用户："将相应矿物的粉碎产物对应相应的熔炼配方"）
        //   【熔炼热值要求 = 它对应矿石的热值】✓（用户："熔炼要求也要统一"）
        //     铁/金/铜/银 = 2（圆石熔炉）／**秘银 = 3（黑曜石熔炉）**／**艾德曼 = 4（下界岩熔炉）**
        //   → 热值在 FurnaceHeatMixin 里按输入物品 id 统一设定 ✓
        addCrushedSmelting(CMItems.crushedIron, Item.ingotIron);
        addCrushedSmelting(CMItems.crushedGold, Item.ingotGold);
        addCrushedSmelting(CMItems.crushedCopper, Item.ingotCopper);
        addCrushedSmelting(CMItems.crushedSilver, Item.ingotSilver);
        addCrushedSmelting(CMItems.crushedMithril, Item.ingotMithril);
        addCrushedSmelting(CMItems.crushedAdamantium, Item.ingotAdamantium);
        event.registerShapelessRecipe(
                new ItemStack(CMItems.ingotBrass, 2), false,
                new ItemStack(Item.ingotCopper, 1, 0), new ItemStack(CMItems.ingotZinc, 1, 0));

        // ================= ★ 暖手石 / 冷暖手石（2026-10-05 用户定稿 ✓）=================
        //   ① 合成（**所有工作台**都能做 ✓ 用户原话 ✓）：
        //        空  圆石  空
        //        圆石  空  圆石      ⇒ 一次出 **2 个冷暖手石** ✓
        //        空  圆石  空
        //   ② 熔炼：**冷暖手石** --任意熔炉--> **暖手石** ✓
        //      ★ 故意**不写进 FurnaceHeatMixin** ✗ ⇒ 走 MITE 的默认热值 **1**
        //        ⇒ 砂岩 / 黏土 / 硬化黏土熔炉（热值 1）**也能烤** ✓
        //          完全符合用户"**所有熔炉均可烤**" ✓（燃料只要热值 ≥ 1：木板/木头/木炭/煤炭… ✓）
        //   ③ 用满两次 ⇒ 物品自己变回冷暖手石 ✓（见 ItemHandWarmer.becomeCold ✓）⇒ 再烤 ⇒ 循环 ✓
        if (CMItems.handWarmerCold != null) {
            event.registerShapedRecipe(new ItemStack(CMItems.handWarmerCold, 2), false,
                    " C ", "C C", " C ",
                    Character.valueOf('C'), new ItemStack(Block.cobblestone, 1, 0));
            System.out.println("[CreateMITE] 暖手石配方已注册：4 圆石（十字）→ 冷暖手石 ×2");
            if (CMItems.handWarmer != null) {
                FurnaceRecipes.smelting().addSmelting(CMItems.handWarmerCold.itemID,
                        new ItemStack(CMItems.handWarmer, 1));
                System.out.println("[CreateMITE] 暖手石熔炼已注册：冷暖手石 → 暖手石（任意熔炉 ✓）");
            }
        } else {
            System.out.println("[CreateMITE] ★ 冷暖手石未注册 → 两条配方已跳过");
        }

        // ---- 锌粒（2026-09-28 新增）：**原版配方原样还原**，不改动 ✓ ----
        //   原版 create 的两条（1.21.1-6.0.10 jar 里的原文）：
        //     zinc_nugget_from_decompacting : 无序 锌锭 ×1        -> 锌粒 ×9
        //     zinc_ingot_from_compacting    : 有序 锌粒 3×3 (9 个) -> 锌锭 ×1
        if (CMItems.zincNugget != null) {
            event.registerShapelessRecipe(new ItemStack(CMItems.zincNugget, 9), false,
                    new ItemStack(CMItems.ingotZinc, 1, 0));
            event.registerShapedRecipe(new ItemStack(CMItems.ingotZinc, 1), false,
                    "###", "###", "###",
                    Character.valueOf('#'), new ItemStack(CMItems.zincNugget, 1, 0));
        } else {
            System.out.println("[CreateMITE] ★ 锌粒未注册 → 锌粒的两条配方已跳过");
        }

        // ---- 黄铜粒（2026-09-28 新增）：和锌粒同构，1 黄铜锭 ↔ 9 黄铜粒 ✓ ----
        if (CMItems.brassNugget != null) {
            event.registerShapelessRecipe(new ItemStack(CMItems.brassNugget, 9), false,
                    new ItemStack(CMItems.ingotBrass, 1, 0));
            event.registerShapedRecipe(new ItemStack(CMItems.ingotBrass, 1), false,
                    "###", "###", "###",
                    Character.valueOf('#'), new ItemStack(CMItems.brassNugget, 1, 0));
        } else {
            System.out.println("[CreateMITE] ★ 黄铜粒未注册 → 黄铜粒的两条配方已跳过");
        }

        // ---- 金属/合金块（2026-09-28 新增）：9 锭 ↔ 1 块 ✓ ----
        registerStorageRecipe(event, CMBlocks.blockZincBlock, CMItems.ingotZinc);
        registerStorageRecipe(event, CMBlocks.blockBrassBlock, CMItems.ingotBrass);
        registerStorageRecipe(event, CMBlocks.blockAndesiteAlloyBlock, CMItems.andesiteAlloy);

        // ---- 多方块熔炉的六个结构方块（2026-09-29 用户给的配方 ✓）----
        registerFurnaceStructureRecipes(event);

        // ---- M1 动力部件（2026-09-28 按原版机械动力对齐）----
        // ★ 任意木板：MITE(1.6.4) 的 ShapedRecipes 里 subtype == 32767 表示"任意元数据"
        //   （javap 实证：sipush 32767 → if_icmpeq）→ 用 32767 就能匹配橡木/云杉/白桦/丛林木四种木板 ✓
        final ItemStack ANY_PLANKS = new ItemStack(Block.planks, 1, 32767);

        if (CMItems.andesiteAlloy != null) {
            // 安山合金：**原版机械动力的对角摆法**（2026-09-28 用户要求还原）：
            //   原版 = 安山岩 + 铁粒（或锌粒），有序对角 "BA"/"AB" ✓
            // 【两处本土化，都不是改配方本身】
            //   · 1.6.4 **没有安山岩**（已查过 MITE 的语言文件：en_US 里没有 andesite/granite/diorite ✗）
            //     → 继续用**圆石**当岩石侧 ✓
            //   · 1.6.4 **没有铁粒**（铁粒是 MC 1.11 才有的 ✗）→ 用我们刚做的**锌粒**（原版第二个变体就是锌粒 ✓）
            // ⚠️ 原版 jar 里这条配方产出是 **1 个**；用户明确要求 **×4**，这里按用户说的做成 4 ✓
            // ★★ 变体一：**铁粒** + 圆石（原版第一个变体）
            //   【更正】之前我以为 1.6.4 没有铁粒 ✗ —— **MITE 自带铁粒** ✓
            //   （javap 实证：net.minecraft.Item.ironNugget，语言键 item.ironNugget.name = Iron Nugget；
            //     铜/银/秘银/艾德曼/远古金属也都有各自的粒 ✓）。用户实测"铁粒 + 圆石合成不了"就是这个原因。
            //   用元数据通配 32767：万一 MITE 的粒带子类型也能匹配上 ✓
            if (Item.ironNugget != null) {
                event.registerShapedRecipe(new ItemStack(CMItems.andesiteAlloy, 4), false,
                        "BA", "AB",
                        Character.valueOf('B'), new ItemStack(Item.ironNugget, 1, 32767),
                        Character.valueOf('A'), new ItemStack(Block.cobblestone, 1, 0));
            } else {
                System.out.println("[CreateMITE] ★ 没找到 MITE 的铁粒（Item.ironNugget）→ 只保留锌粒变体");
            }

            // 变体二：**锌粒** + 圆石（原版第二个变体 ✓）
            if (CMItems.zincNugget != null) {
                event.registerShapedRecipe(new ItemStack(CMItems.andesiteAlloy, 4), false,
                        "BA", "AB",
                        Character.valueOf('B'), new ItemStack(CMItems.zincNugget, 1, 0),
                        Character.valueOf('A'), new ItemStack(Block.cobblestone, 1, 0));
            } else {
                System.out.println("[CreateMITE] ★ 锌粒未注册 → 安山合金配方退回「圆石 + 铁锭」版");
                event.registerShapedRecipe(new ItemStack(CMItems.andesiteAlloy, 4), false,
                        "BA", "AB",
                        Character.valueOf('B'), new ItemStack(Item.ingotIron, 1, 0),
                        Character.valueOf('A'), new ItemStack(Block.cobblestone, 1, 0));
            }

            // 传动轴：原版「安山合金 ×2 竖排 → 8 根」；用户要求**产量减半** → 4 根 ✓
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockShaft, 4), false,
                    "A", "A",
                    Character.valueOf('A'), new ItemStack(CMItems.andesiteAlloy, 1, 0));

            // ---- 五根金属传动杆（2026-09-30 用户规格：材料 = 对应金属锭 + 圆石 ✓）----
            //   摆法 「锭 圆石 / 圆石 锭」（2x2 共四个物品 ✓）→ 1 根
            // ★★ 2026-09-30 修正②：工作台等级**不是**看锭的制造难度 ✗，而是看**产物物品的材质** ✗ ——
            //   MITE 的 RecipeHelper.addRecipe 会给我们这条配方自动设
            //   setMaterialToCheckToolBenchHardnessAgainst(产物物品.getHardestMetalMaterial()) ✓，
            //   而 ItemBlock 的材质是从**方块材质**抄来的（Block.addItemBlockMaterials ✓）。
            //   所以：闸门完全由 CMBlocks 里那根传动杆的 Material 决定 ✓
            //     （铜/银/金 = 4.0 → 至少铜工作台 ✓；铁 = 8.0 → 至少铁工作台 ✓；秘银 = 64.0 → 至少秘银工作台 ✓）
            //   启动日志里有一行「[CreateMITE] 传动杆工作台闸门: ...」可以自查 ✓
            registerMaterialShaftRecipe(event, CMBlocks.shaftCopper, Item.ingotCopper);
            registerMaterialShaftRecipe(event, CMBlocks.shaftSilver, Item.ingotSilver);
            registerMaterialShaftRecipe(event, CMBlocks.shaftGold, Item.ingotGold);
            registerMaterialShaftRecipe(event, CMBlocks.shaftIron, Item.ingotIron);
            registerMaterialShaftRecipe(event, CMBlocks.shaftMithril, Item.ingotMithril);

            // 手摇曲柄：原版摆法 **加上两根木棍**（用户 2026-09-28 指定）：
            //   第 1 行 = 木板 木板 木板
            //   第 2 行 = 空   木棍 安山合金      ← 第二行第二格 = 木棍
            //   第 3 行 = 木棍 空   空            ← 第三行第一格 = 木棍
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockHandCrank, 1), false,
                    "CCC", " SA", "S  ",
                    Character.valueOf('C'), ANY_PLANKS,
                    Character.valueOf('S'), new ItemStack(Item.stick, 1, 0),
                    Character.valueOf('A'), new ItemStack(CMItems.andesiteAlloy, 1, 0));
        } else {
            System.out.println("[CreateMITE] ★ 安山合金未注册 → 安山合金/传动轴/手摇曲柄配方已跳过");
        }

        // ★★ 齿轮 / 大齿轮：**改回原版合成方法**（2026-09-28 用户要求）
        //   【为什么改回来】之前用户让我们把齿轮改成"传动轴围一圈木板"，
        //   但那个 3×3 环 + 中心传动轴的形状，**和水车的原版配方一模一样** ✗ ——
        //   两条配方完全撞车（谁先注册谁生效），用户实测发现冲突后要求还原原版 ✓。
        //   原版 Create 的两条（1.21.1-6.0.10 jar 原文）：
        //     cogwheel       : 无序 shaft + planks      → ×1
        //     large_cogwheel : 无序 shaft + planks ×2   → ×1
        //   木板的通配 32767 在**无序**配方里同样有效 ✓
        //   （javap 实证 net.minecraft.ShapelessRecipes 里也有 sipush 32767 的通配分支 ✓）
        final ItemStack PLANKS_B = new ItemStack(Block.planks, 1, 32767);   // 独立实例，避免同一对象被两条配方共用

        // ★★ 2026-09-28 末：**用户给的定型摆法**（他直接发了合成表截图）——
        //   齿轮    ： 空 木板 空 ／ 木板 传动轴 木板 ／ 空 木板 空  → 齿轮 ×1
        //   大齿轮  ： 空 木板 空 ／ 木板 齿轮   木板 ／ 空 木板 空  → 大齿轮 ×1
        //   木板 = **任意木板**（32767 通配）✓（用户原话："任意木板皆可"）
        //   ⚠️ 和水车区分清楚：水车是木板**围满一整圈**（八个）+ 中心传动轴 ✗ 不是十字 ✗，两条不会撞车 ✓
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockCogwheel, 1), false,
                " P ", "PSP", " P ",
                Character.valueOf('P'), ANY_PLANKS,
                Character.valueOf('S'), new ItemStack(CMBlocks.blockShaft, 1, 0));

        // ★ 石磨（2026-09-28 用户定稿的 3×3）：
        //   安山机壳  任意原木  安山机壳
        //   小齿轮    铁块      小齿轮
        //   安山机壳  安山机壳  安山机壳
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockMillstone, 1), false,
                "CLC", "GIG", "CCC",
                Character.valueOf('C'), new ItemStack(CMBlocks.blockAndesiteCasing, 1, 0),
                Character.valueOf('L'), new ItemStack(Block.wood, 1, 32767),
                Character.valueOf('G'), new ItemStack(CMBlocks.blockCogwheel, 1, 0),
                Character.valueOf('I'), new ItemStack(Block.blockIron, 1, 0));

        // ---- M2 批次 1：传动扩展 ----
        // ★★ 大齿轮：**v0.5 起的原版配方 = 无序 齿轮 ×1 + 任意木板 ×1 → 大齿轮 ×1** ✓
        //   【依据】mcmod「大齿轮」合成表（www.mcmod.cn/item/tab/196523.html）逐条核对：
        //     · 传动杆 + 木板 ×2   → 备注「**在 0.5 中被移除**」✗（我们之前用的就是这条 ✗，用户实测发现不对）
        //     · **齿轮 + 木板**     → 备注「**需要 0.5 或更高版本**」✓ ← 就是这一条
        //     · 流水线装配（机械手/动力锯）→ 机器配方，我们不做
        //   1.21.1-6.0.10 jar 里两条都在（large_cogwheel.json = 轴+木板×2、
        //   large_cogwheel_from_little.json = 齿轮+木板），但**按资料站的口径，当前版本用"齿轮+木板"** ✓
        //   → 只保留「齿轮 + 木板」这一条（旧的那条是老版本残留，删掉免得混淆）✗
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockLargeCogwheel, 1), false,
                " P ", "PGP", " P ",
                Character.valueOf('P'), PLANKS_B,
                Character.valueOf('G'), new ItemStack(CMBlocks.blockCogwheel, 1, 0));

        // ---- 2026-09-28 用户定稿：这三条**照原版机械动力还原** ✓ ----
        // 十字齿轮箱（原版 gearbox.json）：十字 —— 中心 **安山机壳**，上下左右各一个 **齿轮**
        //     " C "
        //     "CBC"
        //     " C "
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockGearbox, 1), false,
                " C ", "CBC", " C ",
                Character.valueOf('C'), new ItemStack(CMBlocks.blockCogwheel, 1, 0),
                Character.valueOf('B'), new ItemStack(CMBlocks.blockAndesiteCasing, 1, 0));

        // 离合器（原版 clutch.json）：无序 安山机壳 + 传动轴 + 红石粉
        event.registerShapelessRecipe(new ItemStack(CMBlocks.blockClutch, 1), false,
                new ItemStack(CMBlocks.blockAndesiteCasing, 1, 0),
                new ItemStack(CMBlocks.blockShaft, 1, 0),
                new ItemStack(Item.redstone, 1, 0));

        // 反转齿轮箱（原版 gearshift.json）：无序 安山机壳 + **齿轮** + 红石粉
        event.registerShapelessRecipe(new ItemStack(CMBlocks.blockGearshift, 1), false,
                new ItemStack(CMBlocks.blockAndesiteCasing, 1, 0),
                new ItemStack(CMBlocks.blockCogwheel, 1, 0),
                new ItemStack(Item.redstone, 1, 0));

        // 扳手（原版 wrench.json）：有序 GG / GP / " S"
        //   原版 G = 金板；MITE 没有"金属板" ✗ → 用户定稿：**暂时用金压力板代替** ✓
        //     "GG"
        //     "GP"
        //     " S"
        event.registerShapedRecipe(new ItemStack(CMItems.wrench, 1), false,
                "GG", "GP", " S",
                Character.valueOf('G'), new ItemStack(Block.pressurePlateGold, 1, 0),
                Character.valueOf('P'), new ItemStack(CMBlocks.blockCogwheel, 1, 0),
                Character.valueOf('S'), new ItemStack(Item.stick, 1, 0));

        // ---- 水车 / 大型水车（2026-09-28 **还原原版配方**）----
        // 原版 water_wheel       : 有序 3×3 环 木板×8 + **中心传动轴** → 水车 ×1 ✓
        // 原版 large_water_wheel : 有序 3×3 环 木板×8 + **中心水车**   → 大型水车 ×1 ✓（"水车再包一圈木板"）
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockWaterWheel, 1), false,
                "SSS", "SCS", "SSS",
                Character.valueOf('S'), ANY_PLANKS,
                Character.valueOf('C'), new ItemStack(CMBlocks.blockShaft, 1, 0));
        event.registerShapedRecipe(new ItemStack(CMBlocks.blockLargeWaterWheel, 1), false,
                "SSS", "SCS", "SSS",
                Character.valueOf('S'), ANY_PLANKS,
                Character.valueOf('C'), new ItemStack(CMBlocks.blockWaterWheel, 1, 0));

        // ★★ 粉碎轮（2026-09-28 末 用户定稿的 3×3）：
        //     铁锭    铁锭      铁锭
        //     秘银锭  黑曜石熔炉  秘银锭
        //     银块    铜砧      金块
        //   【铜砧可换】用户原话："配方中的铜砧可替换金砧、银砧合成（非必须铜砧）"
        //   → 同一套摆法**注册三条**，只有砧的材质不同 ✓（MITE 的配方系统没有"任意砧"这种标签 ✗，
        //      FML 的 OreDictionary 在这个版本的 RecipeRegistryEvent 里也用不上 ✗ → 三条最稳 ✓）
        //   【砧用 32767 通配】MITE 的砧有"完好/轻微损坏/严重损坏"三种 metadata ✓ → 通配后任意状态都能用 ✓
        //   产出仍是 **2 个**（和原版一致；配方变贵了但数量不改 ✓）
        final ItemStack CR_IRON = new ItemStack(Item.ingotIron, 1, 0);
        final ItemStack CR_MITHRIL = new ItemStack(Item.ingotMithril, 1, 0);
        final ItemStack CR_OVEN = new ItemStack(Block.furnaceObsidianIdle, 1, 0);
        final ItemStack CR_SILVER = new ItemStack(Block.blockSilver, 1, 0);
        final ItemStack CR_GOLD = new ItemStack(Block.blockGold, 1, 0);
        final net.minecraft.Block[] CR_ANVILS = {
                Block.anvilCopper, Block.anvilSilver, Block.anvilGold
        };
        for (int ai = 0; ai < CR_ANVILS.length; ai++) {
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockCrushingWheel, 2), false,
                    "III", "MFM", "SAG",
                    Character.valueOf('I'), CR_IRON,
                    Character.valueOf('M'), CR_MITHRIL,
                    Character.valueOf('F'), CR_OVEN,
                    Character.valueOf('S'), CR_SILVER,
                    Character.valueOf('G'), CR_GOLD,
                    Character.valueOf('A'), new ItemStack(CR_ANVILS[ai], 1, 32767));
        }

        // ---- M3：机壳系列 ----
        // 资料 227807/227809 的比例是"木板×6 + 安山合金×2 + 原木×1 → ×4"，
        // MITE 里没有安山合金/黄铜板 ✗ → 材料本土化：安山机壳用**锌锭**、黄铜机壳用**黄铜锭** ✓
        // ★★ 2026-09-28 用户定稿：机壳改成**无序合成**（一步搞定）
        //   安山机壳 = 无序 **任意原木 + 安山合金** → 1 ✓
        //   黄铜机壳 = 无序 **任意原木 + 黄铜锭**   → 1 ✓
        //   （原木用 32767 通配 → 橡木/云杉/白桦/丛林木都能用 ✓）
        event.registerShapelessRecipe(new ItemStack(CMBlocks.blockAndesiteCasing, 1), false,
                new ItemStack(Block.wood, 1, 32767),
                new ItemStack(CMItems.andesiteAlloy, 1, 0));
        event.registerShapelessRecipe(new ItemStack(CMBlocks.blockBrassCasing, 1), false,
                new ItemStack(Block.wood, 1, 32767),
                new ItemStack(CMItems.ingotBrass, 1, 0));

        System.out.println("[CreateMITE] 配方已注册（含 M1 动力部件 + M2 批次 1 传动扩展 + M2 批次 2 新方块）");
    }

    /**
     * 该配方产物是否属于"本模组主动删掉的 MITE 原版配方"。
     *
     * 目前只有一条：**小麦 × 3 → 面粉**（删掉后面粉只能从石磨磨出来，1 小麦 = 1 面粉）。
     * 由 {@link net.dsh.createmite.mixin.CraftingManagerMixin} 在 CraftingManager
     * 构造结束时调用 —— 之所以不用 FML 的 RecipeModifyEvent，那里面有个会崩的类加载器缺陷，
     * 详见那个 Mixin 的注释。
     */
    /** 注册一条"粉碎矿物 → 对应锭"的熔炼配方 ✓（任一边为 null 就跳过 ✓，不抛异常） */
    private static void addCrushedSmelting(Item crushed, Item ingot) {
        if (crushed == null || ingot == null) {
            System.out.println("[CreateMITE] ★ 粉碎矿熔炼跳过（物品未注册）");
            return;
        }
        FurnaceRecipes.smelting().addSmelting(crushed.itemID, new ItemStack(ingot, 1, 0));
    }

    /**
     * 多方块熔炉的六个结构方块 —— 配方**逐字照用户 2026-09-29 给的** ✓
     *
     * 圆石组（核心/传动杆）：至少**铜工作台** ✓（用户："其中的铜工作台可替换为银工作台、金工作台"）
     * 黑曜石组：至少**铁工作台** ✓
     * 地狱岩组：至少**秘银工作台** ✓
     *
     * 【工作台等级怎么表达】MITE 的工作台是 Block[58] 的元数据 ✓
     *   （参考导出 block_metadata：4=铜 5=银 6=金 7=铁 8=远古金属 9=秘银 10=艾德曼 ✓）
     * 「最低要求」不写在这里 ✗ —— 统一交给 CraftingManagerMixin → toolBenchMaterialFor(out) ✓
     * （沿用 MITE 自己的机制 IRecipe.setMaterialToCheckToolBenchHardnessAgainst ✓，
     *   比它更高级的工作台照样能合成 ✓）
     *
     * 【通配】石熔炉/黑曜石熔炉/地狱岩熔炉的元数据是朝向(2-5) → 用 32767 通配 ✓
     *   只匹配"未点燃"那一档：石熔炉 = Block[61]（Block.furnaceIdle）、
     *   黑曜石熔炉 = 222、地狱岩熔炉 = 224 ✓ 与用户写的名字一致 ✓
     */
    /** 金属传动杆：**对应金属锭 + 圆石**（摆成 2x2 共四个物品 ✓）→ 1 根 ✓ */
    private static void registerMaterialShaftRecipe(RecipeRegistryEvent event, net.minecraft.Block shaft,
                                                    net.minecraft.Item ingot) {
        if (shaft == null || ingot == null) return;
        event.registerShapedRecipe(new ItemStack(shaft, 1), false,
                "AB", "BA",
                Character.valueOf('A'), new ItemStack(ingot, 1, 0),
                Character.valueOf('B'), new ItemStack(Block.cobblestone, 1, 0));
    }

    private static void registerFurnaceStructureRecipes(RecipeRegistryEvent event) {
        if (CMBlocks.blockCobblestoneFurnaceCore == null || CMBlocks.blockShaft == null) {
            System.out.println("[CreateMITE] ★ 熔炉六件套方块未注册 → 六条配方已跳过");
            return;
        }
        // ★ MITE 只给自己的方块设过"制造难度"，**原版石熔炉 Block[61] 没设** ✗ ——
        //   拿它当材料时 FML 会刷 6 条 "component crafting difficulty not set: Stone Furnace [61]" ✗，
        //   而且这条配方自己的难度也算不出来 ✗。按 MITE 参考导出里的数值补上 ✓：
        //   recipe_components.txt →「Item[61] 石熔炉 (idle): difficulty = 1600」✓
        Item stoneFurnaceItem = Item.itemsList[Block.furnaceIdle.blockID];
        if (stoneFurnaceItem != null) {
            float d = stoneFurnaceItem.getLowestCraftingDifficultyToProduce();
            if (d <= 0.0F || d >= Float.MAX_VALUE) {
                stoneFurnaceItem.setLowestCraftingDifficultyToProduce(1600.0F);
                System.out.println("[CreateMITE] 石熔炉的制造难度已补设为 1600（MITE 自己没给原版方块设过 ✓）");
            }
        }

        Block obsidianFurnace = Block.blocksList[222];     // 黑曜石熔炉（未点燃）
        Block netherrackFurnace = Block.blocksList[224];   // 地狱岩熔炉（未点燃）
        final int ANY = 32767;

        // ① 圆石熔炉核心：圆石×6 + 石熔炉×2 + 工作台×1（铜 4 / 银 5 / 金 6 各一条 ✓）
        int[] benchMetas = {4, 5, 6};
        for (int i = 0; i < benchMetas.length; i++) {
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockCobblestoneFurnaceCore, 1), false,
                    "CCC", "FWF", "CCC",
                    Character.valueOf('C'), new ItemStack(Block.cobblestone, 1, 0),
                    Character.valueOf('F'), new ItemStack(Block.furnaceIdle, 1, ANY),
                    Character.valueOf('W'), new ItemStack(Block.workbench, 1, benchMetas[i]));
        }

        // ② 圆石熔炉传动杆：十字（圆石 + 中心传动杆 ✓）
        // ★ 2026-09-30 用户规格：中心改为 **铜 / 银 / 金 传动杆**（三种都能用 → 三条配方 ✓）
        if (CMBlocks.wrappedShaftCobblestone != null) {
            net.minecraft.Block[] centers = {CMBlocks.shaftCopper, CMBlocks.shaftSilver, CMBlocks.shaftGold};
            for (net.minecraft.Block c : centers) {
                if (c == null) continue;
                event.registerShapedRecipe(new ItemStack(CMBlocks.wrappedShaftCobblestone, 1), false,
                        " C ", "CSC", " C ",
                        Character.valueOf('C'), new ItemStack(Block.cobblestone, 1, 0),
                        Character.valueOf('S'), new ItemStack(c, 1, 0));
            }
        }

        // ③ 黑曜石熔炉核心：黑曜石×6 + 黑曜石熔炉×2 + **铁工作台**（元数据 7）
        if (CMBlocks.blockObsidianFurnaceCore != null && obsidianFurnace != null) {
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockObsidianFurnaceCore, 1), false,
                    "OOO", "FWF", "OOO",
                    Character.valueOf('O'), new ItemStack(Block.obsidian, 1, 0),
                    Character.valueOf('F'), new ItemStack(obsidianFurnace, 1, ANY),
                    Character.valueOf('W'), new ItemStack(Block.workbench, 1, 7));
        }

        // ④ 黑曜石熔炉传动杆：十字（黑曜石 + 中心传动杆 ✓）
        // ★ 2026-09-30 用户规格：中心 = **机械动力自带传动杆 或 铁传动杆**（两条配方 ✓）
        if (CMBlocks.wrappedShaftObsidian != null) {
            net.minecraft.Block[] centers = {CMBlocks.blockShaft, CMBlocks.shaftIron};
            for (net.minecraft.Block c : centers) {
                if (c == null) continue;
                event.registerShapedRecipe(new ItemStack(CMBlocks.wrappedShaftObsidian, 1), false,
                        " O ", "OSO", " O ",
                        Character.valueOf('O'), new ItemStack(Block.obsidian, 1, 0),
                        Character.valueOf('S'), new ItemStack(c, 1, 0));
            }
        }

        // ⑤ 地狱岩熔炉核心：地狱岩×6 + 地狱岩熔炉×2 + **秘银工作台**（元数据 9）
        if (CMBlocks.blockNetherrackFurnaceCore != null && netherrackFurnace != null) {
            event.registerShapedRecipe(new ItemStack(CMBlocks.blockNetherrackFurnaceCore, 1), false,
                    "NNN", "FWF", "NNN",
                    Character.valueOf('N'), new ItemStack(Block.netherrack, 1, 0),
                    Character.valueOf('F'), new ItemStack(netherrackFurnace, 1, ANY),
                    Character.valueOf('W'), new ItemStack(Block.workbench, 1, 9));
        }

        // ⑥ 地狱岩熔炉传动杆：十字（地狱岩 + 中心传动杆 ✓）
        // ★ 2026-09-30 用户规格：中心改为 **秘银传动杆** ✓
        if (CMBlocks.wrappedShaftNetherrack != null && CMBlocks.shaftMithril != null) {
            event.registerShapedRecipe(new ItemStack(CMBlocks.wrappedShaftNetherrack, 1), false,
                    " N ", "NSN", " N ",
                    Character.valueOf('N'), new ItemStack(Block.netherrack, 1, 0),
                    Character.valueOf('S'), new ItemStack(CMBlocks.shaftMithril, 1, 0));
        }

        System.out.println("[CreateMITE] 熔炉六件套配方已注册：圆石核心×3(铜/银/金工作台)+圆石传动杆"
                + " ｜ 黑曜石核心+传动杆(铁工作台) ｜ 地狱岩核心+传动杆(秘银工作台) —— 共 8 条 ✓");
    }

    /**
     * 注册"9 锭 ↔ 1 块"两条（有序合 / 无序拆）✓。
     *
     * 任一边为 null（方块 id 被占、或合金没注册）就整组跳过 ✓，不抛异常。
     */
    private static void registerStorageRecipe(RecipeRegistryEvent event, Block block, Item item) {
        if (block == null || item == null) {
            System.out.println("[CreateMITE] ★ 金属块配方跳过（方块或材料未注册）");
            return;
        }
        event.registerShapedRecipe(new ItemStack(block, 1), false,
                "III", "III", "III",
                Character.valueOf('I'), new ItemStack(item, 1, 0));
        event.registerShapelessRecipe(new ItemStack(item, 9), false,
                new ItemStack(block, 1, 0));
    }

    /**
     * 这条配方的产物是不是"我们模组的物品 / 方块"✓
     * —— 用来统一给它们设"**最低工作台要求**"（见 CraftingManagerMixin）。
     *
     * 本模组占用的 id：方块 2300、2310–**2341**（含熔炉六件套 2336–2341 ✓）；物品 2356–2363 ✓
     */
    public static boolean isOurRecipeOutput(ItemStack out) {
        if (out == null) return false;
        int id = out.itemID;
        return (id >= 2300 && id <= 2341) || (id >= 2356 && id <= 2363);
    }

    /** 粉碎轮 2321：需要"秘银级工作台"的配方之一 ✓ */
    public static final int ID_CRUSHING_WHEEL = 2321;

    /**
     * 这条配方要求的**最低工作台材料**（CraftingManagerMixin 用它设 MITE 自己的闸 ✓）。
     *
     * 用户 2026-09-29 的口径：
     *   圆石熔炉核心 / 圆石熔炉传动杆 → 至少**铜**工作台 ✓
     *   黑曜石熔炉核心 / 黑曜石熔炉传动杆 → 至少**铁**工作台 ✓
     *   地狱岩熔炉核心 / 地狱岩熔炉传动杆 → 至少**秘银**工作台 ✓
     *   粉碎轮 → 秘银 ✓（2026-09-28 用户要求，保持不变）
     *   其余本模组配方 → 铁 ✓（2026-09-28 用户要求，保持不变）
     *
     * ★ MITE 的判定是"工作台材料的硬度 >= 这里给的材料" → **更高级的工作台照样能合成** ✓
     *   （所以铜组拿铜/银/金/铁… 工作台都能合 ✓，与用户说的"可替换为银、金"一致 ✓）
     */
    public static net.minecraft.Material toolBenchMaterialFor(ItemStack out) {
        if (out == null) return net.minecraft.Material.iron;
        switch (out.itemID) {
            case 2338:   // 圆石熔炉核心
            case 2341:   // 圆石熔炉传动杆
                return net.minecraft.Material.copper;
            case 2337:   // 地狱岩熔炉核心
            case 2340:   // 地狱岩熔炉传动杆
                return net.minecraft.Material.mithril;
            case ID_CRUSHING_WHEEL:
                return net.minecraft.Material.mithril;
            default:
                return net.minecraft.Material.iron;
        }
    }

    public static boolean isRemovedRecipe(ItemStack out) {
        if (out == null) return false;
        // ① 小麦 ×3 → 面粉：删掉后面粉只能靠石磨磨（1 小麦 = 1 面粉）✓
        if (out.getItem() == Item.flour) return true;
        // ② ★ 2026-09-28 用户要求：**删掉 MITE 自带的骨粉配方**，骨粉改成只能靠石磨磨
        //    （MITE/原版是 无序 骨头 → 骨粉 ×3 ✓；骨粉 = 染料 15 号 ✓）
        //    石磨那边的产物是 骨头 → 骨粉 ×1（+30% 额外 1 个）✓
        if (out.getItem() == Item.dyePowder && out.getItemSubtype() == 15) return true;
        return false;
    }
}
