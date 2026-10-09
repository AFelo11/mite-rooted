package net.dsh.createmite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

/**
 * Create x MITE 配置：config/createmite.properties
 *
 * 为什么用 .properties 而不是 FML 的配置系统：
 * 本工程里的 mite-cheat-unlock 已经用同一套写法（相对路径 config/xxx.properties，
 * 因为启动时的工作目录就是 .minecraft），实测可用，不再引入新依赖。
 *
 * 懒加载 + 缓存：第一次被问到时读文件；文件不存在就写一份带注释的默认值。
 * 改完要重启游戏生效。
 */
public final class CMConfig {

    /** 应力过载时怎么办 */
    public enum OverstressMode {
        /** 整张网络停机（Create 的做法，默认） */
        STOP,
        /** 不罢工，但按 容量/占用 的比例降速 */
        SLOW,
        /** 只记录状态，不影响转速（调试用） */
        IGNORE
    }

    private static final String DIR = "config";
    private static final String FILE_NAME = "createmite.properties";

    private static final String DEFAULT_TEXT =
            "# ===== MITE-扎根 (MITE-Rooted) 配置 =====\n"
          + "# 改完需要重启游戏生效。\n"
          + "\n"
          + "# ---- 应力过载 ----\n"
          + "# 每个方块“占用”应力，每个动力源“提供”应力；\n"
          + "# 同一张动力网络里 占用总和 > 提供总和 就会过载。\n"
          + "#\n"
          + "# overstress.mode 有三个取值：\n"
          + "#   stop   整张网络直接停机（默认，最像 Create）\n"
          + "#   slow   不罢工，但按 容量/占用 的比例降速\n"
          + "#   ignore 只标记状态，转速不受影响（调试用）\n"
          + "overstress.mode = stop\n"
          + "\n"
          + "# ---- 应力数值 ----\n"
          + "# 手摇曲柄提供 8 应力，正好够带动 1 台石磨(4) 外加几根轴和齿轮；\n"
          + "# 想同时带两台石磨(8) 就会过载 —— 这就是“一台曲柄带一台磨”的手感来源。\n"
          + "stress.impact.shaft = 0.5\n"
          + "stress.impact.cogwheel = 1.0\n"
          + "stress.impact.millstone = 4.0\n"
          + "stress.capacity.hand_crank = 8.0\n"
          + "stress.impact.large_cogwheel = 1.5\n"
          + "stress.impact.gearbox = 1.0\n"
          + "stress.impact.clutch = 0.5\n"
          + "stress.impact.gearshift = 0.5\n"
          + "\n"
          + "# ---- 石磨 ----\n"
          + "# 磨完一份需要的进度点数。一次手摇曲柄大约给 104 点，所以：\n"
          + "#   104 → 摇 1 次出一份；400 → 摇 4 次；800 → 摇 8 次（默认）。\n"
          + "millstone.progress_required = 400\n"
          + "\n"
          + "# ---- 大熔炉：热值池（2026-10-01 用户 ①②③ 定稿）----\n"
          + "# 燃烧值刻度：木质 100 ／ 木炭 200 ／ 煤炭 400 ／ 岩浆桶 800 ／ 烈焰棒 1000\n"
          + "# 烧一份要花：木头档 200 ／ 铜银金锌矿 400 ／ 铁 600 ／ 秘银 800 ／ 艾德曼 1000\n"
          + "# 燃烧值**存进池子、可以叠加**：木头本来烧不了铁矿，\n"
          + "#   但在大熔炉里把木头一份份烧进去、叠够 600 就能烧铁 ✓（池子最多存 10000）\n"
          + "# 但**能烧什么级别**由炉子材质卡死（不是容量）：\n"
          + "#   圆石 600 ／ 黑曜石 800 ／ 下界岩 1000 ⇒ 圆石大熔炉叠到 10000 也烧不了秘银/艾德曼 ✓\n"
          + "#\n"
          + "# 烧一份燃料要多少 tick 才把它的燃烧值存进池子（默认 20 = 1 秒）\n"
          + "#   ⇒ 池子满了就自动停烧，一份燃料都不浪费 ✓\n"
          + "furnace.fuel_burn_ticks = 20\n"
          + "\n"
          + "# ★ 燃烧时长：每多少点热力值 = 能烧 1 分钟（默认 200 ⇒ 满池 10000 = 50 分钟）\n"
          + "#   调大 = 更耐烧（1 点热力值烧更久）；调小 = 更费\n"
        + "#   ⚠️ 只在**真的在烧**（有格在熔炼）时才掉 ✗ —— 空转不掉、走开也不漏 ✓\n"
          + "#\n"
          + "furnace.burn_heat_per_minute = 200\n"
          + "\n"
          + "# ---- 体感效率挂钩（2026-10-05 用户拍板）----\n"
          + "# 挖掘 / 移动的效率表（CMAmbientFeel.digPercent / movePercent）要不要真的生效：\n"
          + "#   0 = 只显示不挂钩（老行为）；1 = 挂钩（默认）\n"
          + "# 口径：挖掘直接乘档位表（50/70/85/90/75/100%）；移动只在 < -15 档 = 70%\n"
          + "# ⚠️ 工具耐久**不受影响** ✓（耐久走 BlockBreakInfo 那条路，只看方块不看耗时 ✓）\n"
          + "feel.dig_hook = 1\n"
          + "feel.move_hook = 1\n"
          + "\n"
          + "# ---- 体感：天气修正（2026-10-05 用户定稿）----\n"
          + "# 天气只有 5 个状态（MITE **没有**阴天/雾天，别找了 ✗）：\n"
          + "#   晴 +3 ／ 雨 -5 ／ 雷暴 -7 ／ 雪 -8 ／ 雪暴 -12\n"
          + "# 雪 = 同一场降水 + 那一列温度 <= 0.15 ⇒ **只会在雪地群系和冬季**出现 ✓（用户指定 ✓）\n"
          + "# 屋檐下 / 矿洞里淋不到 ⇒ **遮挡 = 0** ✓（用户 2026-10-05：**不能白拿晴天的 +3** ✗）\n"
          + "# 0 = 关（天气完全不影响体感）\n"
          + "feel.weather.enabled = 1\n"
          + "feel.weather.clear = 3.0\n"
          + "feel.weather.sheltered = 0.0\n"
          + "feel.weather.rain = -5.0\n"
          + "feel.weather.storm = -7.0\n"
          + "feel.weather.snow = -8.0\n"
          + "feel.weather.blizzard = -12.0\n"
          + "\n"
          + "# ---- 方块自发热 / 吸热（2026-10-05 用户拍板）----\n"
          + "# 格式： heat.<方块> = 发热值℃ , 半径（格）\n"
          + "# 规则：不做遮挡判定（隔墙也算）/ **方块之间不叠加**（含同种方块 ⇒ 只取最强的那个）\n"
          + "#       **物品的暖独立一项**，不受这条限制（暖手石等，见 CMHeat.itemHeat）\n"
          + "#       冷源**不设门槛**（用户：双重惩罚就双重）/ 衰减 = C 方案（前 60% 全额、最后一圈衰减到 0）\n"
          + "# heat.falloff = 0 退回一刀切；heat.interval = 每多少 tick 重算一次（默认 10）\n"
          + "# 本轮只登记：火把 / 火 / 岩浆 / 雪层 / 雪块 / 冰 / 水（熔炉与大熔炉放下一批）\n"
          + "heat.enabled = 1\n"
          + "heat.falloff = 1\n"
          + "heat.interval = 10\n"
          + "heat.torch = 1,2\n"
          + "heat.fire = 12,3\n"
          + "heat.lava = 20,4\n"
          + "heat.snow = -3,3\n"
          + "heat.snow_block = -5,3\n"
          + "heat.ice = -6,3\n"
          + "heat.water = -6,0\n"
          + "\n"
          + "# ---- 暖手石（2026-10-05 深夜 用户要求强化 ✓）----\n"
          + "#   heat = 每次给多少摄氏度（**默认 8** ✓ 用户 2026-10-05 深夜从 3 提到 8 ✓）\n"
          + "#   minutes = 每次持续多少分钟（默认 3 ✓）\n"
          + "#   uses = 烤一次能用几次（默认 2 ✓；用完自动变回冷暖手石 ⇒ 再烤 ⇒ 循环 ✓）\n"
          + "#   ⚠️ 冷却条（物品耐久条）的刻度跟着 minutes 走 ⇒ 改完**要重启**才准 ✓\n"
          + "warm_stone.heat = 8.0\n"
          + "warm_stone.minutes = 3\n"
          + "warm_stone.uses = 2\n"
          + "\n"
          + "# ---- 食物 → 体感温度（2026-10-06 用户拍板）----\n"
          + "# 格式： food.<食物> = 体感℃ , 持续分钟\n"
          + "# ★ 食物是**第三套独立系统**（环境＝方块 / 物品＝暖手石 / 食物＝这一套）⇒ 三者**相加**\n"
          + "# ★ 食物与食物**不叠加**，但可以**顶替**：**绝对值大的赢**\n"
          + "#     暖食：牛肉汤(+12) 顶掉 牛奶(+3) ✓ ／ 冷食：雪葩(-8) 顶掉 水碗(-2) ✓\n"
          + "#     同数值（再吃一碗同样的）⇒ **刷新时间** ✓ ／ 更弱的 ⇒ 无事发生 ✓\n"
          + "# ⚠️ 数值给 0 ＝ 这一项不算（例：沙拉 = 0,0）\n"
          + "food.enabled = 1\n"
          + "food.mushroom_stew = 7,3\n"
          + "food.beef_soup = 12,5\n"
          + "food.chicken_soup = 10,4\n"
          + "food.vegetable_soup = 9,4\n"
          + "food.cream_mush_soup = 8,4\n"
          + "food.cream_veg_soup = 10,4\n"
          + "food.pumpkin_soup = 6,3\n"
          + "food.mashed_potato = 8,3\n"
          + "food.porridge = 5,3\n"
          + "food.cereal = 6,3\n"
          + "food.salad = 0,0\n"
          + "food.milk_bowl = 2,2\n"
          + "food.milk_bucket = 3,2\n"
          + "food.water_bowl = -2,2\n"
          + "food.ice_cream = -6,3\n"
          + "food.sorbet = -8,3\n"
          + "# ---- 2026-10-06 新增：我们自己的碗/桶饮料（用户定稿）----\n"
          + "#   ★★ 降温链（两级，都用耐久条当倒计时 ✓ 逐渐减少 ✓）：\n"
          + "#      热水碗 --drink.hot_water_minutes--> 温水碗 --drink.warm_water_minutes--> 水碗(1167)\n"
          + "#   冰水碗 = 水碗 ＋ 雪球（无序）✓；热牛奶碗 / 热奶桶 ×7 都由熔炉烧出来 ✓\n"
          + "#   ★ 喝完**返还空容器**：碗 → 碗(281) ✓ ／ 奶桶 → **自己材质的空桶** ✓\n"
          + "#     （铜 1142 ／ 银 1143 ／ 金 1144 ／ 铁 325 ／ 秘银 1145 ／ 艾德曼 1146 ／ 古代金属 1147 ✓）\n"
          + "food.hot_water_bowl = 6,3\n"
          + "food.warm_water_bowl = 1,2\n"
          + "food.ice_water_bowl = -4,3\n"
          + "food.hot_milk_bowl = 5,3\n"
          + "food.hot_milk_bucket = 6,3\n"
          + "# ---- 2026-10-07 用户补充：温水碗放 3 分钟会变成水碗 ✓ ----\n"
          + "#   降温链：热水碗 --5 分钟--> 温水碗 --3 分钟--> 水碗(1167) ✓\n"
          + "#   ⚠️ 耐久条刻度跟着这两个数走 ⇒ 改完要重启才准 ✓\n"
          + "drink.hot_water_minutes = 5\n"
          + "drink.warm_water_minutes = 3\n"
          + "# ---- 2026-10-07 套餐 A：苹果派线 ＋ 巧克力奶线（用户拍板 ✓）----\n"
          + "#   苹果派胚 = 面团+苹果+糖+鸡蛋（无序）--熔炉--> **热苹果派** --放 5 分钟--> 苹果派\n"
          + "#   苹果派 --熔炉--> 热苹果派（★ 用户批准「回炉再热」✓ 循环 ✓）\n"
          + "#   巧克力 + 牛奶碗（无序）= **巧克力奶** --熔炉--> **热巧克力奶** --放 5 分钟--> 巧克力奶\n"
          + "food.apple_pie = 7,4\n"
          + "food.hot_apple_pie = 11,4\n"
          + "food.chocolate_milk_bowl = 4,4\n"
          + "food.hot_chocolate_milk_bowl = 9,4\n"
          + "# 热苹果派 / 热巧克力奶 放多少分钟后自己凉（默认 5 分钟 ✓ 改完要重启 ✓）\n"
          + "hot_food.minutes = 5\n";

