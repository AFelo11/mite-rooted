package net.dsh.createmite.kinetics.client;

import net.dsh.createmite.CMBlocks;
import net.dsh.createmite.kinetics.KineticTileEntity;
import net.minecraft.Block;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import net.minecraft.TileEntity;
import net.minecraft.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 动力部件的旋转渲染（1.6.4 固定管线，手写几何）。
 *
 * 齿轮的几何与逐面 UV 全部来自 `CogwheelGeometry`（= Create 的 cogwheel.json），
 * 与背包里的 3D 显示共用同一份数据，保证"世界/背包"外观一致。
 *
 * 坐标：几何使用方块局部 0..1（像素/16）。这里先把原点移到方块中心，
 * 做完朝向与自转后平移 (-0.5,-0.5,-0.5)，于是几何的 0..1 正好落在本方块内。
 *
 * 绘制顺序：
 *   1) 按 kinetic.axis() 做基准旋转，把局部 Y 轴对到真实轴向
 *   2) 按 TE 角度自转（绕局部 Y，即绕真实轴向）
 *   3) 画几何
 */
public class KineticRenderer extends TileEntitySpecialRenderer {

    // 旧的手写几何贴图常量（保留以免误删历史信息；实际渲染已改用 LAYER_TEX）
    private static final ResourceLocation TEX_SHAFT = new ResourceLocation("textures/blocks/shaft.png");
    private static final ResourceLocation TEX_COGWHEEL = new ResourceLocation("textures/blocks/cogwheel.png");
    private static final ResourceLocation TEX_COGWHEEL_AXIS = new ResourceLocation("textures/blocks/cogwheel_axis.png");
    private static final ResourceLocation TEX_AXIS_TOP = new ResourceLocation("textures/blocks/axis_top.png");
    private static final ResourceLocation TEX_MILLSTONE = new ResourceLocation("textures/blocks/millstone.png");

    private static final ResourceLocation TEX_SHAFT_TOP = new ResourceLocation("textures/blocks/shaft_top.png");

    /** 临时诊断：上一次打印过的马达 metadata（只在变化时打印，避免刷屏） */
    private static int cm$lastMotorMeta = Integer.MIN_VALUE;

