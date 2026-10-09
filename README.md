# MITE-扎根（MITE-Rooted）

把《Create 机械动力》的动力与加工体系，**原生重制**到 **Minecraft 1.6.4 / MITE R196 / FishModLoader 3.4.2** 上。

不是搬运、也不是移植现成的 jar：动力网络、应力、大熔炉、机壳与传动杆、四季、温度、食物体系等全部按 MITE 的代码风格重新实现。

**English**: A native re-implementation of Create's kinetics and processing systems for Minecraft 1.6.4 (MITE R196 + FishModLoader 3.4.2). This repository contains **source code only** — see 「编译」 below for build requirements.

---

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.6.4 |
| MITE | R196 |
| FishModLoader | 3.4.2（前置，必须） |
| Java | 17 |

## 编译（源码 → jar）

本仓库只有源码。编译需要**自备**下面这些（它们受第三方版权保护，不随仓库分发）：

- JDK 17
- 一份 MITE R196 客户端：提供编译所需的映射版 jar（`.minecraft/.fml/remappedJars/1.6.4-MITE.jar-3.4.2.jar`）
- FishModLoader 3.4.2、gson 2.10.1、LWJGL 2.9.4 —— MITE 客户端的 `libraries` 里都有

```powershell
# 按自己的路径改 build.ps1 顶部两个参数（或命令行传入）
powershell -ExecutionPolicy Bypass -File build.ps1 -McDir "X:\...\.minecraft" -JdkBin "C:\java17\bin"

# 产物：build\mite-rooted-0.1.0.jar  →  丢进 .minecraft\mods\
```

