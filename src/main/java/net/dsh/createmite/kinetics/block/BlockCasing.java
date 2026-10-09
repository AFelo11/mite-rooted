package net.dsh.createmite.kinetics.block;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.IBlockAccess;
import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.Material;

/**
 * 机壳（安山机壳 / 黄铜机壳）—— 装饰性外壳方块。
 *
 * == 为什么直接继承 Block，而不是 BlockKineticBase ==
 * 机壳**不是机器**：它自己不转、不参与动力网络、不需要方块实体。
 * 继承 BlockKineticBase 会自动带一个 KineticTileEntity → 每 tick 跑动力网络、
 * 还会被 KineticHelper 当成元件认下来（凭空多出网络成员）✗。
 * 所以和占位方块同样的做法：继承最朴素的 Block，网络完全看不见它 ✓。
 *
 * == 资料对照（mcmod 227807 / 227809）==
 * 原版机壳三条用途：① 可放置的建筑方块（**材质会相连** ✓）；② 对传动杆/传送带右键"封装"；
 * ③ 对齿轮"装壳"后可用扳手关闭某一面的连接。本类负责 ① ✓。
 *
 * ============================ 材质相连（47 格 CT，2026-09-27）============================
 * 【原版做法】Create 用 CT（Connected Textures）：机壳贴图配一张 128×128 的
 * {@code *_casing_connected.png} = **8×8 个 16×16 子格 = 标准 47 格图集**（只有 47 格有内容），
 * 由 {@code CasingConnectivity} / {@code EncasedCTBehaviour} 按 8 邻居算上下文，再取对应子格。
 * 实测规则：基础贴图 = 木纹中心 + 四周各 2px 灰框；**哪条边相邻的是同种机壳，那条边的灰框就被木纹顶掉** ✓
 * （图集 idx0 = 四边都不连，与 andesite_casing.png **逐像素完全一致** ✓ 已验证）。
 *
 * 【1.6.4 怎么落地】MITE 保留了原版通路：{@code RenderBlocks.getBlockIcon(Block,IBlockAccess,…)}
 * → {@code Block.getBlockTexture(IBlockAccess,x,y,z,side)} ✓（字节码实证），
 * 所以**不用改成 TESR**（TESR 得手工算光照，机壳墙会"发死" ✗）。
 * 做法 = 把原版图集的 47 个子格**原样裁成 47 张独立贴图**（{@code <材质>_casing_t<idx>.png}，
 * 裁图脚本 {@code _analysis\tools\cut-casing-tiles.ps1}），运行时按同样的算法算 idx 选一张 ✓。
 *
 * 【算法是逐行照搬 Create 的】{@code ConnectedTextureBehaviour.buildContext} +
 * {@code AllCTTypes.OMNIDIRECTIONAL.getTextureIndex}。**不要自己推 UV 绕序** ✗ ——
 * 图集与公式是一套自洽的东西，照搬才不会再出镜像反了的错。
 */
public class BlockCasing extends Block {

    // ===================== 方向常量（MC 约定，与 KineticHelper 一致）=====================
    private static final int D_DOWN = 0, D_UP = 1, D_NORTH = 2, D_SOUTH = 3, D_WEST = 4, D_EAST = 5;
    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** 反方向（0<->1、2<->3、4<->5） */
    private static final int[] OPPOSITE = {1, 0, 3, 2, 5, 4};
    /** 每个方向属于哪个轴（0=X 1=Y 2=Z） */
    private static final int[] AXIS_OF = {1, 1, 2, 2, 0, 0};

    /**
     * 原版图集里**真实存在**的 47 个子格索引（其余 17 格是全透明的空位）。
     * 只注册这 47 张 —— 否则会去注册不存在的贴图、日志里一堆 "Resource not found" ✗。
     */
    private static final int[] VALID_TILES = {
        0, 1, 2, 3, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19, 20, 21,
        24, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35, 36, 37, 38,
        40, 41, 42, 43, 44, 45, 46, 48, 49, 50, 51, 52, 53, 54, 56, 57, 58
    };

