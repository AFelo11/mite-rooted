package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 十字齿轮箱：**一个方块内把转轴掰 90°**。
 *
 * == 接轴规则（逐字复刻 Create）==
 * Create 的 GearboxBlock 只有 AXIS 一个属性，接轴的面完全由它推出来：
 *
 *     hasShaftTowards(dir) { return dir.getAxis() != state.getValue(AXIS); }
 *
 * 也就是：**只在与自身轴垂直的 4 个面接轴**（自身轴那两个面不接）。
 * 所以"哪面进哪面出"根本不需要存状态、不需要发包、也不需要 GUI ——
 * 我们 4 bit 的 metadata（轴向 2 位）完全够用。
 *
 * 例：轴 = Y 时，它在 4 个水平面接轴，自身绕 Y 转；
 * 旁边一根 X 轴的传动轴接上来 → 转速就从"绕 X"变成"绕 Y"。
 */
public class BlockGearbox extends BlockKineticBase {

    public BlockGearbox(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("gearbox", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 13;
    }

    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }
}
