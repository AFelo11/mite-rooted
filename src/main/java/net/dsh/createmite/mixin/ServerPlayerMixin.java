package net.dsh.createmite.mixin;

import net.dsh.createmite.ToolCompat;
import net.dsh.createmite.kinetics.block.BlockKineticBase;
import net.minecraft.Block;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.ServerPlayer;
import net.minecraft.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 机器方块的**挖掘等级闸**：只有"铁及以上的镐类/斧类"能挖动。
 *
 * 【为什么光设 setMinHarvestLevel 不够】
 * MITE 有两条独立的判定：
 *   1. {@code ItemTool.isEffectiveAgainstBlock} —— 决定"能不能正常收获/掉落"，
 *      它确实读 block.getMinHarvestLevel(meta)。我们设成 3 之后，日志实测铜/银/金都是 false ✓；
 *   2. **挖掘进度**走的是 {@code ServerPlayer.getDamageVsBlock}（每次点击给方块加多少损伤），
 *      这条不看 getMinHarvestLevel —— 于是"挖不动"这条根本没生效，
 *      表现就是"设了等级，弱工具照样能挖穿"。
 * 所以必须在这里补一刀：不允许的工具直接返回 0 损伤，方块永远挖不穿。
 * （判定见 ToolCompat.canMineMachine：镐类/斧类 且 等级 >= 3）
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements net.dsh.createmite.furnace.BigFurnaceUi.WindowIdHolder {

    /**
     * 大熔炉界面用的窗口号。
     *
     * 【为什么要借原版这套】windowId 必须**服务端与客户端一致**，槽位点击（Packet102）才对得上 ✓。
     * 原版自己有个 {@code currentWindowId}（私有）+ {@code incrementWindowID()}（私有 ✗），
     * 所以这里用 Shadow 拿字段、自己按同样的规则（+1 取模 100）取号 —— 这样既不会和原版
     * 开箱子/熔炉的号撞车 ✓，客户端也用包里带回来的同一个号 ✓。
     */
    @org.spongepowered.asm.mixin.Shadow
    private int currentWindowId;

    @Override
    public int cm$nextWindowId() {
        this.currentWindowId = (this.currentWindowId + 1) % 100;
        return this.currentWindowId;
    }

    @Inject(method = "getDamageVsBlock(IIIZ)F", at = @At("HEAD"), cancellable = true)
    private void createmite$machineMiningGate(int x, int y, int z, boolean flag,
                                              CallbackInfoReturnable<Float> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        World world = ((EntityPlayer) self).worldObj;
        if (world == null || world.isRemote) return;

        // ★ 创造模式不受挖掘等级限制 ✓（和 ItemInWorldManagerMixin 同一条理由，
        //   免得客户端已经拆掉、服务端还在，留下"看不见但挡路"的假方块 ✗）
        if (((EntityPlayer) self).capabilities != null
                && ((EntityPlayer) self).capabilities.isCreativeMode) return;

        Block block = Block.blocksList[world.getBlockId(x, y, z)];
        // 机器方块（原样）+ 熔炉六件套（核心是普通 Block，也必须过同一道闸 ✓）
        if (!(block instanceof BlockKineticBase) && !ToolCompat.isFurnaceStructureBlock(block)) return;

        ItemStack held = self.getHeldItemStack();
        // ★ 2026-09-29：改成按**方块自己的**最低挖掘等级判定（原来是写死常量"铁起步"）。
        //   原机器方块的最低等级本来就是 3 → 行为不变 ✓；
        //   新加的包裹传动杆按材质走（圆石/下界岩 2、黑曜石 3）✓。
        if (ToolCompat.canMineBlock(held, block, world.getBlockMetadata(x, y, z))) return;

        cir.setReturnValue(0.0F);   // 工具不行 → 一点损伤都不给
    }
}
