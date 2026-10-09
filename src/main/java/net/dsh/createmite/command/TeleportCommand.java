package net.dsh.createmite.command;

import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.ICommandSender;
import net.minecraft.ServerPlayer;
import net.minecraft.World;

import java.util.Arrays;
import java.util.List;

/**
 * /O 维度传送（测试用）：
 *   /O 0  主世界 (dim 0)
 *   /O 1  地下世界 (dim -2, MITE Underworld)
 *   /O 2  下界 (dim -1)
 */
public class TeleportCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "O";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("o");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/O <0|1|2>  —  0=主世界 1=地下世界 2=下界";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ServerPlayer player = getCommandSenderAsPlayer(sender);
        if (player == null) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("该指令只能由玩家执行"));
            return;
        }
        if (args.length < 1) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText(getCommandUsage(sender)));
            return;
        }
        int sel;
        try {
            sel = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("参数必须是 0 / 1 / 2"));
            return;
        }

        int dim;
        String name;
        switch (sel) {
            case 1:
                dim = World.DIMENSION_ID_UNDERWORLD;
                name = "地下世界";
                break;
            case 2:
                dim = World.DIMENSION_ID_NETHER;
                name = "下界";
                break;
            case 0:
            default:
                dim = World.DIMENSION_ID_OVERWORLD;
                name = "主世界";
                break;
        }

        if (player.dimension == dim) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("你已经在" + name + "了"));
            return;
        }
        try {
            player.travelToDimension(dim);
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§a已传送至" + name + "（dim=" + dim + "）"));
        } catch (Throwable t) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§c传送失败: " + t));
            t.printStackTrace();
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[]{"0", "1", "2"});
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
