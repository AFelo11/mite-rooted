package net.dsh.createmite.command;

import net.dsh.createmite.CMHints;
import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.ICommandSender;
import net.minecraft.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/**
 * /cmhint —— 聊天栏提示开关的**服务端落地**。
 *
 *   /cmhint 0  关闭提示
 *   /cmhint 1  开启提示（默认）
 *   /cmhint 2  报告朝向（老的 /M 2 兼容入口）
 *
 * 【为什么保留这个指令】2026-09-28 起，玩家侧的入口是**键位 V**（见 client/CMKeybinds）✓。
 *   按键本身改不了服务端状态 —— CMHints 的那份"禁用集合"是服务端静态表，
 *   而所有 sendChatToPlayer 的调用点都在服务端分支里。所以按键走的正是这条指令
 *   （客户端玩家 sendChatMessage("/cmhint 0|1")），服务端的回复顺便当成按键反馈 ✓。
 *   **老名字 /M 已经不再注册** ✗（用户要求改成键位），指令名改成 cmhint 免得和玩家的肌肉记忆打架。
 */
public class HintCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "cmhint";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("createhint");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cmhint <0|1>  —  0=关闭提示 1=开启提示（平时直接按 V 键即可）";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;   // 单机测试用
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ServerPlayer player = getCommandSenderAsPlayer(sender);
        if (player == null) {
            sender.sendChatToPlayer(createMessage("该指令只能由玩家执行"));
            return;
        }
        if (args.length < 1) {
            sender.sendChatToPlayer(createMessage(getCommandUsage(sender)));
            return;
        }
        // /M 2 —— 顺便报告朝向（/F 单独注册死活不生效，先挂在这里保证能用）
        if (args[0].equals("2")) {
            float norm = ((player.rotationYaw % 360.0F) + 360.0F) % 360.0F;
            String dir;
            if (norm < 45.0F || norm >= 315.0F) dir = "南 (+Z)";
            else if (norm < 135.0F) dir = "西 (-X)";
            else if (norm < 225.0F) dir = "北 (-Z)";
            else dir = "东 (+X)";
            sender.sendChatToPlayer(createMessage("朝向: " + dir
                    + "  (yaw " + (int) norm + ", pitch " + (int) player.rotationPitch + ")"));
            return;
        }

        boolean on;
        if (args[0].equals("1")) {
            on = true;
        } else if (args[0].equals("0")) {
            on = false;
        } else {
            sender.sendChatToPlayer(createMessage("参数必须是 0 / 1 / 2（2 = 报告朝向）"));
            return;
        }
        CMHints.setEnabled(player, on);
        sender.sendChatToPlayer(createMessage(on
                ? "§a已开启提示"
                : "§e已关闭提示"));
    }

    private static ChatMessageComponent createMessage(String text) {
        return ChatMessageComponent.createFromText(text);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[]{"0", "1"});
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
