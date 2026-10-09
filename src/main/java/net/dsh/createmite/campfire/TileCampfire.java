package net.dsh.createmite.campfire;

import net.minecraft.Block;
import net.minecraft.NBTTagCompound;
import net.minecraft.TileEntity;
import net.minecraft.World;

/**
 * 营火方块实体：燃烧计时 ＋ 已加木材数 ＋ 核心格坐标 ✓
 *   ⚠️ 计时真身放在 CampfireClock 静态表里（换方块 id 会重建 TE ⇒ 放 TE 里会丢 ✗）
 *      TE 上的字段只是 NBT 快照 ✓
 */
public class TileCampfire extends TileEntity {

    private long burnUntil = 0L;
    private int woodAdded = 0;
    private int cornerX = Integer.MIN_VALUE;
    private int cornerY = Integer.MIN_VALUE;
    private int cornerZ = Integer.MIN_VALUE;

    public void setCorner(int x, int y, int z) { cornerX = x; cornerY = y; cornerZ = z; }
    public int cornerX() { return cornerX; }
    public int cornerY() { return cornerY; }
    public int cornerZ() { return cornerZ; }

    // ---------------------------------------------------------------- 计时读取

    private long until(World w) {
        long[] v = CampfireClock.get(w, xCoord, yCoord, zCoord);
        return v == null ? burnUntil : v[0];
    }

    public int woodAdded() {
        long[] v = CampfireClock.get(worldObj, xCoord, yCoord, zCoord);
        return v == null ? woodAdded : (int) v[1];
    }

    // ---------------------------------------------------------------- 点燃 / 加柴

    public void ignite(World world) {
        if (world == null || world.isRemote) return;
        long until = world.getTotalWorldTime() + (long) CampfireFuel.igniteMinutes() * 1200L;
        CampfireClock.put(world, xCoord, yCoord, zCoord, until, 0);
        this.burnUntil = until;
        this.woodAdded = 0;
        swapSelf(world, BlockCampfire.lit());
    }

    public boolean addWood(World world) {
        if (world == null || world.isRemote) return false;
        long[] v = CampfireClock.get(world, xCoord, yCoord, zCoord);
        long until = v == null ? burnUntil : v[0];
        int wood = v == null ? woodAdded : (int) v[1];
        if (wood >= CampfireFuel.maxWood()) return false;
        wood++;
        until += (long) CampfireFuel.woodMinutes() * 1200L;
        CampfireClock.put(world, xCoord, yCoord, zCoord, until, wood);
        this.burnUntil = until;
        this.woodAdded = wood;
        return true;
    }

    public boolean isBurning(World world) {
        if (world == null || BlockCampfire.lit() == null) return false;
        if (world.getBlockId(xCoord, yCoord, zCoord) != BlockCampfire.lit().blockID) return false;
        return until(world) > world.getTotalWorldTime();
    }

    // ---------------------------------------------------------------- 每 tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        try { CampfireForm.processDemolish(worldObj); } catch (Throwable ignored) { }
        if (BlockCampfire.lit() == null || BlockCampfire.unlit() == null) return;
        if (worldObj.getBlockId(xCoord, yCoord, zCoord) != BlockCampfire.lit().blockID) return;
        long now = worldObj.getTotalWorldTime();
        long until;
        long[] v = CampfireClock.get(worldObj, xCoord, yCoord, zCoord);
        if (v != null) {
            until = v[0];
        } else if (burnUntil > now) {
            until = burnUntil;                                   // 读档捡回来 ✓
            CampfireClock.put(worldObj, xCoord, yCoord, zCoord, until, woodAdded);
        } else {
            until = now + (long) CampfireFuel.igniteMinutes() * 1200L;   // 状态真丢了 ⇒ 重新计时 ✓ 不立刻熄灭 ✓
            CampfireClock.put(worldObj, xCoord, yCoord, zCoord, until, 0);
        }
        if (now < until) return;
        CampfireClock.clear(worldObj, xCoord, yCoord, zCoord);
        this.burnUntil = 0L;
        this.woodAdded = 0;
        swapSelf(worldObj, BlockCampfire.unlit());
        try { worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "random.fizz", 0.6F, 1.4F); } catch (Throwable ignored) { }
    }

    /**
     * ★ 熄灭 <-> 燃烧：换方块 id（亮度只能注册期定 ✓）
     *   换 id 期间必须开 BlockCampfire 的「正在换」闩 ✓ 否则会被当成玩家挖掉 ⇒ 整机自毁 ✗
     *   换完立刻读回实际 id / TE 打在日志里 ✓（诊断用 ✓）
     */
    private void swapSelf(World world, Block target) {
        if (target == null) return;
        NBTTagCompound saved = new NBTTagCompound();
        try { writeToNBT(saved); } catch (Throwable ignored) { }
        BlockCampfire.beginStateSwap();
        try { world.setBlock(xCoord, yCoord, zCoord, target.blockID); } catch (Throwable ignored) { }
        finally { BlockCampfire.endStateSwap(); }
        try { world.markBlockForUpdate(xCoord, yCoord, zCoord); } catch (Throwable ignored) { }
        try {
            TileEntity now = world.getBlockTileEntity(xCoord, yCoord, zCoord);
            if (now instanceof TileCampfire && now != this) {
                ((TileCampfire) now).readFromNBT(saved);
            } else {
                TileEntity fresh = new TileCampfire();
                fresh.readFromNBT(saved);
                fresh.xCoord = xCoord; fresh.yCoord = yCoord; fresh.zCoord = zCoord;
                world.setBlockTileEntity(xCoord, yCoord, zCoord, fresh);
                fresh.validate();
            }
        } catch (Throwable t) { System.out.println("[CreateMITE][CAMPFIRE] swap TE 失败: " + t); }
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        long[] v = CampfireClock.get(worldObj, xCoord, yCoord, zCoord);
        n.setLong("cmBurnUntil", v != null ? v[0] : burnUntil);
        n.setInteger("cmWood", v != null ? (int) v[1] : woodAdded);
        n.setInteger("cmCX", cornerX);
        n.setInteger("cmCY", cornerY);
        n.setInteger("cmCZ", cornerZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        burnUntil = n.getLong("cmBurnUntil");
        woodAdded = n.getInteger("cmWood");
        cornerX = n.hasKey("cmCX") ? n.getInteger("cmCX") : Integer.MIN_VALUE;
        cornerY = n.hasKey("cmCY") ? n.getInteger("cmCY") : Integer.MIN_VALUE;
        cornerZ = n.hasKey("cmCZ") ? n.getInteger("cmCZ") : Integer.MIN_VALUE;
    }
}
