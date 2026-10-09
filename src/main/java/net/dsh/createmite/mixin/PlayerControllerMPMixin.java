package net.dsh.createmite.mixin;

import net.dsh.createmite.ToolCompat;
import net.dsh.createmite.kinetics.block.BlockKineticBase;
import net.minecraft.Block;
import net.minecraft.EnumFace;
import net.minecraft.ItemStack;
import net.minecraft.Minecraft;
import net.minecraft.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 机器方块挖掘等级的**客户端那一半**：未达等级时**不出现挖掘进度**。
 *
 * 【为什么服务端拦了还不够】MITE 的挖掘是两套并行的：
 *   - 服务端 ItemInWorldManager.onBlockClicked → {@code ServerPlayer.getDamageVsBlock} 决定**方块会不会真的被挖掉**；
 *   - 客户端每个 tick 走 {@code PlayerControllerMP.onPlayerDamageBlock} 累加 {@code curBlockDamageMP}，
 *     这个值只驱动**裂纹/进度显示**，跟服务端互不通信。
 * 只堵服务端的话，方块挖不掉，但玩家手里那把不合格的工具**照样一路把进度条推满**，
 * 看起来就像"限制没生效"。所以这里也要拦，并且把残留进度清零，
 * 免得"先拿铁镐挖到 90% 再换成铜镐"还能接着挖。
 *
 * 判定同 ToolCompat.canMineMachine：**镐类或斧类，且等级 >= 3（铁起步）**。
 *
 * ============================ 2026-09-29 追加：熔炉六件套 ============================
 * 用户原话：「我要的是**连挖掘进度都不会有**！与原版 MITE 一样！」
 *
 * 【六件套走的是另一条判据】它们要的是"**MITE 的镐类（镐 + 战锤）且等级按材质**" ✓，
 *   与机器方块那套白名单（镐/斧/战锤/手斧/鹤嘴锄/战斧 + 一律铁起步）不同 ✗ ——
 *   所以这里对六件套改判 {@link ToolCompat#canMineBlock}（六件套 = 完全走 MITE 自己的
 *   isEffectiveAgainstBlock ✓：铜战锤挖不动黑曜石、铁战锤可以 ✓），
 *   和 ServerPlayerMixin / ItemInWorldManagerMixin 用的是**同一条**规则 ✓。
 *
 * 【为什么客户端这一刀非挨不可（两个真原因）】
 *   ① 进度条是客户端自己累加的：反汇编 MITE 的 PlayerControllerMP.onPlayerDamageBlock：
 *        curBlockDamageMP += mc.thePlayer.getDamageVsBlock(x, y, z, true);
 *      —— 只要给 0，进度**一点都不涨** ✓（这就是用户要的"连进度都没有" ✓）
 *   ② **创造模式根本不走进度那条路**：同一个方法里
 *        if (currentGameType.isCreative()) { blockHitDelay = 5; sendDiggingPacket(...); clickBlockCreative(...); }
 *      —— 创造模式是"点一下就整块没" ✗，不看 getDamageVsBlock ✗。
 *      在 HEAD 取消 onPlayerDamageBlock 就同时掐掉了这条 ✓（否则会看到方块被瞬间打掉、
 *      服务端又拒绝 → 方块闪一下又回来 ✗）。
 */
@Mixin(PlayerControllerMP.class)
public abstract class PlayerControllerMPMixin {

    @Shadow public float curBlockDamageMP;
    @Shadow public boolean isHittingBlock;

    /**
     * ★ 结构选择器：**左键按下的第一次**也要拦。
     *
     * 【为什么 onPlayerDamageBlock 里那一刀不够】MC 的挖掘分两段：
     *   clickBlock(x,y,z,face)  = 刚按下的那一下；
     *   onPlayerDamageBlock(...) = 按住时每 tick 的进度累加。
     * **创造模式在 clickBlock 里就直接把方块打掉了** ✗（onPlayerDestroyBlock），
     * 根本轮不到 onPlayerDamageBlock ✓ → 用户实测"选点的时候方块没了"就是这条 ✓。
     */
    @Inject(method = "clickBlock(IIILnet/minecraft/EnumFace;)V", at = @At("HEAD"), cancellable = true)
    private void createmite$wandSelectOnClick(int x, int y, int z, EnumFace face, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;

        ItemStack held = mc.thePlayer.getHeldItemStack();
        if (held == null || !(held.getItem() instanceof net.dsh.createmite.item.ItemStructureWand)) return;

        net.dsh.createmite.CMSelection.setA(x, y, z, mc.theWorld.provider.dimensionId);
        if (net.dsh.createmite.item.ItemStructureWand.shouldTell(x, y, z)) {   // 去重 ✓
            net.dsh.createmite.item.ItemStructureWand.tell(mc.thePlayer,
                    "§a[结构选择器] A 点 = " + x + ", " + y + ", " + z
                            + "   （B 点 = " + net.dsh.createmite.CMSelection.describeB() + "）");
        }
        this.curBlockDamageMP = 0.0F;
        this.isHittingBlock = false;   // 连"按住"都不算，方块纹丝不动 ✓
        ci.cancel();
    }

    @Inject(method = "onPlayerDamageBlock(IIILnet/minecraft/EnumFace;)V", at = @At("HEAD"), cancellable = true)
    private void createmite$machineMiningGate(int x, int y, int z, EnumFace face, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;

        // ★ 结构选择器（开发工具）：左键点方块 = 选 A 点，**不挖方块** ✓
        net.minecraft.ItemStack wandHeld = mc.thePlayer.getHeldItemStack();
        if (wandHeld != null && wandHeld.getItem() instanceof net.dsh.createmite.item.ItemStructureWand) {
            // 【选点在 clickBlock 里做】这里**只负责"不许挖"** ✓
            //   —— 这条会每 tick 跑一次，所以绝对不能在这里选点/播报 ✗
            //      （那正是"点一次报两次"的来源 ✓；已经验证过 ✗）
            this.curBlockDamageMP = 0.0F;
            this.isHittingBlock = false;
            ci.cancel();
            return;
        }

        Block block = Block.blocksList[mc.theWorld.getBlockId(x, y, z)];
        boolean strict = ToolCompat.isFurnaceStructureBlock(block);   // 熔炉六件套：镐 + 战锤，按材质等级 ✓
        if (!strict && !(block instanceof BlockKineticBase)) return;

        // ★ 创造模式不拦（只保留"拿结构选择器时不许挖"那一条 ✓）——理由见 ItemInWorldManagerMixin
        boolean creative = mc.thePlayer.capabilities != null && mc.thePlayer.capabilities.isCreativeMode;
        if (creative && !(mc.thePlayer.getHeldItemStack() != null
                && mc.thePlayer.getHeldItemStack().getItem() instanceof net.dsh.createmite.item.ItemStructureWand)) {
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItemStack();
        boolean allowed = strict
                ? ToolCompat.canMineBlock(held, block, mc.theWorld.getBlockMetadata(x, y, z))
                : ToolCompat.canMineMachine(held);
        if (allowed) return;

        this.curBlockDamageMP = 0.0F;   // 连残留进度一起清掉 ✓（用户：连挖掘进度都不要有 ✓）
        ci.cancel();
    }
}