    /**
     * 已知配置项：{键, 文件里缺这一项时补上去的整段文本}。
     *
     * 为什么要有这个：配置文件只在**第一次**生成时写全，
     * 后续版本新增的键如果不补，玩家就永远看不到新旋钮（改了也没用，因为文件里没有）。
     */
    private static final String[][] KNOWN_KEYS = {
        {"overstress.mode",
         "# 过载表现：stop=整张网络停机 / slow=按容量比例降速 / ignore=只标记不停机\n"
       + "overstress.mode = stop"},
        {"stress.impact.shaft", "stress.impact.shaft = 0.5"},
        {"stress.impact.cogwheel", "stress.impact.cogwheel = 1.0"},
        {"stress.impact.millstone", "stress.impact.millstone = 4.0"},
        {"stress.capacity.hand_crank", "stress.capacity.hand_crank = 8.0"},
        {"stress.impact.large_cogwheel", "stress.impact.large_cogwheel = 1.5"},
        {"stress.impact.gearbox", "stress.impact.gearbox = 1.0"},
        {"stress.impact.clutch", "stress.impact.clutch = 0.5"},
        {"stress.impact.gearshift", "stress.impact.gearshift = 0.5"},
        {"millstone.progress_required",
         "# 石磨磨完一份需要的进度点数。一次手摇曲柄约给 104 点：\n"
       + "#   104 → 摇 1 次出一份；400 → 摇 4 次（默认）；800 → 摇 8 次\n"
       + "millstone.progress_required = 400"},
        {"furnace.fuel_burn_ticks",
          "# ---- 大熔炉：热值池（2026-10-01 用户 ①②③ 定稿）----\n"
       + "# 燃烧值：木质 100 ／ 木炭 200 ／ 煤炭 400 ／ 岩浆桶 800 ／ 烈焰棒 1000\n"
       + "# 门槛（热力值到了才烧得动）：木头档200 ／ 一档矿(金银铜锌)400 ／ 二档矿(铁)600 ／ 三档矿(秘银)800 ／ 四档矿(艾德曼)1000\n"
       + "# 热力值存进池子可叠加（最多 10000 = 50 分钟燃烧时长）\n"
        + "# 能烧到哪一档由材质卡死：圆石=最多铁 ／ 黑曜石=最多秘银 ／ 下界岩=最多艾德曼\n"
        + "#   ⇒ 超出的热力值**不浪费**，照样算燃烧时长 ✓（用户特别注明 ✓）\n"
       + "# 烧一份燃料要多少 tick 才把燃烧值存进池子（默认 20 = 1 秒；池子满就停烧、不浪费 ✓）\n"
       + "furnace.fuel_burn_ticks = 20"},
        {"furnace.burn_heat_per_minute",
          "# 每烧出一份产物扣掉的燃烧值 = 基础值 × 这个倍率\n"
       + "#   调大 = 更耐烧（1 点热力值烧更久）；调小 = 更费\n"
        + "#   ⚠️ 只在**真的在烧**（有格在熔炼）时才掉 ✗ —— 空转不掉、走开也不漏 ✓\n"
       + "furnace.burn_heat_per_minute = 200"},
        {"feel.weather.enabled",
          "# ---- 体感：天气修正（2026-10-05 用户定稿）----\n"
        + "# 天气只有 5 个状态（MITE **没有**阴天/雾天）：晴 +3 ／ 雨 -5 ／ 雷暴 -7 ／ 雪 -8 ／ 雪暴 -12\n"
        + "# 雪 = 同一场降水 + 那一列温度 <= 0.15 ⇒ **只在雪地群系和冬季**出现 ✓\n"
        + "# 屋檐下 / 矿洞里淋不到 ⇒ **遮挡 = 0**（不能白拿晴天的 +3）；0 = 关掉天气修正\n"
        + "feel.weather.enabled = 1"},
        {"feel.weather.clear", "feel.weather.clear = 3.0"},
        {"feel.weather.sheltered", "feel.weather.sheltered = 0.0"},
        {"feel.weather.rain", "feel.weather.rain = -5.0"},
        {"feel.weather.storm", "feel.weather.storm = -7.0"},
        {"feel.weather.snow", "feel.weather.snow = -8.0"},
        {"feel.weather.blizzard", "feel.weather.blizzard = -12.0"},
        {"heat.enabled",
          "# ---- 方块自发热 / 吸热（2026-10-05 用户拍板）----\n"
        + "# 格式： heat.<方块> = 发热值℃ , 半径（格）\n"
        + "# 规则：不做遮挡判定 / **方块之间不叠加**（含同种方块 ⇒ 取最强的那个，按绝对值比）\n"
        + "#       **物品的暖独立一项**（暖手石等）/ 冷源不设门槛 / 衰减 = C 方案\n"
        + "# heat.falloff = 0 退回一刀切；heat.interval = 重算间隔（tick）\n"
        + "# 本轮只登记：火把 / 火 / 岩浆 / 雪层 / 雪块 / 冰 / 水（熔炉与大熔炉下一批）\n"
        + "heat.enabled = 1"},
        {"heat.falloff", "heat.falloff = 1"},
        {"heat.interval", "heat.interval = 10"},
        {"heat.torch", "heat.torch = 1,2"},
        {"heat.fire", "heat.fire = 12,3"},
        {"heat.lava", "heat.lava = 20,4"},
        {"heat.snow", "heat.snow = -3,3"},
        {"heat.snow_block", "heat.snow_block = -5,3"},
        {"heat.ice", "heat.ice = -6,3"},
        {"heat.water", "heat.water = -6,0"},
        {"food.enabled",
          "# ---- 食物 → 体感温度（2026-10-06 用户拍板，规则 2026-10-07 更正）----\n"
        + "# 格式： food.<食物> = 体感℃ , 持续分钟（数值 0 ＝ 不算）\n"
        + "# ★ 食物是第三套独立系统（环境/物品/食物）⇒ 三者相加\n"
        + "# ★★ 食物之间的规则（最终版，两条都已实测）：\n"
        + "#     同号 ⇒ **更极端的顶替**（暖的取更高、冷的取更低；等值只刷新时间，更弱的不算）\n"
        + "#     异号 ⇒ **两个数值相加**，时间取两者中**较长**的那个\n"
        + "#     同值 ⇒ 刷新时间；更弱的 ⇒ 无事发生\n"
        + "food.enabled = 1"},
        {"food.mushroom_stew", "food.mushroom_stew = 7,3"},
        {"food.beef_soup", "food.beef_soup = 12,5"},
        {"food.chicken_soup", "food.chicken_soup = 10,4"},
        {"food.vegetable_soup", "food.vegetable_soup = 9,4"},
        {"food.cream_mush_soup", "food.cream_mush_soup = 8,4"},
        {"food.cream_veg_soup", "food.cream_veg_soup = 10,4"},
        {"food.pumpkin_soup", "food.pumpkin_soup = 6,3"},
        {"food.mashed_potato", "food.mashed_potato = 8,3"},
        {"food.porridge", "food.porridge = 5,3"},
        {"food.cereal", "food.cereal = 6,3"},
        {"food.salad", "food.salad = 0,0"},
        {"food.milk_bowl", "food.milk_bowl = 2,2"},
        {"food.milk_bucket", "food.milk_bucket = 3,2"},
        {"food.water_bowl", "food.water_bowl = -2,2"},
        {"food.ice_cream", "food.ice_cream = -6,3"},
        {"food.sorbet", "food.sorbet = -8,3"},
        {"food.hot_water_bowl",
          "# ---- 2026-10-06 新增：我们自己的碗/桶饮料（用户定稿）----\n"
        + "#   降温链：热水碗 --5 分钟--> 温水碗 --3 分钟--> 水碗(1167) ／ 冰水碗 = 水碗+雪球\n"
        + "#   热牛奶碗 / 热奶桶×7 = 熔炉烧；★ 喝完返还空容器（碗→281；奶桶→自己材质的空桶）\n"
        + "food.hot_water_bowl = 6,3"},
        {"food.warm_water_bowl", "food.warm_water_bowl = 1,2"},
        {"food.ice_water_bowl", "food.ice_water_bowl = -4,3"},
        {"food.hot_milk_bowl", "food.hot_milk_bowl = 5,3"},
        {"food.hot_milk_bucket", "food.hot_milk_bucket = 6,3"},
        {"drink.hot_water_minutes",
          "# ---- 2026-10-07 用户补充：温水碗放 3 分钟会变成水碗 ✓ ----\n"
        + "#   降温链：热水碗 --5 分钟--> 温水碗 --3 分钟--> 水碗(1167)\n"
        + "#   ⚠️ 耐久条刻度跟着这两个数走 ⇒ 改完要重启才准\n"
        + "drink.hot_water_minutes = 5"},
        {"drink.warm_water_minutes", "drink.warm_water_minutes = 3"},
        {"food.apple_pie",
          "# ---- 2026-10-07 套餐 A：苹果派线 ＋ 巧克力奶线（用户拍板）----\n"
        + "#   苹果派胚 = 面团+苹果+糖+鸡蛋（无序）--熔炉--> 热苹果派 --放 5 分钟--> 苹果派\n"
        + "#   苹果派 --熔炉--> 热苹果派（用户批准回炉再热）／ 巧克力+牛奶碗 = 巧克力奶 --> 热巧克力奶\n"
        + "food.apple_pie = 7,4"},
        {"food.hot_apple_pie", "food.hot_apple_pie = 11,4"},
        {"food.chocolate_milk_bowl", "food.chocolate_milk_bowl = 4,4"},
        {"food.hot_chocolate_milk_bowl", "food.hot_chocolate_milk_bowl = 9,4"},
        {"hot_food.minutes",
          "# 热苹果派 / 热巧克力奶 放多少分钟后自己凉（默认 5 分钟）\n"
        + "# ⚠️ 耐久条刻度跟着它走 ⇒ 改完要重启才准\n"
        + "hot_food.minutes = 5"},
        {"warm_stone.heat",
          "# ---- 暖手石（2026-10-05 深夜 用户要求强化 ✓）----\n"
        + "#   heat = 每次给多少摄氏度（默认 **8** ✓）／ minutes = 每次多少分钟（默认 3 ✓）\n"
        + "#   uses = 烤一次能用几次（默认 2 ✓）；用完自动变回冷暖手石 ⇒ 再烤 ⇒ 循环\n"
        + "#   ⚠️ 冷却条刻度跟着 minutes 走 ⇒ 改完重启才准\n"
        + "warm_stone.heat = 8.0"},
        {"warm_stone.minutes", "warm_stone.minutes = 3"},
        {"warm_stone.uses", "warm_stone.uses = 2"},
        {"feel.dig_hook",
          "# ---- 体感效率挂钩（2026-10-05 用户拍板）----\n"
        + "# 挖掘 / 移动的效率表要不要真的生效：0 = 只显示不挂钩 / 1 = 挂钩（默认）\n"
        + "# 口径：挖掘直接乘档位表（50/70/85/90/75/100%）；移动只在 < -15 档 = 70%\n"
        + "# ⚠️ 工具耐久**不受影响** ✓（耐久走 BlockBreakInfo 那条路，只看方块不看耗时 ✓）\n"
        + "feel.dig_hook = 1"},
        {"feel.move_hook", "feel.move_hook = 1"},
        {"seasons.crop_baseline_days",
          "# ★ 天然基线天数：耕地浇水 + 不密植 + 全光照 时，几天一熟（默认 7.0）\n"
        + "#   所有作物倍率 = 基线 / 当季目标天数（CMSeasons.cropTargetDays 里那张表）\n"
        + "#   实测觉得整体太快/太慢，**只改这一个数**就能整体缩放 ✓\n"
        + "seasons.crop_baseline_days = 7.0"},
         {"seasons.crops",
          "# ---- 作物四季生长（2026-10-01 用户定稿）----\n"
        + "# 0 = 关（作物完全不吃四季）；1 = 开（默认）\n"
        + "# 倍率表在代码里：CMSeasons.cropGrowthFactor（小麦/胡萝卜洋葱/马铃薯/瓜梗）\n"
        + "# 口径：最快的季节 = x1.0（只减速）；冬季 = 0（完全不长）\n"
        + "# ⚠️ 耕地附近有水就不会枯死，没水会 5% 枯死（MITE 原有规则）\n"
        + "seasons.crops = 1"},
         {"seasons.enabled",
         "# ---- 四季系统（2026-09-30 开工）----\n"
       + "# 季节 = 世界天数的纯函数（不存状态 ✓ 所以存档/重登/多人天然一致 ✓）\n"
       + "# 一年 80 天、每季 20 天；**新世界第 0 天 = 春** ✓\n"
       + "# 冬季规则：前 3 天不结冰 → 第 4 天开始结冰 → 最后 3 天逐渐融冰 ✓\n"
       + "# 游戏内用 /se 查看、/se 1|2|3|4 拨季节（只改相位，不改世界天数 ✓）\n"
       + "#   0 = 关掉四季（温度偏移归零，其余一切照旧）\n"
       + "seasons.enabled = 1"},
        {"seasons.amplitude",
         "# 温度偏移幅度（MITE 原版生物群系温度刻度：结冰线 = 0.15 ✓）\n"
       + "# 0.25 ≈ 冬天能把温带（0.5~0.8）压到接近/越过结冰线 ✓；觉得太狠就往下调（0.15）\n"
       + "# 夏天则是 +幅度（沙漠那种本来就热的会更热 ✓）\n"
       + "seasons.amplitude = 0.25"},
        {"seasons.retint_per_tick",
         "# 换季【重新上色】的速度：每 tick 重建几个区块 ✓（默认 15）\n"
       + "# 一圈 = (2*半径+1)^2 个区块；半径 8 就是 289 个，每 tick 15 个 ⇒ 约 1.0 秒刷完一圈 ✓\n"
       + "# 嫌慢就调大（12~16 = 更快但更容易掉帧 ✗）；弱机卡就往小调（3~4）✓\n"
       + "seasons.retint_per_tick = 15"},
        {"seasons.retint_radius_chunks",
         "# 【重新上色】的扫描半径（区块；默认 15 = 240 格 ✓）\n"
       + "# 只影响【换季时主动重建】的范围；超出这个范围的区块，等你走近时会自然重建 ✓\n"
       + "seasons.retint_radius_chunks = 15"},
        {"seasons.ice_radius_chunks",
         "# 冬季结冰/融冰的**扫描半径**（区块；12 区块 = 192 格 ✓ 默认）\n"
       + "# 每 tick 扫 2 个区块、由内向外一圈圈来 ⇒ 12 区块约 16 秒扫完一遍 ✓\n"
       + "# 想大面积上冻就调大（24 = 384 格 / 48 = 768 格），代价是扫完一遍更久；上限 48 ✓\n"
       + "# ⚠️ 只处理**已加载**的区块 ✗，而且只冻 y >= 60 的**露天水面** ✓\n"
       + "seasons.ice_radius_chunks = 12"},
        {"panel.thermometer_debug",
         "# 玩家面板（I 键）里两根温度计的**调试假数据**：\n"
       + "#   0 = 关（默认）。体温 / 环境温度都显示「未知」—— 这是正常状态：\n"
       + "#       体温要等**四季系统**注入，环境温度要等**饰品系统**（戴上测温饰品才显示）✓\n"
       + "#   1 = 开。用慢速来回走的假数据驱动两根温度计，只为看外观 / 动画 ✓（纯客户端 ✓）\n"
       + "panel.thermometer_debug = 0"},
        {"temp.enabled",
         "# ---- 四季温度与体温（2026-10-01 开工）----\n"
       + "# 环境温度 = 季节温度（摄氏度，与玩家所处环境无关）：\n"
       + "#   春 1~25 / 夏 18~39 / 秋 10~28 / 冬 -10~17（每季分 3 段，见计划书）\n"
       + "#   · 每 2 分钟（真实时间 = 2400 tick）换一次值，始终落在当天区间内、平滑游走\n"
       + "#   · 段与段之间用 2 天过渡（用户 2026-10-01 定），不再出现 20 度的断崖\n"
       + "#   · 它只喂给玩家体温 / 以后的环境温度饰品；冬季结冰那套完全不受影响（仍是原版机制）\n"
       + "#   0 = 关掉整套温度系统（体温不动、没有 buff、饥饿倍率恒为 1）\n"
       + "temp.enabled = 1"},
        {"temp.body_enabled",
         "# 玩家体温单独开关：0 = 只算环境温度、不动体温（默认 1）\n"
       + "temp.body_enabled = 1"},
        {"temp.ambient_step_ticks",
         "# 环境温度多久换一次值（tick，默认 2400 = 真实 2 分钟，用户指定）\n"
       + "temp.ambient_step_ticks = 2400"},
        {"temp.body_rate",
         "# 体温向目标逼近的快慢（每 tick 的比例，默认 0.0005，约 100 秒走完 63%）\n"
       + "# 调大 = 忽冷忽热更刺激；调小 = 更迟钝。上限 0.05\n"
       + "temp.body_rate = 0.0005"},
        {"temp.lava_radius",
         "# 岩浆影响体温的半径（格，默认 12，用户指定）：范围内体温逐渐升到 39 度上限\n"
       + "# 泡在水里时以水为准（用户 2026-10-01 裁定）：水温把体温压到 35.7 度\n"
       + "temp.lava_radius = 12"},
        {"temp.accelerate_after_ticks",
         "# 同一档待满多久开始加速（tick，默认 6000 = 真实 5 分钟，用户指定）\n"
       + "# 加速 = 逼近速度最多 x3 + 目标再往极端推，不是硬钳死（用户选的 B 方案）\n"
       + "temp.accelerate_after_ticks = 6000"},
        {"temp.accelerate_push",
         "# 加速阶段把目标体温再往极端推多少（度，默认 1.0）\n"
       + "temp.accelerate_push = 1.0"},
        {"panel.body_thermometer_needs_accessory",
         "# 体温计（I 键面板右边那根）要不要先戴饰品才显示：\n"
       + "#   0 = 不用（默认，当前状态：四季已上线，体温直接显示）\n"
       + "#   1 = 要（等体温计饰品做好后改成 1，就是\"没戴 = 未知\"）\n"
       + "panel.body_thermometer_needs_accessory = 0"},
        {"accessory.gui_min_left",
         "# ---- 饰品系统（2026-10-01）----\n"
       + "# 背包界面（GUI 单位）左边缘至少留出多少格：\n"
       + "#   原版是屏幕居中，但左侧那排状态图标（夜视/速度/急迫…）文字一宽就被压住，\n"
       + "#   用户 2026-10-01 要求「完全不遮挡」⇒ 默认 140 给状态栏让位。\n"
       + "#   觉得让太多/太少就改这个数（屏幕太窄时自动退回居中，不会顶出屏幕）\n"
       + "accessory.gui_min_left = 140"},
        {"temp.night_drop.spring",
         "# ---- 昼夜温差（2026-10-01 用户定稿）----\n"
       + "# 夜里比白天低多少摄氏度（春5 / 夏7 / 秋6 / 冬6，用户选的推荐组）\n"
       + "# 曲线：午后最暖 0%、日出前 1 小时最冷 100%，锚点跟着当季昼长走\n"
       + "#   （夏天夜短所以降温时段短、冬天夜长所以冷得久）\n"
       + "temp.night_drop.spring = 5.0"},
        {"temp.night_drop.summer", "temp.night_drop.summer = 7.0"},
        {"temp.night_drop.autumn", "temp.night_drop.autumn = 6.0"},
        {"temp.night_drop.winter", "temp.night_drop.winter = 6.0"},
        {"temp.wet_ramp_ticks",
         "# ---- 雨雪降低体温（2026-10-01 用户定稿）----\n"
       + "# 淋湿 / 干透各要多久（tick，默认 600 = 真实 30 秒），两头都是斜坡\n"
       + "temp.wet_ramp_ticks = 600"},
        {"temp.rain_ambient",
         "# 雨雪对【环境温度】的削减（度）：小雨/普通雨 / 雷暴 / 下雪\n"
       + "# 只有【露天】才吃：屋檐下、洞里、树底下不吃（MITE 自带的见天判定）\n"
       + "temp.rain_ambient = 3.0"},
        {"temp.storm_ambient", "temp.storm_ambient = 4.0"},
        {"temp.snow_ambient", "temp.snow_ambient = 5.0"},
        {"temp.rain_body",
         "# 雨雪对【体温】的直接削减（度）—— 保底用：\n"
       + "#   夏天淋雨按不对称公式只掉 0.11 度，肉眼看不出来，所以再直接降一点\n"
       + "temp.rain_body = 0.30"},
        {"temp.snow_body", "temp.snow_body = 0.60"},
        {"panel.ambient_thermometer_needs_accessory",
         "# 环境温度计（I 键面板左边第二根）要不要先戴饰品才显示：\n"
       + "#   0 = 不用（默认，当前状态：直接显示四季环境温度，摄氏度）\n"
       + "#   1 = 要（环境温度计饰品做好后改成 1）\n"
       + "panel.ambient_thermometer_needs_accessory = 0"},
    };

