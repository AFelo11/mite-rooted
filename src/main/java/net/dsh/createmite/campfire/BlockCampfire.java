package net.dsh.createmite.campfire;

import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.CreativeTabs;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumFace;
import net.minecraft.Icon;
import net.minecraft.IconRegister;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.TileEntity;
import net.minecraft.World;

/** 营火核心（3x3 多方块里带 TE 的那一格 ✓ 熄灭/燃烧两个实例 ✓）*/
public class BlockCampfire extends Block implements net.minecraft.ITileEntityProvider {

    public static final int ID_CORE = 2301;
    public static final int ID_CORE_LIT = 2302;
    private static final int ID_FLINT_AND_STEEL = 259;
    private static BlockCampfire UNLIT;
    private static BlockCampfire LIT;

    /**
     * ★ 熄灭 <-> 燃烧 是换方块 id（2301 <-> 2302 ✓ 亮度只能注册期定 ✓）
     *   但换 id 会让 MITE 对新方块调 breakBlock ⇒ 会被当成「玩家挖掉了」⇒ 整机自毁 ✗✗
     *   ⇒ 换 id 期间把这个闩打开 ✓ breakBlock 里的拆除逻辑跳过 ✓（2026-10-07 修「点火后整台消失」✓）
     */
    private static boolean SWAPPING = false;

    public static void beginStateSwap() { SWAPPING = true; }
    public static void endStateSwap() { SWAPPING = false; }

    private final boolean lit;
    private final String texBase;
    private Icon iconLog;
    private Icon iconFire;

    public BlockCampfire(int blockID, boolean lit) {
        super(blockID, Material.stone, new BlockConstants().setNeverHidesAdjacentFaces());
        this.lit = lit;
        this.texBase = lit ? "campfire_log_lit" : "campfire_log";
        this.setHardness(2.0F);
        this.setResistance(2.0F);
        this.setStepSound(Block.soundWoodFootstep);
        this.setUnlocalizedName(lit ? "campfireLit" : "campfire");
        this.setTextureName("createmite_blank");
        this.setCreativeTab(CreativeTabs.tabBlock);
        // 碰撞箱跟着模型走 ✓（A 方案木头加厚到 0.875 格 ⇒ 不能再用 7/16 ✗ 否则从原木里穿过去 ✓）
        this.setBlockBoundsForCurrentThread(0.0F, 0.0F, 0.0F, 1.0F, 14.0F / 16.0F, 1.0F);
        if (lit) this.setLightValue(0.9375F);
    }

    public static void setInstances(BlockCampfire u, BlockCampfire l) { UNLIT = u; LIT = l; }
    public static BlockCampfire unlit() { return UNLIT; }
    public static BlockCampfire lit() { return LIT; }
    public boolean isLit() { return lit; }

    @Override
    public void registerIcons(IconRegister register) {
        super.registerIcons(register);
        this.iconLog = register.registerIcon(this.texBase);
        this.iconFire = register.registerIcon("campfire_fire");
    }

    public Icon iconLog() { return iconLog; }
    public Icon iconFire() { return iconFire; }

    @Override
    public TileEntity createNewTileEntity(World world) { return new TileCampfire(); }

    /**
     * ★ 挖掉**任何一格** ⇒ 整机 3x3 全部消失 ✓ **什么都不掉落** ✓（用户 2026-10-07 定稿 ✓）
     *   ⚠️ 拆除**不能当场做**（在 breakBlock 里改方块会和区块写回打架 ⇒ 留一堆看不见的占位方块 ✗）
     *      ⇒ 只登记，下一 tick 由 CMHeat 的周期扫描执行 ✓
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, int blockID, int meta) {
        if (!SWAPPING) {
            try { world.removeBlockTileEntity(x, y, z); } catch (Throwable ignored) { }
            try { CampfireForm.demolishLater(world, x, y, z, true); } catch (Throwable t) { System.out.println("[CreateMITE][CAMPFIRE] 拆除登记失败: " + t); }
        }
        super.breakBlock(world, x, y, z, blockID, meta);
    }

    /** 燃烧态的火苗 / 烟（原版篝火的手感 ✓ 只画不判定 ✓）*/
    @Override
    public void randomDisplayTick(World world, int x, int y, int z, java.util.Random rand) {
        if (!lit) return;
        try {
            if (rand.nextInt(24) == 0) {
                world.spawnParticle(net.minecraft.EnumParticle.largesmoke,
                        x + 0.5D + (rand.nextDouble() - 0.5D) * 0.9D, y + 1.0D,
                        z + 0.5D + (rand.nextDouble() - 0.5D) * 0.9D, 0.0D, 0.02D, 0.0D);
            }
            if (rand.nextInt(3) == 0) {
                double dx = (rand.nextDouble() - 0.5D) * 2.4D;
                double dz = (rand.nextDouble() - 0.5D) * 2.4D;
                world.spawnParticle(net.minecraft.EnumParticle.flame,
                        x + 0.5D + dx, y + 0.35D, z + 0.5D + dz, 0.0D, 0.01D, 0.0D);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    EnumFace face, float hx, float hy, float hz) {
        if (world == null) return false;
        if (world.isRemote) { net.dsh.createmite.CMCampfireBridge.forward(player, x, y, z); return true; }
        return useOnServer(world, x, y, z, player);
    }

    public static boolean useOnServer(World world, int x, int y, int z, EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return false;
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!(te instanceof TileCampfire)) return false;
        TileCampfire cf = (TileCampfire) te;
        ItemStack held = null;
        try { held = player.getHeldItemStack(); } catch (Throwable ignored) { }
        if (held == null) return false;
        if (cf.isBurning(world)) {
            if (!CampfireFuel.isWood(held)) return false;
            if (!cf.addWood(world)) return false;
            try { if (held.stackSize > 1) held.stackSize--; else player.setHeldItemStack(null); } catch (Throwable ignored) { }
            try { world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "step.wood", 0.7F, 1.0F); } catch (Throwable ignored) { }
            System.out.println("[CreateMITE][CAMPFIRE] addWood " + x + "," + y + "," + z + " wood=" + cf.woodAdded());
            return true;
        }
        if (!isFlint(held)) return false;
        cf.ignite(world);
        try {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "fire.ignite", 1.0F, 1.0F);
            for (int i = 0; i < 6; i++) {
                world.spawnParticle(net.minecraft.EnumParticle.flame,
                        x + 0.5D + (world.rand.nextDouble() - 0.5D) * 1.8D, y + 0.4D,
                        z + 0.5D + (world.rand.nextDouble() - 0.5D) * 1.8D, 0.0D, 0.02D, 0.0D);
            }
            world.spawnParticle(net.minecraft.EnumParticle.smoke, x + 0.5D, y + 0.9D, z + 0.5D, 0.0D, 0.01D, 0.0D);
        } catch (Throwable ignored) { }
        System.out.println("[CreateMITE][CAMPFIRE] ignite " + x + "," + y + "," + z);
        return true;
    }

    private static boolean isFlint(ItemStack stack) {
        if (stack == null) return false;
        try {
            if (stack.itemID == ID_FLINT_AND_STEEL) return true;
            Item it = stack.getItem();
            if (it == null) return false;
            String n = String.valueOf(it.getUnlocalizedName()).toLowerCase();
            return n.contains("flintandsteel") || n.contains("firestarter");
        } catch (Throwable t) { return false; }
    }
}