    /** CreateModels 各图层对应的原图（顺序必须与 CreateModels.LAYER_NAMES 一一对应） */
    private static final ResourceLocation[] LAYER_TEX = {
        new ResourceLocation("textures/blocks/axis.png"),                    // 0
        new ResourceLocation("textures/blocks/axis_top.png"),                // 1
        new ResourceLocation("textures/blocks/andesite_casing_short.png"),   // 2
        new ResourceLocation("textures/blocks/smooth_dark_log_top.png"),     // 3
        new ResourceLocation("textures/blocks/gearbox.png"),                 // 4
        new ResourceLocation("textures/blocks/millstone.png"),               // 5
        new ResourceLocation("textures/blocks/cogwheel_axis.png"),           // 6
        new ResourceLocation("textures/blocks/large_cogwheel.png"),          // 7
        new ResourceLocation("textures/blocks/gearshift_off.png"),           // 8
        new ResourceLocation("textures/blocks/gearshift_on.png"),            // 9
        new ResourceLocation("textures/blocks/clutch_off.png"),              // 10
        new ResourceLocation("textures/blocks/clutch_on.png"),               // 11
        new ResourceLocation("textures/blocks/andesite_funnel_frame.png"),   // 12
        new ResourceLocation("textures/blocks/andesite_casing.png"),         // 13
        new ResourceLocation("textures/items/wrench.png"),                   // 14（物品贴图）
        // ★ 这三张 + 下面 7 张是 M2 批次 2 加的。**改 LAYER_NAMES 时必须同步这里**，
        //   否则 LAYER_COUNT 比 LAYER_TEX 长 → bindTexture 越界，
        //   表现就是世界里一放创造马达/水车就崩（ArrayIndexOutOfBoundsException）。
        new ResourceLocation("textures/blocks/creative_casing.png"),         // 15
        new ResourceLocation("textures/blocks/creative_motor.png"),          // 16
        new ResourceLocation("textures/blocks/flap_display_front.png"),      // 17
        new ResourceLocation("textures/blocks/crushing_wheel_insert.png"),   // 18
        new ResourceLocation("textures/blocks/crushing_wheel_plates.png"),   // 19
        new ResourceLocation("textures/blocks/waterwheel_metal.png"),        // 20
        new ResourceLocation("textures/blocks/log_oak.png"),                 // 21（原版）
        new ResourceLocation("textures/blocks/log_oak_top.png"),             // 22
        new ResourceLocation("textures/blocks/log_spruce_top.png"),          // 23   // ★ MITE jar 里确实有 log_spruce_top.png（478B，与 log_oak_top.png 同尺寸）
                                                                             //    ✗ 曾误改成 tree_spruce_top.png —— MITE 里**没有**这张，
                                                                             //      run135/136 日志实测 "Resource not found: textures/blocks/tree_spruce_top.png"
                                                                             //      → 封装箱上下面的木纹全变成紫黑格（用户截图里的紫色条纹就是它）
        new ResourceLocation("textures/blocks/planks_oak.png"),              // 24
            new ResourceLocation("textures/blocks/brass_casing.png"),          // 25  ← 黄铜封装（必须有，否则封装箱模型引用的图层 25 会越界）
            new ResourceLocation("textures/blocks/andesite_encased_cogwheel_side.png"),   // 26
            new ResourceLocation("textures/blocks/brass_encased_cogwheel_side.png"),      // 27
            new ResourceLocation("textures/blocks/andesite_encased_cogwheel_side_connected.png"), // 28（32×32 大齿专用）
            new ResourceLocation("textures/blocks/brass_encased_cogwheel_side_connected.png"),    // 29
            new ResourceLocation("textures/blocks/brass_gearbox.png"),                            // 30
            // ★ 熔炉结构（2026-09-29）新增的三层机壳贴图：直接用 MITE 自带贴图，
            //   所以 mod 资源目录里不用再放一份（离线渲染器取不到会退回纯色，正常）
            new ResourceLocation("textures/blocks/obsidian.png"),                                // 31
            new ResourceLocation("textures/blocks/netherrack.png"),                              // 32
            new ResourceLocation("textures/blocks/cobblestone.png"),                             // 33
    };

    /**
     * ★ 启动自检：LAYER_TEX 必须和 CreateModels.LAYER_NAMES **一一对应**。
     *
     * 这两个表分开维护，加贴图层时很容易只改一边 —— 那样世界里一放用到新层的方块
     * 就会 bindTexture 越界直接崩（ArrayIndexOutOfBoundsException），而且崩的是渲染线程，
     * 报错信息看不出来是哪张表少了一项。这里在类加载时就喊出来。
     */
    static {
        if (LAYER_TEX.length != CreateModels.LAYER_COUNT) {
            System.out.println("[MITE][严重] LAYER_TEX(" + LAYER_TEX.length
                    + ") 与 CreateModels.LAYER_NAMES(" + CreateModels.LAYER_COUNT + ") 数量不一致！"
                    + " 新增贴图层必须同时改三处：CreateModels.LAYER_NAMES / KineticRenderer.LAYER_TEX"
                    + " / tools/OfflineRender.LAYERS");
        }
    }

