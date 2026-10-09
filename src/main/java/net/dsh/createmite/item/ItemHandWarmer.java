package net.dsh.createmite.item;

import net.dsh.createmite.CMHeat;
import net.dsh.createmite.CMItems;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.NBTTagCompound;

/**
 * ★ 暖手石 / 冷暖手石（2026-10-05 用户定稿 ✓）
 *
 * 【用户给的规格，逐条实现】
 *   · **合成**：空圆石空 ／ 圆石空圆石 ／ 空圆石空 ⇒ 一次出 **2 个冷暖手石** ✓ **所有工作台**都能做 ✓（见 CMRecipes ✓）
 *   · **冷暖手石**放进熔炉烤一下 ⇒ **暖手石** ✓（MITE 默认热值 1 ⇒ **所有熔炉**（含砂岩/黏土/硬化黏土）都能烤 ✓
 *     燃料只要热值 ≥ 1（木板/木头/木炭/煤炭…）✓ 所以**故意没写进 FurnaceHeatMixin** ✓）
 *   · **暖手石可用两次**，每次 **3 分钟 +3℃** ✓
 *   · **不可叠加 + 有冷却**：上一次没结束就点，只会提示、**不消耗次数** ✓
 *   · 两次用满 ⇒ **自动变回冷暖手石** ✓ ⇒ 再烤 ⇒ 循环 ✓（用户选的"回充式" ✓）
 *
 * 【为什么次数存 NBT】物品堆叠只有一个（不可堆叠 ✓）⇒ 每个石头各记各的 ✓ 换手/丢地上都不丢 ✓
 * 【为什么客户端也起一次】体感在两侧各算一份 ✓（HUD 边框在客户端 ✓）
 *   服务端负责扣次数 ✓ 客户端只做"本地表现" ✓ 两侧数值完全一样 ✓
 */
public class ItemHandWarmer extends CMItem implements net.minecraft.IDamageableItem {

    // ================= ★ 冷却条 = 物品耐久条（用户：「我需要它和末影珍珠一样」✓）=================
    //  ★★ 2026-10-05 深夜④：**MITE 里能不能有耐久条，是【构造时就定死的】** ✗✗
    //    反汇编 Item.isDamageable()：
    //        public final boolean isDamageable() { return this instanceof IDamageableItem; }
    //    ⇒ 不 implements 这个接口的物品，setMaxDamage() 会被**直接拒绝**（MITE 会打
    //      "setMaxDamage: called for non-damageable item" ✗）⇒ isItemDamaged() 永远 false
    //      ⇒ RenderItem 里那段画条的代码**根本不走** ⇒ 没有冷却条 ✓（这就是前三版一直没条的原因 ✓）
    //    ⇒ 实现它之后，耐久条就按普通工具那样画 ✓ 我们只是把 damage 挪作"剩余冷却秒数" ✓
    @Override
    public int getNumComponentsForDurability() {
        return 1;                                  // 单件物品 ✓（工具是 2、护甲看材质 ✓）
    }

    @Override
    public int getRepairCost() {
        return 0;                                  // 不参与修理 ✓（它只做冷却条用 ✓）
    }

    /** NBT 里的剩余次数键名 */
    public static final String NBT_USES = "cmWarmUses";

    // ---- ★ 三个数值都做成配置项（2026-10-05 深夜 ✓）----
    /** 烤一次能用几次（默认 2 ✓ 用户规格 ✓）*/
    public static int usesPerHeat() {
        return Math.max(1, (int) net.dsh.createmite.CMConfig.getFloat("warm_stone.uses", 2.0F));
    }

    /** 每次持续多少 tick（默认 3 分钟 = 3600 ✓ 用户规格 ✓）*/
    public static int durationTicks() {
        return Math.max(20, (int) (net.dsh.createmite.CMConfig.getFloat("warm_stone.minutes", 3.0F) * 60.0F * 20.0F));
    }

    /**
     * 每次给多少℃（★ 2026-10-05 深夜用户要求：**3 → 8** ✓）
     *   （8 也是最早推荐表里的数 ✓；用户当时给的规格写的是 +3，所以先做了 +3 ✓ 现在按 8 来 ✓）
     */
    public static float heat() {
        return net.dsh.createmite.CMConfig.getFloat("warm_stone.heat", 8.0F);
    }

