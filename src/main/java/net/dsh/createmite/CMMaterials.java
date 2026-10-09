package net.dsh.createmite;

import net.minecraft.Material;

/**
 * 本模组新增的材料。
 * MITE 的 Material(String) 构造器会自动把新材料登记进 Material.materials[]，
 * durability 决定由 ItemIngot 推导出的"合成难度"(= durability * 100)。
 */
public final class CMMaterials {

    /** 锌：比铜略软（铜锭难度 400，锌锭定为 300） */
    public static Material zinc;

    /** 黄铜：铜锌合金，与铜同级（难度 400） */
    public static Material brass;

    private CMMaterials() {}

    public static void register() {
        if (zinc != null) return;
        zinc = new Material("zinc").setDurability(3.0F);
        brass = new Material("brass").setDurability(4.0F);
        System.out.println("[CreateMITE] 材料已注册: zinc / brass");
    }
}
