# MITE-扎根 · MITE-Rooted

把Create的部分动力与加工体系，**原生重制**到 **Minecraft 1.6.4 / MITE R196 / FishModLoader 3.4.2** 上。

A native re-implementation of Create's kinetics and processing systems for **Minecraft 1.6.4 (MITE R196 + FishModLoader 3.4.2)**.

**中文** · [English](#english) · ☕ [赞助 / Support](https://afdian.com/a/H_MIU)

---

# 中文

## 这是什么

不是搬运、也不是移植现成的 jar：动力网络、应力、机壳与传动杆、大熔炉、四季、温度、食物体系等全部按 MITE 的代码风格**重新实现**。本仓库**只有源码**，编译方法见下。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.6.4 |
| MITE | R196 |
| FishModLoader | 3.4.2（前置，必须） |
| Java | 17 |

## 编译（源码 → jar）

编译需要**自备**下面这些（受第三方版权保护，不随本仓库分发）：

- JDK 17
- 一份 MITE R196 客户端 —— 提供编译用的映射版 jar：`.minecraft/.fml/remappedJars/1.6.4-MITE.jar-3.4.2.jar`
- FishModLoader 3.4.2、gson 2.10.1、LWJGL 2.9.4 —— MITE 客户端的 `libraries` 里都有

```powershell
# 按自己的路径改 build.ps1 顶部两个参数（也可以命令行传入）
powershell -ExecutionPolicy Bypass -File build.ps1 -McDir "X:\...\.minecraft" -JdkBin "C:\java17\bin"

# 产物：build\mite-rooted-0.1.0.jar  →  放进 .minecraft\mods\
```

> ⚠️ 同一个 mod id 只能存在一个包：换包前**先删掉** `mods\` 里的旧包，否则游戏启动会失败。

## 内容一览

### 一、四季与昼夜
春夏秋冬四季轮转：作物生长速度、积雪、结冰、昼夜时长都随季节变化。季节长度与昼夜时长写在 `config/createmite.properties`（首次启动自动生成）。

### 二、天气
5 种天气状态，随季节分布；下雨下雪会影响环境温度与积雪。

### 三、温度系统
- **环境温度**：由生物群系、季节、时间、天气、高度等推算。
- **体感温度** = 环境温度 ＋ 装备/手持物品 ＋ 食物 三套分别计算后叠加（**同号取更强的一项、异号相加**）。
- 体感过低会持续掉血；屏幕上有面板显示当前温度与来源。

### 四、热源与降温
- 热源：**营火**（+12，半径 12）、**暖手石**（+8，可反复烤热）。
- 降温链：**热水碗** —5 分钟→ **温水碗** —3 分钟→ **水碗**；**冰水碗** = 水碗 + 雪球。

### 五、食物与饮品
- 碗类饮料：热水碗 / 温水碗 / 冰水碗 / 热牛奶碗；热奶桶（7 种金属材质各一份）。
- 喝完**返还容器**：碗还碗，奶桶还对应材质的空桶。
- 苹果派线：苹果派胚 —熔炉→ 热苹果派 —放 5 分钟→ 苹果派（**可回炉再热**）。
- 巧克力奶线：巧克力奶 —熔炉→ 热巧克力奶。
- 每种食物/饮品都带自己的体感加成。

### 六、动力网络
- 动力源「提供」应力、设备「占用」应力；同一张网里**占用 > 提供**就过载（默认停机并提示，模式可配置）。
- 传动件：传动轴、齿轮 / 大齿轮、手摇曲柄、水车 / 大型水车、机壳系列（安山岩、黄铜、铜等）。

### 七、加工设备
- **3×3×3 巨型熔炉**：多方块结构，成型后可烧炼并显示燃烧状态。
- **石磨 / 粉碎**：把矿石等粉碎成可加工的产物。

### 八、3×3 营火（多方块）

搭建图案（**任意木板** ×6、**任意羊毛** ×2、**任意原木** ×1，不分朝向）：

```
木板 木板 木板
羊毛 原木 羊毛
木板 木板 木板
```

- 摆好即成型（熄灭态，无火光）。
- 用**打火石**右键点燃，燃烧 **3 分钟**；燃烧中**右键加木材**（任意可燃、非金属），每次 **+1 分钟**，最多 9 次（合计 12 分钟）。
- 烧完自动熄灭；燃烧时提供火把级光照、冒火苗与烟，并作为热源（+12 / 半径 12）。
- **挖掉任何一格** ⇒ 整台 3×3 一起消失，且**不掉落任何东西**。
- 站在营火上不会受伤。

### 九、其它
工作台等级闸门（配方按工具等级解锁）、结构选择器（配合指令导出结构）、提示系统、玩家面板。
### 十、必需脂肪（本项目的将原版中MITE中脂肪增加了对应机制）
原版 MITE 里「必需脂肪」只记录、不产生任何效果，也没有任何食物能补充它。本项目把它启用成了一整套**「保温换灵活」**的取舍：

| 必需脂肪 | 体感温度 | 移动速度 | 手感 |
| --- | --- | --- | --- |
| ≥ 75% | **+1.5 ℃** | **×0.9** | 抗冻但笨重 |
| 25% ~ 75% | 0 | ×1.0 | 中性 |
| 5% ~ 25% | **−1.5 ℃** | **×1.1** | 轻快、开始怕冷 |
| < 5% | **−3.0 ℃** | **×1.3** | 又轻又快，但冬天很难熬 |

- **挨饿保护**：脂肪高于 65% 时，饥饿归零后的**掉血速率降为原版的 0.35 倍**；同时脂肪会被快速消耗到 50% 为止（约 2.5 分钟），烧完保护自动结束。
- **补充方式**（共 28 种含脂食物，按「营养值 × 8000」计算）：奶制品（各种奶桶、一碗牛奶、奶酪、热牛奶碗、热奶桶）、甜点（蛋糕、南瓜派、巧克力、冰淇淋、苹果派）、以及猪肉。
- 玩家面板（按 I）里那一行会显示当前档位：`体脂充足 / 正常 / 体脂偏低 / 体脂告急`。

## 操作

### 按键

| 键 | 功能 |
| --- | --- |
| V | 提示（列出当前可做的事） |
| I | 玩家面板（体感温度等状态） |

两者都能在「选项 → 控制」里改键。

---

# English

## What is this

Not a port of any existing jar and not a copy of Create's code: the kinetics network, stress, casings, shafts, the giant furnace, seasons, temperature and the food system are all **re-implemented from scratch** in MITE's own style. This repository contains **source code only** — see Build below.

## Requirements

| Item | Version |
| --- | --- |
| Minecraft | 1.6.4 |
| MITE | R196 |
| FishModLoader | 3.4.2 (hard dependency) |
| Java | 17 |

## Build (source → jar)

You must provide the following yourself — they are third-party and are **not** distributed here:

- JDK 17
- A MITE R196 client installation, which supplies the remapped compile jar: `.minecraft/.fml/remappedJars/1.6.4-MITE.jar-3.4.2.jar`
- FishModLoader 3.4.2, gson 2.10.1, LWJGL 2.9.4 — all present in the MITE client's `libraries`

```powershell
# edit the two parameters at the top of build.ps1, or pass them on the command line
powershell -ExecutionPolicy Bypass -File build.ps1 -McDir "X:\...\.minecraft" -JdkBin "C:\java17\bin"

# output: build\mite-rooted-0.1.0.jar  ->  drop into .minecraft\mods\
```

> ⚠️ Only one jar per mod id: **delete the old jar** in `mods\` before installing a new one, or the game will fail to start.

## Features

### 1. Seasons and day/night length
Four seasons cycle over time: crop growth speed, snow cover, ice formation and day/night length all follow the season. Season length and day/night timing live in `config/createmite.properties` (generated on first launch).

### 2. Weather
Five weather states distributed across the seasons; rain and snow affect ambient temperature and snow cover.

### 3. Temperature system
- **Ambient temperature** derived from biome, season, time of day, weather and altitude.
- **Perceived temperature** = ambient + gear/held items + food, each computed separately and then combined (same sign: keep the stronger one; opposite signs: add them).
- Freezing drains health over time; an on-screen panel shows the current temperature and its sources.

### 4. Heat sources and cooling
- Heat: **campfire** (+12, radius 12), **hand warmer** (+8, reheatable).
- Cooling chain: **hot water bowl** —5 min→ **warm water bowl** —3 min→ **water bowl**; **ice water bowl** = water bowl + snowball.

### 5. Food and drinks
- Bowl drinks: hot / warm / ice water bowls, hot milk bowl; hot milk buckets (one per metal, 7 kinds).
- **Containers are returned** when drunk: bowls come back as bowls, buckets as their own metal's empty bucket.
- Apple pie line: raw apple pie —furnace→ hot apple pie —5 min→ apple pie (can be **re-baked**).
- Chocolate milk line: chocolate milk —furnace→ hot chocolate milk.
- Every food/drink carries its own temperature effect.

### 6. Kinetics network
- Generators **provide** stress, machines **consume** it; if consumption exceeds supply on the same network it overloads (default: stop and warn; the mode is configurable).
- Components: shafts, cogwheels / large cogwheels, hand crank, water wheel / large water wheel, casing series (andesite, brass, copper, ...).

### 7. Processing machines
- **3×3×3 giant furnace**: a multiblock that smelts and shows its burning state.
- **Millstone / crushing**: crushes ores into processable products.

### 8. 3×3 campfire (multiblock)

Build pattern (any **planks** ×6, any **wool** ×2, any **log** ×1; both orientations work):

```
planks planks planks
wool   log    wool
planks planks planks
```

- Assembles itself once complete (unlit, no flame).
- Light it with **flint and steel**: burns for **3 minutes**; right-click with wood (any burnable, non-metal item) to add **+1 minute**, up to 9 times (12 minutes total).
- Burns out on its own; while burning it emits torch-level light, flame and smoke particles, and acts as a heat source (+12 / radius 12).
- Breaking **any one block** removes the whole 3×3 and drops **nothing**.
- Standing on the campfire does no damage.

### 9. Misc
Workbench tier gating (recipes unlock by tool tier), structure selector (exports structures via command), hint system and player panel.
### 10. Essential fats (an original mechanic of this project)
In vanilla MITE the essential-fats value is tracked but has **no effect at all**, and no food can replenish it. This project turns it into a trade-off between **insulation and agility**:

| Essential fats | Perceived temp | Movement speed | Feel |
| --- | --- | --- | --- |
| ≥ 75% | **+1.5 °C** | **×0.9** | Cold-resistant but heavy |
| 25% – 75% | 0 | ×1.0 | Neutral |
| 5% – 25% | **−1.5 °C** | **×1.1** | Light on your feet, but cold |
| < 5% | **−3.0 °C** | **×1.3** | Fast and frail in winter |

- **Starvation buffer**: while fats are above 65%, starvation damage is dealt at **0.35x the vanilla rate**; the reserve burns down to 50% in about 2.5 minutes, after which the protection ends.
- **Sources** (28 fatty foods, each giving nutrition × 8000): dairy (all milk buckets, milk bowl, cheese, hot milk bowl, hot milk buckets), desserts (cake, pumpkin pie, chocolate, ice cream, apple pie) and pork.
- The player panel (press I) shows the current tier: abundant / normal / low / critical.

## Controls

### Keys

| Key | Function |
| --- | --- |
| V | Hints (what you can do right now) |
| I | Player panel (temperature and status) |

Both are rebindable in Options → Controls.

---

## 方块与物品清单 / Blocks & Items

### 方块 Blocks（31 项）

| ID | 名称 | English |
| --- | --- | --- |
| 2335 | 安山合金块 | Block of Andesite Alloy |
| 2325 | 安山机壳 | Andesite Casing |
| 2334 | 黄铜块 | Block of Brass |
| 2326 | 黄铜机壳 | Brass Casing |
| 2301 | 篝火 | Campfire |
| 2302 | 燃烧的篝火 | Lit Campfire |
| 2303 | 营火 | Campfire |
| 2316 | 离合器 | Clutch |
| 2338 | 圆石熔炉核心 | Cobblestone Furnace Core |
| 2341 | 圆石熔炉传动杆 | Cobblestone Cased Shaft |
| 2311 | 齿轮 | Cogwheel |
| 2342 | 铜-传动杆 | Copper Shaft |
| 2321 | 粉碎轮 | Crushing Wheel |
| 2315 | 十字齿轮箱 | Gearbox |
| 2317 | 反转齿轮箱 | Gearshift |
| 2344 | 金-传动杆 | Gold Shaft |
| 2312 | 手摇曲柄 | Hand Crank |
| 2345 | 铁-传动杆 | Iron Shaft |
| 2314 | 大齿轮 | Large Cogwheel |
| 2320 | 大型水车 | Large Water Wheel |
| 2313 | 石磨 | Millstone |
| 2346 | 秘银-传动杆 | Mithril Shaft |
| 2337 | 地狱岩熔炉核心 | Netherrack Furnace Core |
| 2340 | 地狱岩熔炉传动杆 | Netherrack Cased Shaft |
| 2336 | 黑曜石熔炉核心 | Obsidian Furnace Core |
| 2339 | 黑曜石熔炉传动杆 | Obsidian Cased Shaft |
| 2300 | 锌矿石 | Zinc Ore |
| 2310 | 传动轴 | Shaft |
| 2343 | 银-传动杆 | Silver Shaft |
| 2319 | 水车 | Water Wheel |
| 2333 | 锌块 | Block of Zinc |

### 物品 Items（27 项）

| ID | 名称 | English |
| --- | --- | --- |
| 2361 | 安山合金 | Andesite Alloy |
| 2385 | 苹果派 | Apple Pie |
| 2384 | 苹果派胚 | Unbaked Apple Pie |
| 2640 | 苹果派胚（旧） | Unbaked Apple Pie (old) |
| 2363 | 黄铜粒 | Brass Nugget |
| 2387 | 巧克力奶 | Bowl of Chocolate Milk |
| 2369 | 粉碎艾德曼矿石 | Crushed Raw Adamantium |
| 2366 | 粉碎铜矿石 | Crushed Raw Copper |
| 2365 | 粉碎金矿石 | Crushed Raw Gold |
| 2364 | 粉碎铁矿石 | Crushed Raw Iron |
| 2368 | 粉碎秘银矿石 | Crushed Raw Mithril |
| 2359 | 粉碎粗锌 | Crushed Raw Zinc |
| 2367 | 粉碎银矿石 | Crushed Raw Silver |
| 2371 | 暖手石 | Hand Warmer |
| 2372 | 冷暖手石 | Cold Hand Warmer |
| 2386 | 热苹果派 | Hot Apple Pie |
| 2388 | 热巧克力奶 | Bowl of Hot Chocolate Milk |
| 2376 | 热牛奶碗 | Bowl of Hot Milk |
| 2373 | 热水碗 | Bowl of Hot Water |
| 2375 | 冰水碗 | Bowl of Ice Water |
| 2358 | 黄铜锭 | Brass Ingot |
| 2357 | 锌锭 | Zinc Ingot |
| 2356 | 粗锌 | Raw Zinc |
| 2370 | 结构选择器 | Structure Selector |
| 2374 | 温水碗 | Bowl of Warm Water |
| 2360 | 扳手 | Wrench |
| 2362 | 锌粒 | Zinc Nugget |

---

## 赞助 / Support

如果这个项目让你玩得开心，欢迎请我喝杯咖啡 ☕（爱发电）：

**<https://afdian.com/a/H_MIU>**


---

## 许可与署名 / License & Credits

- **代码 / Code: GNU GPL-3.0**，全文见 `LICENSE`。
  你可以自由使用、修改、再分发，**但衍生作品必须同样以 GPL-3.0 开源**，并保留版权声明。
  Licensed under the **GNU General Public License v3.0** — you may use, modify and redistribute it, but **derivative works must also be released under GPL-3.0** and keep the copyright notice.
- 版权 / Copyright (C) 2026 **MITE-扎根 (MITE-Rooted)**
- **贴图素材 / Textures**：
  - 部分贴图来自 **Create**（MIT License，作者 simibubi 及贡献者）—— 详见 `ATTRIBUTION.md`
    Some textures come from **Create** (MIT License, by simibubi and contributors) — see `ATTRIBUTION.md`
  - 营火贴图取自 **Minecraft 1.20.1** 客户端资源，版权归 Mojang，仅用于学习交流
    Campfire textures are taken from the **Minecraft 1.20.1** client assets; copyright Mojang, used for study and non-commercial exchange only
- 本项目与 Create 官方团队、MITE 制作组均无隶属关系。
  This project is not affiliated with the Create team or the MITE authors.
