package net.dsh.createmite.mixin;

import net.dsh.createmite.CMSeasonsWeather;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 四季天气偏置：当天天气表**生成完之后**按季节改一遍 ✓（见 CMSeasonsWeather ✓） */
@Mixin(World.class)
public abstract class SeasonsWeatherMixin {

    @Inject(method = "generateWeatherEvents(I)Ljava/util/List;", at = @At("RETURN"))
    private void cm$biasWeather(int day, CallbackInfoReturnable<List> cir) {
        CMSeasonsWeather.apply(cir.getReturnValue(), (World) (Object) this, day);
    }
}
