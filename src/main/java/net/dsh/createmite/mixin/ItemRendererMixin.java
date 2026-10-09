package net.dsh.createmite.mixin;

import net.dsh.createmite.item.ItemWrench;
import net.dsh.createmite.kinetics.client.ItemModelRender;
import net.minecraft.EntityClientPlayerMP;
import net.minecraft.EntityLivingBase;
import net.minecraft.ItemRenderer;
import net.minecraft.ItemStack;
import net.minecraft.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持 / 第三人称 / 掉落物状态的 3D 模型物品。
 *
 * 【为什么钩这里】ItemRenderer.renderItemInFirstPerson 自己不画几何，
 * 它在偏移 1875 / 1965 / 1977 三处调用
 *     ItemRenderer.renderItem(EntityLivingBase, ItemStack, int)
 * 把"画物品"整件事交出去。所以这里就是第一人称、第三人称、方块实体展示框共用的**唯一漏斗**，
 * 钩它一处即可，不需要去碰 renderItemInFirstPerson 那一大坨手部动画代码。
 *
 * 【GL 状态】进来时调用方已经摆好了手部矩阵并开了标准物品光照，
 * 所以我们只需在最外层套 display.firstperson_righthand，再关掉光照用几何自带的明暗。
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "renderItem(Lnet/minecraft/EntityLivingBase;Lnet/minecraft/ItemStack;I)V",
            at = @At("HEAD"), cancellable = true)
    private void createmite$renderWrench3D(EntityLivingBase entity, ItemStack stack, int pass,
                                           CallbackInfo ci) {
        if (stack == null || pass != 0) return;
        if (!(stack.getItem() instanceof ItemWrench)) return;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);   // 用几何逐面明暗，免得被光照冲白
        // ★ 必须关掉背面剔除：CreateModels 的四边形绕序不是六面全部朝外，
        //   开着 culling 时只画出一部分面 —— 表现就是"模型不完整"。
        //   TESR 那条路一直是关的，第一人称这条默认是开的，踩过这个坑。
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Minecraft.getMinecraft().getTextureManager().bindTexture(ItemModelRender.TEX_WRENCH);
        if (entity instanceof EntityClientPlayerMP) {
            // display.firstperson_righthand：rotation [-4.5, 100.25, 10] / translation [1, 4, 1]
            ItemModelRender.applyDisplay(1.0F, 4.0F, 1.0F, -4.5F, 100.25F, 10.0F, 1.0F);
        } else {
            // display.thirdperson_righthand：rotation [-21.5, 90, 0] / translation [0, 3.25, -2.25]
            ItemModelRender.applyDisplay(0.0F, 3.25F, -2.25F, -21.5F, 90.0F, 0.0F, 1.0F);
        }
        ItemModelRender.drawWrench();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        ci.cancel();
    }
}
