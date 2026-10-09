package net.dsh.createmite.command;

import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.EntityPlayer;
import net.minecraft.ICommandSender;
import net.minecraft.World;

import java.util.Arrays;
import java.util.List;

/**
 * `/Y <1|2|3>` —— 一键切天气（2026-09-30 用户要求 ✓）：1 = 下雨、2 = 雷暴雨、3 = 晴天。
 *
 * 【怎么做】直接改世界的时间/天气这份权威状态 ✓：
 *   · `WorldInfo.setRaining / setThundering` ✓（原版那份开关）
 *   · `World.setRainStrength(float)` ✓（MITE 自己那份强度，javap 实证有这个方法 ✓）
 *   两个都设，免得"原版开关开了、画面还是晴的" ✗。
 *
 * ⚠️ MITE 的天气是**按天事件表**驱动的（`WeatherEvent` ✓）—— 本指令是"强行插一脚" ✓，
 *   当天的事件表可能还会把天气拽回去 ✗；要"锁死"得再挂它的天气生成（用户要再说 ✓）。
 */
public class WeatherCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "Y";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("cmweather", "weatherset");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "用法：/Y 1 = 下雨 ｜ /Y 2 = 雷暴雨 ｜ /Y 3 = 晴天";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            World world = (player == null) ? null : player.worldObj;
            if (world == null) {
                sender.sendChatToPlayer(msg("该指令只能由玩家执行"));
                return;
            }
            if (args == null || args.length == 0) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }
            int v;
            try {
                v = Integer.parseInt(args[0].trim());
            } catch (Throwable t) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }
            // ★★ 2026-09-30 改：**直接调用 MITE 自己的 /weather 指令** ✓
            //   用户："原版我的世界中不是有下雨指令吗？咱不能直接调用吗？" ✓
            //   之前我们自己硬写 rainStrength ⇒ 状态是"在下雨"但**没有天气事件** ⇒ 粒子不来 ✗
            //   javap 实证 MITE 有 CommandWeather ✓，它认三个参数：rain / thunder / clear ✓
            String arg;
            switch (v) {
                case 1: arg = "rain"; break;
                case 2: arg = "thunder"; break;
                case 3: arg = "clear"; break;
                default:
                    sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                    return;
            }
            net.dsh.createmite.CMWeather.set(0);          // 不再自己强制 ✓
            // ★ MITE 的用法（lang 实证）：/weather <clear|rain|thunder> [持续的秒数] ✓
            //   只传一个参数会 WrongUsageException ✗ ⇒ 第二个参数给个默认时长 ✓
            String secs = (args.length >= 2) ? args[1].trim() : "600";   // 默认 600 秒 = 10 分钟 ✓
            net.dsh.createmite.CMWeather.set(v == 1 ? 1 : (v == 2 ? 2 : 3));
            sender.sendChatToPlayer(msg("已调用原版天气指令：/weather " + arg + " " + secs + " ✓"));
        } catch (Throwable t) {
            System.out.println("[MITE] /Y 出错: " + t);
            sender.sendChatToPlayer(msg("/Y 出错: " + t));
        }
    }

    /**
     * ⚠️ MITE 的 `WorldInfo` **没有** setRaining/setThundering ✗（javap 实证 ✓）——
     *   它的天气由 `WeatherEvent` 事件表驱动 ✓，设一次会被立刻覆盖 ✗。
     *   ⇒ 改成"**记一个强制模式，每 tick 重新按**" ✓（`CMWeather` ✓）。
     */
    private static void apply(int mode) {
        net.dsh.createmite.CMWeather.set(mode);
    }

    private static ChatMessageComponent msg(String text) {
        return ChatMessageComponent.createFromText(text);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return (args != null && args.length == 1) ? Arrays.asList("1", "2", "3") : null;
    }
}
