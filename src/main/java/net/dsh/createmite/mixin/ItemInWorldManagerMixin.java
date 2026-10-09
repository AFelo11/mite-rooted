package net.dsh.createmite.mixin;

import net.dsh.createmite.ToolCompat;
import net.minecraft.Block;
import net.minecraft.ItemInWorldManager;
import net.minecraft.ItemStack;
import net.minecraft.ServerPlayer;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 熔炉六件套的**第二道**闸：玩家真正把方块拿下来那一步（tryHarvestBlock）。
 *
 * 【为什么非加不可 —— 创造模式会绕过第一道闸】
 * 反汇编 MITE 的 ItemInWorldManager.onBlockClicked：
 *     if (isCreative()) { if (!world.extinguishFire(...)) tryHarvestBlock(x,y,z); }
 *     else { ...走 ServerPlayer.getDamageVsBlock 那条路（我们的第一道闸在 ServerPlayerMixin）... }
 * 也就是：**创造模式下根本不问 getDamageVsBlock** ✗ —— 手上拿斧子（甚至空手）也能瞬间挖掉 ✗。
 * 用户实测就是这条：「铁战斧和铁斧为啥还能挖熔炉传动杆？」（测试时是创造模式 ✓）
 *
 * 【只拦熔炉六件套】机器方块在创造模式下的老行为**保持原样** ✓（用户没提，别顺手改 ✗）。
 */
@Mixin(ItemInWorldManager.class)
public abstract class ItemInWorldManagerMixin {

    @Inject(method = "tryHarvestBlock(III)Z", at = @At("HEAD"), cancellable = true)
    private void createmite$strictPickaxeHarvest(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ItemInWorldManager self = (ItemInWorldManager) (Object) this;
        World world = self.theWorld;
        ServerPlayer player = self.thisPlayerMP;
        if (world == null || world.isRemote || player == null) return;

        // ★ 结构选择器（开发工具）兜底：拿着它的时候，服务端一律不接受"拿下方块" ✓
        //   （客户端已经在 clickBlock / onPlayerDamageBlock 两处拦了 ✓，这里是第三道保险）
        ItemStack wandHeld = player.getHeldItemStack();
        if (wandHeld != null && wandHeld.getItem() instanceof net.dsh.createmite.item.ItemStructureWand) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }

        // ★★ 2026-09-29 修「假方块」：**创造模式一律放行** ✓
        //   【为什么会留下假方块】创造模式点一下，**客户端立刻把方块去掉了** ✓（局部预测），
        //   然后才通知服务端 ✓；服务端这道闸（挖掘等级）一拒绝 ✗ → 两边不一致：
        //   客户端看不见了 ✗、服务端那格还在 ✓ → 就是用户看到的"透明的、却挡路的方块" ✓。
        //   创造模式本来就是拆东西用的 ✓ → 直接放过 ✓，规则只在生存模式生效 ✓。
        if (self.isCreative()) return;

        Block block = Block.blocksList[world.getBlockId(x, y, z)];
        if (!ToolCompat.isFurnaceStructureBlock(block)) return;   // 只管六件套 ✓

        ItemStack held = player.getHeldItemStack();
        if (ToolCompat.canMineBlock(held, block, world.getBlockMetadata(x, y, z))) return;

        System.out.println("[CreateMITE][镐子闸] tryHarvestBlock 拒绝：" + block.getUnlocalizedName()
                + " @ " + x + "," + y + "," + z + "（手上不是合格镐类，或等级不够 ✓）");
        cir.setReturnValue(Boolean.FALSE);
    }
}
