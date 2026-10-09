package net.dsh.createmite.mixin;

import net.dsh.createmite.CMFood;
import net.minecraft.EntityPlayer;
import net.minecraft.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ★ 食物 → 体感温度（2026-10-06 用户拍板 ✓ 第 31 个 mixin）
 *
 * 【为什么挂这里】反汇编实证：MITE **所有吃法**最后都走
 *   ~~EntityPlayer.addFoodValue(Item)~~ ✓
 *   （调用者：ItemBowl / ItemFood / ItemBucketMilk / Potion / BlockCake / ItemBlock ✓）
 *   ⇒ 一个注入点覆盖全部食物 ✓ 而且签名里就带着【吃的是哪个 Item】✓
 * 挂 HEAD ✓ —— 这个回调本身就是"真的吃下去了"那一刻 ✓（不是咬第一口 ✓）
 *
 * ⚠️ 只在**服务端**记账（~~onServer~~ ✓ MITE 自己也是这么判的 ✓）
 *    客户端要显示的话读同一张表（单机同 JVM ✓）⇒ 见 CMFood.foodHeat ✓
 */
@Mixin(EntityPlayer.class)
public abstract class FoodValueMixin {

    @Inject(method = "addFoodValue(Lnet/minecraft/Item;)V", at = @At("HEAD"))
    private void cm$foodFeel(Item item, CallbackInfo ci) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        try {
            if (self.worldObj != null && !self.worldObj.isRemote) {
                CMFood.onEaten(self, item);
            }
        } catch (Throwable ignored) { }
    }
}