    private final String texName;
    /** 64 个图集槽位（下标 = idx = tileX + 8*tileY），只有 VALID_TILES 里的那些非 null */
    private final Icon[] variants = new Icon[64];

    public BlockCasing(int blockID, String unlocalizedName, String textureName) {
        super(blockID, Material.iron, new BlockConstants());
        this.texName = textureName;

        // 与机器方块同一套硬度/挖掘等级（BlockKineticBase.applyMachineDefaults 的数值）✓
        this.setHardness(BlockKineticBase.HARDNESS_PER_BLOCK);
        this.setMinHarvestLevel(BlockKineticBase.MIN_HARVEST_LEVEL);
        this.setStepSound(Block.soundMetalFootstep);
        this.setUnlocalizedName(unlocalizedName);
        this.setTextureName(textureName);
        this.setCreativeTab(CreativeTabs.tabBlock);
    }

    /**
     * 注册"47 张连通变体"。
     * 基础贴图仍然进 {@code blockIcon}（物品栏图标、挖掘粒子用它 ✓）；
     * 世界渲染一律走 {@link #getBlockTexture} ✓。
     */
    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);
        for (int i = 0; i < VALID_TILES.length; i++) {
            int idx = VALID_TILES[i];
            this.variants[idx] = register.registerIcon(this.texName + "_t" + idx);
        }
    }

    // ===================== 多方块熔炉的成型通知（2026-09-30）=====================
    // 机壳也是熔炉结构的一部分（27 格里的 20 格），所以它的放置/破坏都可能
    // "搭完最后一格"或者"拆掉一格" —— 两种情况都要让旁边的核心重新判定 ✓
    // 判定本身在核心 TE 的下一 tick 做，这里只打标记 ✓

    @Override
    public void onBlockAdded(net.minecraft.World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
    }

    @Override
    public void breakBlock(net.minecraft.World world, int x, int y, int z, int blockID, int meta) {
        net.dsh.createmite.furnace.FurnaceMultiblock.onChange(world, x, y, z);
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /** 右键机壳 = 打开这台大熔炉的界面（顶/底面不开 ✓、没成型不开 ✓）*/
    @Override
    public boolean onBlockActivated(net.minecraft.World world, int x, int y, int z,
                                    net.minecraft.EntityPlayer player, net.minecraft.EnumFace face,
                                    float hitX, float hitY, float hitZ) {
        net.dsh.createmite.furnace.FurnaceMultiblock.tryOpenUi(world, x, y, z, player, face);
        return false;
    }

    /** 邻居是不是同一种机壳（原版：{@code state.getBlock() == other.getBlock()} ✓） */
    private boolean same(IBlockAccess access, int x, int y, int z) {
        return access.getBlockId(x, y, z) == this.blockID;
    }

    /**
     * **按坐标选图标**（1.6.4 原版通路：{@code RenderBlocks.getBlockIcon} 会调这里 ✓）。
     *
     * 流程完全照搬原版：
     *  ① {@code buildContext}：算出这个面的"贴图上下左右"分别对应哪个世界方向，
     *     再沿这 4 个方向各看一格（角上还要"两条边都连"才算连角 ✓）；
     *  ② {@code OMNIDIRECTIONAL.getTextureIndex}：8 个布尔 → 图集子格索引；
     *  ③ 取那一张变体贴图 ✓。
     */
    @Override
    public Icon getBlockTexture(IBlockAccess access, int x, int y, int z, int side) {
        if (side < 0 || side >= 6 || this.variants[0] == null) return this.blockIcon;

        // ---- ① 复刻 ConnectedTextureBehaviour.buildContext ----
        // getRightDirection：轴为 X 的面 → SOUTH，其余 → WEST
        // getUpDirection  ：水平面（轴 X/Z）→ UP，竖直面（轴 Y）→ NORTH
        boolean positive = (side == D_UP || side == D_SOUTH || side == D_EAST);
        int h = (AXIS_OF[side] == 0) ? D_SOUTH : D_WEST;
        int v = (AXIS_OF[side] != 1) ? D_UP : D_NORTH;
        if (positive) h = OPPOSITE[h];
        if (side == D_DOWN) { v = OPPOSITE[v]; h = OPPOSITE[h]; }
        // EncasedCTBehaviour → Base 都没有覆写 reverseUVs* → sh = sv = 1 ✓

        boolean up    = this.same(access, x + DX[v], y + DY[v], z + DZ[v]);
        boolean down  = this.same(access, x - DX[v], y - DY[v], z - DZ[v]);
        boolean left  = this.same(access, x - DX[h], y - DY[h], z - DZ[h]);
        boolean right = this.same(access, x + DX[h], y + DY[h], z + DZ[h]);
        // 角：必须**相邻两条边都连**才算（原版就是这么写的 ✓），否则图集里根本没有对应格
        boolean topLeft     = up && left
                && this.same(access, x + DX[v] - DX[h], y + DY[v] - DY[h], z + DZ[v] - DZ[h]);
        boolean topRight    = up && right
                && this.same(access, x + DX[v] + DX[h], y + DY[v] + DY[h], z + DZ[v] + DZ[h]);
        boolean bottomLeft  = down && left
                && this.same(access, x - DX[v] - DX[h], y - DY[v] - DY[h], z - DZ[v] - DZ[h]);
        boolean bottomRight = down && right
                && this.same(access, x - DX[v] + DX[h], y - DY[v] + DY[h], z - DZ[v] + DZ[h]);

        // ---- ②③ 取子格 ----
        int idx = textureIndex(up, down, left, right, topLeft, topRight, bottomLeft, bottomRight);
        Icon icon = (idx >= 0 && idx < 64) ? this.variants[idx] : null;
        return icon != null ? icon : this.blockIcon;
    }

    /**
     * 逐行照搬原版 {@code AllCTTypes.OMNIDIRECTIONAL.getTextureIndex}（8×8 图集，47 格）。
     *
     * 规则回顾：前两位 bit 按"哪几条边连着"定基准格；四边全连时再用**角**微调；
     * 只有一条边没连时用第 4~7 行那批"缺一条边"的专用格；恰好两条边没连时再挪 3 格 ✓。
     * **不要"优化"这段** —— 它和原版图集的排布是一一对应的，改了就取错格 ✗。
     */
    private static int textureIndex(boolean up, boolean down, boolean left, boolean right,
                                    boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
        int tileX = 0, tileY = 0;
        int borders = (up ? 0 : 1) + (down ? 0 : 1) + (left ? 0 : 1) + (right ? 0 : 1);

        if (up) tileX++;
        if (down) tileX += 2;
        if (left) tileY++;
        if (right) tileY += 2;

        if (borders == 0) {
            if (topRight) tileX++;
            if (topLeft) tileX += 2;
            if (bottomRight) tileY += 2;
            if (bottomLeft) tileY++;
        }

        if (borders == 1) {
            if (!right) {
                if (topLeft || bottomLeft) {
                    tileY = 4;
                    tileX = -1 + (bottomLeft ? 1 : 0) + (topLeft ? 1 : 0) * 2;
                }
            }
            if (!left) {
                if (topRight || bottomRight) {
                    tileY = 5;
                    tileX = -1 + (bottomRight ? 1 : 0) + (topRight ? 1 : 0) * 2;
                }
            }
            if (!down) {
                if (topLeft || topRight) {
                    tileY = 6;
                    tileX = -1 + (topLeft ? 1 : 0) + (topRight ? 1 : 0) * 2;
                }
            }
            if (!up) {
                if (bottomLeft || bottomRight) {
                    tileY = 7;
                    tileX = -1 + (bottomLeft ? 1 : 0) + (bottomRight ? 1 : 0) * 2;
                }
            }
        }

        if (borders == 2) {
            if ((up && left && topLeft) || (down && left && bottomLeft)
                    || (up && right && topRight) || (down && right && bottomRight)) {
                tileX += 3;
            }
        }

        return tileX + 8 * tileY;
    }
}