> ⚠️ 同一个 mod id 只能存在一个包：换包前**先删掉** `mods\` 里的旧包，否则游戏启动会失败。

## 内容一览

### 一、四季与昼夜
- 春夏秋冬四季轮转：作物生长速度、积雪、结冰、昼夜时长都随季节变化
- 季节长度、昼夜时长等参数写在 `config/createmite.properties`（首次启动自动生成）

### 二、天气
- 5 种天气状态，随季节分布；下雪/下雨会影响环境温度与积雪

### 三、温度系统
- **环境温度**：由生物群系、季节、时间、天气、高度等推算
- **体感温度**：环境温度 ＋ 装备/手持物品 ＋ 食物 三套分别计算后叠加
  （同号取更强的一项、异号相加；食物与物品的暖/凉效果按这个规则合成）
- 体感过低会持续掉血；屏幕上有面板显示当前温度与来源

### 四、热源与降温
- 热源：**营火**（+12，半径 12）、**暖手石**（+8，可手持/烤热）
- 降温链：**热水碗** ——5 分钟——> **温水碗** ——3 分钟——> **水碗**；**冰水碗** = 水碗 + 雪球

### 五、食物与饮品（套餐 A）
- 碗类饮料：热水碗 / 温水碗 / 冰水碗 / 热牛奶碗；热奶桶（7 种金属材质各一份）
- 喝完**返还容器**：碗还碗、奶桶还对应材质的空桶
- 苹果派线：苹果派胚 ——熔炉——> 热苹果派 ——放 5 分钟——> 苹果派（**可回炉再热**）
- 巧克力奶线：巧克力奶 ——熔炉——> 热巧克力奶
- 每种食物/饮品都带自己的体感加成

### 六、动力网络（应力）
- 动力源「提供」应力、设备「占用」应力；同一张网里**占用 > 提供**就会过载
  （过载行为在配置里可选，默认停机并提示）
- 传动件：传动轴、齿轮 / 大齿轮、手摇曲柄、水车 / 大型水车、机壳系列（安山岩、黄铜、铜等）

### 七、加工设备
- **3×3×3 巨型熔炉**：多方块结构，成型后可烧炼并显示燃烧状态
- **石磨 / 粉碎**：把矿石等粉碎成可加工的产物

### 八、3×3 营火（多方块）

搭建图案（**任意木板** ×6、**任意羊毛** ×2、**任意原木** ×1，不分朝向）：

```
木板 木板 木板
羊毛 原木 羊毛
木板 木板 木板
```

- 摆好即成型（熄灭态，无火光）
- 用**打火石**右键点燃：燃烧 **3 分钟**；燃烧中**右键添加木材**（任意可燃、非金属），每次 **+1 分钟**，最多 9 次（合计 12 分钟）
- 烧完自动熄灭；燃烧时提供火把级光照、冒火苗与烟、并作为热源（+12 / 半径 12）
- **挖掉任何一格** ⇒ 整台 3×3 一起消失，且**不掉落任何东西**
- 站在营火上不会受伤

### 九、其它
- 工作台等级闸门：配方按工具等级解锁
- 结构选择器：配合指令导出结构
- 提示系统（默认按 **V**）：列出当前可做的事
- 玩家面板（默认按 **I**）：显示体感温度等状态

## 操作

### 按键

| 键 | 功能 |
| --- | --- |
| V | 机械动力提示 |
| I | 玩家面板 |

（均可在「选项 → 控制」里改键）

### 指令

| 指令 | 功能 |
| --- | --- |
| /P | 切换游戏模式 |
| /O | 维度传送 |
| /T | 导出结构 |
| /cmf | 朝向信息 |
| /cmhint | 提示开关 |
| /se | 季节信息 |
| /S | 昼夜时长 |
| /Y | 天气 |

## 方块与物品清单

（下面两张表由源码里的名称表自动提取，中英对照）

### 方块（31 项）

| 名称 | English |
| --- | --- |
| 安山合金块 | Block of Andesite Alloy |
| 安山机壳 | Andesite Casing |
| 黄铜块 | Block of Brass |
| 黄铜机壳 | Brass Casing |
| 篝火 | Campfire |
| 燃烧的篝火 | Lit Campfire |
| 营火 | Campfire |
| 离合器 | Clutch |
| 圆石熔炉核心 | Cobblestone Furnace Core |
| 圆石熔炉传动杆 | Cobblestone Cased Shaft |
| 齿轮 | Cogwheel |
| 铜-传动杆 | Copper Shaft |
| 粉碎轮 | Crushing Wheel |
| 十字齿轮箱 | Gearbox |
| 反转齿轮箱 | Gearshift |
| 金-传动杆 | Gold Shaft |
| 手摇曲柄 | Hand Crank |
| 铁-传动杆 | Iron Shaft |
| 大齿轮 | Large Cogwheel |
| 大型水车 | Large Water Wheel |
| 石磨 | Millstone |
| 秘银-传动杆 | Mithril Shaft |
| 地狱岩熔炉核心 | Netherrack Furnace Core |
| 地狱岩熔炉传动杆 | Netherrack Cased Shaft |
| 黑曜石熔炉核心 | Obsidian Furnace Core |
| 黑曜石熔炉传动杆 | Obsidian Cased Shaft |
| 锌矿石 | Zinc Ore |
| 传动轴 | Shaft |
| 银-传动杆 | Silver Shaft |
| 水车 | Water Wheel |
| 锌块 | Block of Zinc |

### 物品（27 项）

| 名称 | English |
| --- | --- |
| 安山合金 | Andesite Alloy |
| 苹果派 | Apple Pie |
| 苹果派胚 | Unbaked Apple Pie |
| 苹果派胚（旧） | Unbaked Apple Pie (old) |
| 黄铜粒 | Brass Nugget |
| 巧克力奶 | Bowl of Chocolate Milk |
| 粉碎艾德曼矿石 | Crushed Raw Adamantium |
| 粉碎铜矿石 | Crushed Raw Copper |
| 粉碎金矿石 | Crushed Raw Gold |
| 粉碎铁矿石 | Crushed Raw Iron |
| 粉碎秘银矿石 | Crushed Raw Mithril |
| 粉碎粗锌 | Crushed Raw Zinc |
| 粉碎银矿石 | Crushed Raw Silver |
| 暖手石 | Hand Warmer |
| 冷暖手石 | Cold Hand Warmer |
| 热苹果派 | Hot Apple Pie |
| 热巧克力奶 | Bowl of Hot Chocolate Milk |
| 热牛奶碗 | Bowl of Hot Milk |
| 热水碗 | Bowl of Hot Water |
| 冰水碗 | Bowl of Ice Water |
| 黄铜锭 | Brass Ingot |
| 锌锭 | Zinc Ingot |
| 粗锌 | Raw Zinc |
| 结构选择器 | Structure Selector |
| 温水碗 | Bowl of Warm Water |
| 扳手 | Wrench |
| 锌粒 | Zinc Nugget |

## 许可与署名

- **代码：MIT**，见 `LICENSE`
- **贴图素材**：
  - 部分贴图来自 **Create**（MIT License，作者 simibubi 及贡献者）—— 详见 `ATTRIBUTION.md`
  - 营火贴图取自 **Minecraft 1.20.1** 客户端资源，版权归 Mojang，仅作学习交流用途
- 本项目与 Create 官方团队、MITE 制作组均无隶属关系
