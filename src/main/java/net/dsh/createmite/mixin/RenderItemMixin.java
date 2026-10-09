package net.dsh.createmite.mixin;

import net.dsh.createmite.item.ItemWrench;
import net.dsh.createmite.kinetics.client.ItemModelRender;
import net.minecraft.FontRenderer;
import net.minecraft.ItemStack;
import net.minecraft.RenderItem;
import net.minecraft.TextureManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 背包 / 快捷栏 / 箱子里的 3D 模型物品（目前只有扳手）。
 *
 * 【背景】Create 的扳手是**3D 模型物品**：models/item/wrench.json → create:item/wrench/item，
 * 贴图 create:item/wrench 是那个模型的 UV 展开表，而不是画好的 16x16 图标。
 * MITE 没有物品模型系统，会把它摊平成一坨看不懂的花纹。
 */
@Mixin(RenderItem.class)
public abstract class RenderItemMixin {

    /**
     * **掉在地上的扳手 + 展示框里的扳手** ✓（2026-09-28 用户实测反馈"建模不对"）
     *
     * 【为什么只挂这一个方法就能两处一起修】字节码实证：
     *   · 掉落物：RenderItem.doRender(...) → **doRenderItem(...)** ✓
     *   · 展示框：RenderItemFrame.func_82402_b 会**临时 new 一个 EntityItem** 装框里的物品，
     *     再经 RenderManager 派发 → 还是 RenderItem.doRender → doRenderItem ✓
     *     （RenderItemFrame 引用的是 doRender + getEntityItem，不直接引 doRenderItem ✓）
     *   → 所以 doRenderItem 是**唯一必经之路** ✓，在这里替换成 3D 模型，两处一起好 ✓
     *
     * 【为什么原来看起来是坏的】MITE 没有物品模型系统 ✗ → 它按 2D 图标画这个物品，
     *   而 Create 的 item/wrench.png 是**模型的 UV 展开表**（不是画好的 16x16 图标）→ 摊平了就是花屏 ✓
     *
     * 【变换】用模型 json 的 display.ground：rotation [-90,0,0] / translation [0,-2.3,0] / scale 0.76914 ✓
     *   在展示框里：框自己已经把坐标系转到"面朝外"，所以同一套 ground 变换正好是**贴着框面** ✓
     */
    @Inject(method = "doRenderItem(Lnet/minecraft/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void createmite$renderWrenchInWorld(net.minecraft.EntityItem entity, double x, double y, double z,
                                                 float yaw, float partial, CallbackInfo ci) {
        if (entity == null) return;
        ItemStack stack = entity.getEntityItem();
        if (stack == null || !(stack.getItem() instanceof ItemWrench)) return;

        net.minecraft.RenderManager rm = net.minecraft.RenderManager.instance;
        if (rm == null || rm.renderEngine == null) return;

        // ★ 展示框 vs 掉落物：**两处的坐标系不一样**，必须分开处理 ✓
        //   用户实测：掉落物用 ground 是对的 ✓；但**展示框里再套 ground 的 -90° 会让扳手
        //   垂直于框面戳出来** ✗（框自己已经把坐标系转到"面朝外"了 ✓）
        final boolean inFrame = net.minecraft.RenderItem.renderInFrame;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        // 掉落物稍微抬一点（贴地不好看）；展示框里**不要**抬，否则偏心 ✗
        GL11.glTranslatef((float) x, (float) y + (inFrame ? 0.0F : 0.15F), (float) z);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // 模型没有法线 ✗ → 关掉光照用统一亮度，否则世界里的明暗会把它切得一块一块的 ✓
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (inFrame) {
            // ★★ 展示框：**完全照原版机械动力的做法**（用户 2026-09-28："原版咋样就咋样"）
            //
            // 【原版是怎么做的】展示框里的物品走的是模型 json 的 **fixed** 变换 ✓
            //   （这是 MC 的规矩，不是 Create 自己写的：物品展示框 → display.fixed ✓）
            //   而 Create 的扳手模型 create:item/wrench/item 的 parent 是 **block/block**
            //   （assets/create/models/item/wrench/item.json 实证："parent": "block/block" ✓），
            //   它自己**没有**写 fixed ✗ → 于是就用 block/block 的默认值：
            //       fixed: rotation [0,0,0] ／ translation [0,0,0] ／ scale [0.5,0.5,0.5] ✓
            //   → 也就是"**不转、不挪、缩到一半**" —— 扳手立着贴在框面里 ✓
            //
            // 【为什么不能再自己加旋转】上一版我按"水平放"绕 Z 转了 -90°，那就**不是原版**了 ✗。
            //   原版固定值里 rotation 是 0 ✓ —— 所以这里老老实实 0,0,0 + 0.5 就对了 ✓
            ItemModelRender.applyDisplay(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F);
        } else {
            // 掉落物：display.ground（模型 json 原样）✓
            ItemModelRender.applyDisplay(0.0F, -2.3F, 0.0F, -90.0F, 0.0F, 0.0F, 0.76914F);
        }

        rm.renderEngine.bindTexture(ItemModelRender.TEX_WRENCH);
        ItemModelRender.drawWrench();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        ci.cancel();
    }

    @Inject(method = "renderItemAndEffectIntoGUI", at = @At("HEAD"), cancellable = true)
    private void createmite$renderWrench3D(FontRenderer font, TextureManager tex, ItemStack stack,
                                           int x, int y, CallbackInfo ci) {
        if (stack == null || !(stack.getItem() instanceof ItemWrench)) return;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // 原版 RenderItem.setupGuiTransform 的基底
        GL11.glTranslatef(x, y, 100.0F);
        GL11.glTranslatef(8.0F, 8.0F, 0.0F);
        GL11.glScalef(1.0F, 1.0F, -1.0F);
        GL11.glScalef(16.0F, 16.0F, 16.0F);

        // 模型自带 display.gui：rotation [28, -163, 43] / translation [0.5, 0, 0] / scale 1.09453
        ItemModelRender.applyDisplay(0.5F, 0.0F, 0.0F, 28.0F, -163.0F, 43.0F, 1.09453F);

        tex.bindTexture(ItemModelRender.TEX_WRENCH);
        ItemModelRender.drawWrench();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        ci.cancel();
    }
}
