package net.dsh.createmite.kinetics.client;

import net.minecraft.Icon;
import net.minecraft.Tessellator;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Create 的 OBJ 模型（水车 / 大型水车 / 粉碎轮）—— 运行时的网格加载与绘制。
 *
 * 【为什么用二进制资源而不是 Java 源码表】
 * 水车 1276 个三角形、大型水车 2136 个。编成 float[] 字面量会远超 JVM
 * 单方法 64KB 字节码上限（静态初始化块直接爆）。所以由 tools/ObjGen.java
 * 转成 .mesh 放进 assets/createmite/meshes/，运行时读。
 *
 * 【格式】小端：magic("MESH") + triCount + layerCount + 每层一个贴图名
 *        + 每三角形 3×(x,y,z,u,v) + 一个图层号。
 *
 * 【图层对应】.mesh 里记的是**贴图名**（如 create:block/waterwheel_metal），
 * 加载时按名字去 CreateModels.LAYER_NAMES 里找出全局图层号，所以以后加层不用重转模型。
 * 原版那块有个 1.6.4 与 1.20 的命名差异（oak_log vs log_oak），这里手动对一下。
 */
public final class CreateMeshes {

    private CreateMeshes() {}

    /** 一个网格：verts 每 15 个 float 一个三角形（3 顶点 × x,y,z,u,v），layers 是全局图层号 */
    public static final class Mesh {
        public final float[] verts;
        public final int[] layers;

        Mesh(float[] verts, int[] layers) {
            this.verts = verts;
            this.layers = layers;
        }

        public int triCount() {
            return this.layers.length;
        }
    }

    private static final Map<String, Mesh> CACHE = new HashMap<String, Mesh>();

    public static Mesh get(String name) {
        Mesh m = CACHE.get(name);
        if (m != null) return m;
        m = load(name);
        CACHE.put(name, m);
        return m;
    }

    private static Mesh load(String name) {
        InputStream raw = null;
        try {
            raw = CreateMeshes.class.getResourceAsStream("/assets/createmite/meshes/" + name + ".mesh");
            if (raw == null) {
                System.out.println("[CreateMITE] 找不到网格资源 " + name + ".mesh");
                return null;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
            if (in.readInt() != 0x4D455348) {
                System.out.println("[CreateMITE] 网格 " + name + " magic 不对");
                return null;
            }
            int triCount = in.readInt();
            int layerCount = in.readInt();
            int[] localToGlobal = new int[layerCount];
            for (int i = 0; i < layerCount; i++) {
                byte[] buf = new byte[in.readInt()];
                in.readFully(buf);
                localToGlobal[i] = globalLayer(new String(buf, "UTF-8"), name);
            }
            float[] verts = new float[triCount * 15];
            int[] layers = new int[triCount];
            for (int i = 0; i < triCount; i++) {
                for (int k = 0; k < 15; k++) verts[i * 15 + k] = in.readFloat();
                layers[i] = localToGlobal[in.readInt()];
            }
            in.close();

            // 【已撤销缩放】之前试过把大型水车在 X/Y 上缩到 3 格，结果**模型被拉成竖长的椭圆** ✗，
            // 比不缩还难看。所以恢复成 OBJ 原样。要做 3x3 得另想办法（见交接文档）。
            return new Mesh(verts, layers);
        } catch (Throwable t) {
            System.out.println("[CreateMITE] 网格 " + name + " 加载失败: " + t);
            return null;
        } finally {
            try { if (raw != null) raw.close(); } catch (Throwable ignore) { }
        }
    }

    /** OBJ 里的贴图名 → CreateModels.LAYER_NAMES 的下标 */
    private static int globalLayer(String tex, String meshName) {
        String n = tex;
        int colon = n.indexOf(':');
        if (colon >= 0) n = n.substring(colon + 1);       // create:block/axis → block/axis
        if (n.startsWith("block/")) n = n.substring(6);   // block/axis → axis

        // 原版 1.6.4 与 1.20 的命名差异
        if (n.equals("oak_log")) n = "log_oak";
        else if (n.equals("oak_log_top")) n = "log_oak_top";
        else if (n.equals("oak_planks")) n = "planks_oak";
        else if (n.equals("spruce_log_top")) n = "log_spruce_top";

        for (int i = 0; i < CreateModels.LAYER_NAMES.length; i++) {
            if (CreateModels.LAYER_NAMES[i].equals(n)) return i;
        }
        System.out.println("[CreateMITE] 网格 " + meshName + " 里的贴图 " + tex + "（规范化后 " + n
                + "）不在 LAYER_NAMES 里，该三角形会被跳过");
        return -1;
    }

    /**
     * 画一个网格，只画指定图层。
     *
     * @param icons   图集图标（按 LAYER_NAMES 顺序）—— 调用方负责在外层按层 bindTexture
     * @param layer   只画这一层（其余跳过）；传 -1 画全部
     */
    public static void drawLayer(Mesh mesh, Icon[] icons, int layer, Tessellator tess) {
        if (mesh == null) return;
        float[] v = mesh.verts;
        int[] l = mesh.layers;

        // ★ **整层一次提交**。原先每个三角形都 startDrawing/draw，
        //   大型水车 2136 个三角形就是 2136 次提交 —— 卡到没法玩。
        //
        // 【为什么用四边形而不是 GL_TRIANGLES】
        // 试过 startDrawing(4)，世界里画出来是一堆拉长的破面 —— MITE 的 Tessellator
        // 那条路和现有模型走的不一样（现有模型全走 startDrawingQuads，是**确定可用**的）。
        // 所以这里把每个三角形塞进四边形批次：第 4 个顶点复制第 3 个，
        // 得到一个退化四边形，GL 光栅化时会自动忽略退化的那半边，效果等同三角形。
        tess.startDrawingQuads();
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        for (int i = 0; i < l.length; i++) {
            if (l[i] != layer) continue;
            Icon icon = icons != null && layer < icons.length ? icons[layer] : null;
            // 有图集图标就映射到图集子矩形（物品栏那条），否则用模型自带的 0..1 UV（TESR 条）
            float u0 = icon != null ? icon.getMinU() : 0.0F;
            float v0 = icon != null ? icon.getMinV() : 0.0F;
            float su = icon != null ? icon.getMaxU() - icon.getMinU() : 1.0F;
            float sv = icon != null ? icon.getMaxV() - icon.getMinV() : 1.0F;

            int o = i * 15;
            for (int k = 0; k < 4; k++) {          // 第 4 个顶点复用第 3 个 → 退化四边形
                int kk = k < 3 ? k : 2;
                tess.addVertexWithUV(v[o + kk * 5], v[o + kk * 5 + 1], v[o + kk * 5 + 2],
                        u0 + v[o + kk * 5 + 3] * su, v0 + v[o + kk * 5 + 4] * sv);
            }
        }
        tess.draw();
    }
}
