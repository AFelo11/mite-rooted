package net.dsh.createmite.client;

import net.minecraft.GameSettings;
import net.minecraft.KeyBinding;
import net.minecraft.Minecraft;

/**
 * 客户端按键绑定：「提示」开关（默认 **V**，默认**开**）。
 *
 * 【为什么从 /M 0|1 改成按键】用户要求：
 *   「/M 0 指令是你弄的，现在我需要你改成键位 V 开关（按键设置里的名称为：MITE提示）（开关默认开启）」
 *   —— 聊天栏开关每次都要打字太麻烦，改成按一下 V 就切。
 *   （2026-10-09 定稿：这个显示名最终就叫「提示」✓ 下面提到的 MITE提示 都是指它）
 *
 * 【为什么按 V 是"发一条指令"而不是自己发包】
 *   真正拦消息的地方在**服务端**（见 CMHints：它按玩家名记一个禁用集合，
 *   所有 sendChatToPlayer 的调用点都在服务端分支里）。客户端想要改它，
 *   最省事又最稳的路子就是让客户端玩家把 /cmhint 0|1 当成聊天发出去 —— 
 *   这是原版就支持的路径（EntityClientPlayerMP.sendChatMessage 会把它当指令执行）✓，
 *   不用新写网络包、也不用碰 MITE 的 PacketRegisterEvent。
 *   服务端执行完会把「已开启/已关闭」再打回聊天栏 —— 顺带当成按键反馈 ✓。
 *
 * 【按键怎么进"按键设置"菜单】
 *   1.6.4 的 GuiControls 直接遍历 GameSettings.keyBindings ✓，所以只要把 KeyBinding 追加进去；
 *   2. 追加后**必须**调 KeyBinding.resetKeyBindingArrayAndHash()（KeyBinding 靠静态 hash 表查键）✗；
 *   3. 显示名走 I18n：description 给 "key.createmite.hints"，
 *      中文翻译在 CreateMite.onLanguageReload 里注册成「提示」✓（2026-10-09 定稿；早先按 MITE提示 称呼它）。
 */
public final class CMKeybinds {

    /** 语言键（翻译在 CreateMite.onLanguageReload 里注册） */
    public static final String HINT_KEY_DESC = "key.createmite.hints";

    /** 默认键：V（LWJGL 键码 47） */
    public static final int HINT_KEY_CODE = org.lwjgl.input.Keyboard.KEY_V;

    // ================= 2026-09-30 新增：I 键「角色状态」界面 =================
    // 用户要求：「先制作 I 键可以打开一个 UI 界面吧，UI 与原版一致即可，
    //   至于 UI 内有什么还没想好，暂时先放一个血量和一个饱食度吧」
    // 【为什么走按键轮询、而不是在 GuiScreen 里监听】和 V 键同一套（见类注释 ✓）：
    //   KeyBinding.pressed 只在"当前没有界面打开"时被置位 ✓ → 天然不会"在别的界面里误触发" ✓
    // 【为什么打开界面不需要发包】这个面板是**纯客户端只读**的：
    //   血量/饱食度在客户端本来就有（EntityPlayer.getHealth / getFoodStats ✓），
    //   不开容器、不占窗口号、不碰服务端 ✓ —— 所以不用像大熔炉 UI 那样走 Packet100OpenWindow ✓
    /** 语言键（翻译在 CreateMite.onLanguageReload 里注册） */
    public static final String STATUS_KEY_DESC = "key.createmite.status";

    /** 默认键：I（LWJGL 键码 23） */
    public static final int STATUS_KEY_CODE = org.lwjgl.input.Keyboard.KEY_I;

    private static KeyBinding hintBinding;
    private static KeyBinding statusBinding;

    /**
     * 客户端侧的状态镜像 —— **只用来决定"按一下要发 0 还是 1"**，
     * 真正的闸门在服务端 CMHints（那份才是权威）。
     * 默认 **true**（= 开关默认开启 ✓，和用户要求一致）。
     */
    private static boolean hintsOn = true;

    private CMKeybinds() {}

    /** 当前镜像状态（给需要即时判断的客户端代码用） */
    public static boolean hintsOn() {
        return hintsOn;
    }

    /**
     * 把按键挂进 GameSettings.keyBindings（每次调用都检查一遍）。
     *
     * 【为什么不缓存"已注册"标志】GameSettings.initKeybindings() 会把 keyBindings 整个重建
     *   （改键、换语言、重开世界都可能触发），缓存了就会"注册过一次以后再也不补" ✗。
     *   数组只有二十来项，每 tick 扫一遍的开销可以忽略 ✓。
     */
    public static void ensureRegistered() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) return;
        GameSettings gs = mc.gameSettings;
        KeyBinding[] cur = gs.keyBindings;
        if (cur == null) return;
        if (hintBinding == null) {
            hintBinding = new KeyBinding(HINT_KEY_DESC, HINT_KEY_CODE);
        }
        if (statusBinding == null) {
            statusBinding = new KeyBinding(STATUS_KEY_DESC, STATUS_KEY_CODE);
        }
        // 两个键一起补（哪个缺补哪个 ✓ 已存在的绝不重复加 ✗）
        int missing = 0;
        if (!cm$contains(cur, hintBinding)) missing++;
        if (!cm$contains(cur, statusBinding)) missing++;
        if (missing == 0) return;                      // 都在表里 ✓

        KeyBinding[] next = new KeyBinding[cur.length + missing];
        System.arraycopy(cur, 0, next, 0, cur.length);
        int k = cur.length;
        if (!cm$contains(cur, hintBinding)) next[k++] = hintBinding;
        if (!cm$contains(cur, statusBinding)) next[k++] = statusBinding;
        gs.keyBindings = next;
        KeyBinding.resetKeyBindingArrayAndHash();      // ★ 少了这一句按键根本不生效
        System.out.println("[MITE] 按键已挂上: " + HINT_KEY_DESC + "=V ｜ " + STATUS_KEY_DESC + "=I");
    }

    /** 表里是不是已经有这一条了 */
    private static boolean cm$contains(KeyBinding[] arr, KeyBinding want) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == want) return true;
        }
        return false;
    }

    /** 每 tick 调一次（由 MinecraftMixin 注入 Minecraft.runTick 的 HEAD 调用） */
    public static void tick() {
        ensureRegistered();
        Minecraft mc = Minecraft.getMinecraft();

        // ---------- V：提示 开关 ----------
        KeyBinding kb = hintBinding;
        if (kb != null && kb.pressed) {
            kb.pressed = false;    // ★ 必须手动清：不清就会"按一下触发好几 tick"
            if (mc != null && mc.thePlayer != null) {
                hintsOn = !hintsOn;
                // 把状态交给服务端那份权威开关（服务端会把结果打回聊天栏当反馈 ✓）
                mc.thePlayer.sendChatMessage("/cmhint " + (hintsOn ? "1" : "0"));
            }
        }

        // ---------- I：角色状态界面（纯客户端 ✓ 不开容器、不发包 ✓）----------
        KeyBinding sk = statusBinding;
        if (sk != null && sk.pressed) {
            sk.pressed = false;
            if (mc != null && mc.thePlayer != null) {
                mc.displayGuiScreen(new net.dsh.createmite.client.gui.GuiPlayerStatus());
            }
        }
    }
}