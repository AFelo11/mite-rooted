package net.dsh.createmite;

import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.NBTTagCompound;
import net.minecraft.World;

/**
 * ★★ 「放着会自己变」的共享倒计时（2026-10-07 从 ItemBowlDrink 抽出来 ✓）
 *
 *   用它的东西：热水碗 ✓ 温水碗 ✓ 热苹果派 ✓ 热巧克力奶 ✓（以后还有谁要降温都接这里 ✓）
 *
 * ================== ★★ 三条铁律（都是实机踩出来的，别再各写一份 ✗）==================
 *
 *   ① **倒计时存 NBT 的"绝对到期时刻"**（cmCoolUntil ✓），不靠 damage 自增 ✗
 *      · 理由：`NetServerHandler.handleWindowClick` 结尾会拿客户端的物品和服务器算出来的
 *        做 `ItemStack.areItemStacksEqual`（比 itemID ／ subtype ／ **damage** ／ NBT ✓），
 *        不一致就发「拒绝」让客户端回滚这次点击 ⇒ 表现是**物品栏里点不动它** ✗（bug ② ✓）
 *      · damage 改成由 `(到期时刻 − 当前世界时刻)` **两侧各自算**⇒ 永远是同一个数 ✓
 *
 *   ② **到期时刻只在服务端写一次** ✓ 客户端绝不写 NBT ✗（写了又是一次不一致 ✗）
 *
 *   ③ **正在使用它的时候一个字段都别改** ✗✗
 *      · 理由：`EntityPlayer.onUpdate` 判断「还在用同一件东西」靠的是**对象引用**
 *        （`held == this.itemInUse` ✓ javap 实证 ✓）——一改就是一次槽位包 ⇒ 客户端那份堆叠被换成新对象
 *        ⇒ 引用不等 ⇒ `clearItemInUse()` ⇒ **吃到一半/喝到一半被取消** ✗（bug ③ ✓）
 *
 *   另外：**每秒才动一次**（20 tick ✓）—— 条只有 13 像素宽，一秒一格肉眼就是连续下降 ✓
 *   而两侧同节奏 ⇒ 数值稳定 ⇒ 点击不会被回滚 ✓
 */
public final class CMCool {

    /** 1 分钟 = 60 秒 × 20 tick ✓ */
    public static final int TICKS_PER_MINUTE = 60 * 20;

    /** ★ NBT 键：**绝对到期时刻**（世界总时刻 ✓）*/
    public static final String NBT_UNTIL = "cmCoolUntil";

    /** 条的刷新间隔：**20 tick = 1 秒**（两侧必须同节奏 ✓）*/
    public static final int BAR_INTERVAL = 20;

    private CMCool() {}

    /** 读到期时刻（没有 ⇒ 0 ✓）*/
    public static long until(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0L;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null || !t.hasKey(NBT_UNTIL)) return 0L;
        try { return t.getLong(NBT_UNTIL); } catch (Throwable e) { return 0L; }
    }

    /** 写到期时刻（★ 只允许服务端调 ✓）*/
    public static void setUntil(ItemStack stack, long until) {
        if (stack == null) return;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); stack.setTagCompound(t); }
        t.setLong(NBT_UNTIL, until);
    }

    /**
     * 每 tick 调一次（放物品的 onUpdate 里 ✓）。
     *
     * @param coolTicks 放多少 tick 之后变（0 = 永不变 ⇒ 直接返回 false ✓）
     * @return **true = 到点了** ⇒ 调用方负责把物品换成下一级（换 itemID ＋ 清 NBT ＋ damage 归零 ✓）
     *         ★ 只可能在**服务端**返回 true ✓（客户端永远 false ⇒ 不会自己乱换 ✓）
     */
    public static boolean tick(ItemStack stack, World world, Entity entity, int coolTicks) {
        if (stack == null || world == null || coolTicks <= 0) return false;
        // ★ 铁律③：正在吃/喝它 ⇒ 这一秒什么都别改 ✗（改了会打断进食 ✗）
        try {
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).getItemInUse() == stack) return false;
        } catch (Throwable ignored) { }
        long now = world.getTotalWorldTime();
        long until = until(stack);
        if (until <= 0L) {
            if (world.isRemote) return false;                 // ★ 铁律②：客户端不写 NBT ✓
            until = now + coolTicks;                          // ★ 起表（只此一次 ✓）
            setUntil(stack, until);
        }
        long left = until - now;
        if (left > 0L) {
            if (now % BAR_INTERVAL != 0L) return false;        // ★ 每秒才动一次 ✓
            int elapsed = (int) (coolTicks - left);            // = 已经放了多久 ✓（条越走越短 ✓）
            if (elapsed < 0) elapsed = 0;
            if (elapsed > coolTicks) elapsed = coolTicks;
            try {
                if (stack.getItemDamage() != elapsed) stack.setItemDamage(elapsed);
            } catch (Throwable ignored) { }
            return false;
        }
        return !world.isRemote;                                // 到点：只让服务端换 ✓
    }
}