    /** 冷却条刻度（秒 ✓ = 每次持续多少秒 ✓）*/
    public static int cooldownSeconds() {
        return Math.max(1, durationTicks() / 20);
    }

    /** 冷却条的"耐久上限"（**故意 +1** ✗ ⇒ damage 永远不等于 max ⇒ 不会被当成损坏 ✓）*/
    public static int barMax() {
        return cooldownSeconds() + 1;
    }

    private final boolean warm;

    public ItemHandWarmer(int idArg, boolean warm, String unlocalizedName, String textureName) {
        super(idArg, Material.stone, unlocalizedName, textureName, warm ? 40.0F : 20.0F);
        this.warm = warm;
        this.setMaxStackSize(1);                 // ★ 不可叠加 ✓（用户规格 ✓）
    }

    /** 手里这块还剩几次 ✓（不是暖手石 / 没 NBT ⇒ 0 ✓）*/
    public static int usesLeft(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return usesPerHeat();
        NBTTagCompound t = stack.getTagCompound();
        if (t == null || !t.hasKey(NBT_USES)) return usesPerHeat();
        return t.getIntegerWithDefault(NBT_USES, usesPerHeat());
    }

    /** 写回剩余次数 ✓ */
    public static void setUses(ItemStack stack, int uses) {
        if (stack == null) return;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); stack.setTagCompound(t); }
        t.setInteger(NBT_USES, uses);
    }

    /**
     * ★★ 服务端权威的"热到什么时候"（世界总时刻 ✓ 存 NBT ✓）
     *   【为什么非要有它】单机里客户端与服务端**是同一个 JVM** ⇒ CMHeat 里那张静态表是**共享**的 ✗
     *   第一次实测的 bug 就是：**客户端那次点击先把静态计时器点着了** ✗
     *   ⇒ 服务端这次点击一看"还在热着" ⇒ 直接返回 ✗ ⇒ 次数不扣、逻辑不走 ✓ = 用户说的"暖手石不能用" ✓
     *   ⇒ 修法：**服务端只认 NBT**（自己的权威状态 ✓），客户端只认静态表（它自己的表现 ✓）⇒ 互不挡 ✓
     */
    public static final String NBT_UNTIL = "cmWarmUntil";

    public static long warmUntil(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0L;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null || !t.hasKey(NBT_UNTIL)) return 0L;
        try { return t.getLong(NBT_UNTIL); } catch (Throwable e) { return 0L; }
    }

    public static void setWarmUntil(ItemStack stack, long until) {
        if (stack == null) return;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); stack.setTagCompound(t); }
        t.setLong(NBT_UNTIL, until);
    }

    /** 用满两次 ⇒ 变回**冷暖手石** ✓（同一个 ItemStack 就地换 itemID ✓ 用户要的循环 ✓）*/
    private static void becomeCold(ItemStack stack) {
        if (stack == null || CMItems.handWarmerCold == null) return;
        stack.itemID = CMItems.handWarmerCold.itemID;
        stack.stackSize = 1;
        stack.setTagCompound(null);
    }

    /**
     * ★ 右键使用（2026-10-05 用户改版 ✓）
     *   · **一句聊天提示都不要** ✗（用户明确："冷暖手石和暖手石提示不需要" ✓）
     *     反馈只给一个很轻的音效 ✓（不然用起来像坏了 ✓）
     *   · **客户端只认静态表、服务端只认 NBT** ⇒ 两边互不挡 ✓（修掉"暖手石不能用" ✓）
     */
    @Override
    public boolean onItemRightClick(EntityPlayer player, float partial, boolean flag) {
        if (player == null || player.worldObj == null) return true;
        player.swingArm();
        boolean remote = player.worldObj.isRemote;

        if (!warm) return true;                   // 冷暖手石：**什么都不做、也不提示** ✓

        if (remote) {
            // ---- 客户端：只管"本地表现"（HUD/边框用 ✓）—— 只看静态表 ✓ 不碰 NBT ✓ ----
            if (!CMHeat.isItemWarmActive(player)) {
                CMHeat.startItemWarm(player, durationTicks(), heat());
            }
            return true;
        }

        // ---- 服务端：权威逻辑 ✓ ----
        ItemStack held = player.getHeldItemStack();
        if (held == null) return true;            // 拿不到手上的东西就不动 ✓（免得白送热 ✓）
        long now = player.worldObj.getTotalWorldTime();

        // ★★ 冷却判定用**玩家级**的（服务端那张表 ✓ 2026-10-05 深夜改 ✓）：
        //    ⇒ **所有暖手石一起冷却** ✓（用户：「我还有一个烧好的暖手石，它应该和末影珍珠冷却一样不能用」✓）
        if (CMHeat.isItemWarmActive(player) || now < warmUntil(held)) return true;

        int uses = usesLeft(held);
        if (uses <= 0) { becomeCold(held); return true; }

        CMHeat.startItemWarm(player, durationTicks(), heat());
        setWarmUntil(held, now + durationTicks());  // ★ 也写进 NBT（兜底 ✓ 换维度/重登不丢 ✓）
        setBar(held, cooldownSeconds());            // ★ 冷却条：先压到"空的" ✓
        uses--;
        if (uses <= 0) {
            becomeCold(held);                      // ★ 最后一次用完 ⇒ 变回冷暖手石 ✓
        } else {
            setUses(held, uses);
        }
        try {                                      // 很轻的一声"嘶" ✓ 当作"点着了"的反馈 ✓
            player.worldObj.playSoundAtEntity(player, "random.fizz", 0.35F, 1.5F);
        } catch (Throwable ignored) { }
        System.out.println("[MITE][WARM] used id=" + player.entityId
                + " left=" + Math.max(0, uses) + " until=" + (now + durationTicks()) + " now=" + now
                + " heat=" + heat() + " min=" + (durationTicks() / 1200));
        return true;
    }

    // ================= ★ 冷却条（用户 2026-10-05：「我需要它和末影珍珠一样」✓）=================
    //  MITE 里没有"物品冷却"这套系统（javap 实证：ItemEnderPearl 只有 setMaxStackSize(16) ✗），
    //  所以用**物品自己的耐久条**来当冷却条 ✓ —— 就是工具耐久条那个样子 ✓
    //  画法：damage = 剩余秒数 ⇒ **冷却时条是空的，越凉越满，满了就能再用** ✓
    //  ⚠️ 上限故意设成 COOLDOWN_SECONDS + 1 ✗ ⇒ damage 永远不等于 max，不会被当成"损坏" ✓
    private static void setBar(ItemStack stack, int seconds) {
        if (stack == null) return;
        try {
            int v = seconds;
            if (v < 0) v = 0;
            if (v > cooldownSeconds()) v = cooldownSeconds();
            stack.setItemDamage(v);
        } catch (Throwable ignored) { }
    }

    /**
     * 每秒刷一下冷却条 ✓（由 CMAmbientFeel.tick 每 tick 调 ✓ 只在服务端动手 ✓）
     *   · 手上拿的是暖手石、而玩家正在冷却 ⇒ 条 = 剩余秒数 ✓
     *   · 冷却结束 ⇒ 条清零（= 满 ✓ 可以再用 ✓）
     */
    public static void tickCooldownBar(EntityPlayer p) {
        if (p == null || p.worldObj == null) return;
        // ★ 两侧都刷 ✓（条是**客户端**画的 ✗ ⇒ 客户端那份 stack 也得改 ✓ 见 CMHeat.itemWarmUntil 的回退 ✓）
        if (p.ticksExisted % 20 != 0) return;             // 每秒一次 ✓
        try {
            ItemStack held = p.getHeldItemStack();
            if (held == null || !(held.getItem() instanceof ItemHandWarmer)) return;
            if (!((ItemHandWarmer) held.getItem()).warm) return;   // 只有暖手石画条 ✓
            long now = p.worldObj.getTotalWorldTime();
            long until = CMHeat.itemWarmUntil(p);         // ★ 玩家级冷却（哪块石头都一样 ✓）
            int left = until > now ? (int) ((until - now) / 20L) : 0;
            setBar(held, left);
        } catch (Throwable ignored) { }
    }
}
