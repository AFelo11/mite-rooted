package net.dsh.createmite.kinetics.block;

import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.Material;

/**
 * 齿轮：与相邻齿轮啮合换向。
 *
 * 【渲染分工 / 两个踩过的坑】
 *  1) 世界里：只由 KineticRenderer（TESR）画真正的 3D 齿轮（木纹齿 + 中间传动轴柱）。
 *     MITE 还会用 blockIcon + 碰撞箱（setBoundsForAxis 的 4x4 柱）额外画一遍"方块本体"，
 *     和 TESR 的轴柱完全重合、互相闪烁 —— 表现就是"中间多出一根木纹柱子"。
 *     解决办法：把本体贴图设成全透明的 createmite_blank（配合 alpha test 直接丢弃，
 *     不写深度），于是本体等于没画，剩下的就是 TESR 的模型。
 *  2) 背包/手持：保留 renderType=0（默认），这样 ItemRenderer 会走 3D 分支调用
 *     RenderBlocks.renderBlockAsItem，由 RenderBlocksMixin 接管画成 3D 齿轮
 *     （与 Create 一样在物品栏里是立体模型，而不是一张平面图）。
 *     注意：**不能**把 renderType 设成 -1 —— 那样物品会退回 16x16 平面图标。
 */
public class BlockCogwheel extends BlockKineticBase {

    private Icon iconGear;
    private Icon iconAxisSide;
    private Icon iconAxisTop;
    private Icon iconShaftSide;
    private Icon iconShaftTop;

    public BlockCogwheel(int blockID) {
        super(blockID, Material.iron, machineConstants());
        // 本体立方体使用全透明贴图（真正的样子由 TESR 画）
        this.applyMachineDefaults("cogwheel", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 6;
    }

    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);                       // createmite_blank -> blockIcon（透明）
        this.iconGear = register.registerIcon("cogwheel");
        this.iconAxisSide = register.registerIcon("cogwheel_axis");
        this.iconAxisTop = register.registerIcon("axis_top");
        this.iconShaftSide = register.registerIcon("shaft");
        this.iconShaftTop = register.registerIcon("shaft_top");
    }

    public Icon getGearIcon() { return this.iconGear; }

    public Icon getAxisSideIcon() { return this.iconAxisSide; }

    public Icon getAxisTopIcon() { return this.iconAxisTop; }

    public Icon getShaftSideIcon() { return this.iconShaftSide; }

    public Icon getShaftTopIcon() { return this.iconShaftTop; }

    // 【不要重写 getItemIconName()】
    // 一旦重写，ItemBlock 会把图标注册进"物品图集"，ItemStack.getSpriteNumber() 变成 1，
    // 而 ItemRenderer.renderItem 的 3D 分支要求 spriteNumber == 0，
    // 于是物品栏会退化成一张 16x16 平面图（而不是 Create 那样的 3D 模型）。
    // 保持为 null，物品就作为普通方块物品走 3D 渲染，由 RenderBlocksMixin 画齿轮模型。
}
