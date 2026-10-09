package net.dsh.createmite.item;

import net.dsh.createmite.kinetics.KineticHelper;
import net.dsh.createmite.kinetics.block.BlockKineticBase;
import net.minecraft.Block;
import net.minecraft.ChatMessageComponent;
import net.minecraft.CreativeTabs;
import net.minecraft.EntityPlayer;
import net.minecraft.Item;
import net.minecraft.RaycastCollision;
import net.minecraft.World;

/**
 * 扳手：右键**旋转动力方块的轴向**（X → Y → Z 循环）。
 *
 * 存在的意义：动力方块的轴向是**放置时按点击面定死**的，放歪了以前只能拆了重放。
 * 有了扳手，搭齿轮组／调轴线就不用反复拆装。
 *
 * 【哪些方块能被转】由 BlockKineticBase.isWrenchRotatable() 决定，
 * 默认能转；**石磨覆写为 false** —— 它的轴向被锁死成竖直 Y，
 * 否则"只能被齿轮带动"那条规则就没有确定含义了。
 *
 * 【为什么不做 Create 的"潜行右键拆机器"】MITE 的 destroyBlock 签名和原版不一样，
 * 走它要绕开正常的掉落/难度结算；而"拆方块"本来就能用镐子做到，所以先不做。
 */
public class ItemWrench extends Item {

