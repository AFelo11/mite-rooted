package net.dsh.createmite.mixin;

import net.dsh.createmite.kinetics.block.BlockKineticBase;
import net.minecraft.Block;
import net.minecraft.EffectRenderer;
import net.minecraft.EntityDiggingFX;
import net.minecraft.Icon;
import net.minecraft.Minecraft;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 机器方块的**破坏碎屑粒子**。
 *
 * 【为什么不在 Block.breakBlock 里喷】
 * breakBlock 只在**服务端**有保证（"破坏方块要移除方块实体"那条修复就是靠服务端这条路径），
 * 客户端根本没走到那里 —— 所以之前写在 breakBlock 里的粒子一个都没出来。
 * 客户端真正的入口是这里：{@code EffectRenderer.addBlockDestroyEffects}。
 *
 * 【为什么不能让它自己喷】MITE 是拿**方块的图集图标**当粒子贴图的，
 * 而我们的方块图标是 createmite_blank（全透明，专门让原版那趟整方块渲染看不见）
 * → 粒子喷了但全透明。这里接过来自己 new EntityDiggingFX，
 * 用 setParticleIcon 换成模型自己的贴图（见 BlockKineticBase.particleLayer）。
 */
@Mixin(EffectRenderer.class)
public abstract class EffectRendererMixin {

    @Inject(method = "addBlockDestroyEffects(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void createmite$machineBreakParticles(int x, int y, int z, int blockId, int meta, int arg,
                                                  CallbackInfo ci) {
        Block block = Block.blocksList[blockId];
        if (!(block instanceof BlockKineticBase)) return;
        Icon icon = ((BlockKineticBase) block).particleIcon();
        if (icon == null) return;

        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        EffectRenderer self = (EffectRenderer) (Object) this;

        for (int i = 0; i < 24; i++) {
            double px = x + world.rand.nextDouble();
            double py = y + world.rand.nextDouble();
            double pz = z + world.rand.nextDouble();
            EntityDiggingFX fx = new EntityDiggingFX(world, px, py, pz,
                    px - ((double) x + 0.5D), py - ((double) y + 0.5D), pz - ((double) z + 0.5D),
                    block, meta);
            fx.setParticleIcon(icon);
            self.addEffect(fx);
        }
        ci.cancel();
    }
}
