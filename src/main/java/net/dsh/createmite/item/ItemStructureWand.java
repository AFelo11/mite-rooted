package net.dsh.createmite.item;

import net.dsh.createmite.CMSelection;
import net.minecraft.ChatMessageComponent;
import net.minecraft.EntityPlayer;
import net.minecraft.Material;
import net.minecraft.RaycastCollision;

/**
 * 结构选择器（开发用）—— 用来把玩家搭的东西导出成文本给 AI 看。
 *
 * 用法：
 *   左键点一个方块 = 选 **A 点**（不会挖掉方块 ✓，见 PlayerControllerMPMixin）
 *   右键点一个方块 = 选 **B 点**
 *   然后输入  /T <名称>   → 往 .minecraft/MITE/structures/<名称>.txt 写一份结构清单 ✓
 *
 * 【贴图】和木棍一样 ✓（setTextureName("stick")）
 * 【获取】**没有合成配方** ✓，只能从创造模式物品栏拿 ✓（用户要求）
 */
public class ItemStructureWand extends CMItem {

    public ItemStructureWand(int idArg, String unlocalizedName) {
        super(idArg, Material.wood, unlocalizedName, "stick", 0.0F);
        this.setMaxStackSize(1);
    }

    @Override
    public boolean onItemRightClick(EntityPlayer player, float partial, boolean flag) {
        player.swingArm();
        RaycastCollision rc = player.getSelectedObject(partial, false);
        if (rc == null || !rc.isBlock()) {
            // 对着空气右键 = 报一下当前选了什么，不用跑指令 ✓
            tell(player, "§e[结构选择器] A = " + CMSelection.describeA()
                    + "   B = " + CMSelection.describeB());
            return true;
        }
        int dim = player.worldObj != null ? player.worldObj.provider.dimensionId : 0;
        CMSelection.setB(rc.block_hit_x, rc.block_hit_y, rc.block_hit_z, dim);
        if (shouldTell(rc.block_hit_x, rc.block_hit_y, rc.block_hit_z)) {   // 去重 ✓
            tell(player, "§b[结构选择器] B 点 = " + rc.block_hit_x + ", " + rc.block_hit_y + ", "
                    + rc.block_hit_z + "   （A 点 = " + CMSelection.describeA() + "）");
        }
        return true;
    }

    /**
     * 播报去重：一次左键会经过 clickBlock **和** onPlayerDamageBlock 两条路 ✓，
     * 不去重就会"点一次报两次" ✗（用户实测 ✓）。
     * 规则：**格子变了就报** ✓；格子没变（同一格连点/按住）则 400ms 内只报一次 ✓。
     */
    private static long lastTellMs = 0L;
    private static int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;

    public static boolean shouldTell(int x, int y, int z) {
        long now = System.currentTimeMillis();
        boolean changed = (x != lastX || y != lastY || z != lastZ);
        if (changed || now - lastTellMs > 400L) {
            lastX = x; lastY = y; lastZ = z; lastTellMs = now;
            return true;
        }
        return false;
    }

    /** 聊天栏反馈（这个工具是显式操作，不走 V 键那个开关 ✓） */
    public static void tell(EntityPlayer player, String msg) {
        if (player != null) player.sendChatToPlayer(ChatMessageComponent.createFromText(msg));
    }
}
