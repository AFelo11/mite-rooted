package net.dsh.createmite.command;

import net.dsh.createmite.CMSeasons;
import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.EntityPlayer;
import net.minecraft.ICommandSender;
import net.minecraft.World;

import java.util.Arrays;
import java.util.List;

/**
 * `/se` —— 四季控制 / 查询（2026-09-30 用户指定 ✓）。
 *
 * 【用户原话】「指令需要修改，**不影响原版查看天数指令**，新增 `/se （1、2、3、4档）`
 *   可调节当前世界的季节」✓
 *
 * 【怎么做到"不影响原版"】`/se` 只改**季节相位**（把今天当成一年里的第几天 ✓），
 *   **绝不动世界天数** ✗ —— 所以原版（MITE）看天数/时间那一套一个字都没变 ✓。
 *
 * 【★ 2026-09-30 用户改后的最终规格（照这个来 ✓）】
 *   用户原话：「/se 我不需要这条指令的效果【因为原版 MITE 好像就有特定指令，**但你不要去动它！！**】，
 *   我需要的仅仅是 `/se 1.2.3.4` 的使用而已【特殊指令 `/se R` 报告当前季节和世界天数，
 *   `/se 1.2.3.4` 的提示改变为例如：**跳到夏季**】」
 *
 * 【★ 踩过的坑】在注释里给指令名加粗时，**别让「两颗星号」紧贴「斜杠」** ✗✗
 *   —— 那两下正好凑成一个注释结束符，整段 javadoc 会被提前关掉 ✗，
 *     后面所有注释行都被当成代码，javac 报一整屏"非法字符" ✓
 *   （2026-09-30 连犯两次 ✗；以后给指令名加粗一律用**反引号**包起来 ✓）
 *
 * | 输入 | 行为 | 回话 |
 * |---|---|---|
 * | `/se` | **只给用法**（不再打印报告 ✗ —— 看天数/时间有 MITE 自己的指令 ✓，别抢它的活 ✓） | 用法那一行 |
 * | `/se 1\|2\|3\|4` | 跳到 春/夏/秋/冬 的**第 1 天** ✓ | **「跳到夏季」**这种 ✓ |
 * | `/se R` | **报告当前季节 + 世界天数** ✓（大小写都行 ✓） | 「当前季节：夏季｜世界第 1234 天（本年第 21/80 天）」|
 * | `/se R 13` | **把今天拨到"本季第 13 天"** ✓（季节不变 ✓；用户 2026-09-30 追加 ✓） | **「跳到夏季第 13 天」** ✓ |
 * | `/se 9` | （额外留的调试项）清掉本局相位偏移 ✓ | 一句确认 |
 *
 * ⚠️ **绝不改世界天数** ✗ —— 原版（MITE）那套看天数/时间的指令**一个字都没动** ✓
 *   （我们只是把"今天"当成一年里的第几天来算季节 ✓）。
 * ⚠️ 相位偏移**只在本局有效** ✗（重启游戏回到默认 ✓），它是调试/验证用的 ✓。
 */
