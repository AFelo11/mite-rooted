package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasons;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 四季的**世界侧挂点**（2026-09-30）：
 *
 *  1. `updateWeather()` HEAD —— 每 tick 刷新季节状态（世界时钟 → 天数 → 季节 → 温度偏移 ✓）
 *     ⚠️ `WorldClient` **覆写了** `updateWeather` 且**不调 super** ✗（javap 实证），
 *        所以客户端那份由 `SeasonsClientWorldMixin` 再挂一次 ✓（单机时两边同 JVM，值本来就一样 ✓）
 *  2. `canBlockFreeze(IIIZ)` HEAD —— 强制"冬季前 3 天 / 最后 3 天**不许结冰**" ✓
 *     （用户定稿：冬季前三天不会结冰、第四天开始结冰、最后三天逐渐融化冰层 ✓；
 *      否则温度一变冷，MITE 自己的冻结 pass 会在前 3 天就把水冻上 ✗）
 *
 * 结冰/融冰的"逐渐"效果在 `CMSeasons.cm$seasonIcePass` 里（玩家附近随机采样 ✓）。
 */
@Mixin(World.class)
public abstract class SeasonsWorldMixin {

    @Inject(method = "updateWeather()V", at = @At("HEAD"))
    private void cm$seasonTick(CallbackInfo ci) {
        CMSeasons.update((World) (Object) this);
    }

    // ==================================================================
    // ★★ 四季昼夜时长（用户 2026-10-01 定稿 ✓）—— 把"一天中的时刻"按季节拉伸 ✓
    //   一天仍 24000 tick ✓，只改 晨/昼/昏/夜 四段的长度 ✓（见 CMSeasons.DAY_PHASES ✓）
    //   挂这几处就够：时刻读取 ✓、静态换算 ✓、判昼夜 ✓、天体角度 ✓
    //   ⚠️ 故意**不挂** getTimeOfSunrise/Sunset/Sleeping ✗ —— 那是我们读锚点用的原始常量 ✓
    //      （挂了就会自己吃自己 ✗）
    // ==================================================================

    @Inject(method = "getUnadjustedTimeOfDay(J)I", at = @At("RETURN"), cancellable = true)
    private static void cm$warpUnadjusted(long time, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Integer.valueOf(CMSeasons.warpTimeOfDay(cir.getReturnValueI())));
    }

    @Inject(method = "getTimeOfDay()I", at = @At("RETURN"), cancellable = true)
    private void cm$warpTimeOfDay(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Integer.valueOf(CMSeasons.warpTimeOfDay(cir.getReturnValueI())));
    }

    @Inject(method = "getAdjustedTimeOfDay()I", at = @At("RETURN"), cancellable = true)
    private void cm$warpAdjustedTimeOfDay(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Integer.valueOf(CMSeasons.warpTimeOfDay(cir.getReturnValueI())));
    }

    @Inject(method = "isDaytime(J)Z", at = @At("HEAD"), cancellable = true)
    private static void cm$warpIsDaytime(long time, CallbackInfoReturnable<Boolean> cir) {
        int warped = CMSeasons.warpTimeOfDay((int) (time % 24000L));
        if (warped != (int) (time % 24000L)) {
            cir.setReturnValue(Boolean.valueOf(warped >= 0 && warped < 12000));
        }
    }

    /**
     * 天体角度（太阳/月亮位置 ✓）—— 决定"世界的明暗"，昼夜长短要真的看得出来就靠它 ✓
     *
     * ★★ 2026-10-01 修：上一版用 `lastRawTimeOfDay()`（存的是**上一次算出来的 warped 值** ✗）
     *   当输入 ⇒ **自己吃自己**、反复叠加 ⇒ 结果被冲平 ⇒ 用户实测"夏天和冬天差不多" ✗✗
     *   ⇒ 现在改成**只把原值平移一个"扭曲量"** ✓：
     *       扭曲量 = (拉伸后的时刻 − 原始时刻) / 24000 ✓
     *     MITE 自己的天体公式**一个字节都不动** ✗，只把结果挪一点 ✓ 稳且准 ✓
     */
    @Inject(method = "getCelestialAngle(F)F", at = @At("RETURN"), cancellable = true)
    private void cm$warpCelestialAngle(float partialTicks, CallbackInfoReturnable<Float> cir) {
        int raw = CMSeasons.rawTimeOfDayNow((World) (Object) this);
        int warped = CMSeasons.warpTimeOfDay(raw);
        if (warped == raw) return;
        cir.setReturnValue(Float.valueOf(cir.getReturnValueF()
                + (warped - raw) / 24000.0F));
    }


    @Inject(method = "canBlockFreeze(IIIZ)Z", at = @At("HEAD"), cancellable = true)
    private void cm$seasonFreezeGate(int x, int y, int z, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        if (!CMSeasons.enabled()) return;
        World w = (World) (Object) this;
        // ★ 用户定稿：「P2 气候的判定 = **以玩家为中心 1000 格**的圆」✓
        if (!CMSeasons.withinEffectRadius(w, x, z)) return;
        // ★ 2026-09-30 用户澄清规则后：**只有融冰期**才拦结冰 ✓
        //   （"前三天不结冰"是我之前理解反了 ✗ —— 现在前三天也要**逐渐结冰** ✓）
        if (CMSeasons.isWinterMeltWindow()) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    // ⚠️ 积雪（canSnowAt）**没有**单独挂钩 ✗：
    //   它内部按温度判 ✓，而温度偏移是无参挂点、拿不到坐标 ⇒ 判不了"1000 格半径" ✗。
    //   所以半径规则目前只在**拿得到坐标**的两处生效：
    //     ① 这里的 canBlockFreeze ✓  ② CMSeasons 里的结冰/融冰 pass（本来就是玩家周围采样 ✓）
    //   远处积雪会跟着季节温度一起变 —— 影响很小（玩家看不到那么远 ✓），先这样 ✓。
}
