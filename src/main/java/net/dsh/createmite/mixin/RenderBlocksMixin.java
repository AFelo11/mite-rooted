package net.dsh.createmite.mixin;

import net.dsh.createmite.kinetics.block.BlockClutch;
import net.dsh.createmite.kinetics.block.BlockCogwheel;
import net.dsh.createmite.kinetics.block.BlockGearbox;
import net.dsh.createmite.kinetics.block.BlockGearshift;
import net.dsh.createmite.kinetics.block.BlockHandCrank;
import net.dsh.createmite.kinetics.block.BlockLargeCogwheel;
import net.dsh.createmite.kinetics.block.BlockKineticBase;
import net.dsh.createmite.kinetics.block.BlockMillstone;
import net.dsh.createmite.kinetics.block.BlockShaft;
import net.dsh.createmite.kinetics.client.CogwheelGeometry;
import net.dsh.createmite.kinetics.client.CreateModels;
import net.minecraft.Block;
import net.minecraft.Icon;
import net.minecraft.RenderBlocks;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让动力部件在"背包 / 手持"里也显示成真正的 3D 模型（与 Create 一致）。
 *
 * 为什么必须在这里接管：
 *  - 方块物品走 3D 分支时调用 RenderBlocks.renderBlockAsItem()，该方法只会按方块范围画长方体；
 *    而我们的方块本体贴图是全透明的（防止世界里和 TESR 重叠闪烁），所以必须自己画模型。
 *  - 这些方块的 getItemIconName() 一律不要重写：一旦重写，ItemBlock 会把图标注册进物品图集
 *    （ItemStack.getSpriteNumber() 变成 1），ItemRenderer 就根本不走 3D 分支，只能显示一张平面图。
 *
 * 光照：标准物品光照会叠加到 >1 把木色冲成米白，这里关掉，改用几何自带的逐面明暗。
 */
@Mixin(RenderBlocks.class)
public abstract class RenderBlocksMixin {

