package net.dsh.createmite.kinetics.block;

import net.minecraft.Material;

/**
 * 传动轴：沿轴向传递转速。
 *
 * 外观 = Create 原版 `shaft.json`（一根 6,0,6 → 10,16,10 的 4x4x16 柱），
 * 由 CreateModels.SHAFT 渲染；本体贴图是全透明占位（createmite_blank），
 * 避免 MITE 用 blockIcon + 碰撞箱再画一遍和 TESR 重叠。物品栏由 RenderBlocksMixin 画 3D 模型。
 *
 * 【封装】手持机壳右键 → 变成"安山/黄铜传动杆箱"✓（资料 396859/396860）。
 * 入口统一在 {@link BlockKineticBase#onBlockActivated}（那里有"只有传动杆/齿轮/大齿轮可装壳"的白名单）✓，
 * 本类别再写第二份 onBlockActivated —— 子类会盖住基类，就是这个 bug 的成因 ✗。
 */
public class BlockShaft extends BlockKineticBase {

    public BlockShaft(int blockID) {
        super(blockID, Material.iron, machineConstants());
        this.applyMachineDefaults("shaft", "createmite_blank");
    }

    /** 挖掘粒子的贴图层（见 BlockKineticBase.particleLayer 的说明） */
    @Override
    protected int particleLayer() {
        return 0;
    }
}
