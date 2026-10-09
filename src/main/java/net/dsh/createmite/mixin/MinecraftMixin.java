package net.dsh.createmite.mixin;

import net.dsh.createmite.client.CMKeybinds;
import net.minecraft.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每客户端 tick 轮询一次「机械动力提示」按键（V）。
 *
 * 【为什么注入 runTick()V 而不是找个 Tick 事件】
 *   FishModLoader 3.4.2 的 net.xiaoyu233.fml.reload.event 里**没有**客户端 tick 事件
 *   （只有注册类事件 + HandleChatCommand / PlayerLoggedIn），所以走 Mixin 最直接 ✓。
 *   注入点用 HEAD：这一处每 tick 必到、且与 GUI 无关；
 *   KeyBinding.pressed 由原版在"没有界面打开"时才置位 ✓ ——
 *   正好满足"在聊天栏打字时按 V 不该切换"这个直觉 ✓。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "runTick()V", at = @At("HEAD"))
    private void cm$pollHintKey(CallbackInfo ci) {
        CMKeybinds.tick();
        cm$windowTitle();
        // ★ 暖手石的冷却条：**客户端也要刷** ✗（条是客户端画的 ✓
        //   而 Item.onItemRightClick 只在服务端跑 ⇒ 客户端那份 damage 得自己维护 ✓ 见 CMHeat.itemWarmUntil ✓）
        try {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.thePlayer != null) {
                net.dsh.createmite.item.ItemHandWarmer.tickCooldownBar(mc.thePlayer);
            }
        } catch (Throwable ignored) { }
    }

    /**
     * ★ 窗口标题 = MITE-扎根（用户 2026-10-08 定名 ✓）
     *   MITE 自己在 startGame() 里把标题设成 "MITE" ⇒ 我们盖掉它 ✓
     *   每 20 tick 校一次（万一被谁改回去就再设一遍 ✓ 每秒一次开销可忽略 ✓）
     */
    private static final String CM_TITLE = "MITE-扎根";
    private static int cm$titleTick = 0;

    private void cm$windowTitle() {
        try {
            if ((cm$titleTick++ % 20) != 0) return;
            if (!CM_TITLE.equals(org.lwjgl.opengl.Display.getTitle())) {
                org.lwjgl.opengl.Display.setTitle(CM_TITLE);
            }
        } catch (Throwable ignored) { }
    }

    /**
     * 调试开关（默认关）：配置 debug.auto_open_inventory = 1 时，每隔 100 tick 自动打开一次背包。
     *
     * 为什么要它：开发时要在不碰键盘的前提下截图核对"饰品面板长什么样"
     * （这台机器上 PostMessage 注入按键不生效，所以用自动打开代替）。
     * 产品包里这个开关默认 0，玩家碰不到。
     */
    private static boolean cm$debugFilled = false;
    private static boolean cm$debugOpened = false;

    private void cm$debugAutoOpenInventory() {
        try {
            if (net.dsh.createmite.CMConfig.getFloat("debug.auto_open_inventory", 0.0F) == 0.0F) return;
            net.minecraft.Minecraft mc = (net.minecraft.Minecraft) (Object) this;
            if (mc.thePlayer == null) return;
            // 先让服务端往 0 号饰品槽塞一根木棍（验证"库存 + 客户端同步"✓ 只发一次）
            if (mc.thePlayer.ticksExisted == 120 && !cm$debugFilled) {
                cm$debugFilled = true;
                mc.thePlayer.sendChatMessage("/acc 0");
            }
            if (mc.currentScreen != null) return;
            if (mc.thePlayer.ticksExisted < 140) return;
            if ((mc.thePlayer.ticksExisted % 100) != 0) return;
            if (cm$debugOpened) return;                 // ★ 整局只自动开一次（不要每 5 秒弹一次 ✗）
            cm$debugOpened = true;
            mc.displayGuiScreen(new net.minecraft.GuiInventory(mc.thePlayer));
        } catch (Throwable t) {
            // 调试开关，出错不影响游戏
        }
    }
}