package net.dsh.createmite.mixin;

import net.dsh.createmite.CMBlocks;
import net.minecraft.BiomeDecorator;
import net.minecraft.World;
import net.minecraft.WorldGenMinable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 锌矿生成：只在 MITE 的地下世界（DIM-2 / World.isUnderworld()）生成。
 * 密度对齐银：10 脉/区块 × 脉长 6（MITE 地下世界实测：铜 40×6、银 10×6）。
 * Overworld 分支不注入 → 天然满足"只在地下世界"。
 */
@Mixin(BiomeDecorator.class)
public abstract class BiomeDecoratorMixin {

    @Unique
    private static boolean createmite$logged = false;

    @Shadow protected World currentWorld;

    @Shadow protected abstract void genMinable(int count, WorldGenMinable generator);

    @Inject(method = "generateOres", at = @At("TAIL"))
    private void createmite$generateZincOre(CallbackInfo ci) {
        if (CMBlocks.oreZinc == null) return;
        World world = this.currentWorld;
        if (world == null || !world.isUnderworld()) return;
        if (!createmite$logged) {
            createmite$logged = true;
            System.out.println("[MITE] 地下世界锌矿生成已生效（10 脉 x 6）");
        }
        this.genMinable(10, new WorldGenMinable(CMBlocks.oreZinc.blockID, 6));
    }
}
