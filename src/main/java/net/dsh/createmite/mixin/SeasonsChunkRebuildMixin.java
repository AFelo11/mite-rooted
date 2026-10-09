package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块**重建完成**时给那一格盖个"本赛季已上色"的章 ✓（2026-09-30）。
 *
 * 【为什么要这一手】四季的颜色是**烘进区块网格顶点色**的 ✗ ⇒ 只有"重建网格"才会换色 ✓。
 *   而 `RenderGlobal.markBlockForRenderUpdate` 对**渲染网格之外**的坐标是**静默忽略**的 ✗
 *   （javap 实证：它按 `renderChunksWide/Tall/Deep` 取模找渲染器 ✓）。
 *   → 所以"标记了"≠"会重建" ✗，盖章必须等**真的重建完** ✓（见 `CMSeasons.stampChunkTinted` ✓）
 *   → 这样滚动补色才知道谁还没补上 ✓（用户报的"偶尔一整条区块链没上色"就是从这儿根治的 ✓）
 *
 * `posX / posZ` 是 `WorldRenderer` 的私有字段（javap 实证 ✓）→ 用 @Shadow 拿 ✓
 * （它就是那一格区块的**方块坐标**起点 ✓，右移 4 位得到区块坐标 ✓）
 */
@Mixin(WorldRenderer.class)
public abstract class SeasonsChunkRebuildMixin {

    @Shadow private int posX;
    @Shadow private int posZ;

    @Inject(method = "updateRenderer()V", at = @At("RETURN"))
    private void cm$stampChunkTinted(CallbackInfo ci) {
        CMSeasons.stampChunkTinted(this.posX >> 4, this.posZ >> 4);
    }
}
