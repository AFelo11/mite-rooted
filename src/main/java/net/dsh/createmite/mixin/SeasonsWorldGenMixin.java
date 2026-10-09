package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.BiomeGenBase;
import net.minecraft.Chunk;
import net.minecraft.ChunkProviderGenerate;
import net.minecraft.IChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 四季 × 世界生成：**生成期间把季节温度偏移屏蔽掉** ✓（2026-09-30 用户选 A ✓）
 *
 * 【问题】MITE 的 `ChunkProviderGenerate` 生成区块时读 `BiomeGenBase.getFloatTemperature()`
 *   决定放不放雪/冰（javap 实证 ✓）—— 我们的季节偏移恰好加在那个方法上 ✗
 *   ⇒ 夏天生成的雪地群系温度被抬到结冰线以上 → **一片雪都不放** ✗；
 *     冬天又铺得更多 ⇒ 同一片雪地"老区块有雪、新区块没雪" ✓（用户报的"雪地生成怪怪的" ✓）
 *
 * 【修法】在这三个入口的 HEAD/RETURN 前后压/弹一个计数 ✓，期间 `temperatureOffset()` 返回 0 ✓
 *   → 世界生成 **100% 是 MITE 原版** ✓；季节继续只管运行时看得见的地方 ✓。
 *   · provideChunk  —— 地形 + 雪/冰摆放 ✓
 *   · populate      —— 装饰（树、矿、花草 ✓）
 *   · replaceBlocksForBiome —— 地表替换 ✓
 *
 * ⚠️ 三个入口是**嵌套**的（populate 会触发邻区块 provideChunk ✓）→ 所以用计数器 ✓
 * ⚠️ `CMSeasons.update` 每 tick 会把计数清零兜底 ✓（防止异常路径漏掉 RETURN ✗）
 *
 * 【只管主世界】下界/地下世界没有雪冰 ✓，且它们的温度偏移不产生生成后果 ✓ → 不挂 ✓。
 */
@Mixin(ChunkProviderGenerate.class)
public abstract class SeasonsWorldGenMixin {

    @Inject(method = "provideChunk(II)Lnet/minecraft/Chunk;", at = @At("HEAD"))
    private void cm$genBegin(int cx, int cz, CallbackInfoReturnable<Chunk> cir) {
        CMSeasons.pushGenSuppress();
    }

    @Inject(method = "provideChunk(II)Lnet/minecraft/Chunk;", at = @At("RETURN"))
    private void cm$genEnd(int cx, int cz, CallbackInfoReturnable<Chunk> cir) {
        CMSeasons.popGenSuppress();
    }

    @Inject(method = "populate(Lnet/minecraft/IChunkProvider;II)V", at = @At("HEAD"))
    private void cm$populateBegin(IChunkProvider provider, int cx, int cz, CallbackInfo ci) {
        CMSeasons.pushGenSuppress();
    }

    @Inject(method = "populate(Lnet/minecraft/IChunkProvider;II)V", at = @At("RETURN"))
    private void cm$populateEnd(IChunkProvider provider, int cx, int cz, CallbackInfo ci) {
        CMSeasons.popGenSuppress();
    }

    // ⚠️ 描述符里**数组不能写 []** ✗ —— JVM 描述符是 `[Lnet/minecraft/BiomeGenBase;` ✓
    //   （第一版写成 `...[Lnet/minecraft/BiomeGenBase[];` ⇒ InvalidInjectionException ✗，整类没注入成 ✗）
    //   ★ 这一处**必须有** —— javap 实证：读温度的那段代码就在 replaceBlocksForBiome 里 ✓
    @Inject(method = "replaceBlocksForBiome(II[BLnet/minecraft/BiomeGenBase;)V", at = @At("HEAD"))
    private void cm$replaceBegin(int cx, int cz, byte[] blocks, BiomeGenBase[] biomes, CallbackInfo ci) {
        CMSeasons.pushGenSuppress();
    }

    @Inject(method = "replaceBlocksForBiome(II[BLnet/minecraft/BiomeGenBase;)V", at = @At("RETURN"))
    private void cm$replaceEnd(int cx, int cz, byte[] blocks, BiomeGenBase[] biomes, CallbackInfo ci) {
        CMSeasons.popGenSuppress();
    }
}
