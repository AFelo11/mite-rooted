package net.dsh.createmite.mixin;

import net.dsh.createmite.CMAmbientFeel;
import net.minecraft.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ★★ 体感温度 -> 挖掘 / 移动效率（2026-10-05 用户拍板 ✓）
 *
 * 效率表的最后一块空白：以前 digPercent/movePercent **只用于显示** ✗（写了"还没挂钩" ✓）。
 * 用户 2026-10-05 确认口径：**挖掘直接乘档位表**（50/70/85/90/75/100%），
 * **移动只在 < -15 档 = 70%**，其余档 100% ✓
 *
 * ============================ 挂哪儿、为什么 ============================
 * 【挖掘】{@code EntityPlayer.getDamageVsBlock(IIIZ)F}
 *   javap 实证（1.6.4-MITE.jar 反汇编 ✓）：
 *     · getDamageVsBlock = 1 / (相对硬度 × 512)，相对硬度 = 方块硬度 / 工具强度
 *     · 客户端：PlayerControllerMP.onPlayerDamageBlock（偏移 722）与 Minecraft.clickMouse
 *       一路（偏移 139）都拿它累加 curBlockDamageMP —— **达到 1.0 才发"挖完了"包** ✓
 *       ⇒ 客户端才是挖掘的**时钟** ✓ 乘 0.5 = 挖掘时间翻倍 ✓
 *     · 服务端：ItemInWorldManager.onBlockClicked（偏移 125）拿它判断瞬破方块 ✓
 *     · 全 jar 扫过：引用 getDamageVsBlock 的只有 Minecraft / PlayerControllerMP /
 *       ItemInWorldManager / EntityPlayer 四个类 ✓ —— **一个注入点覆盖两侧** ✓
 *   ⚠️ 只乘正数 ✗：0 = 挖不动（工具不对）、负值 = 不可挖 ⇒ 一律原样返回 ✓
 *
 * 【移动】{@code EntityPlayer.getAIMoveSpeed()F}
 *   = 移动速度属性（SharedMonsterAttributes.movementSpeed）✓
 *   EntityPlayer.moveEntityWithHeading 是 invokespecial 调父类，
 *   父类 EntityLivingBase.moveEntityWithHeading 用 getAIMoveSpeed() 算位移 ✓
 *   ClientPlayer / EntityClientPlayerMP **都没覆写** ✓ ⇒ 同样一次覆盖两侧 ✓
 *   （全 jar 里引用它的只有 EntityLivingBase / EntityPlayer / 猪 / 马 / 生物 AI ⇒
 *     挂在 EntityPlayer 上只影响玩家、怪物一律不动 ✓）
 *
 * ============================ 耐久为什么不受影响 ============================
 * 用户要求「工具耐久按原速」✓ —— 实测**天然成立** ✓：
 *   耐久走的是另一条路 ItemTool.onBlockDestroyed(BlockBreakInfo)
 *     -> getToolDecayFromBreakingBlock(BlockBreakInfo) -> getBaseDecayRateForBreakingBlock(Block)
 *   **只看方块、不看耗时** ✓ ⇒ 挖得慢 ≠ 磨得快 ✓ 一个字节都不用改 ✓
 *
 * ============================ 其它 ============================
 * · 创造模式不走进度那条路（点一下整块没）⇒ 不受影响 ✓ 合理 ✓
 * · 数值一律取 CMAmbientFeel（每 tick 缓存一次 ✓ 不重复算体感 ✓）
 * · 不舒适档才打一行诊断（ASCII 标签 ✓ 中文进日志会被毁成 U+FFFD ✗）
 */
@Mixin(EntityPlayer.class)
public abstract class FeelEfficiencyMixin {

    /** 挖掘：伤害值 × 效率（客户端进度 + 服务端瞬破都吃这一口 ✓） */
    @Inject(method = "getDamageVsBlock(IIIZ)F", at = @At("RETURN"), cancellable = true)
    private void cm$feelDigEfficiency(int x, int y, int z, boolean flag, CallbackInfoReturnable<Float> cir) {
        float v = cir.getReturnValueF();
        if (v <= 0.0F) return;                       // 0 = 挖不动 / 负值 = 不可挖 ⇒ 不碰 ✓
        EntityPlayer p = (EntityPlayer) (Object) this;
        float m = CMAmbientFeel.digMultiplierOf(p);
        if (m >= 1.0F) return;
        cir.setReturnValue(Float.valueOf(v * m));
        CMAmbientFeel.efficiencyLog(p, m, 1.0F, "DIG", v, v * m);
    }

    /** 移动：走路速度 × 效率（只有 <-15 档会动手 ✓） */
    @Inject(method = "getAIMoveSpeed()F", at = @At("RETURN"), cancellable = true)
    private void cm$feelMoveEfficiency(CallbackInfoReturnable<Float> cir) {
        EntityPlayer p = (EntityPlayer) (Object) this;
        float m = CMAmbientFeel.moveMultiplierOf(p);
        if (m >= 1.0F) return;
        float before = cir.getReturnValueF();
        cir.setReturnValue(Float.valueOf(before * m));
        CMAmbientFeel.efficiencyLog(p, 1.0F, m, "MOVE", before, before * m);
    }
}
