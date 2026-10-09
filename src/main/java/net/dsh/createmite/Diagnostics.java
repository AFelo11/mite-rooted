package net.dsh.createmite;

import net.minecraft.Block;
import net.minecraft.Icon;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.ResourceLocation;

/**
 * 诊断：打印贴图可达性与图标解析结果。
 * 注意：不要在图集上传后去读 TextureAtlasSprite 的像素数据（framesTextureData 已被清空，会抛异常）。
 */
public final class Diagnostics {

    private static final String[] PATHS = {
            "textures/blocks/shaft.png",
            "textures/blocks/cogwheel.png",
            "textures/blocks/millstone.png",
            "textures/blocks/zinc_ore.png",
            "textures/items/raw_zinc.png",
            "textures/items/ingots/zinc.png",
            "textures/items/wrench.png"
    };

    private Diagnostics() {}

    public static void scheduleTextureCheck() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(18000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                int ok = 0;
                for (String path : PATHS) {
                    boolean exists;
                    try {
                        exists = new ResourceLocation(path).exists();
                    } catch (Throwable t2) {
                        continue;
                    }
                    if (exists) ok++;
                }
                System.out.println("[MITE][诊断] 贴图文件可达 " + ok + "/" + PATHS.length);
                dumpBlock("对照 原版石头", Block.stone);
                dumpBlock("锌矿石", CMBlocks.oreZinc);
                dumpBlock("传动轴", CMBlocks.blockShaft);
                dumpBlock("齿轮", CMBlocks.blockCogwheel);
                dumpBlock("手摇曲柄", CMBlocks.blockHandCrank);
                dumpBlock("石磨", CMBlocks.blockMillstone);
                dumpItem("对照 原版铁锭", Item.ingotIron);
                dumpItem("对照 粗锌", CMItems.rawZinc);
                dumpItem("扳手", CMItems.wrench);
                dumpStack("背包 扳手", new ItemStack(CMItems.wrench, 1, 0));
                System.out.println("[MITE][诊断] 扳手 精灵号="
                        + (CMItems.wrench == null ? "?" : String.valueOf(CMItems.wrench.getSpriteNumber()))
                        + " 是ItemBlock=" + (((net.minecraft.Item) CMItems.wrench) instanceof net.minecraft.ItemBlock));
                dumpStack("背包 传动轴", new ItemStack(CMBlocks.blockShaft, 1, 0));
                dumpStack("背包 齿轮", new ItemStack(CMBlocks.blockCogwheel, 1, 0));
                dumpStack("背包 手摇曲柄", new ItemStack(CMBlocks.blockHandCrank, 1, 0));
                dumpStack("背包 石磨", new ItemStack(CMBlocks.blockMillstone, 1, 0));
            }
        }, "createmite-diagnostics");
        t.setDaemon(true);
        t.start();
    }

    private static void dumpBlock(String label, Block block) {
        if (block == null) { System.out.println("[MITE][诊断] " + label + " = null"); return; }
        Icon icon = block.getIcon(0, 0);
        boolean cube = false;
        boolean solid = false;
        try {
            cube = block.isAlwaysStandardFormCube();
            solid = block.isSolid(0);
        } catch (Throwable ignored) {
        }
        System.out.println("[MITE][诊断] 方块 " + label + " id=" + block.blockID
                + " 图标=" + (icon == null ? "null" : icon.getIconName())
                + " 整方块=" + cube + " 固体=" + solid);
    }

    private static void dumpItem(String label, Item item) {
        if (item == null) return;
        Icon icon = item.getIconFromSubtype(0);
        System.out.println("[MITE][诊断] 物品 " + label + " id=" + item.itemID
                + " 图标=" + (icon == null ? "null" : icon.getIconName()));
    }

    private static void dumpStack(String label, ItemStack stack) {
        try {
            Item item = stack.getItem();
            Icon icon = item == null ? null : item.getIconIndex(stack);
            System.out.println("[MITE][诊断] " + label + " -> "
                    + (icon == null ? "null" : icon.getIconName()));
        } catch (Throwable t) {
            System.out.println("[MITE][诊断] " + label + " 异常 " + t);
        }
    }
}
