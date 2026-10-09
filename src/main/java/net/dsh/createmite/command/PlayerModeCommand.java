package net.dsh.createmite.command;

import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.EnumGameType;
import net.minecraft.ICommandSender;
import net.minecraft.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/**
 * /P 指令（测试用）：
 *   /P 0  生存（恢复）
 *   /P 1  创造模式
 *   /P 2  旁观者模式（1.6.4 无旁观者，此处为简易模拟：无碰撞 + 隐形 + 免伤 + 可飞行 + 禁止编辑方块）
 */
public class PlayerModeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "P";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("p");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/P <0|1|2>  —  0=生存 1=创造 2=旁观者(简易)";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // 单机测试用：任何玩家可用（联机时建议改为 op 校验）
        return true;
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
        int mode;
        try {
            mode = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendChatToPlayer(createMessage("参数必须是 0 / 1 / 2"));
            return;
        }

        switch (mode) {
            case 1:
                applyCreative(player);
                sender.sendChatToPlayer(createMessage("§a已切换为创造模式"));
                break;
            case 2:
                applySpectator(player);
                sender.sendChatToPlayer(createMessage("§b已切换为旁观者模式（简易：穿墙/隐形/免伤）"));
                break;
            case 0:
            default:
                applySurvival(player);
                sender.sendChatToPlayer(createMessage("§e已恢复为生存模式"));
                break;
        }
    }

    private void applyCreative(ServerPlayer player) {
        clearSpectator(player);
        player.setGameType(EnumGameType.CREATIVE);
        player.sendPlayerAbilities();
    }

    private void applySpectator(ServerPlayer player) {
        // 保持生存游戏模式（不使用创造背包），但赋予旁观者特性
        player.setGameType(EnumGameType.SURVIVAL);
        player.noClip = true;
        player.setInvisible(true);
        player.capabilities.disableDamage = true;
        player.capabilities.allowFlying = true;
        player.capabilities.isFlying = true;
        player.capabilities.allowEdit = false;
        player.sendPlayerAbilities();
    }

    private void applySurvival(ServerPlayer player) {
        clearSpectator(player);
        player.setGameType(EnumGameType.SURVIVAL);
        player.sendPlayerAbilities();
    }

    private void clearSpectator(ServerPlayer player) {
        player.noClip = false;
        player.setInvisible(false);
        player.capabilities.disableDamage = false;
        player.capabilities.allowFlying = false;
        player.capabilities.isFlying = false;
        player.capabilities.allowEdit = true;
    }

    /** 1.6.4 的聊天消息对象 */
    private static ChatMessageComponent createMessage(String text) {
        return ChatMessageComponent.createFromText(text);
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
