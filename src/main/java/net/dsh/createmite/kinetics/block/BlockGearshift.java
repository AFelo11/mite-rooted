package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 反转齿轮箱：**红石信号 → 输出反向**。
 *
 * 和离合器一样，红石状态现读不存盘。
 * 取反写在 KineticHelper.transfer 里：由它驱动下游时把乘数取负。
 */
public class BlockGearshift extends BlockKineticBase {

    public BlockGearshift(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("gearshift", "createmite_blank");
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
