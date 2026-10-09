package net.dsh.createmite.kinetics.client;

/**
 * 熔炉结构的「包裹传动杆」几何（用户提供的 Blockbench 工程，2026-09）。
 *
 * 【坐标变换】工程里轴是沿 Z、开口朝北（-Z）画的；本引擎的模型一律「局部 +Y = 自转轴」，
 * 所以生成时做了一次刚性旋转：x2 = x, y2 = 16 - z, z2 = y（-Z->+Y、+Z->-Y、+Y->+Z、-Y->-Z、正负X 不变）。
 * 于是轴沿局部 Y、开口朝局部 +Y（顶面）；放置时再按 metadata 的轴向与朝向位摆到真实方向。
 *
 * 【UV】不照抄工程里的 uv 数字，而是用工程自己那套「按位置投影」规则（N/S->(x,y)、E/W->(z,y)、U/D->(x,z)）
 * 对旋转后的盒子重算 —— 这样机壳各外表面仍然拼成一整张无缝 16x16。
 * 生成脚本：_analysis 下的 _gen_furnace_models.mjs（重跑即可覆盖本文件）。
 */
public final class CreateModelsFurnace {

    private CreateModelsFurnace() {}

    /** 机壳（5 个元素：西壁/东壁/下壁/上壁/南封板）—— OBSIDIAN */
    public static final CreateModels.El[] WRAPPED_SHELL_OBSIDIAN = {
        new CreateModels.El(0F, 2F, 0F, 6F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{31, -1, 31, 31, 31, -1},
              new float[][]{{0F, 0F, 6F, 16F}, null, {0F, 2F, 6F, 16F}, {0F, 2F, 6F, 16F}, {0F, 2F, 16F, 16F}, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(10F, 2F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{31, -1, 31, 31, -1, 31},
              new float[][]{{10F, 0F, 16F, 16F}, null, {10F, 2F, 16F, 16F}, {10F, 2F, 16F, 16F}, null, {0F, 2F, 16F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 0F, 10F, 16F, 6F, 0F, 1, 8F, 8F, 8F,
              new int[]{31, -1, 31, -1, -1, -1},
              new float[][]{{6F, 0F, 10F, 6F}, null, {6F, 2F, 10F, 16F}, null, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 10F, 10F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{31, -1, -1, 31, -1, -1},
              new float[][]{{6F, 10F, 10F, 16F}, null, null, {6F, 2F, 10F, 16F}, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 0F, 16F, 2F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, 31, 31, 31, 31, 31},
              new float[][]{null, {0F, 0F, 16F, 16F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /** 机壳（5 个元素：西壁/东壁/下壁/上壁/南封板）—— NETHERRACK */
    public static final CreateModels.El[] WRAPPED_SHELL_NETHERRACK = {
        new CreateModels.El(0F, 2F, 0F, 6F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{32, -1, 32, 32, 32, -1},
              new float[][]{{0F, 0F, 6F, 16F}, null, {0F, 2F, 6F, 16F}, {0F, 2F, 6F, 16F}, {0F, 2F, 16F, 16F}, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(10F, 2F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{32, -1, 32, 32, -1, 32},
              new float[][]{{10F, 0F, 16F, 16F}, null, {10F, 2F, 16F, 16F}, {10F, 2F, 16F, 16F}, null, {0F, 2F, 16F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 0F, 10F, 16F, 6F, 0F, 1, 8F, 8F, 8F,
              new int[]{32, -1, 32, -1, -1, -1},
              new float[][]{{6F, 0F, 10F, 6F}, null, {6F, 2F, 10F, 16F}, null, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 10F, 10F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{32, -1, -1, 32, -1, -1},
              new float[][]{{6F, 10F, 10F, 16F}, null, null, {6F, 2F, 10F, 16F}, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 0F, 16F, 2F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, 32, 32, 32, 32, 32},
              new float[][]{null, {0F, 0F, 16F, 16F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /** 机壳（5 个元素：西壁/东壁/下壁/上壁/南封板）—— COBBLESTONE */
    public static final CreateModels.El[] WRAPPED_SHELL_COBBLESTONE = {
        new CreateModels.El(0F, 2F, 0F, 6F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{33, -1, 33, 33, 33, -1},
              new float[][]{{0F, 0F, 6F, 16F}, null, {0F, 2F, 6F, 16F}, {0F, 2F, 6F, 16F}, {0F, 2F, 16F, 16F}, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(10F, 2F, 0F, 16F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{33, -1, 33, 33, -1, 33},
              new float[][]{{10F, 0F, 16F, 16F}, null, {10F, 2F, 16F, 16F}, {10F, 2F, 16F, 16F}, null, {0F, 2F, 16F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 0F, 10F, 16F, 6F, 0F, 1, 8F, 8F, 8F,
              new int[]{33, -1, 33, -1, -1, -1},
              new float[][]{{6F, 0F, 10F, 6F}, null, {6F, 2F, 10F, 16F}, null, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(6F, 2F, 10F, 10F, 16F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{33, -1, -1, 33, -1, -1},
              new float[][]{{6F, 10F, 10F, 16F}, null, null, {6F, 2F, 10F, 16F}, null, null},
              new int[]{0, 0, 0, 0, 0, 0}),
        new CreateModels.El(0F, 0F, 0F, 16F, 2F, 16F, 0F, 1, 8F, 8F, 8F,
              new int[]{-1, 33, 33, 33, 33, 33},
              new float[][]{null, {0F, 0F, 16F, 16F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}, {0F, 0F, 16F, 2F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

    /** 里面的那根轴（1 个元素）—— 三种材质共用同一份几何与贴图 */
    public static final CreateModels.El[] WRAPPED_SHAFT = {
        new CreateModels.El(6F, 0F, 6F, 10F, 16F, 10F, 0F, 1, 8F, 8F, 8F,
              new int[]{1, -1, 0, 0, 0, 0},
              new float[][]{{6F, 6F, 10F, 10F}, null, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}, {6F, 0F, 10F, 16F}},
              new int[]{0, 0, 0, 0, 0, 0}),
    };

}