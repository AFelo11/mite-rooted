package net.dsh.createmite.kinetics.client;

import net.minecraft.Icon;
import net.minecraft.Tessellator;

/**
 * Create 原版模型的几何数据（传动轴 / 手摇曲柄 / 石磨）。
 *
 * 数据由 Create jar 里的 `assets/create/models/block/*.json` 直接解析生成，
 * 逐元素、逐面 UV、逐面贴图、元素旋转都原样保留，不再手写方盒子。
 *
 * 面索引与 CogwheelGeometry 一致：0=+Y 1=-Y 2=-Z 3=+Z 4=-X 5=+X
 * 顶点顺序满足 Minecraft 的 UV 约定（c0→c1 是贴图 u 方向，c0→c3 是 v 方向）。
 *
 * UV 一律是 0..16 模型空间（与贴图分辨率无关）。
 */
public final class CreateModels {

    private CreateModels() {}

    /**
     * 全局贴图层编号。
     *
     * 注意：这只是"图层编号表"，不是"每个模型都要画的表" ——
     * 渲染时 KineticRenderer.drawModel 会先扫一遍这个模型实际用到哪几层，只绑那几张，
     * 所以后面继续加方块不会让每个方块都白白多绑一堆贴图。
     */
    public static final String[] LAYER_NAMES = {
        "axis",                    // 0
        "axis_top",                // 1
        "andesite_casing_short",   // 2
        "smooth_dark_log_top",     // 3
        "gearbox",                 // 4
        "millstone",               // 5
        "cogwheel_axis",           // 6
        "large_cogwheel",          // 7
        "gearshift_off",           // 8  ← 反转齿轮箱 / 离合器共用同一套几何，"外壳"贴图不同
        "gearshift_on",            // 9
        "clutch_off",              // 10
        "clutch_on",               // 11
        "andesite_funnel_frame",   // 12
        "andesite_casing",         // 13
        "wrench",                  // 14  ← 物品用（3D 物品渲染，不是方块）
        "creative_casing",         // 15  ← 创造马达（Create creative_motor/block.json）
        "creative_motor",          // 16
        "flap_display_front",      // 17
        "crushing_wheel_insert",   // 18  ← 粉碎轮（OBJ 网格）
        "crushing_wheel_plates",   // 19
        "waterwheel_metal",        // 20  ← 水车 / 大型水车
        "log_oak",                 // 21  ← 原版贴图，用 1.6.4 的命名
        "log_oak_top",             // 22
        "log_spruce_top",          // 23
        "planks_oak",              // 24
        "brass_casing",            // 25
        "andesite_encased_cogwheel_side", // 26  ← 封装**小**齿轮侧面条纹（原版 16×16）
        "brass_encased_cogwheel_side",    // 27  ← 黄铜版
        "andesite_encased_cogwheel_side_connected", // 28  ← 封装**大**齿轮专用！原版是 32×32 的
                                                    //    "connected" 版，模型 UV 走的是 [8,13,16,16]
                                                    //    这种"右半张"坐标 —— 之前错绑 16×16 那张，
                                                    //    采到的是完全不相干的像素（用户："大齿轮箱四面不对"）
        "brass_encased_cogwheel_side_connected",    // 29
        "brass_gearbox",                            // 30  ← 黄铜版"齿轮箱/开口"面（原版 brass_gearbox）
        "obsidian",                                 // 31  ← 熔炉结构：黑曜石包裹传动杆的机壳（原版黑曜石贴图）
        "netherrack",                               // 32  ← 下界岩包裹传动杆的机壳（原版地狱岩贴图）
        "cobblestone",                              // 33  ← 圆石包裹传动杆的机壳（原版圆石贴图）
    };
// ===== ENCASED_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side

// ===== ENCASED_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side
// ===== ENCASED_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side
    public static final CreateModels.El[] ENCASED_COGWHEEL = {
        new CreateModels.El(0F, 1F, 0F, 16F, 6F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{23, 4, 26, 26, 26, 26},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,10F,16F,15F}, {0F,10F,16F,15F}, {0F,10F,16F,15F}, {0F,10F,16F,15F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 6F, 0F, 16F, 10F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 4, 26, 26, 26, 26},
              new float[][]{null, null, {0F,6F,16F,10F}, {0F,6F,16F,10F}, {0F,6F,16F,10F}, {0F,6F,16F,10F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(15.95F, 6F, 0.05F, 0.05F, 10F, 15.95F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 4, 26, 26, 26, 26},
              new float[][]{null, null, {0F,6F,16F,10F}, {0F,6F,16F,10F}, {0F,6F,16F,10F}, {0F,6F,16F,10F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 10F, 0F, 16F, 15F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 23, 26, 26, 26, 26},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,1F,16F,6F}, {0F,1F,16F,6F}, {0F,1F,16F,6F}, {0F,1F,16F,6F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 0F, 16F, 16F, 2F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 4, 26, 13, 26, 26},
              new float[][]{{0F,0F,16F,2F}, null, {0F,0F,16F,1F}, {0F,0F,16F,1F}, {0F,0F,2F,1F}, {14F,0F,16F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 14F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 4, 13, 26, 26, 26},
              new float[][]{{0F,14F,16F,16F}, null, {0F,0F,16F,1F}, {0F,0F,16F,1F}, {14F,0F,16F,1F}, {0F,0F,2F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 2F, 2F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 4, 4, 4, 26, 13},
              new float[][]{{0F,2F,2F,14F}, null, null, null, {2F,0F,14F,1F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(14F, 15F, 2F, 16F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 4, 4, 4, 13, 26},
              new float[][]{{14F,2F,16F,14F}, null, null, null, {2F,0F,14F,1F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 0F, 16F, 1F, 2F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 26, 13, 26, 26},
              new float[][]{null, {0F,14F,16F,16F}, {0F,15F,16F,16F}, {0F,0F,16F,1F}, {0F,15F,2F,16F}, {14F,15F,16F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(14F, 0F, 2F, 16F, 1F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 4, 4, 13, 26},
              new float[][]{null, {14F,2F,16F,14F}, null, null, {2F,0F,14F,1F}, {2F,15F,14F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 14F, 16F, 1F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 13, 26, 26, 26},
              new float[][]{null, {0F,0F,16F,2F}, {0F,0F,16F,1F}, {0F,15F,16F,16F}, {14F,15F,16F,16F}, {0F,15F,2F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 2F, 2F, 1F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 4, 4, 26, 13},
              new float[][]{null, {0F,2F,2F,14F}, null, null, {2F,15F,14F,16F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };


// ===== ENCASED_LARGE_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side_connected

// ===== ENCASED_LARGE_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side_connected
// ===== ENCASED_LARGE_COGWHEEL ��ͼ�� =====
//   0 -> minecraft:block/stripped_spruce_log_top
//   1 -> create:block/gearbox
//   2 -> create:block/andesite_casing
//   3 -> create:block/andesite_encased_cogwheel_side_connected
    public static final CreateModels.El[] ENCASED_LARGE_COGWHEEL = {
        new CreateModels.El(0F, 1F, 0F, 16F, 6F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{23, 4, 28, 28, 28, 28},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {8F,13F,16F,15.5F}, {8F,13F,16F,15.5F}, {8F,13F,16F,15.5F}, {8F,13F,16F,15.5F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        // ★ 原版大齿轮箱在这 4px 中带**故意没有几何**（留给大齿轮露出来的齿），
        //   我们不在壳子里画齿轮，所以必须补一圈中带，否则从侧面能直接看穿整块方块。
        //   UV {8,11,16,13} = 32×32 connected 贴图右半张的"槽"那 4 行。
        new CreateModels.El(0F, 6F, 0F, 16F, 10F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 4, 28, 28, 28, 28},
              new float[][]{null, null, {8F,11F,16F,13F}, {8F,11F,16F,13F}, {8F,11F,16F,13F}, {8F,11F,16F,13F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 10F, 0F, 16F, 15F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 23, 28, 28, 28, 28},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {8F,8.5F,16F,11F}, {8F,8.5F,16F,11F}, {8F,8.5F,16F,11F}, {8F,8.5F,16F,11F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(14F, 15F, 2F, 16F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{14F,2F,16F,14F}, {0F,0F,0F,0F}, {0F,0F,0F,0F}, {0F,0F,0F,0F}, {2F,0F,14F,1F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 0F, 16F, 16F, 2F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{0F,0F,16F,2F}, {0F,0F,0F,0F}, {0F,0F,16F,1F}, {0F,0F,16F,1F}, {0F,0F,2F,1F}, {14F,0F,16F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 2F, 2F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{0F,2F,2F,14F}, {0F,0F,0F,0F}, {0F,0F,0F,0F}, {0F,0F,0F,0F}, {2F,0F,14F,1F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 15F, 14F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{0F,14F,16F,16F}, {0F,0F,0F,0F}, {0F,0F,16F,1F}, {0F,0F,16F,1F}, {14F,0F,16F,1F}, {0F,0F,2F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 0F, 16F, 1F, 2F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 13, 13, 13, 13},
              new float[][]{null, {0F,14F,16F,16F}, {0F,15F,16F,16F}, {0F,0F,16F,1F}, {0F,15F,2F,16F}, {14F,15F,16F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(14F, 0F, 2F, 16F, 1F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 4, 4, 13, 13},
              new float[][]{null, {14F,2F,16F,14F}, null, null, {2F,0F,14F,1F}, {2F,15F,14F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 2F, 2F, 1F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 4, 4, 13, 13},
              new float[][]{null, {0F,2F,2F,14F}, null, null, {2F,15F,14F,16F}, {2F,0F,14F,1F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 14F, 16F, 1F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 13, 13, 13, 13, 13},
              new float[][]{null, {0F,0F,16F,2F}, {0F,0F,16F,1F}, {0F,15F,16F,16F}, {14F,15F,16F,16F}, {0F,15F,2F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };


//   0 -> create:block/andesite_casing
//   1 -> create:block/gearbox

// ===== ENCASED_SHAFT ��ͼ�� =====

//   0 -> create:block/andesite_casing

//   1 -> create:block/gearbox

// ===== ENCASED_SHAFT ��ͼ�� =====

//   0 -> create:block/andesite_casing

//   1 -> create:block/gearbox

// ===== ENCASED_SHAFT ��ͼ�� =====

//   0 -> create:block/andesite_casing

//   1 -> create:block/gearbox

// ===== ENCASED_SHAFT ��ͼ�� =====

//   0 -> create:block/andesite_casing

//   1 -> create:block/gearbox

    public static final CreateModels.El[] ENCASED_SHAFT = {

        new CreateModels.El(0F, 0F, 0F, 16F, 16F, 2F, 0F, 1, 8F, 8F, 8F,

              new int[]{13, 13, 13, 13, 13, 13},

              new float[][]{{0F,14F,16F,16F}, {0F,14F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,2F,16F}, {14F,0F,16F,16F}},

              new int[]{180, 0, 0, 0, 0, 0}),

        new CreateModels.El(1F, 0.95F, 2F, 15F, 15.05F, 14F, 0F, 1, 8F, 8F, 8F,

              new int[]{4, 4, 4, 4, 4, 4},

              new float[][]{{1F,2F,15F,14F}, {1F,2F,15F,14F}, null, null, null, null},

              new int[]{180, 0, 0, 0, 0, 0}),

        new CreateModels.El(0F, 0F, 14F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,

              new int[]{13, 13, 13, 13, 13, 13},

              new float[][]{{0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}, {14F,0F,16F,16F}, {0F,0F,2F,16F}},

              new int[]{180, 0, 0, 0, 0, 0}),

        new CreateModels.El(0F, 0F, 2F, 2F, 16F, 14F, 0F, 1, 8F, 8F, 8F,

              new int[]{13, 13, 4, 4, 13, 13},

              new float[][]{{14F,2F,16F,14F}, {0F,2F,2F,14F}, null, null, {2F,0F,14F,16F}, {2F,0F,14F,16F}},

              new int[]{180, 0, 0, 0, 0, 0}),

        new CreateModels.El(14F, 0F, 2F, 16F, 16F, 14F, 0F, 1, 8F, 8F, 8F,

              new int[]{13, 13, 4, 4, 13, 13},

              new float[][]{{0F,2F,2F,14F}, {14F,2F,16F,14F}, null, null, {2F,0F,14F,16F}, {2F,0F,14F,16F}},

              new int[]{180, 0, 0, 0, 0, 0}),

    };






    // ===================== 工程③：装壳齿轮"堵上某一端"用的机壳盖板 =====================
    //
    // 原版把某一端关掉之后，那一端换成的是**不带开口的基础模型**（encased_cogwheel/block.json），
    // 也就是一整块平的机壳面。我们不再生成一套基础模型，而是直接**补一块盖板**：
    //   · 端面那 4 条边框元素围出来的洞是 x2..14 / z2..14 的 12×12 ✓
    //   · 盖板取同样大小、UV 也取 {2,2,14,14} → 和边框的贴图**严丝合缝** ✓
    //   · 盖完之后端面就是一整块平机壳 —— 和原版基础模型看起来完全一致 ✓
    // 只画朝外的那一个面（正端画 up、负端画 down）：四个侧面被边框包着，
    // 画了反而会和边框内壁打架（同深度 z-fighting）✗。
    private static final El ENCASED_PLUG_POS_EL = new El(2F, 15F, 2F, 14F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
            new int[]{13, -1, -1, -1, -1, -1},
            new float[][]{{2F, 2F, 14F, 14F}, null, null, null, null, null},
            new int[]{0, 0, 0, 0, 0, 0});

    private static final El ENCASED_PLUG_NEG_EL = new El(2F, 0F, 2F, 14F, 1F, 14F, 0F, 1, 8F, 8F, 8F,
            new int[]{-1, 13, -1, -1, -1, -1},
            new float[][]{null, {2F, 2F, 14F, 14F}, null, null, null, null},
            new int[]{0, 0, 0, 0, 0, 0});

    public static final El[] ENCASED_PLUG_POS_ANDESITE = { ENCASED_PLUG_POS_EL };
    public static final El[] ENCASED_PLUG_NEG_ANDESITE = { ENCASED_PLUG_NEG_EL };
    /** 黄铜版：同一块盖板，机壳层 13（安山机壳）→ 25（黄铜机壳）✓ */
    public static final El[] ENCASED_PLUG_POS_BRASS = retint(new El[]{ ENCASED_PLUG_POS_EL }, 13, 25);
    public static final El[] ENCASED_PLUG_NEG_BRASS = retint(new El[]{ ENCASED_PLUG_NEG_EL }, 13, 25);

    public static final int LAYER_COUNT = LAYER_NAMES.length;

    /** 顶点级整体旋转开关（四分之一圈数）。调用方设好 -> 画一次 -> 复位。渲染单线程，静态字段安全。 */
    public static int yawQuarters = 0;
    public static int pitchQuarters = 0;

    /**
     * 顶点级整体旋转开关（四分之一圈数），**只有创造马达用**。
     * 调用方设好值 → 画一次 → 立刻复位。渲染是单线程的，所以用静态字段是安全的。
     */


    /** 把表里某一层整层换成另一层，返回新表（原表不动）—— on/off 两态就靠它，不用抄两份元素表 */
    private static El[] retint(El[] src, int from, int to) {
        El[] out = new El[src.length];
        for (int i = 0; i < src.length; i++) {
            El e = src[i];
            int[] layer = e.layer.clone();
            for (int k = 0; k < 6; k++) if (layer[k] == from) layer[k] = to;
            out[i] = new El(e.x0, e.y0, e.z0, e.x1, e.y1, e.z1,
                    e.rotAngle, e.rotAxis, e.rx, e.ry, e.rz, layer, e.uv, e.uvRot);
        }
        return out;
    }

    /** Minecraft 经典逐面明暗 */
    private static final float[] FACE_SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.6F, 0.6F};

    private static final float[][] NORMALS = {
        {0F, 1F, 0F}, {0F, -1F, 0F}, {0F, 0F, -1F}, {0F, 0F, 1F}, {-1F, 0F, 0F}, {1F, 0F, 0F}
    };

    /** 6 个面的顶点索引（= xi*4 + yi*2 + zi），顺序满足 MC 的 UV 约定 */
    private static final int[][] FACES = {
        {2, 6, 7, 3},   // +Y
        {0, 4, 5, 1},   // -Y
        {6, 2, 0, 4},   // -Z
        {3, 7, 5, 1},   // +Z
        {2, 3, 1, 0},   // -X
        {7, 6, 4, 5}    // +X
    };

    /** 一个长方体元素 */
    public static final class El {
        final float x0, y0, z0, x1, y1, z1;
        final float rotAngle;          // 元素旋转
        final int rotAxis;             // 0=X 1=Y 2=Z
        final float rx, ry, rz;        // 旋转原点（像素）
        final int[] layer;             // 6 面 → 全局贴图层
        final float[][] uv;            // 6 面 → {u0,v0,u1,v1}（null = 该面不画）
        final int[] uvRot;             // 6 面 → UV 旋转 0/90/180/270

        El(float x0, float y0, float z0, float x1, float y1, float z1,
           float rotAngle, int rotAxis, float rx, float ry, float rz,
           int[] layer, float[][] uv, int[] uvRot) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.rotAngle = rotAngle; this.rotAxis = rotAxis;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.layer = layer; this.uv = uv; this.uvRot = uvRot;
        }
    }

    // ===== 由 Create 模型生成的元素表 =====
    /** Create shaft.json —— 1 个元素 */
    public static final El[] SHAFT = {
        new El(6F, 0F, 6F, 10F, 16F, 10F, 0F, 1, 8F, 8F, 8F,
              new int[]{1, 1, 0, 0, 0, 0},
              new float[][]{{6F, 6F, 10F, 10F}, {6F, 6F, 10F, 10F}, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /** Create crank_base.json —— 1 个元素 */
    public static final El[] CRANK_BASE = {
        new El(6F, 0F, 6F, 10F, 5F, 10F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, 1, 0, 0, 0, 0},
              new float[][]{null, {6F, 6F, 10F, 10F}, {6F, 0F, 10F, 5F}, {6F, 0F, 10F, 5F}, {6F, 0F, 10F, 5F}, {6F, 0F, 10F, 5F}},
              new int[]{0, 0, 180, 180, 180, 180}),
    };

    /** Create crank_handle.json —— 4 个元素 */
    public static final El[] CRANK_HANDLE = {
        new El(5F, 5F, 11F, 11F, 11F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{3, 3, 3, 3, 3, 3},
              new float[][]{{6F, 0F, 12F, 3F}, {6F, 13F, 12F, 16F}, {5F, 5F, 11F, 11F}, {5F, 5F, 11F, 11F}, {0F, 3F, 3F, 9F}, {13F, 6F, 16F, 12F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(-2F, 6F, 10F, 14F, 10F, 13F, 0F, 1, 8F, 8F, 8F,
              new int[]{2, 2, 2, 2, 2, 2},
              new float[][]{{0F, 6F, 16F, 9F}, {0F, 6F, 16F, 9F}, {0F, 6F, 16F, 10F}, {0F, 6F, 16F, 10F}, {0F, 0F, 4F, 3F}, {0F, 0F, 4F, 3F}},
              new int[]{0, 0, 0, 0, 90, 90}),
        new El(0F, 7F, 9F, 2F, 9F, 10F, 0F, 1, 9F, 8F, 9F,
              new int[]{2, 2, 2, 2, 2, 2},
              new float[][]{{0F, 0F, 2F, 1F}, {0F, 0F, 2F, 1F}, {0F, 0F, 2F, 2F}, {0F, 0F, 2F, 2F}, {0F, 0F, 1F, 2F}, {0F, 0F, 1F, 2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(-0.5F, 6.5F, 3F, 2.5F, 9.5F, 9F, 45F, 2, 1F, 8F, 9F,
              new int[]{0, 0, 0, 0, 0, 0},
              new float[][]{{7F, 1F, 10F, 7F}, {7F, 1F, 10F, 7F}, {6F, 11F, 9F, 14F}, {7F, 5F, 10F, 8F}, {7F, 1F, 10F, 7F}, {7F, 1F, 10F, 7F}},
              new int[]{0, 0, 90, 0, 90, 90}),
    };

    /** Create millstone_block.json —— 6 个元素 */
    public static final El[] MILLSTONE = {
        new El(0F, 0F, 0F, 16F, 6F, 2F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 4, 5, 5, 5, 5},
              new float[][]{{0F, 13F, 8F, 14F}, {0F, 14F, 16F, 16F}, {0F, 13F, 8F, 16F}, {0F, 13F, 8F, 16F}, {0F, 13F, 1F, 16F}, {7F, 13F, 8F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(0F, 0F, 14F, 16F, 6F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 4, 5, 5, 5, 5},
              new float[][]{{0F, 13F, 8F, 14F}, {0F, 16F, 16F, 14F}, {8F, 13F, 0F, 16F}, {8F, 13F, 0F, 16F}, {1F, 13F, 0F, 16F}, {8F, 13F, 7F, 16F}},
              new int[]{180, 0, 0, 0, 0, 0}),
        new El(0F, 0F, 2F, 2F, 6F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 4, -1, -1, 5, 5},
              new float[][]{{1F, 13F, 7F, 14F}, {0F, 2F, 2F, 14F}, null, null, {1F, 13F, 7F, 16F}, {1F, 13F, 7F, 16F}},
              new int[]{270, 0, 0, 0, 0, 0}),
        new El(14F, 0F, 2F, 16F, 6F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 4, -1, -1, 5, 5},
              new float[][]{{1F, 13F, 7F, 14F}, {2F, 2F, 0F, 14F}, null, null, {7F, 13F, 1F, 16F}, {7F, 13F, 1F, 16F}},
              new int[]{90, 0, 0, 0, 0, 0}),
        new El(2F, 1F, 2F, 14F, 6F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 4, -1, -1, -1, -1},
              new float[][]{{0F, 0F, 6F, 6F}, {2F, 2F, 14F, 14F}, null, null, null, null},
              new int[]{270, 0, 0, 0, 0, 0}),
        new El(2F, 12F, 2F, 14F, 16F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{16F, 2F, 10F, 8F}, {16F, 2F, 10F, 8F}, {10F, 8F, 16F, 10F}, {10F, 8F, 16F, 10F}, {10F, 8F, 16F, 10F}, {10F, 8F, 16F, 10F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /** Create millstone_inner.json —— 6 个元素 */
    public static final El[] MILLSTONE_INNER = {
        new El(6.5F, 6F, -1F, 9.5F, 12F, 17F, 0F, 1, 0F, -0.5F, 0F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{0F, 8.5F, 9F, 10F}, {0F, 8.5F, 9F, 10F}, {9F, 10F, 10.5F, 13F}, {9F, 10F, 10.5F, 13F}, {0F, 10F, 9F, 13F}, {0F, 10F, 9F, 13F}},
              new int[]{90, 90, 0, 0, 0, 0}),
        new El(6.5F, 6F, -1F, 9.5F, 12F, 17F, 45F, 1, 8F, 7.5F, 8F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{0F, 8.5F, 9F, 10F}, {0F, 8.5F, 9F, 10F}, {9F, 10F, 10.5F, 13F}, {9F, 10F, 10.5F, 13F}, {0F, 10F, 9F, 13F}, {0F, 10F, 9F, 13F}},
              new int[]{90, 90, 0, 0, 0, 0}),
        new El(-1F, 6F, 6.5F, 17F, 12F, 9.5F, 45F, 1, 8F, 7.5F, 8F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{0F, 8.5F, 9F, 10F}, {0F, 8.5F, 9F, 10F}, {0F, 10F, 9F, 13F}, {0F, 10F, 9F, 13F}, {9F, 10F, 10.5F, 13F}, {9F, 10F, 10.5F, 13F}},
              new int[]{0, 180, 0, 0, 0, 0}),
        new El(-1F, 6F, 6.5F, 17F, 12F, 9.5F, 0F, 1, 0F, -0.5F, 0F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{0F, 8.5F, 9F, 10F}, {0F, 8.5F, 9F, 10F}, {0F, 10F, 9F, 13F}, {0F, 10F, 9F, 13F}, {9F, 10F, 10.5F, 13F}, {9F, 10F, 10.5F, 13F}},
              new int[]{0, 180, 0, 0, 0, 0}),
        new El(2F, 6.5F, 2F, 14F, 11.5F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{5, 5, 5, 5, 5, 5},
              new float[][]{{0F, 0F, 6F, 6F}, {0F, 0F, 6F, 6F}, {0F, 6F, 6F, 8.5F}, {0F, 6F, 6F, 8.5F}, {0F, 6F, 6F, 8.5F}, {0F, 6F, 6F, 8.5F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6F, 0F, 6F, 10F, 8F, 10F, 0F, 1, 8F, 8F, 8F,
              new int[]{1, 1, 0, 0, 0, 0},
              new float[][]{{6F, 6F, 10F, 10F}, {6F, 6F, 10F, 10F}, {6F, 8F, 10F, 16F}, {6F, 8F, 10F, 16F}, {6F, 8F, 10F, 16F}, {6F, 8F, 10F, 16F}},
              new int[]{0, 0, 180, 180, 180, 180}),
    };

    // ===== Create large_cogwheel.json —— 16 个元素（用 tools.ModelGen 生成）=====
    /** Create large_cogwheel.json —— 大齿轮，半径超过 1 格（-7..23 px），靠 TESR 画 */
    public static final El[] LARGE_COGWHEEL = {
        new El(-2F, 6.625F, -2F, 18F, 9.375F, 18F, 45F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,0F,10F,10F}, {0F,0F,10F,10F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6F, 0F, 6F, 10F, 16F, 10F, 0F, 1, 8F, 8F, 8F,
              new int[]{1, 1, 6, 6, 6, 6},
              new float[][]{{6F,6F,10F,10F}, {6F,6F,10F,10F}, {6F,0F,10F,16F}, {6F,0F,10F,16F}, {6F,0F,10F,16F}, {6F,0F,10F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(-7F, 6.525F, 6.5F, 23F, 9.475F, 9.5F, 22.5F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(-7F, 6.525F, 6.5F, 23F, 9.475F, 9.5F, -22.5F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6.5F, 6.525F, -7F, 9.5F, 9.475F, 23F, 22.5F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}},
              new int[]{90, 90, 0, 0, 0, 0}),
        new El(-2F, 6.6F, -2F, 18F, 9.4F, 18F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,0F,10F,10F}, {0F,0F,10F,10F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}, {0F,10F,10F,11.5F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(1F, 5.975F, 1F, 15F, 10.025F, 15F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{1.5F,1.5F,8.5F,8.5F}, {1.5F,1.5F,8.5F,8.5F}, {10F,0F,12.5F,7F}, {10F,0F,12.5F,7F}, {10F,0F,12.5F,7F}, {10F,0F,12.5F,7F}},
              new int[]{0, 0, 90, 90, 90, 90}),
        new El(-1F, 5.975F, 1F, 1F, 10.025F, 15F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, -1},
              new float[][]{{12F,0F,13F,7F}, {13F,0F,14F,7F}, {10F,0F,11F,1F}, {10F,6F,11F,7F}, {10F,0F,11F,7F}, null},
              new int[]{180, 180, 90, 90, 90, 0}),
        new El(1F, 5.975F, -1F, 15F, 10.025F, 1F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, -1, 7, 7},
              new float[][]{{13F,0F,14F,7F}, {12F,0F,13F,7F}, {10F,0F,11F,7F}, null, {10F,6F,11F,7F}, {10F,0F,11F,1F}},
              new int[]{270, 90, 90, 0, 90, 90}),
        new El(15F, 5.975F, 1F, 17F, 10.025F, 15F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, -1, 7},
              new float[][]{{15F,0F,16F,7F}, {14F,0F,15F,7F}, {10F,6F,11F,7F}, {10F,0F,11F,1F}, null, {10F,0F,11F,7F}},
              new int[]{0, 0, 90, 90, 0, 90}),
        new El(1F, 5.975F, 15F, 15F, 10.025F, 17F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, -1, 7, 7, 7},
              new float[][]{{14F,0F,15F,7F}, {15F,0F,16F,7F}, null, {10F,0F,11F,7F}, {10F,0F,11F,1F}, {10F,6F,11F,7F}},
              new int[]{90, 270, 0, 90, 90, 90}),
        new El(6.5F, 6.525F, -7F, 9.5F, 9.475F, 23F, -22.5F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}},
              new int[]{90, 90, 0, 0, 0, 0}),
        new El(-7F, 6.525F, 6.5F, 23F, 9.475F, 9.5F, 45F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6.5F, 6.525F, -7F, 9.5F, 9.475F, 23F, 45F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}},
              new int[]{90, 90, 0, 0, 0, 0}),
        new El(-7F, 6.525F, 6.5F, 23F, 9.475F, 9.5F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6.5F, 6.525F, -7F, 9.5F, 9.475F, 23F, 0F, 1, 8F, 8F, 8F,
              new int[]{7, 7, 7, 7, 7, 7},
              new float[][]{{0F,11.5F,15F,13F}, {0F,11.5F,15F,13F}, {10F,9.5F,11.5F,11F}, {10F,9.5F,11.5F,11F}, {0F,13F,15F,14.5F}, {0F,13F,15F,14.5F}},
              new int[]{90, 90, 0, 0, 0, 0}),
    };

    // ===== Create gearshift/block.json —— 3 个元素 =====
    /**
     * 反转齿轮箱 / 离合器**共用**这套几何（Create 里 clutch/block.json 就是
     * parent = gearshift/block 再换一张外壳贴图）。所以这里只写一份，
     * on/off 两态用 retint() 换外壳层。
     */
    private static final El[] SHIFT_BASE = {
        new El(0F, 0F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{12, 12, 8, 8, 8, 8},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,16F}},
              new int[]{180, 0, 270, 270, 270, 270}),
        new El(1.95F, 16F, 1.95F, 14.05F, 15F, 14.05F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, -1, 4, 4, 4, 4},
              new float[][]{{2F,2F,14F,14F}, null, {2F,15F,14F,16F}, {2F,0F,14F,1F}, {15F,2F,16F,14F}, {0F,2F,1F,14F}},
              new int[]{180, 0, 0, 180, 90, 270}),
        new El(1.95F, 1F, 1.95F, 14.05F, 0F, 14.05F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, 4, 4, 4, 4, 4},
              new float[][]{null, {2F,2F,14F,14F}, {2F,15F,14F,16F}, {2F,0F,14F,1F}, {0F,2F,1F,14F}, {15F,2F,16F,14F}},
              new int[]{0, 0, 180, 0, 90, 270}),
    };

    public static final El[] GEARSHIFT = SHIFT_BASE;                       // 外壳 = gearshift_off
    public static final El[] GEARSHIFT_POWERED = retint(SHIFT_BASE, 8, 9); // 外壳 = gearshift_on
    public static final El[] CLUTCH = retint(SHIFT_BASE, 8, 10);           // 外壳 = clutch_off
    public static final El[] CLUTCH_POWERED = retint(SHIFT_BASE, 8, 11);   // 外壳 = clutch_on

    // ===== Create gearbox/block.json —— 3 个元素 =====
    /** Create gearbox/block.json —— 十字齿轮箱（安山外壳 + 4 面齿轮盘） */
    public static final El[] GEARBOX = {
        new El(0F, 0F, 0F, 16F, 2F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,14F,16F,16F}, {0F,14F,16F,16F}, {0F,14F,16F,16F}, {0F,14F,16F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(0.95F, 2F, 0.95F, 15.05F, 14F, 15.05F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, -1, 4, 4, 4, 4},
              new float[][]{null, null, {1F,2F,15F,14F}, {1F,2F,15F,14F}, {1F,2F,15F,14F}, {1F,2F,15F,14F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(0F, 14F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{13, 13, 13, 13, 13, 13},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /**
     * 齿轮箱"内部那圈齿轮"单独成表 —— 外壳固定、内部齿轮要转，
     * 所以渲染时外壳画一次、这张表再套一层自转画一次。
     */
    public static final El[] GEARBOX_INNER = { GEARBOX[1] };

    // ===== Create models/item/wrench/item.json —— 7 个元素（3D 物品，不是方块）=====
    /**
     * 扳手的**物品模型**：Create 的扳手是 3D 模型物品，不是一张 16x16 图标。
     * 贴图 create:item/wrench 是它的 **UV 展开表**，所以那张图上全是平铺的花纹 ——
     * 直接当图标用就是一坨看不懂的东西，必须按模型画出来才还原。
     */
    public static final El[] WRENCH_ITEM = {
        new El(7.6F, 0F, 7.5F, 8.6F, 7F, 8.5F, 0F, 1, 8.5F, 11F, 8F,
              new int[]{-1, 14, 14, 14, 14, 14},
              new float[][]{null, {2F,12F,4F,14F}, {2F,0F,4F,14F}, {2F,0F,4F,14F}, {2F,0F,4F,14F}, {2F,0F,4F,14F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(6.5F, 8.5F, 7.5F, 8.5F, 14.5F, 8.5F, 0F, 1, 7.5F, 20F, 8F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{5F,7F,7F,8F}, {0F,0F,1F,1F}, {12F,0F,16F,12F}, {16F,0F,12F,12F}, {16F,0F,14F,12F}, {14F,0F,16F,12F}},
              new int[]{90, 0, 0, 0, 0, 0}),
        new El(8.354F, 5F, 7.146F, 9.354F, 12F, 8.146F, -45F, 1, 8.5F, 11F, 8F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{0F,0F,1F,1F}, {0F,0F,1F,1F}, {0F,0F,2F,14F}, {0F,0F,2F,14F}, {0F,2F,2F,16F}, {0F,2F,2F,16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(7F, 14F, 7F, 11F, 15F, 9F, 0F, 1, 8.5F, 11F, 7F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{4F,0F,8F,8F}, {8F,0F,12F,8F}, {4F,8F,12F,10F}, {12F,8F,4F,10F}, {4F,6F,8F,8F}, {4F,0F,8F,2F}},
              new int[]{90, 90, 0, 0, 180, 0}),
        new El(8F, 12F, 7F, 11F, 13F, 9F, 0F, 1, 8.5F, 11F, 7F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{8F,0F,12F,6F}, {4F,0F,8F,6F}, {10F,8F,4F,10F}, {4F,8F,10F,10F}, {4F,6F,8F,8F}, {4F,0F,8F,2F}},
              new int[]{90, 90, 180, 180, 0, 180}),
        new El(6.4F, 8F, 7F, 10.4F, 9F, 9F, 0F, 1, 9F, 11F, 8F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{4F,10F,12F,14F}, {4F,10F,12F,13F}, {4F,10F,12F,12F}, {12F,10F,4F,12F}, {4F,10F,6F,14F}, {4F,10F,6F,14F}},
              new int[]{180, 180, 0, 0, 90, 90}),
        new El(7.5F, 6F, 7F, 9.5F, 7F, 9F, 45F, 1, 8.5F, 11F, 8F,
              new int[]{14, 14, 14, 14, 14, 14},
              new float[][]{{12F,12F,16F,16F}, {12F,12F,16F,16F}, {12F,12F,16F,14F}, {12F,12F,16F,14F}, {12F,12F,16F,14F}, {12F,12F,16F,14F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    // ===== 渲染 =====

    /**
     * 画一组元素。
     *
     * @param axis    方块轴向 0=X 1=Y 2=Z（把模型的 Y 轴摆到真实轴向）
     * @param icons   图集图标（按 LAYER_NAMES 顺序；TESR 走整张贴图那条重载）
     */
    public static void emit(Tessellator tess, El[] els, double ox, double oy, double oz, int axis,
                            Icon[] icons) {
        emit(tess, els, ox, oy, oz, axis, 0F, icons);
    }

    /**
     * 带"模型空间预旋转"的版本。
     *
     * Create 的 hand_crank/handle.json 是**以 Z 轴为转轴**设计的
     * （摇臂沿 X 展开 [-2,6,10]→[14,10,13]，围绕 x=y=8 那条线转；握把也带 Z 轴 45° 旋转），
     * 而其余模型的本征轴都是 Y。给手柄传 preRotX=90 即可把它摆正，
     * 之后整条渲染管线（GL 自转、轴向映射）就完全统一了。
     */
    public static void emit(Tessellator tess, El[] els, double ox, double oy, double oz, int axis,
                            float preRotX, Icon[] icons) {
        float[] uMin = new float[LAYER_COUNT];
        float[] vMin = new float[LAYER_COUNT];
        float[] uScale = new float[LAYER_COUNT];
        float[] vScale = new float[LAYER_COUNT];
        boolean[] enabled = new boolean[LAYER_COUNT];
        for (int l = 0; l < LAYER_COUNT; l++) {
            Icon ic = icons[l];
            if (ic == null) continue;
            enabled[l] = true;
            uMin[l] = ic.getMinU(); vMin[l] = ic.getMinV();
            uScale[l] = ic.getMaxU() - ic.getMinU();
            vScale[l] = ic.getMaxV() - ic.getMinV();
        }
        emitAll(tess, els, ox, oy, oz, axis, preRotX, uMin, vMin, uScale, vScale, enabled, false);
    }

    /** TESR 版：只画指定图层（调用前 bindTexture 那张原图，UV 用整张 0..1） */
    public static void emitRawLayer(Tessellator tess, El[] els, double ox, double oy, double oz,
                                    int axis, int layer) {
        emitRawLayer(tess, els, ox, oy, oz, axis, 0F, layer);
    }

    /** TESR 版 + 模型空间预旋转 */
    public static void emitRawLayer(Tessellator tess, El[] els, double ox, double oy, double oz,
                                    int axis, float preRotX, int layer) {
        float[] uMin = new float[LAYER_COUNT];
        float[] vMin = new float[LAYER_COUNT];
        float[] uScale = new float[LAYER_COUNT];
        float[] vScale = new float[LAYER_COUNT];
        boolean[] enabled = new boolean[LAYER_COUNT];
        for (int l = 0; l < LAYER_COUNT; l++) uScale[l] = 1.0F;
        for (int l = 0; l < LAYER_COUNT; l++) vScale[l] = 1.0F;
        enabled[layer] = true;
        emitAll(tess, els, ox, oy, oz, axis, preRotX, uMin, vMin, uScale, vScale, enabled, true);
    }

    private static void emitAll(Tessellator tess, El[] els, double ox, double oy, double oz, int axis,
                                float preRotX,
                                float[] uMin, float[] vMin, float[] uScale, float[] vScale,
                                boolean[] enabled, boolean shaded) {
        for (int i = 0; i < els.length; i++) {
            emitEl(tess, els[i], ox, oy, oz, axis, preRotX, uMin, vMin, uScale, vScale, enabled, shaded);
        }
    }

    private static void emitEl(Tessellator tess, El e, double ox, double oy, double oz, int axis,
                               float preRotX,
                               float[] uMin, float[] vMin, float[] uScale, float[] vScale,
                               boolean[] enabled, boolean shaded) {
        float[][] corners = new float[8][3];
        int idx = 0;
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    corners[idx][0] = (xi == 0 ? e.x0 : e.x1) / 16F;
                    corners[idx][1] = (yi == 0 ? e.y0 : e.y1) / 16F;
                    corners[idx][2] = (zi == 0 ? e.z0 : e.z1) / 16F;
                    idx++;
                }
            }
        }

        // ★ 顶点级"整体旋转"（绕方块中心）。
        //
        // 【为什么不用 glRotatef】实测在 TESR 里调 glRotatef 之后画面毫无变化
        // （metadata、客户端同步都已用日志确认没问题），所以改成**直接变换顶点坐标** ——
        // 顶点是硬算出来的，GL 矩阵怎么被重置都不影响。
        //
        // 只影响一次绘制；调用方负责在画完后把这两个值复位。
        if (yawQuarters != 0 || pitchQuarters != 0) {
            for (int c = 0; c < 8; c++) {
                float px = corners[c][0] - 0.5F;
                float py = corners[c][1] - 0.5F;
                float pz = corners[c][2] - 0.5F;
                for (int q = 0; q < ((yawQuarters % 4) + 4) % 4; q++) {
                    float nx = pz;              // 绕 Y 转 90°：(x,z) -> (z, -x)
                    float nz = -px;
                    px = nx; pz = nz;
                }
                for (int q = 0; q < ((pitchQuarters % 4) + 4) % 4; q++) {
                    float ny = -pz;             // 绕 X 转 90°：(y,z) -> (-z, y)
                    float nz = py;
                    py = ny; pz = nz;
                }
                corners[c][0] = px + 0.5F;
                corners[c][1] = py + 0.5F;
                corners[c][2] = pz + 0.5F;
            }
        }

        for (int f = 0; f < 6; f++) {
            float[] uv = e.uv[f];
            if (uv == null) continue;
            int layer = e.layer[f];
            if (layer < 0 || !enabled[layer]) continue;

            double fu0 = uMin[layer] + (uv[0] / 16.0D) * uScale[layer];
            double fv0 = vMin[layer] + (uv[1] / 16.0D) * vScale[layer];
            double fu1 = uMin[layer] + (uv[2] / 16.0D) * uScale[layer];
            double fv1 = vMin[layer] + (uv[3] / 16.0D) * vScale[layer];
            double[][] uvs = {{fu0, fv0}, {fu1, fv0}, {fu1, fv1}, {fu0, fv1}};
            int r = ((e.uvRot[f] % 360) + 360) % 360 / 90;
            if (r != 0) {
                double[][] rot = new double[4][];
                for (int i = 0; i < 4; i++) rot[i] = uvs[(i + 4 - r) % 4];
                uvs = rot;
            }

            float[] normal = rotateVector(NORMALS[f], e, axis, preRotX);
            tess.setNormal(normal[0], normal[1], normal[2]);
            float s = shaded ? FACE_SHADE[f] : 1.0F;
            tess.setColorOpaque_F(s, s, s);

            int[] face = FACES[f];
            for (int v = 0; v < 4; v++) {
                double[] p = localToWorld(corners[face[v]], e, axis, preRotX);
                tess.addVertexWithUV(ox + p[0], oy + p[1], oz + p[2], uvs[v][0], uvs[v][1]);
            }
        }
    }

    /** 点：先按元素 rotation 绕其 origin 旋转，再按 preRotX 绕方块中心转，最后按轴向把 Y 摆到真实轴向 */
    private static double[] localToWorld(float[] p, El e, int axis, float preRotX) {
        double x = p[0], y = p[1], z = p[2];
        if (e.rotAngle != 0F) {
            double ox = e.rx / 16.0D, oy = e.ry / 16.0D, oz = e.rz / 16.0D;
            double dx = x - ox, dy = y - oy, dz = z - oz;
            double r = Math.toRadians(e.rotAngle), c = Math.cos(r), sn = Math.sin(r);
            if (e.rotAxis == 0) {          // X
                y = oy + dy * c - dz * sn;
                z = oz + dy * sn + dz * c;
            } else if (e.rotAxis == 1) {   // Y
                x = ox + dx * c - dz * sn;
                z = oz + dx * sn + dz * c;
            } else {                        // Z
                x = ox + dx * c - dy * sn;
                y = oy + dx * sn + dy * c;
            }
        }
        if (preRotX != 0F) {
            double r = Math.toRadians(preRotX), c = Math.cos(r), sn = Math.sin(r);
            double dy = y - 0.5D, dz = z - 0.5D;
            y = 0.5D + dy * c - dz * sn;
            z = 0.5D + dy * sn + dz * c;
        }
        if (axis == 0) return new double[]{y, 1.0D - x, z};
        if (axis == 2) return new double[]{x, 1.0D - z, y};
        return new double[]{x, y, z};
    }

    private static float[] rotateVector(float[] v, El e, int axis, float preRotX) {
        double x = v[0], y = v[1], z = v[2];
        if (e.rotAngle != 0F) {
            double r = Math.toRadians(e.rotAngle), c = Math.cos(r), sn = Math.sin(r);
            if (e.rotAxis == 0) { double ny = y * c - z * sn, nz = y * sn + z * c; y = ny; z = nz; }
            else if (e.rotAxis == 1) { double nx = x * c - z * sn, nz = x * sn + z * c; x = nx; z = nz; }
            else { double nx = x * c - y * sn, ny = x * sn + y * c; x = nx; y = ny; }
        }
        if (preRotX != 0F) {
            double r = Math.toRadians(preRotX), c = Math.cos(r), sn = Math.sin(r);
            double ny = y * c - z * sn, nz = y * sn + z * c;
            y = ny; z = nz;
        }
        if (axis == 0) return new float[]{(float) y, (float) -x, (float) z};
        if (axis == 2) return new float[]{(float) x, (float) -z, (float) y};
        return new float[]{(float) x, (float) y, (float) z};
    }

    // ===== 创造马达（Create creative_motor/block.json，层号已重映射）=====
// ===== CREATIVE_MOTOR ��ͼ�� =====
//   0 -> create:block/creative_casing
//   1 -> create:block/creative_motor
//   2 -> create:block/flap_display_front
//   3 -> create:block/axis

        // ===== 创造马达·竖向（Create creative_motor/block_vertical.json，层号已重映射）=====
// ===== CREATIVE_MOTOR_VERTICAL ��ͼ�� =====
//   0 -> create:block/creative_casing
//   1 -> create:block/creative_motor
//   2 -> create:block/axis

        // ===== 创造马达·**带输出轴**的模型（Create creative_motor/item.json，层号已重映射）=====
    //  ★ 这一版比 block.json 多一根伸到 z=16 的输出轴，那根轴就是输出面的视觉标志。
// ===== CREATIVE_MOTOR_ITEM ��ͼ�� =====
//   0 -> create:block/creative_casing
//   1 -> create:block/creative_motor
//   2 -> create:block/flap_display_front
//   3 -> create:block/axis_top
//   4 -> create:block/axis

    
    // ===== 创造马达·**带输出轴**的模型（Create creative_motor/item.json，层号已重映射）=====
    //  ★ 这一版比 block.json 多一根伸到 z=16 的输出轴，那根轴就是输出面的视觉标志。
// ===== CREATIVE_MOTOR_ITEM ��ͼ�� =====
//   0 -> create:block/creative_casing
//   1 -> create:block/creative_motor
//   2 -> create:block/flap_display_front
//   3 -> create:block/axis_top
//   4 -> create:block/axis

    
    // ===== 水车·安装底板（Create water_wheel/block.json，层号已重映射）=====
    //  ★ 这只是底板；轮子本体是 water_wheel.obj，由 TESR 画的网格，两者要一起画。
// ===== WATER_WHEEL ��ͼ�� =====
//   0 -> create:block/gearbox
//   1 -> create:block/funnel/andesite_funnel_frame

    public static final El[] WATER_WHEEL = {
        new El(1.95F, 1F, 1.95F, 14.05F, 0F, 14.05F, 0F, 1, 8F, 8F, 8F,
              new int[]{-12, 4, 4, 4, 4, 4},
              new float[][]{null, {2F,2F,14F,14F}, {2F,0F,14F,1F}, {2F,15F,14F,16F}, {15F,2F,16F,14F}, {0F,2F,1F,14F}},
              new int[]{0, 180, 180, 0, 270, 90}),
        new El(1.95F, 16F, 1.95F, 14.05F, 15F, 14.05F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, -12, 4, 4, 4, 4},
              new float[][]{{2F,2F,14F,14F}, null, {2F,0F,14F,1F}, {2F,15F,14F,16F}, {0F,2F,1F,14F}, {15F,2F,16F,14F}},
              new int[]{0, 0, 0, 180, 270, 90}),
        new El(2F, 2F, 2F, 14F, 14F, 14F, 0F, 1, 8F, 8F, 8F,
              new int[]{-12, -12, 4, 4, 4, 4},
              new float[][]{null, null, {6F,6F,10F,10F}, {6F,6F,10F,10F}, {6F,6F,10F,10F}, {6F,6F,10F,10F}},
              new int[]{0, 0, 180, 0, 270, 90}),
        new El(0F, 14F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{12, 4, 12, 12, 12, 12},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new El(0F, 0F, 0F, 16F, 2F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{4, 12, 12, 12, 12, 12},
              new float[][]{{0F,0F,16F,16F}, {0F,0F,16F,16F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}, {0F,0F,16F,2F}},
              new int[]{0, 0, 180, 180, 180, 180}),
    };

}