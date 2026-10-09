package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端世界也要刷季节（`WorldClient` 覆写了 `updateWeather` 且不调 super ✗，见 `SeasonsWorldMixin`）。
 *
 * 单机时客户端/服务端同 JVM，两边算出来是同一个值 ✓；联机时客户端也靠自己这份算 ✓ ——
 * **这就是"季节用纯函数"最大的便宜：一个包都不用发** ✓。
 */
@Mixin(WorldClient.class)
public abstract class SeasonsClientWorldMixin {

    @Inject(method = "updateWeather()V", at = @At("HEAD"))
    private void cm$seasonTickClient(CallbackInfo ci) {
        CMSeasons.update((WorldClient) (Object) this);
    }
}
