package net.dsh.createmite.mixin;

import net.dsh.createmite.CMBodyTemp;
import net.minecraft.EntityPlayer;
import net.minecraft.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家体温的三个挂点（2026-10-01 新建）。
 *
 * 1. EntityPlayer.onUpdate() 的头上：每 tick 推进体温（内部只认服务端 ServerPlayer）。
 *    javap 实证：ServerPlayer.onUpdateEntity() 会 invokespecial EntityPlayer.onUpdate()，
 *    所以挂在父类这里，服务端玩家一定会走到。
 * 2. writeEntityToNBT / readEntityFromNBT 的尾巴：把体温写进 / 读回玩家存档（用户裁定存盘）。
 */
@Mixin(EntityPlayer.class)
public abstract class BodyTempPlayerMixin {

    @Inject(method = "onUpdate()V", at = @At("HEAD"))
    private void createmite$bodyTempTick(CallbackInfo ci) {
        net.dsh.createmite.CMAmbientFeel.tick((EntityPlayer) (Object) this);
    }

    @Inject(method = "writeEntityToNBT(Lnet/minecraft/NBTTagCompound;)V", at = @At("RETURN"))
    private void createmite$saveBodyTemp(NBTTagCompound nbt, CallbackInfo ci) {
        CMBodyTemp.writeNBT((EntityPlayer) (Object) this, nbt);
    }

    @Inject(method = "readEntityFromNBT(Lnet/minecraft/NBTTagCompound;)V", at = @At("RETURN"))
    private void createmite$loadBodyTemp(NBTTagCompound nbt, CallbackInfo ci) {
        CMBodyTemp.readNBT((EntityPlayer) (Object) this, nbt);
    }
}
