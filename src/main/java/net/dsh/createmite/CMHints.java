package net.dsh.createmite;

import net.minecraft.EntityPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 玩家级的"聊天栏提示"开关。
 *
 *   /M 0  关闭提示
 *   /M 1  开启提示（默认）
 *
 * 只记在内存里（重启游戏恢复默认开启）—— 这是测试期的便利开关，没必要为它做持久化。
 * 目前受它控制的是两类消息：
 *   1. 空手右键石磨时的状态回读；
 *   2. 石磨吸进原料却没转时的那句提醒。
 */
public final class CMHints {

    private static final Set<String> DISABLED = Collections.synchronizedSet(new HashSet<String>());

    private CMHints() {}

    public static boolean enabled(EntityPlayer player) {
        if (player == null) return false;
        // 注意：EntityPlayer.username 在 MITE 里是 protected，只能用公开的取值方法
        return !DISABLED.contains(player.getCommandSenderName());
    }

    public static boolean setEnabled(EntityPlayer player, boolean on) {
        if (player == null) return false;
        String name = player.getCommandSenderName();
        if (on) DISABLED.remove(name);
        else DISABLED.add(name);
        return on;
    }
}