public class SeasonCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "se";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("cmse", "season");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "用法：/se 1|2|3|4 = 跳到春/夏/秋/冬 ｜ /se R = 报告当前季节/天数/温度"
                + " ｜ /se R 13 = 跳到本季第 13 天 ｜ /se T 38.5 = 直接设体温（调试）";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    /** `/se C` 用：一行一种作物 ✓（目标天数 + 实际倍率 ✓）*/
    private static String cm$cropLine(int kind, String name) {
        float target = CMSeasons.cropTargetDays(kind);
        if (target < 0.0F) return name + "：不受季节影响 ✓";
        float f = CMSeasons.cropGrowthFactor(kind);
        if (target == 0.0F) return name + "：**本季不长** ✓（倍率 0 ✓）";
        return name + "：目标 " + net.dsh.createmite.CMAmbient.fmt(target) + " 天 ⇒ 倍率 x" + net.dsh.createmite.CMAmbient.fmt(f)
                + "（" + (f >= 1.0F ? "比天然快" : "比天然慢") + " ✓）";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            World world = (player == null) ? null : player.worldObj;

            // ---- 无参：只给用法（**不打印报告** ✗ —— 看天数/时间有 MITE 自己的指令 ✓，别抢它的活 ✓）----
            if (args == null || args.length == 0) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }

            String first = args[0].trim();

            // ---- /se R ：报告当前季节 + 世界天数 ✓ ----
            // ---- /se R <数字> ：把今天拨到**本季第 n 天** ✓（用户 2026-09-30 追加 ✓）----
            if (first.equalsIgnoreCase("R")) {
                if (args.length >= 2) {
                    int day;
                    try {
                        day = Integer.parseInt(args[1].trim());
                    } catch (Throwable t) {
                        sender.sendChatToPlayer(msg("季内天数要 1~" + CMSeasons.DAYS_PER_SEASON + "（例如 /se R 13）"));
                        return;
                    }
                    if (day < 1 || day > CMSeasons.DAYS_PER_SEASON) {
                        sender.sendChatToPlayer(msg("季内天数要 1~" + CMSeasons.DAYS_PER_SEASON + "（例如 /se R 13）"));
                        return;
                    }
                    CMSeasons.jumpToDayOfSeason(day, world);
                    net.dsh.createmite.CMSeasonsWeather.invalidateTodayCache(world);   // ★ 天气表立即生效 ✓
                    sender.sendChatToPlayer(msg("跳到" + CMSeasons.seasonName(CMSeasons.currentSeason())
                            + "季第 " + day + " 天"));
                    sender.sendChatToPlayer(msg(net.dsh.createmite.CMAmbient.reportLine(world)));      // ★ 环境温度 ✓
                    return;
                }
                sender.sendChatToPlayer(msg(CMSeasons.reportLine(world)));
                sender.sendChatToPlayer(msg(net.dsh.createmite.CMAmbient.reportLine(world)));      // ★ 环境温度 ✓
                return;
            }

            // ---- /se C ：打印四张作物表的**当前数值** ✓（排查"感觉不对劲"用 ✓）----
            if (first.equalsIgnoreCase("C")) {
                sender.sendChatToPlayer(msg(cm$cropLine(CMSeasons.CROP_WHEAT, "小麦")));
                sender.sendChatToPlayer(msg(cm$cropLine(CMSeasons.CROP_CARROT_ONION, "胡萝卜·洋葱")));
                sender.sendChatToPlayer(msg(cm$cropLine(CMSeasons.CROP_POTATO, "马铃薯")));
                sender.sendChatToPlayer(msg(cm$cropLine(CMSeasons.CROP_STEM, "瓜梗")));
                sender.sendChatToPlayer(msg("基线 = " + CMSeasons.cropBaselineDays()
                        + " 天（config: seasons.crop_baseline_days ✓ 倍率 = 基线 ÷ 目标天数）"));
                return;
            }

            // ---- /se T <摄氏度> ：直接把体温设成这个值 ✓（调试六档 buff 用 ✓）----
            // ---- /se F [摄氏|C] ：体感温度系统（2026-10-02 用户方案 ✓）----
            //   /se F        = 报告体感温度 + 各分项 + 效率
            //   /se F 20     = 把体感温度**锁定**在 20°C（逐档验效率用 ✓）
            //   /se F C      = 解除锁定
            if (first.equalsIgnoreCase("F")) {
                if (args.length >= 2) {
                    String a = args[1].trim();
                    if (a.equalsIgnoreCase("C")) {
                        net.dsh.createmite.CMAmbientFeel.clearForce();
                        sender.sendChatToPlayer(msg("体感温度锁定已解除 ✓"));
                        return;
                    }
                    try {
                        float v = Float.parseFloat(a);
                        net.dsh.createmite.CMAmbientFeel.force(v);
                        sender.sendChatToPlayer(msg("体感温度已锁定 " + net.dsh.createmite.CMAmbient.fmt(v) + "°C："
                                + net.dsh.createmite.CMAmbientFeel.tierName(net.dsh.createmite.CMAmbientFeel.tierOf(v))
                                + "（/se F 看详情 ｜ /se F C 解锁）"));
                    } catch (Throwable t) {
                        sender.sendChatToPlayer(msg("用法：/se F ｜ /se F 20 ｜ /se F C"));
                    }
                    return;
                }
                if (player == null) { sender.sendChatToPlayer(msg("该指令只能由玩家执行")); return; }
                sender.sendChatToPlayer(msg(net.dsh.createmite.CMAmbientFeel.report(player)));
                if (net.dsh.createmite.CMAmbientFeel.isForced()) {
                    sender.sendChatToPlayer(msg("⚠️ 当前是**锁定值**（/se F C 解锁 ✓）"));
                }
                return;
            }

            // ★ 2026-10-09：/se T 整块删掉 ✓（体温系统已卸载 ⇒ 只剩 /se F 设体感温度 ✓）

            int v;
            try {
                v = Integer.parseInt(first);
            } catch (Throwable t) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }

            if (v == 9) {
                CMSeasons.jumpToSeason(1, world);          // 先拨到春，再把相位归零 ✓
                CMSeasons.clearPhase();
                sender.sendChatToPlayer(msg("四季相位已清除（回到配置默认 ✓）"));
                return;
            }
            if (v < CMSeasons.SPRING || v > CMSeasons.WINTER) {
                sender.sendChatToPlayer(msg(getCommandUsage(sender)));
                return;
            }

            // ---- /se 1|2|3|4 ：跳季 ✓（用户要的提示就是这个：比如"跳到夏季" ✓）----
            CMSeasons.jumpToSeason(v, world);
            net.dsh.createmite.CMSeasonsWeather.invalidateTodayCache(world);       // ★ 天气表立即生效 ✓
            sender.sendChatToPlayer(msg("跳到" + CMSeasons.seasonName(v) + "季"));
        } catch (Throwable t) {
            System.out.println("[MITE] /se 出错: " + t);
            sender.sendChatToPlayer(msg("/se 出错: " + t));
        }
    }

    private static ChatMessageComponent msg(String text) {
        return ChatMessageComponent.createFromText(text);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return Arrays.asList("1", "2", "3", "4", "R", "T");
        }
        return null;
    }
}
