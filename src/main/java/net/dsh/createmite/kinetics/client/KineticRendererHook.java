package net.dsh.createmite.kinetics.client;

import net.dsh.createmite.kinetics.KineticTileEntity;
import net.dsh.createmite.kinetics.tile.MillstoneTileEntity;
import net.minecraft.TileEntityRenderer;
import net.minecraft.TileEntitySpecialRenderer;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 渲染器注册。
 *
 * 背景：FML 的 TileEntityRendererRegisterEvent 在本实例里没有触发（实测），
 * 因此改为反射把渲染器直接放进 TileEntityRenderer.specialRendererMap。
 *
 * 两个必须注意的点（都是踩过的坑）：
 *   1) 必须调用 renderer.setTileEntityRenderer(instance) 回填反向引用，
 *      否则 TileEntitySpecialRenderer.bindTexture() 里 this.tileEntityRenderer 为 null → 渲染时 NPE 崩溃；
 *   2) 注册要做成"每次检查、缺了就补"，因为 TileEntityRenderer 实例可能被重建（换世界等），
 *      一次性注册会在重建后失效。
 */
public final class KineticRendererHook {

    private static TileEntitySpecialRenderer ours;
    /** 3x3x3 大熔炉整机的渲染器（挂在核心的方块实体上） */
    private static TileEntitySpecialRenderer bigFurnace;
    /** 五根金属传动杆的渲染器（纯合成件 ✓） */
    private static TileEntitySpecialRenderer shaftRenderer;

    private KineticRendererHook() {}

    @SuppressWarnings("unchecked")
    public static void ensureRegistered() {
        try {
            TileEntityRenderer rendererInstance = TileEntityRenderer.instance;
            if (rendererInstance == null) return;

            Field field = TileEntityRenderer.class.getDeclaredField("specialRendererMap");
            field.setAccessible(true);
            Map<Class<?>, TileEntitySpecialRenderer> map =
                    (Map<Class<?>, TileEntitySpecialRenderer>) field.get(rendererInstance);

            // ★★ 营火整机渲染器（2026-10-07 ✓ 少了这一段 ⇒ 方块是隐形的（createmite_blank）+ 没人画模型 ⇒ 看着像空气 ✓）
            try {
                TileEntitySpecialRenderer cf = map.get(net.dsh.createmite.campfire.TileCampfire.class);
                boolean cfNew = false;
                if (!(cf instanceof net.dsh.createmite.campfire.CampfireRenderer)) {
                    cf = new net.dsh.createmite.campfire.CampfireRenderer();
                    cfNew = true;
                }
                cf.setTileEntityRenderer(rendererInstance);
                map.put(net.dsh.createmite.campfire.TileCampfire.class, cf);
                // ⚠️ 这里每帧都会被调到 ⇒ 只在真的新注册时打日志 ✓（否则一天刷几万行 ✗）
                if (cfNew) System.out.println("[CreateMITE] 营火渲染器已注册 ✓");
            } catch (Throwable t) {
                System.out.println("[CreateMITE] 营火渲染器注册失败: " + t);
            }

            // ★ 大熔炉整机渲染器要先检查/补上：下面那个"已注册就 return"会让它永远轮不到 ✗
            // ★ 金属传动杆渲染器（纯合成件 ✓，同样回填反向引用 ✓）
            TileEntitySpecialRenderer existingShaft = map.get(net.dsh.createmite.shaft.MaterialShaftTileEntity.class);
            if (!(existingShaft instanceof net.dsh.createmite.shaft.client.MaterialShaftRenderer)) {
                if (shaftRenderer == null) {
                    shaftRenderer = new net.dsh.createmite.shaft.client.MaterialShaftRenderer();
                }
                existingShaft = shaftRenderer;
            }
            existingShaft.setTileEntityRenderer(rendererInstance);
            map.put(net.dsh.createmite.shaft.MaterialShaftTileEntity.class, existingShaft);

            TileEntitySpecialRenderer existingBig = map.get(net.dsh.createmite.furnace.FurnaceCoreTileEntity.class);
            if (!(existingBig instanceof FurnaceBigRenderer)) {
                if (bigFurnace == null) {
                    bigFurnace = new FurnaceBigRenderer();
                }
                existingBig = bigFurnace;
            }
            // ★ 无论是事件注册进来的还是这里 new 的，都**回填一次反向引用** ✓
            //   （少了这一步，bindTexture 里 this.tileEntityRenderer 为 null → 渲染时 NPE ✗）
            existingBig.setTileEntityRenderer(rendererInstance);
            map.put(net.dsh.createmite.furnace.FurnaceCoreTileEntity.class, existingBig);

            TileEntitySpecialRenderer existing = map.get(KineticTileEntity.class);
            if (existing instanceof KineticRenderer) {
                return;   // 已经注册且是我们自己的渲染器
            }

            if (ours == null) {
                ours = new KineticRenderer();
            }
            ours.setTileEntityRenderer(rendererInstance);   // ★ 关键：回填反向引用，避免 bindTexture NPE
            map.put(KineticTileEntity.class, ours);
            map.put(MillstoneTileEntity.class, ours);
            System.out.println("[CreateMITE] 动力渲染器已注册（反射注入），当前渲染器表大小 " + map.size());
        } catch (Throwable t) {
            System.out.println("[CreateMITE] 渲染器注册失败: " + t);
        }
    }
}