    /**
     * ★ 成型的大熔炉：27 格方块的本体一律**不画**（整机由核心那格的 FurnaceBigRenderer 画 ✓）。
     *
     * 【为什么在这里】{@code WorldRenderer} 对每个方块只调这一个入口
     * （javap 实证：整块地形渲染里 renderBlockByRenderType 只有一处调用点），
     * 在这里 cancel 掉就等于"这一格不画" ✓，而且开销 O(1)（读一个 metadata 位）✓。
     *
     * 【为什么不用"把方块换成空气"那套】27 格是真实方块：碰撞、光照、动力网络都还在 ✓，
     * 换成空气会把传动杆的动力连接也一起弄没 ✗。
     */
    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void createmite$hideFormedFurnace(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.IBlockAccess access = ((RenderBlocks) (Object) this).blockAccess;
        if (access == null) return;
        if (!(block instanceof net.dsh.createmite.block.FurnaceCoreBlock)
                && net.dsh.createmite.furnace.FurnaceMultiblock.casingTypeOf(block) < 0) return;
        if (!net.dsh.createmite.furnace.FurnaceMultiblock.isHidden(access, x, y, z)) return;

        // ★★ 客户端老存档的**渲染锚点兜底**：
        //   整机是挂在核心方块实体上的 TESR，而 MITE 只在"设置方块"时创建 TE ✗
        //   —— 老熔炉的核心在**客户端也没有 TE** → 27 格消失了但整机透明 ✗（用户实测 ✓）。
        //   这里在"正要隐藏这个核心"的一刻把 TE 补出来 ✓（此后就是一次 map 查询 ✓）。
        if (block instanceof net.dsh.createmite.block.FurnaceCoreBlock) {
            try {
                net.minecraft.Minecraft mc = net.minecraft.Minecraft.getMinecraft();
                net.minecraft.World w = mc == null ? null : mc.theWorld;
                if (w != null && w.isRemote) w.getBlockTileEntity(x, y, z);
            } catch (Throwable t) {
                System.out.println("[CreateMITE] 补核心方块实体失败: " + t);
            }
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "renderBlockAsItem", at = @At("HEAD"), cancellable = true)
    private void createmite$renderKineticAsItem(Block block, int subtype, float scale, CallbackInfo ci) {
        // ★ 金属传动杆（纯合成件）也要 3D 物品外观：它是普通 Block、图标又是全透明的
        //   createmite_blank ✗ → 在物品栏里会显示成"缺贴图"的紫黑格 ✗（用户实测截图 ✓）
        //   这里和动力元件同一套办法：自己画那根柱子 ✓
        if (block instanceof net.dsh.createmite.block.BlockMaterialShaft) {
            net.dsh.createmite.block.BlockMaterialShaft shaft =
                    (net.dsh.createmite.block.BlockMaterialShaft) block;
            Icon side = shaft.iconSide();
            Icon top = shaft.iconTop();
            if (side == null || top == null) return;      // 图集未就绪就交回原逻辑
            Icon[] shaftIcons = new Icon[CreateModels.LAYER_COUNT];
            shaftIcons[0] = side;
            shaftIcons[1] = top;

            boolean cull0 = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHTING);
            GL11.glPushMatrix();
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            if (light0) GL11.glDisable(GL11.GL_LIGHTING);
            if (cull0) GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            net.minecraft.Tessellator t = net.minecraft.Tessellator.instance;
            t.startDrawingQuads();
            t.setColorOpaque_F(1.0F, 1.0F, 1.0F);
            CreateModels.emit(t, CreateModels.SHAFT, 0.0D, 0.0D, 0.0D, 1, shaftIcons);
            t.draw();
            if (cull0) GL11.glEnable(GL11.GL_CULL_FACE);
            if (light0) GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            ci.cancel();
            return;
        }

        if (!(block instanceof BlockKineticBase)) return;
        Icon[] icons = ((BlockKineticBase) block).getModelIcons();
        if (icons == null || icons[0] == null) return;   // 图集未就绪就交回原逻辑

        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        Tessellator tess = Tessellator.instance;

        // ★ 网格类方块（水车 / 大型水车 / 粉碎轮）必须**单独一条路**：
        //   它们用 GL_TRIANGLES 且整层一次提交，塞不进下面那个四边形批次里。
        String meshName = null;
        if (block instanceof net.dsh.createmite.kinetics.block.BlockWaterWheel) meshName = "water_wheel";
        else if (block instanceof net.dsh.createmite.kinetics.block.BlockCrushingWheel) meshName = "crushing_wheel";
        else if (block instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel) meshName = "large_water_wheel";
        if (meshName != null) {
            net.dsh.createmite.kinetics.client.CreateMeshes.Mesh mesh =
                    net.dsh.createmite.kinetics.client.CreateMeshes.get(meshName);
            GL11.glPushMatrix();
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            if (lighting) GL11.glDisable(GL11.GL_LIGHTING);
            if (cullFace) GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // ★ 大模型在 16x16 的物品格里塞不下，按模型实际尺寸缩到格内（绕模型中心缩）
            float itemScale = 1.0F;
            if (block instanceof net.dsh.createmite.kinetics.block.BlockLargeWaterWheel) itemScale = 0.20F;  // 模型宽约 4.9 格
            else if (block instanceof net.dsh.createmite.kinetics.block.BlockWaterWheel) itemScale = 0.48F;  // 宽 2 格
            else if (block instanceof net.dsh.createmite.kinetics.block.BlockCrushingWheel) itemScale = 0.45F; // 宽 2.14 格
            if (itemScale != 1.0F) {
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                GL11.glScalef(itemScale, itemScale, itemScale);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            }
            for (int layer = 0; layer < CreateModels.LAYER_COUNT; layer++) {
                // 物品栏这条路没有 bindTexture（图集已经绑好了），UV 走 icons[layer] 的子矩形
                net.dsh.createmite.kinetics.client.CreateMeshes.drawLayer(mesh, icons, layer, tess);
            }
            if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
            if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            ci.cancel();
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);   // renderBlockAsItem 原本也会做这一步
        if (lighting) GL11.glDisable(GL11.GL_LIGHTING);
        if (cullFace) GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        tess.startDrawingQuads();
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        if (block instanceof BlockCogwheel) {
            BlockCogwheel cog = (BlockCogwheel) block;
            CogwheelGeometry.emit(tess, 0.0D, 0.0D, 0.0D, 1, cog.getGearIcon(),
                    cog.getAxisSideIcon(), cog.getAxisTopIcon(),
                    cog.getShaftSideIcon(), cog.getShaftTopIcon());
        } else if (block instanceof BlockShaft) {
            CreateModels.emit(tess, CreateModels.SHAFT, 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof BlockHandCrank) {
            CreateModels.emit(tess, CreateModels.CRANK_BASE, 0.0D, 0.0D, 0.0D, 1, icons);
            // preRotX=90：把以 Z 为转轴设计的手柄摆正（详见 CreateModels.emit 的说明）
            CreateModels.emit(tess, CreateModels.CRANK_HANDLE, 0.0D, 0.0D, 0.0D, 1, 90F, icons);
        } else if (block instanceof BlockMillstone) {
            CreateModels.emit(tess, CreateModels.MILLSTONE, 0.0D, 0.0D, 0.0D, 1, icons);
            CreateModels.emit(tess, CreateModels.MILLSTONE_INNER, 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof BlockLargeCogwheel) {
            CreateModels.emit(tess, CreateModels.LARGE_COGWHEEL, 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof net.dsh.createmite.kinetics.block.BlockWrappedShaft) {
            // 熔炉结构的包裹传动杆：机壳 + 轴，物品栏里不转（与传动杆箱同样的处理）
            net.dsh.createmite.kinetics.block.BlockWrappedShaft ws =
                    (net.dsh.createmite.kinetics.block.BlockWrappedShaft) block;
            CreateModels.emit(tess, ws.shellModel(), 0.0D, 0.0D, 0.0D, 1, icons);
            CreateModels.emit(tess, ws.shaftModel(), 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof BlockGearbox) {
            CreateModels.emit(tess, CreateModels.GEARBOX, 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof BlockClutch) {
            CreateModels.emit(tess, CreateModels.CLUTCH, 0.0D, 0.0D, 0.0D, 1, icons);
        } else if (block instanceof BlockGearshift) {
            CreateModels.emit(tess, CreateModels.GEARSHIFT, 0.0D, 0.0D, 0.0D, 1, icons);
        }
        tess.draw();

        if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE);
        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
        ci.cancel();
    }
}
