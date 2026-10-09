package net.dsh.createmite.command;

import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.EntityPlayer;
import net.minecraft.ICommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /F —— 报告玩家的**朝向**（南/北/东/西）。
 *
 * 纯读玩家自身的 yaw/pitch，**不做射线检测**（之前那版带了射线检测，
 * 一旦它抛异常整个指令就静默失败 —— 表现就是"/F 没用"）。
 * 外面还套了 try/catch，出错也会把原因发到聊天栏里，不会再"毫无反应"。
 *
 * MC 的 yaw 约定：0 = 南(+Z)、90 = 西(-X)、180 = 北(-Z)、270 = 东(+X)。
 */
public class FacingCommand extends CommandBase {

    @Override
    public String getCommandName() {
        // ✘ 原来叫 "f" —— MITE 自带的指令表里已经有 f，注册被覆盖 → "/F 未知指令"
        //   换成不会撞名的 cmf（别名 cmface / facing）
        return "cmf";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("cmface", "facing");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cmf  —  报告你当前面朝的方向";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            if (player == null) {
                sender.sendChatToPlayer(msg("该指令只能由玩家执行"));
                return;
            }
            float norm = ((player.rotationYaw % 360.0F) + 360.0F) % 360.0F;
            String dir;
            if (norm < 45.0F || norm >= 315.0F) dir = "南 (+Z)";
            else if (norm < 135.0F) dir = "西 (-X)";
            else if (norm < 225.0F) dir = "北 (-Z)";
            else dir = "东 (+X)";

            sender.sendChatToPlayer(msg("朝向: " + dir
                    + "  (yaw " + (int) norm + ", pitch " + (int) player.rotationPitch + ")"));
        } catch (Throwable t) {
            System.out.println("[CreateMITE] /F 出错: " + t);
            sender.sendChatToPlayer(msg("/F 出错: " + t));
        }
    }

    private static ChatMessageComponent msg(String text) {
        return ChatMessageComponent.createFromText(text);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }
}
