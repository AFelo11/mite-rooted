package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 离合器：**红石信号 → 动力通/断**。
 *
 * 红石状态**不存 metadata、不存 NBT**，每次判定时现读
 * （KineticTileEntity.isPowered → World.isBlockIndirectlyGettingPowered）。
 * 好处：客户端和服务端天然一致、不需要发包，红石一变立刻生效。
 *
 * 断开时下游会**立刻停**：v2 网络是"收集整分量 → 从动力源点亮"，
 * 断掉的连线点不亮，下游拿不到转速 —— 这一点是批次 0 改成整分量 BFS 白送的。
 */
public class BlockClutch extends BlockKineticBase {

    public BlockClutch(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("clutch", "createmite_blank");
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
