package net.dsh.createmite.campfire;

import net.dsh.createmite.CMConfig;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;

/** 加木材的判定：木制品全收 ✓ 带金属/燧石/宝石柄的工具武器一律不行 ✓（用户定稿 ✓）*/
public final class CampfireFuel {

    private static final Material[] HARD = {
        Material.iron, Material.copper, Material.silver, Material.gold,
        Material.mithril, Material.adamantium, Material.ancient_metal,
        Material.rusted_iron, Material.flint, Material.obsidian,
        Material.diamond, Material.emerald, Material.quartz, Material.anvil,
    };

    private CampfireFuel() {}

    public static boolean isWood(ItemStack stack) {
        if (stack == null) return false;
        Item item;
        try { item = stack.getItem(); } catch (Throwable t) { return false; }
        if (item == null) return false;
        try { if (item.getBurnTime(stack) <= 0) return false; } catch (Throwable t) { return false; }
        try { for (int i = 0; i < HARD.length; i++) if (item.hasMaterial(HARD[i])) return false; } catch (Throwable ignored) { }
        return true;
    }

    public static int igniteMinutes() { return Math.max(1, (int) CMConfig.getFloat("campfire.burn_minutes", 3.0F)); }
    public static int woodMinutes() { return Math.max(1, (int) CMConfig.getFloat("campfire.wood_minutes", 1.0F)); }
    public static int maxWood() { return Math.max(1, (int) CMConfig.getFloat("campfire.max_wood", 9.0F)); }
}
