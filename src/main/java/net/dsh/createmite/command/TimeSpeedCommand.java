package net.dsh.createmite.command;

import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.EntityPlayer;
import net.minecraft.ICommandSender;
import net.minecraft.World;

import java.util.Arrays;
import java.util.List;

/**
 * `/S <数字>` —— 调节**时间流速**（2026-09-30 用户要求 ✓；单个实例管理员工具）。
 *
 * 【什么意思】`/S 4` = 世界时间**跑快 4 倍** ✓（`/S 1` = 恢复正常 ✓）。
 *   实现：每 tick 额外推进世界的总时间 ✓（服务端推进 ✓，客户端时间由服务端同步 ✓）。
 *
 * 【⚠️ 现有版本的一个已知短板】1.6.4 里"太阳/月亮的位置"看的是**另一份时间**
 *   （`WorldInfo` 的世界时间 ✗），所以这个指令**两份都推** ✓ —— 但客户端天体位置是插值画的，
 *   可能看起来仍偏慢 ✗；要完美得再挂客户端渲染那边（先这样，用户要再说 ✓）。
 */
public class TimeSpeedCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "S";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("cmtime", "timespeed");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "用法：/S <倍速>（1 = 正常，2 = 两倍速，4 = 四倍速…；上限 64）";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            if (args == null || args.length == 0) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender) + "｜当前倍速 = "
                        + net.dsh.createmite.CMTimeSpeed.multiplier()));
                return;
            }
            float v;
            try {
                v = Float.parseFloat(args[0].trim());
            } catch (Throwable t) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }
            if (v < 0.0F) v = 0.0F;
            if (v > 64.0F) v = 64.0F;
            net.dsh.createmite.CMTimeSpeed.set(v);
            sender.sendChatToPlayer(msg("时间流速 = **" + net.dsh.createmite.CMTimeSpeed.multiplier() + " 倍**"
                    + (v == 0.0F ? "（时间暂停 ✓）" : "")));
        } catch (Throwable t) {
            System.out.println("[CreateMITE] /S 出错: " + t);
            sender.sendChatToPlayer(msg("/S 出错: " + t));
        }
    }

    private static ChatMessageComponent msg(String text) {
        return ChatMessageComponent.createFromText(text);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return (args != null && args.length == 1) ? Arrays.asList("0", "1", "2", "4", "10") : null;
    }
}