    public ItemWrench(int id) {
        super(id, "wrench");
        this.setUnlocalizedName("wrench");
        // ★ 这一句不能少：MITE 的 Item(int, String) 只是构造参数，
        //   真正决定贴图的是 setTextureName —— 少了它手持/背包就显示不正常。
        //   （CMItem 里两处都写，就是踩过这个坑。）
        this.setTextureName("wrench");
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTabs.tabTools);
        // 接入 MITE 的材料 / 难度体系，免得它报"没有材料定义"
        this.setMaterial(net.minecraft.Material.iron);
        this.setCraftingDifficultyAsComponent(400.0F);
        this.setLowestCraftingDifficultyToProduce(800.0F);
    }

    @Override
    public boolean onItemRightClick(EntityPlayer player, float partial, boolean flag) {
        // ★ 挥手动作必须放在最前面、**两边都执行**。
        //   之前它写在 "if (world.isRemote) return true;" 之后 —— 那是服务端分支，
        //   本机玩家根本收不到挥手动画，表现就是"用扳手没有任何动作"。
        //   原版对"右键使用物品"也是无条件挥手的，所以这里不去区分成功与否。
        player.swingArm();

        RaycastCollision rc = player.getSelectedObject(partial, false);
        if (rc == null || !rc.isBlock()) return false;

        World world = player.worldObj;
        int x = rc.block_hit_x;
        int y = rc.block_hit_y;
        int z = rc.block_hit_z;

        Block block = Block.blocksList[world.getBlockId(x, y, z)];
        if (!(block instanceof BlockKineticBase)) return false;

        // ★ 潜行 + 右键 = **现场诊断**（不改轴向），把模组内部算出来的东西直接打到聊天栏。
        //   "水车转向和水流不一致"这类问题，看一眼这行就知道是哪一环错了：
        //     力矩=0        → 根本没识别到流动的水（方向位永远不会更新）
        //     方向位不变    → 力矩一直是 0
        //     力矩符号正确但轮子反向 → 只剩渲染符号要翻
        //   只在客户端执行，免得单机里服务端再发一遍 → 聊天栏出现两条一模一样的。
        // ★★ 扳手潜行右击**已封装的传动杆** → 解除封装（资料 396859 原文：
        //   "使用扳手潜行右击可将其单独拆掉" ✓）——注意这是**拆掉外壳**，
        //   不是拆方块：传动杆本体留在原地 ✓，且**不掉任何东西** ✓。
        //   放在诊断分支之前，免得潜行右键时只报数据、不干活 ✗。
        if (player.isSneaking() && !world.isRemote) {
            // ★ 2026-09-27 改版：封装箱是**独立方块** ✓ → 拆壳 = 换回原方块（保留自转轴 ✓）
            if (block instanceof net.dsh.createmite.kinetics.block.BlockEncased) {
                net.dsh.createmite.kinetics.block.BlockEncased enc =
                        (net.dsh.createmite.kinetics.block.BlockEncased) block;
                int axis = world.getBlockMetadata(x, y, z) & 3;
                net.minecraft.Block base = net.dsh.createmite.kinetics.block.BlockEncased
                        .baseBlockFor(enc.variant());
                if (base != null) {
                    // ★ 换壳期间必须置 swappingShell ✗→✓（否则 setBlock 移除旧方块会掉出原方块 ✓）
                    net.dsh.createmite.kinetics.block.BlockEncased.swappingShell = true;
                    try {
                        world.setBlock(x, y, z, base.blockID, axis, 2);   // MITE：setBlock(x,y,z,id,meta,flag) ✓
                    } finally {
                        net.dsh.createmite.kinetics.block.BlockEncased.swappingShell = false;
                    }
                    world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                            "tile.piston.in", 0.4F, 1.2F);
                    if (net.dsh.createmite.CMHints.enabled(player)) {   // ★ 归 V 键开关管 ✓
                        player.sendChatToPlayer(net.minecraft.ChatMessageComponent.createFromText(
                                "§e已拆掉机壳（本体保留，不掉落机壳）"));
                    }
                    return true;
                }
            }
        }

        if (player.isSneaking()) {
            if (world.isRemote) {
                net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
                if (te instanceof net.dsh.createmite.kinetics.KineticTileEntity) {
                    net.dsh.createmite.kinetics.KineticTileEntity k =
                            (net.dsh.createmite.kinetics.KineticTileEntity) te;
                    int meta = world.getBlockMetadata(x, y, z);
                    float t = k.waterTorque();
                    String sgn = t > 0.0001F ? "§a+" : (t < -0.0001F ? "§c-" : "§70");
                    // ★ 你点的是**哪一面** —— MITE 的射线命中直接给了 face_hit（EnumFace），
                    //   用 toString() 打出原枚举名（UP/NORTH/…），再附一个中文，避免歧义。
                    String fn = String.valueOf(rc.face_hit);
                    // 也写一行日志（客户端 stdout 会进 latest.log），这样我不用你手打枚举名就能读到
                    System.out.println("[CreateMITE][WRENCH] block=(" + x + "," + y + "," + z + ")"
                            + " face=" + fn
                            + " offset=(" + String.format("%.3f", rc.block_hit_offset_x)
                            + "," + String.format("%.3f", rc.block_hit_offset_y)
                            + "," + String.format("%.3f", rc.block_hit_offset_z) + ")"
                            + " meta=" + meta
                            + " axis=" + (meta & 3));
                    String cn = fn.contains("UP") ? "上" : fn.contains("DOWN") ? "下"
                            : fn.contains("NORTH") ? "北" : fn.contains("SOUTH") ? "南"
                            : fn.contains("EAST") ? "东" : fn.contains("WEST") ? "西" : "?";
                    if (net.dsh.createmite.CMHints.enabled(player)) {   // ★ 诊断也归 V 键开关管 ✓
                    player.sendChatToPlayer(ChatMessageComponent.createFromText(
                            "§b[诊断] 点击面=" + fn + "(" + cn + ")"
                                    + " 命中偏移=(" + String.format("%.2f", rc.block_hit_offset_x)
                                    + "," + String.format("%.2f", rc.block_hit_offset_y)
                                    + "," + String.format("%.2f", rc.block_hit_offset_z) + ")"
                                    + " | 轴=" + (meta & 3)
                                    + " 方向位=" + (((meta & 8) != 0) ? "正" : "负")
                                    // ★ 工程③：这两端是**客户端**读到的 metadata（bit2/bit3）——
                                    //   服务端刚写完就用这行诊断在客户端核一遍，位有没有同步过来一眼可见 ✓
                                    + " 接轴端[正端=" + (((meta & 4) != 0) ? "封" : "通")
                                    + " 负端=" + (((meta & 8) != 0) ? "封" : "通") + "]"
                                    + " 周围有水=" + (k.waterPowered ? "是" : "否")
                                    + " 力矩=" + sgn + Math.abs(t)
                                    + " 本机转速=" + k.getSourceSpeed()));
                    }
                }
            }
            return true;
        }

        // ★★★ 工程③：装壳齿轮的"接轴开关"（逐条复刻原版 EncasedCogwheelBlock.onWrenched）
        //   原版：
        //       if (点击面.getAxis() != 方块自己的轴) return super.onWrenched(...);  // 点侧面 → 普通扳手＝**转轴** ✓
        //       ... cycle(点击面是正方向 ? TOP_SHAFT : BOTTOM_SHAFT)                 // 点轴向两端 → **开/关那一端** ✓
        //   所以这里不是"装壳后就不能转轴" ✗ —— 点侧面照样转轴 ✓，只有点**轴向那两端**才是开关 ✓。
        //   装壳传动杆箱没有这个开关（原版 EncasedShaftBlock 也没有）→ 走下面的老路 ✓。
        if (block instanceof net.dsh.createmite.kinetics.block.BlockEncased) {
            int variant = ((net.dsh.createmite.kinetics.block.BlockEncased) block).variant();
            int meta = world.getBlockMetadata(x, y, z);
            int dir = rc.face_hit == null ? -1 : rc.face_hit.ordinal();   // EnumFace 序号 = 0下 1上 2北 3南 4西 5东 ✓
            boolean axial = dir >= 0 && net.dsh.createmite.kinetics.KineticHelper.DIR_AXIS[dir] == (meta & 3);
            if (variant != net.dsh.createmite.kinetics.block.BlockEncased.VARIANT_SHAFT && axial) {
                if (world.isRemote) return true;      // 客户端吞掉这次右键，实际改动只在服务端做 ✓
                net.minecraft.TileEntity te = world.getBlockTileEntity(x, y, z);
                if (te instanceof net.dsh.createmite.kinetics.KineticTileEntity) {
                    boolean closed = ((net.dsh.createmite.kinetics.KineticTileEntity) te).toggleShaftEnd(dir);
                    String cn = dir == 1 ? "上" : dir == 0 ? "下" : dir == 3 ? "南" : dir == 2 ? "北"
                            : dir == 5 ? "东" : "西";
                    world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                            "tile.piston.in", 0.4F, closed ? 0.9F : 1.5F);
                    if (net.dsh.createmite.CMHints.enabled(player)) {   // ★ 归 V 键开关管 ✓
                        player.sendChatToPlayer(ChatMessageComponent.createFromText(
                                (closed ? "§c已关闭 " : "§a已打开 ") + cn + "端 的传动轴连接"
                                        + (closed ? "（这一端不再接传动杆）" : "（这一端可以接传动杆）")));
                    }
                }
                return true;
            }
        }

        if (!((BlockKineticBase) block).isWrenchRotatable()) return false;
        if (world.isRemote) return true;   // 客户端吞掉这次右键，实际改动只在服务端做

        int meta = world.getBlockMetadata(x, y, z);
        {
            int axis = meta & 3;
            int next = (axis + 1) % 3;                    // 0=X 1=Y 2=Z 循环
            world.setBlockMetadataWithNotify(x, y, z, (meta & ~3) | next, 2);
        }

        world.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D,
                "tile.piston.out", 0.3F, 1.4F);
        return true;
    }
}