    private static Properties props = null;

    private CMConfig() {}

    private static synchronized void load() {
        if (props != null) return;
        props = new Properties();
        try {
            File dir = new File(DIR);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, FILE_NAME);
            if (!f.exists()) {
                Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
                try {
                    w.write(DEFAULT_TEXT);
                } finally {
                    w.close();
                }
                System.out.println("[CreateMITE] 已生成默认配置: " + f.getAbsolutePath());
            }
            Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            try {
                props.load(r);
            } finally {
                r.close();
            }
            appendMissing(f);
        } catch (Throwable t) {
            System.out.println("[CreateMITE] 读取配置失败，全部使用默认值: " + t);
        }
    }

    /** 把本版本新增、而文件里还没有的配置项追加到文件末尾 */
    private static void appendMissing(File f) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KNOWN_KEYS.length; i++) {
            if (!props.containsKey(KNOWN_KEYS[i][0])) sb.append(KNOWN_KEYS[i][1]).append('\n');
        }
        if (sb.length() == 0) return;
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8");
            try {
                w.write("\n# ---- 以下为升级时自动补上的新配置项 ----\n");
                w.write(sb.toString());
            } finally {
                w.close();
            }
            System.out.println("[CreateMITE] 配置文件缺少新项，已自动追加到 " + f.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[CreateMITE] 追加新配置项失败（不影响运行，用的都是默认值）: " + t);
        }
    }

    /** 读一个浮点数；缺失或格式不对就返回默认值 */
    public static synchronized float getFloat(String key, float def) {
        load();
        String v = props.getProperty(key);
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim());
        } catch (Throwable t) {
            System.out.println("[CreateMITE] 配置项 " + key + " 不是数字（" + v + "），改用默认值 " + def);
            return def;
        }
    }

    /** 读一个字符串；缺失就返回默认值 */
    public static synchronized String getString(String key, String def) {
        load();
        String v = props.getProperty(key);
        return v == null ? def : v.trim();
    }

    /** 体感 -> 天气修正要不要挂钩（默认开 ✓ 用户 2026-10-05 拍板 ✓）*/
    public static boolean feelWeatherHook() {
        return getFloat("feel.weather.enabled", 1.0F) != 0.0F;
    }

    /** 体感 -> 挖掘效率要不要挂钩（默认开 ✓ 用户 2026-10-05 拍板 ✓）*/
    public static boolean feelDigHook() {
        return getFloat("feel.dig_hook", 1.0F) != 0.0F;
    }

    /** 体感 -> 移动效率要不要挂钩（默认开 ✓ 只有 < -15 档会动手 ✓）*/
    public static boolean feelMoveHook() {
        return getFloat("feel.move_hook", 1.0F) != 0.0F;
    }

    public static OverstressMode overstressMode() {
        load();
        String v = props.getProperty("overstress.mode");
        if (v == null) return OverstressMode.STOP;
        v = v.trim().toLowerCase();
        if (v.equals("slow")) return OverstressMode.SLOW;
        if (v.equals("ignore")) return OverstressMode.IGNORE;
        if (!v.equals("stop")) {
            System.out.println("[CreateMITE] 配置项 overstress.mode = " + v + " 不认识，按 stop 处理");
        }
        return OverstressMode.STOP;
    }
}
