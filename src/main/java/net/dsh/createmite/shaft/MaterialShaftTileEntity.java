package net.dsh.createmite.shaft;

import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 金属传动杆的方块实体 —— **只是个渲染锚点**（TESR 必须有 TE 才能挂 ✓）。
 * 不存任何数据、不 tick 任何逻辑（传动杆是纯合成件 ✓）。
 */
public class MaterialShaftTileEntity extends TileEntity {

    /**
     * ★ 2026-09-30 修用户报的 bug：**离远了整根消失** ✗
     *   五根金属传动杆和动力方块一个套路：**本体贴图透明 + 全靠 TESR 画** ✓，
     *   而原版 `TileEntity.getMaxRenderDistanceSquared()` 只给 **4096 = 64 格** ✗
     *   （`TileEntityRenderer.renderTileEntity` 拿它当闸门 ✓）→ 超了就不画 ⇒ 整根不见 ✓
     *   这里抬到 **64 倍 = 512 格** ✓（和其它动力方块一致 ✓）
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return super.getMaxRenderDistanceSquared() * 64.0D;
    }

    @Override
    public void updateEntity() {
        World world = this.worldObj;
        if (world == null) return;
        if (world.isRemote) {
            // 客户端：保证渲染器注册上了（换世界后渲染器表会被重建 ✓）
            net.dsh.createmite.kinetics.client.KineticRendererHook.ensureRegistered();
        }
    }
}