    /** 齿轮的 5 张贴图，顺序与 CogwheelGeometry 的贴图层编号一致 */
    private static final ResourceLocation[] COGWHEEL_LAYERS = {
        TEX_COGWHEEL, TEX_COGWHEEL_AXIS, TEX_AXIS_TOP, TEX_SHAFT, TEX_SHAFT_TOP
    };

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof KineticTileEntity)) return;
        KineticTileEntity kinetic = (KineticTileEntity) te;
        // ★ blockOrNull()：没有 worldObj 的元件一律不画（读 getBlockType 会 NPE，原因见
        //   KineticTileEntity.hasWorld 的说明）。渲染路径以前靠"能进世界才画"侥幸没事，
        //   现在显式挡住，免得以后再踩。
        Block block = kinetic.blockOrNull();
        if (block == null) return;

        // ★ 已经成型的大熔炉：27 格里的包裹传动杆**不再单独画**（整机由核心那格的
        //   FurnaceBigRenderer 画 ✓）。不挡的话，朝外那根传动杆的轴会从大模型的传动口里穿出来 ✗。
        if (block instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft && kinetic.getWorldObj() != null
                && net.dsh.createmite.furnace.FurnaceMultiblock.hasFormedCoreNear(
                        kinetic.getWorldObj(), te.xCoord, te.yCoord, te.zCoord)) {
            return;
        }

        float angle = kinetic.angle + kinetic.clientSpeed * partialTicks * 0.3F;
        int axis = kinetic.axis();
        this.spinFlip = (block != CMBlocks.blockHandCrank) && axis == 0;   // 见 pushSpin 注释

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);

        // ★ 大齿轮**不做任何平移**：模型原样画在主体这一格的中心 ✓（别再加"挪到组中心"的处理 ✗）。
        //   原因：大齿轮占的是"主体 + 平面内上下左右 4 个正交邻居格"，
        //   **主体自己就是中心格**，模型围着它画就已经是"以组中心渲染" ✓。
        //   曾经占 2×2 时这里加过"平移半格到 2×2 组中心" —— 那一版因为
        //   2x2 的中心落在四格共用的**角**上（能互动/能传动的本体在角落）被整体回退了 ✗，
        //   现在这套正十字方案从根上不需要平移，所以这里保持干净的 0 偏移。
        GL11.glDisable(GL11.GL_LIGHTING);

        // 1) 基准旋转：把局部 Y 轴对到真实轴向
        if (block == CMBlocks.blockHandCrank) {
            // 曲柄有"朝向"（轴座指向依附的方块），按 Create 的 blockstate x/y 规则摆正
            applyCrankFacing(kinetic.axis(), kinetic.facePositive());
        } else if (axis == 0) {
            GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
        } else if (axis == 2) {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
        }
        // 2) 原点回到方块最小角：几何坐标是 0..1
        //    【注意】自转**不能**加在这里：那样曲柄的轴座、石磨的外壳都会跟着转。
        //    自转只在各分支里给"该转的部件"单独加（pushSpin/popSpin）。
        GL11.glTranslated(-0.5D, -0.5D, -0.5D);

        Tessellator tess = Tessellator.instance;
        if (cullFace) GL11.glDisable(GL11.GL_CULL_FACE);   // 双面绘制，避免绕序判断出错导致面不可见

        if (block == CMBlocks.blockCogwheel || block == CMBlocks.encAndesiteCog || block == CMBlocks.encBrassCog) {
            if (block instanceof net.dsh.createmite.kinetics.block.BlockEncased) {
                net.dsh.createmite.kinetics.block.BlockEncased enc =
                        (net.dsh.createmite.kinetics.block.BlockEncased) block;
                boolean brass = enc.isBrass();
                this.drawModel(brass ? CreateModelsBrass.BRASS_ENCASED_COGWHEEL : CreateModels.ENCASED_COGWHEEL, 0F, tess);
                // ★ 工程③：被扳手关掉的那一端要**堵上** —— 补一块机壳盖板，端面恢复成整块平机壳 ✓
                //   （复刻原版：关掉之后换用不带开口的基础模型 ✓，见 CreateModels 里盖板那段说明）
                if (kinetic.isShaftEndClosed(net.dsh.createmite.kinetics.KineticHelper.POS_DIR[axis])) {
                    this.drawModel(brass ? CreateModels.ENCASED_PLUG_POS_BRASS
                                         : CreateModels.ENCASED_PLUG_POS_ANDESITE, 0F, tess);
                }
                if (kinetic.isShaftEndClosed(net.dsh.createmite.kinetics.KineticHelper.negativeDir(axis))) {
                    this.drawModel(brass ? CreateModels.ENCASED_PLUG_NEG_BRASS
                                         : CreateModels.ENCASED_PLUG_NEG_ANDESITE, 0F, tess);
                }
            }
            this.pushSpin(angle);
            for (int layer = 0; layer < COGWHEEL_LAYERS.length; layer++) {
                this.bindTexture(COGWHEEL_LAYERS[layer]);
                tess.startDrawingQuads();
                tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
                CogwheelGeometry.emitRawLayer(tess, 0.0D, 0.0D, 0.0D, 1, layer, true);
                tess.draw();
            }
            this.popSpin();
        } else if (block == CMBlocks.blockShaft || block == CMBlocks.encAndesiteShaft || block == CMBlocks.encBrassShaft) {
                // ★ 已封装（资料 396859/396860 安山/黄铜传动杆箱）：外壳静止 + 里面的轴照常自转 ✓
                //   模型取自原版 encased_shaft/block.json（几何完整 ✓），贴图按名字映射 ✓
                if (block instanceof net.dsh.createmite.kinetics.block.BlockEncased) this.drawModel(((net.dsh.createmite.kinetics.block.BlockEncased) block).isBrass() ? CreateModelsBrass.BRASS_ENCASED_SHAFT : CreateModels.ENCASED_SHAFT, 0F, tess);
            // Create 原版 shaft.json：一根 4x4x16 的轴（被带动时整根自转）
            this.pushSpin(angle);
            this.drawModel(CreateModels.SHAFT, 0F, tess);
            this.popSpin();
        } else if (block == CMBlocks.blockHandCrank) {
            // 固定的轴座 + 随手柄一起转的部分
            this.drawModel(CreateModels.CRANK_BASE, 0F, tess);       // 轴座固定
            this.pushSpin(angle);                                    // 只有手柄绕轴自转
            // 【关键】handle.json 以 Z 轴为转轴（摇臂沿 X 展开、握把带 Z 轴 45° 旋转），
            // 用 preRotX=90 在**模型空间**把它摆成以 Y 为转轴，
            // 于是自转、物品栏渲染、朝向旋转全部共用同一条管线，不必再手工叠 GL 矩阵。
            this.drawModel(CreateModels.CRANK_HANDLE, 90F, tess);
            this.popSpin();
        } else if (block == CMBlocks.blockMillstone) {
            // 外壳固定，内部磨盘随转速转
            this.drawModel(CreateModels.MILLSTONE, 0F, tess);        // 外壳固定
            this.pushSpin(angle);                                    // 只有内盘绕轴原地自转
            this.drawModel(CreateModels.MILLSTONE_INNER, 0F, tess);
            this.popSpin();
        } else if (block == CMBlocks.blockLargeCogwheel || block == CMBlocks.encAndesiteLargeCog || block == CMBlocks.encBrassLargeCog) {
            if (block instanceof net.dsh.createmite.kinetics.block.BlockEncased) {
                net.dsh.createmite.kinetics.block.BlockEncased enc =
                        (net.dsh.createmite.kinetics.block.BlockEncased) block;
                boolean brass = enc.isBrass();
                this.drawModel(brass ? CreateModelsBrass.BRASS_ENCASED_LARGE_COGWHEEL : CreateModels.ENCASED_LARGE_COGWHEEL, 0F, tess);
                // ★ 工程③：被扳手关掉的那一端要**堵上** —— 补一块机壳盖板，端面恢复成整块平机壳 ✓
                //   （复刻原版：关掉之后换用不带开口的基础模型 ✓，见 CreateModels 里盖板那段说明）
                if (kinetic.isShaftEndClosed(net.dsh.createmite.kinetics.KineticHelper.POS_DIR[axis])) {
                    this.drawModel(brass ? CreateModels.ENCASED_PLUG_POS_BRASS
                                         : CreateModels.ENCASED_PLUG_POS_ANDESITE, 0F, tess);
                }
                if (kinetic.isShaftEndClosed(net.dsh.createmite.kinetics.KineticHelper.negativeDir(axis))) {
                    this.drawModel(brass ? CreateModels.ENCASED_PLUG_NEG_BRASS
                                         : CreateModels.ENCASED_PLUG_NEG_ANDESITE, 0F, tess);
                }
            }
            // 大齿轮：整块（含轴）一起转
            this.pushSpin(angle);
            this.drawModel(CreateModels.LARGE_COGWHEEL, 0F, tess);
            this.popSpin();
        } else if (block == CMBlocks.blockCrushingWheel) {
            // 粉碎轮：整轮绕轴转；成对摆放时两个轮子咬合
            this.pushSpin(angle);
            this.drawMesh(CreateMeshes.get("crushing_wheel"), tess);
            this.popSpin();
        } else if (block == CMBlocks.blockWaterWheel) {
            // 水车：整块（轮 + 桨叶）绕轴转
            // 底板（Create water_wheel/block.json，静止）+ 轮子本体（OBJ 网格，自转）
            this.drawModel(CreateModels.WATER_WHEEL, 0F, tess);
            this.pushSpin(angle);
            this.drawMesh(CreateMeshes.get("water_wheel"), tess);
            this.popSpin();
        } else if (block == CMBlocks.blockLargeWaterWheel) {
            // ★ **只画主体一层**。
            //
            // 【为什么不再画延伸段】原版的 large_water_wheel 有一个 extension 占位方块（沿轴相邻一格），
            //   我以前让主体的 TESR 把那一层也画了 ✗ —— 结果单放一个大型水车会看到**两层轮子**
            //   （主体 + 延伸段叠在一起），比原版多一层，一眼就不对 ✓（用户拿原版截图对比过 ✓）。
            //   原版单放一个大型水车就是**一层** ✓；要厚度得自己再放一个延伸方块 ✓ —— 我们没做那个方块，
            //   所以这里也只画一层，和原版单方块的外观完全一致 ✓。
            this.pushSpin(angle);
            this.drawMesh(CreateMeshes.get("large_water_wheel"), tess);
            this.popSpin();
        } else if (block instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft) {
            // 熔炉结构的「包裹传动杆」：机壳静止 + 里面那根轴自转 ✓
            //   —— 画法与「安山/黄铜传动杆箱」完全一致（外壳静止、内轴 pushSpin）✓
            //   preRotX=180：工程里开口画在局部 +Y（顶），metadata 的朝向位说"开口朝轴的负方向"时，
            //   整份几何绕方块中心翻 180°（CreateModels.localToWorld 里的 preRotX 就是干这个的 ✓）。
            net.dsh.createmite.kinetics.block.BlockWrappedShaft ws =
                    (net.dsh.createmite.kinetics.block.BlockWrappedShaft) block;
            float wrappedFlip = kinetic.facePositive() ? 0F : 180F;
            this.drawModel(ws.shellModel(), wrappedFlip, tess);
            this.pushSpin(angle);
            this.drawModel(ws.shaftModel(), wrappedFlip, tess);
            this.popSpin();
        } else if (block == CMBlocks.blockGearbox) {
            // 十字齿轮箱：**整块完全不转**。
            // 原版就是这样的：齿轮都藏在外壳里，从外面看不出任何运动，
            // "转不转"只能靠下游机器判断。（之前让中间那圈外壳跟着转是错的。）
            this.drawModel(CreateModels.GEARBOX, 0F, tess);
        } else if (block == CMBlocks.blockClutch) {
            // 离合器：通电/断电换一张外壳贴图（红石状态现读，不存盘）
            this.drawModel(kinetic.isPowered() ? CreateModels.CLUTCH_POWERED : CreateModels.CLUTCH, 0F, tess);
        } else if (block == CMBlocks.blockGearshift) {
            this.drawModel(kinetic.isPowered() ? CreateModels.GEARSHIFT_POWERED : CreateModels.GEARSHIFT, 0F, tess);
        }

        if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    /**
     * 绕"方块中心轴"（局部 Y 轴，位于 x=z=0.5）做自转。
     * 几何坐标是 0..1、原点在方块最小角，所以要先平移到轴线上再旋转、再平移回来，
     * 否则会绕方块角旋转（看起来就是"绕着转圈"而不是原地自转）。
     */
    /**
     * 自转符号修正开关（每次渲染前按方块设置）。
     *
     * 【为什么需要】自转统一用"绕局部 +Y、右手正向"，再靠基准旋转把局部 Y 对到真实轴向：
     *   轴 Z：glRotatef(90, 1,0,0) → 局部 +Y 落到世界 **+Z** ✓ 旋向一致
     *   轴 X：glRotatef(90, 0,0,1) → 局部 +Y 落到世界 **-X** ✗ 旋向被镜像了！
     * 所以在轴 X 上，"正角度"实际是绕世界 +X 的**负**向旋转 —— 齿轮/水车全都会反转。
     * 曲柄走的是 applyCrankFacing（另一套映射），不受影响，不要跟着翻。
     */
    private boolean spinFlip;

    private void pushSpin(float deg) {
        GL11.glPushMatrix();
        GL11.glTranslated(0.5D, 0.0D, 0.5D);
        GL11.glRotatef(this.spinFlip ? -deg : deg, 0.0F, 1.0F, 0.0F);
        GL11.glTranslated(-0.5D, 0.0D, -0.5D);
    }

    private void popSpin() {
        GL11.glPopMatrix();
    }

    /**
     * 按图层逐张贴图画出 Create 模型（CreateModels 里 UV 是 0..16，这里绑原图用整张 0..1）。
     *
     * 【只绑这个模型真正用到的图层】先扫一遍元素表统计用到的层号。
     * 图层表是全局的（现在 14 张），如果不筛，每画一个方块都要白绑 14 次贴图。
     */
    private void drawModel(CreateModels.El[] els, float preRotX, Tessellator tess) {
        boolean[] used = new boolean[CreateModels.LAYER_COUNT];
        for (int i = 0; i < els.length; i++) {
            for (int f = 0; f < 6; f++) {
                int layer = els[i].layer[f];
                if (layer >= 0 && layer < CreateModels.LAYER_COUNT) used[layer] = true;
            }
        }
        for (int layer = 0; layer < CreateModels.LAYER_COUNT; layer++) {
            if (!used[layer]) continue;
            this.bindTexture(LAYER_TEX[layer]);
            tess.startDrawingQuads();
            tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            CreateModels.emitRawLayer(tess, els, 0.0D, 0.0D, 0.0D, 1, preRotX, layer);
            tess.draw();
        }
    }

    /**
     * 按图层逐张贴图画 **OBJ 网格**（水车 / 大型水车 / 粉碎轮）。
     *
     * 和 drawModel 同一个套路：先统计这个网格用到哪些层，只绑那几张，避免白绑 25 次。
     * 差别在 CreateMeshes.draw 内部用 GL_TRIANGLES（OBJ 全是三角形）。
     */
    private void drawMesh(CreateMeshes.Mesh mesh, Tessellator tess) {
        if (mesh == null) return;
        boolean[] used = new boolean[CreateModels.LAYER_COUNT];
        for (int i = 0; i < mesh.layers.length; i++) {
            int l = mesh.layers[i];
            if (l >= 0 && l < CreateModels.LAYER_COUNT) used[l] = true;
        }
        for (int layer = 0; layer < CreateModels.LAYER_COUNT; layer++) {
            if (!used[layer]) continue;
            this.bindTexture(LAYER_TEX[layer]);
            CreateMeshes.drawLayer(mesh, null, layer, tess);   // icons=null → 用模型自带的 0..1 UV
        }
    }

    /**
     * 复刻 Create 的 blockstates/hand_crank.json。
     *
     * 模型本身按"facing=up（轴座朝下）"设计，官方用 x/y 两个旋转把它摆到各个 facing：
     *   up: -          down: x180      north: x90
     *   south: x90,y180    east: x90,y90    west: x90,y270
     *
     * 换算到 GL（Minecraft 的模型旋转是绕轴的**反向**右手旋转，且先 x 后 y）：
     *   glRotatef(-y, 0,1,0)  然后  glRotatef(-x, 1,0,0)
     * 两处都绕方块中心 —— 调用点在 translate(+0.5) 与 translate(-0.5) 之间。
     *
     * @param axis      0=X 1=Y 2=Z
     * @param facePos   facing 是否指向该轴正方向
     */
    private static void applyCrankFacing(int axis, boolean facePos) {
        int x;
        int y;
        if (axis == 1) {              // Y 轴：up / down
            x = facePos ? 0 : 180;
            y = 0;
        } else if (axis == 2) {       // Z 轴：south(+Z) / north(-Z)
            x = 90;
            y = facePos ? 180 : 0;
        } else {                      // X 轴：east(+X) / west(-X)
            x = 90;
            y = facePos ? 90 : 270;
        }
        if (y != 0) GL11.glRotatef(-y, 0.0F, 1.0F, 0.0F);
        if (x != 0) GL11.glRotatef(-x, 1.0F, 0.0F, 0.0F);
    }

    /** 画一个带全幅贴图 UV 的长方体（方块局部 0..1 坐标） */
    private static void drawBox(Tessellator tess, float x0, float y0, float z0, float x1, float y1, float z1) {
        tess.startDrawingQuads();

        tess.setNormal(0.0F, 1.0F, 0.0F);
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        tess.addVertexWithUV(x0, y1, z1, 0.0D, 0.0D);
        tess.addVertexWithUV(x0, y1, z0, 0.0D, 1.0D);
        tess.addVertexWithUV(x1, y1, z0, 1.0D, 1.0D);
        tess.addVertexWithUV(x1, y1, z1, 1.0D, 0.0D);

        tess.setNormal(0.0F, -1.0F, 0.0F);
        tess.setColorOpaque_F(0.5F, 0.5F, 0.5F);
        tess.addVertexWithUV(x1, y0, z1, 1.0D, 0.0D);
        tess.addVertexWithUV(x1, y0, z0, 1.0D, 1.0D);
        tess.addVertexWithUV(x0, y0, z0, 0.0D, 1.0D);
        tess.addVertexWithUV(x0, y0, z1, 0.0D, 0.0D);

        tess.setNormal(0.0F, 0.0F, -1.0F);
        tess.setColorOpaque_F(0.8F, 0.8F, 0.8F);
        tess.addVertexWithUV(x0, y1, z0, 0.0D, 0.0D);
        tess.addVertexWithUV(x0, y0, z0, 0.0D, 1.0D);
        tess.addVertexWithUV(x1, y0, z0, 1.0D, 1.0D);
        tess.addVertexWithUV(x1, y1, z0, 1.0D, 0.0D);

        tess.setNormal(0.0F, 0.0F, 1.0F);
        tess.setColorOpaque_F(0.8F, 0.8F, 0.8F);
        tess.addVertexWithUV(x1, y1, z1, 0.0D, 0.0D);
        tess.addVertexWithUV(x1, y0, z1, 0.0D, 1.0D);
        tess.addVertexWithUV(x0, y0, z1, 1.0D, 1.0D);
        tess.addVertexWithUV(x0, y1, z1, 1.0D, 0.0D);

        tess.setNormal(-1.0F, 0.0F, 0.0F);
        tess.setColorOpaque_F(0.6F, 0.6F, 0.6F);
        tess.addVertexWithUV(x0, y1, z1, 0.0D, 0.0D);
        tess.addVertexWithUV(x0, y0, z1, 0.0D, 1.0D);
        tess.addVertexWithUV(x0, y0, z0, 1.0D, 1.0D);
        tess.addVertexWithUV(x0, y1, z0, 1.0D, 0.0D);

        tess.setNormal(1.0F, 0.0F, 0.0F);
        tess.setColorOpaque_F(0.6F, 0.6F, 0.6F);
        tess.addVertexWithUV(x1, y1, z0, 0.0D, 0.0D);
        tess.addVertexWithUV(x1, y0, z0, 0.0D, 1.0D);
        tess.addVertexWithUV(x1, y0, z1, 1.0D, 1.0D);
        tess.addVertexWithUV(x1, y1, z1, 1.0D, 0.0D);

        tess.draw();
    }
}
