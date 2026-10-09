package net.dsh.createmite.command;

import net.dsh.createmite.CMSelection;
import net.minecraft.Block;
import net.minecraft.ChatMessageComponent;
import net.minecraft.CommandBase;
import net.minecraft.ICommandSender;
import net.minecraft.Minecraft;
import net.minecraft.ServerPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /T <名称> —— 把结构选择器选中的长方体**导出成文本文件**，给 AI 看。
 *
 * 输出：<游戏目录>/MITE/structures/<名称>.txt ✓（名称玩家随便起 ✓ 中文也行 ✓）
 * 内容：每一层（y）一张图，每格写 "方块id:元数据" ✓，文件头带图例（id → 方块名）✓
 * 【为什么写 id:meta】MITE 的方块名在语言文件里，写 id 最稳 —— AI 手上有 MITE 自己的参考导出，可以对上号 ✓
 */
public class StructureCommand extends CommandBase {

    /** 选区上限，防止选太大写出个几十兆的文件 ✓ */
    private static final int MAX = 32;

    @Override
    public String getCommandName() {
        return "T";
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("t", "structure", "cmt");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/T <名称>  —  把左键/右键选的两个点之间的结构导出到 MITE/structures/<名称>.txt";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;   // 单机开发工具 ✓
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
        if (!CMSelection.ready()) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§c先选两个点：左键一个方块 = A，右键一个方块 = B"
                    + "（现在 A=" + CMSelection.describeA() + " B=" + CMSelection.describeB() + "）"));
            return;
        }

        String name = args[0];
        int x1 = Math.min(CMSelection.ax, CMSelection.bx), x2 = Math.max(CMSelection.ax, CMSelection.bx);
        int y1 = Math.min(CMSelection.ay, CMSelection.by), y2 = Math.max(CMSelection.ay, CMSelection.by);
        int z1 = Math.min(CMSelection.az, CMSelection.bz), z2 = Math.max(CMSelection.az, CMSelection.bz);
        int dx = x2 - x1 + 1, dy = y2 - y1 + 1, dz = z2 - z1 + 1;
        if (dx > MAX || dy > MAX || dz > MAX) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§c选区太大了（" + dx + "x" + dy + "x" + dz
                    + "），单边最多 " + MAX + " 格"));
            return;
        }

        try {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "MITE/structures");
            dir.mkdirs();
            File out = new File(dir, name + ".txt");
            PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
            Map<Integer, String> legend = new LinkedHashMap<Integer, String>();

            w.println("# 结构导出: " + name);
            w.println("# 玩家: " + player.getEntityName() + "   维度: " + player.worldObj.provider.dimensionId);
            w.println("# A点(左键): " + CMSelection.ax + "," + CMSelection.ay + "," + CMSelection.az);
            w.println("# B点(右键): " + CMSelection.bx + "," + CMSelection.by + "," + CMSelection.bz);
            w.println("# 尺寸: " + dx + " x " + dy + " x " + dz + "   （x 向东为正, y 向上, z 向南为正）");
            w.println("# 每格写法 = 方块id:元数据；空气写 0:0");
            w.println();

            StringBuilder body = new StringBuilder();
            for (int y = y2; y >= y1; y--) {
                body.append("y=").append(y).append("\n");
                for (int z = z1; z <= z2; z++) {
                    body.append("  z=").append(z).append(" | ");
                    for (int x = x1; x <= x2; x++) {
                        int id = player.worldObj.getBlockId(x, y, z);
                        int meta = player.worldObj.getBlockMetadata(x, y, z);
                        body.append(id).append(':').append(meta).append(' ');
                        if (legend.get(id) == null) {
                            Block b = Block.blocksList[id];
                            legend.put(id, b == null ? "(null)" : b.getUnlocalizedName());
                        }
                    }
                    body.append('\n');
                }
                body.append('\n');
            }

            w.println("# ---- 图例（id -> 方块内部名）----");
            for (Map.Entry<Integer, String> e : legend.entrySet()) {
                w.println("#   " + e.getKey() + " = " + e.getValue());
            }
            w.println();
            w.print(body.toString());
            w.close();

            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§a[结构] 已导出 " + dx + "x" + dy + "x" + dz
                    + " → MITE/structures/" + name + ".txt"));
            CMSelection.clear();
        } catch (Exception ex) {
            sender.sendChatToPlayer(ChatMessageComponent.createFromText("§c导出失败: " + ex));
        }
    }
}
