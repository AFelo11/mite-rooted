package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 水车（Create 的 water_wheel）：**被流动的水推着转**的流体动力源。
 *
 * 【和 Create 的对应】原版水车 8 RPM、应力容量 256；大型水车 4 RPM、容量 512。
 * 这里同样：转速慢但**力大**，是前期最实用的动力源（手摇曲柄只有 8 容量）。
 *
 * 【判定"有没有水"】周围 3x3x3（除自己）里只要有水就算被推着。
 * 每 10 tick 扫一次，不是每 tick，免得 26 次 getBlockMaterial 变成性能负担。
 *
 * 外观 = Create water_wheel.obj（已转成 assets/createmite/meshes/water_wheel.mesh）
 * 本体立方体用全透明占位，真正的样子由 TESR 画网格。
 */
public class BlockWaterWheel extends BlockKineticBase {

    public BlockWaterWheel(int blockID) {
        super(blockID, Material.wood, machineConstants());
        this.applyMachineDefaults("water_wheel", "createmite_blank");
    }

    /** 挖掘粒子用原木那层（= CreateModels.LAYER_NAMES 的 21） */
    @Override
    protected int particleLayer() {
        return 21;
    }

    /** 水车横跨 2x2 格（模型 X/Z 从 -0.5 到 1.5），选取/碰撞箱给整格最稳 */
    @Override
    protected void setBoundsForAxis(int axis) {
        this.setBlockBoundsForCurrentThread(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }
}
