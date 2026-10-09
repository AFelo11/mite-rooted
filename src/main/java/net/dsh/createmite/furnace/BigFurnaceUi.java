package net.dsh.createmite.furnace;

import net.minecraft.EntityPlayer;
import net.minecraft.ICrafting;
import net.minecraft.Packet100OpenWindow;
import net.minecraft.ServerPlayer;
import net.minecraft.TileEntity;

/**
 * 打开大熔炉界面的入口（服务端建容器 + 发开窗包 ✓）。
 *
 * 【为什么要自己发包】MITE 的 {@code EntityPlayer.displayGUIxxx} 在服务端是**空实现** ✗，
 * 也没有 FML 的 openGui ✗ → 只能自己来 ✓。
 *
 * 【为什么复用原版 Packet100】MITE 给 Packet100OpenWindow **加了坐标字段**
 * （{@code setCoords(TileEntity)} / {@code hasTileEntity()} ✓，javap 实证 ✓），
 * 客户端拿到包就能直接找到那一格的方块实体 ✓ —— 省掉一整套自定义包 ✓。
 * 我们只用一个"原版没用到的类型号"，然后在客户端那边拦下来建自己的界面 ✓。
 *
 * 【windowId】必须两边一致，槽位点击（Packet102）才对得上 ✓：
 * 服务端从 ServerPlayer 自己的计数器里取（和原版共用一套号 ✓），
 * 客户端用包里带回来的那个 ✓。
 */
public final class BigFurnaceUi {

    private BigFurnaceUi() {}

    /** 自定义窗口类型号（原版只用到 0~12 ✓）*/
    public static final int WINDOW_TYPE = 30;

    /** 兜底窗口号计数（只有在 mixin 没生效时才会用到 ✓）*/
    private static int FALLBACK_ID = 1;

    /** 服务端调用：给玩家打开这台大熔炉的界面 ✓ */
    public static void open(EntityPlayer player, FurnaceCoreTileEntity core) {
        if (player == null || core == null) return;
        if (player.worldObj == null || player.worldObj.isRemote) return;
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer sp = (ServerPlayer) player;

        BigFurnaceContainer container = new BigFurnaceContainer(player, core);
        // 借 ServerPlayer 自己那套 windowId 计数（mixin 在运行期加上接口，编译期看不到 ✗ → 经 Object 转 ✓）
        int windowId;
        Object raw = sp;
        if (raw instanceof WindowIdHolder) {
            windowId = ((WindowIdHolder) raw).cm$nextWindowId();
        } else {
            windowId = FALLBACK_ID++ % 100;      // 兜底（正常走不到 ✓）
        }
        container.windowId = windowId;
        player.openContainer = container;
        container.addCraftingToCrafters((ICrafting) sp);
        container.detectAndSendChanges();

        Packet100OpenWindow packet = new Packet100OpenWindow(
                container.windowId, WINDOW_TYPE, "大熔炉", container.inventorySlots.size(), true);
        // ★ 把机壳档位一起带过去：客户端必须建出**同样格数**的容器 ✗否则 Packet104 越界崩溃 ✓
        packet.field_111008_f = core.casing();
        TileEntity te = (TileEntity) core;
        packet.setCoords(te);
        sp.sendPacket(packet);

        // ★★ 2026-10-01 修用户报的"幽灵物品"：**开窗之后必须把整份窗口内容推一遍** ✗
        //
        // 【症状】结构散了、东西从顶上掉出来之后，**重新拼好再开界面，里面还显示着那些东西** ✗
        //   而且"点一下才发现是假的"（一点它就消失 ✓ —— 服务端那格本来就是空的 ⇒ 客户端收到回包就把它抹掉了 ✓）
        //
        // 【根因】我们原来只发了 **Packet100（开窗）** ✗，**没发 Packet104（整份内容）** ✗ ⇒
        //   客户端建容器时只能拿**它自己那具方块实体里的旧内容** ✗（客户端那具 TE 一直没人清过 ✓）
        //   而服务端的 detectAndSendChanges 是"**变了才发**" ✗ —— 它建容器时
        //   inventoryItemStacks 镜像就是照当前（已经是空的了）建的 ⇒ 认为"没变化" ⇒ 一格都不发 ✗✗
        //   ⇒ 客户端永远停在旧内容上 ✓
        //
        // 【修法】照原版 openContainer 的做法：把 container.getInventory() **整份**发过去 ✓
        //   ⚠️ 顺序不能反 ✗：Packet100 必须在前 —— 客户端得先建出容器，Packet104 才落得进去 ✓
        //   （这一条同时把"第一次开界面看到的是空的"那个隐患也一起修了 ✓）
        sp.sendContainerAndContentsToPlayer(container, container.getInventory());
    }

    /** ServerPlayer 侧实现（见 ServerPlayerMixin）：借它自己那套 windowId 计数 ✓ */
    public interface WindowIdHolder {
        int cm$nextWindowId();
    }
}
