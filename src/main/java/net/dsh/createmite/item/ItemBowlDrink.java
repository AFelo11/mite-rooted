package net.dsh.createmite.item;

import net.dsh.createmite.CMCool;
import net.dsh.createmite.CMFood;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumItemInUseAction;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * ★ 碗装饮料 / 桶装饮料（2026-10-06 用户定稿 ✓ 2026-10-07 实机反馈后修两个 bug ✓）
 *
 * 【用户给的规格】
 *   ① **热水** = 水碗进熔炉烧 ✓
 *   ② **温水** = 热水放着 5 分钟自动变 ✓；**温水再放 3 分钟 ⇒ 变回水碗(1167)** ✓（用户 2026-10-07 补充 ✓）
 *      ⇒ **两级降温链**：热水碗 --5 分钟--> 温水碗 --3 分钟--> 水碗(1167) ✓ 两级都用耐久条当倒计时 ✓
 *   ③ **冰水** = 水碗 ＋ 雪球（无序合成 ✓）
 *   ④ **热牛奶** = 熔炉烧 ✓（**奶桶也能烧成热奶桶** ✓ 7 种材质 ✓）
 *   ⑤ ★★ **喝完要返还容器** ✓（用户强调：「MITE 中食用碗食品后返还碗，桶也一样」✓）
 *
 * ============ ★★ 2026-10-07 实机反馈：两个 bug 的真凶（都是 MITE 字节码实证 ✓）============
 *
 * 【bug ① 右键喝不了 —— 物品压根不算「能摄取」】✗✗
 *   MITE 右键入口是 EntityPlayer.checkForIngestion()（javap 实证 ✓），它要求：
 *       stack.getItemInUseAction(this) != null 且 isIngestion()      ← 我们返回 DRINK ✓ 这关过 ✓
 *       **canIngest(stack)**                                          ← ★ 卡在这里 ✗
 *   canIngest → item.isIngestable(subtype) → isEatable(subtype) 或 isDrinkable(subtype)，
 *   而基类 **Item.isEatable(int) 和 Item.isDrinkable(int) 都是 return false** ✗✗
 *   ⇒ 我们当初只写了 getItemInUseAction、没写这两个 ⇒ **右键什么都不发生** ✗（用户「都不能吃」就是这个 ✓）
 *   ⇒ 修法：override **isDrinkable → true** ＋ 构造里 **setAlwaysEdible()** ✓
 *     （setAlwaysEdible 是 MITE 牛奶桶同款 ✓ 有了它，饱食度满也能喝 ✓ 水不该挑饿不饿 ✓）
 *
 * 【bug ③ 2026-10-07 第二次实测：**热水碗 / 温水碗喝不进去**（其余 8 件都能喝 ✓）】✗✗
 *   真凶（javap 实证 ✓）：MITE 的 EntityPlayer.onUpdate 里那段「正在使用物品」的判定是
 *       ItemStack held = inventory.getCurrentItemStack();
 *       if (held == this.itemInUse) { 继续倒计时 } else { **clearItemInUse()** }   ← ★ 是**引用相等**（if_acmpne）✗
 *   ⇒ 我们每秒改一次 damage ⇒ 服务端发一次槽位包 ⇒ 客户端的 putStackInSlot 把那份 ItemStack **换成新对象** ✗
 *     ⇒ held != itemInUse ⇒ **clearItemInUse()** ⇒ 喝到一半被取消 ⇒ 表现就是「喝不进去」✓
 *   ⇒ 只有这两件中招：**它们是唯一会自己变的**（其余 8 件没有倒计时 ⇒ 不发包 ⇒ 喝得好好的 ✓）
 *   ⇒ 修法：**正在喝它的时候一个字段都别改** ✗（onUpdate 开头就 return ✓ 见下面代码 ✓）
 *
 * 【bug ② 生存物品栏里鼠标点不动 —— damage 两侧对不上，点击被判「结果不一致」而回滚】✗✗
 *   NetServerHandler.handleWindowClick（javap 实证 ✓）结尾是：
 *       ItemStack s = Container.slotClick(...);
 *       若 areItemStacksEqual(packet.itemStack, s) 成立 ⇒ 发「接受」，否则**发「拒绝」** ⇒ 客户端把这次点击回滚 ✓
 *   而 ItemStack.isItemStackEqual 比的是 **itemID ＋ subtype ＋ damage ＋ NBT** ✓
 *   ⇒ 我们原来**每 tick 把 damage +1**（两侧各加各的 ✗）⇒ 两边必然错开 ⇒ **每次点击都被回滚** ✗
 *     （暖手石没这毛病：它**每秒**才动一次、而且只在拿在手上时动 ✓）
 *   ⇒ 修法（本节代码 ✓）：
 *     · 倒计时**不再靠 damage 自增** ✗，改成 **NBT 里的绝对到期时刻**（cmDrinkCoolUntil ✓）
 *     · 到期时刻**只在服务端写一次** ✓（客户端等同步 ✓ 绝不在客户端写 NBT ✗ 否则又是一次不一致 ✗）
 *     · damage **由 (到期时刻 - 当前世界时刻) 两边各自确定性算出** ✓ ⇒ 两侧永远同一个数 ✓
 *     · 而且**每秒才改一次**（now % 20 == 0 ✓）⇒ 数值稳定 ⇒ 点击不会被回滚 ✓
 *       （13 像素的条要画 300 秒，一秒一格的肉眼效果就是连续下降 ✓）
 *
 * 【容器返还怎么做的】
 *   和 MITE 自己一模一样 ✓：EntityPlayer.convertOneOfHeldItem(ItemStack) ✓
 *   （反汇编 Item.onItemUseFinish 就是这么写的 ✓：onServer 且非创造模式 才换 ✓）
 *   ⇒ 碗 → **空碗(281)** ✓；奶桶 → **同材质的空桶** ✓
 *   ★★ 用户特别提醒过：**MITE 的桶不止铁桶一种** ✗ —— 铜/银/金/铁/秘银/艾德曼/远古金属 **7 种** ✓
 *
 * 【耐久条为什么必须 implements IDamageableItem】
 *   MITE：Item.isDamageable() = this instanceof IDamageableItem ✓ 不实现的话 setMaxDamage 会被拒 ✗ ⇒ 条根本不画 ✗
 *   （暖手石那次踩过 ✓）
 */
public class ItemBowlDrink extends CMItem implements net.minecraft.IDamageableItem {

    /** 1 分钟 = 60 秒 × 20 tick ✓（= CMCool.TICKS_PER_MINUTE ✓ 保留这个名字免得老代码编译不过）*/
    public static final int TICKS_PER_MINUTE = CMCool.TICKS_PER_MINUTE;

    /** 喝完返还的空容器（空碗 281 ／ 对应材质的空桶 ✓）*/
    private final int emptyItemId;
    /** 放着会变成什么（热水 → 温水 ✓ 温水 → 水碗 ✓；0 = 永远不变 ✓）*/
    private final int becomesItemId;
    /** 放多少 tick 之后变（0 = 永不变 ✓）；**每件物品各自一个** ✓ */
    private final int coolTicks;

    /**
     * @param coolMinutes 放着多少**分钟**后变成 becomesItemId（0 = 永不变 ✓）
     *                    · 热水碗 5 ✓（配置 drink.hot_water_minutes ✓）
     *                    · 温水碗 3 ✓（配置 drink.warm_water_minutes ✓ 用户 2026-10-07 补充 ✓）
     */
    public ItemBowlDrink(int idArg, String unlocalizedName, String textureName, Material material,
                         int emptyItemId, int becomesItemId, int coolMinutes) {
        super(idArg, material, unlocalizedName, textureName, 25.0F);
        this.emptyItemId = emptyItemId;
        this.becomesItemId = becomesItemId;
        this.coolTicks = coolMinutes > 0 ? coolMinutes * TICKS_PER_MINUTE : 0;
        this.setMaxStackSize(1);                       // 碗/桶都不可堆叠 ✓（与 MITE 一致 ✓）
        this.setAlwaysEdible();                        // ★ bug① 的一半：饱食度满也能喝 ✓（牛奶桶同款 ✓）
        if (becomesItemId > 0 && coolTicks > 0) {
            this.setMaxDamage(coolTicks + 1);           // 条 = 倒计时刻度（+1 免得 damage==max ✓）
        }
    }

    // ================= ★ bug ① 的另一半：让 MITE 认为它「能喝」 =================
    //  基类 Item.isEatable / isDrinkable 都是 return false ✗ ⇒ isIngestable 永远 false ✗
    //  ⇒ checkForIngestion() 直接返回 ⇒ 右键不开始使用 ⇒ 「不能吃」✓（2026-10-07 实机反馈 ✓）
    @Override
    public boolean isDrinkable(int subtype) {
        return true;                                   // ★ 我们全部是「喝」✓
    }

    @Override
    public boolean isEatable(int subtype) {
        return false;                                  // 不做成「吃」（喝的动作才是 DRINK ✓）
    }

    @Override
    public int getNumComponentsForDurability() {
        return 1;
    }

    @Override
    public int getRepairCost() {
        return 0;
    }

    /** 喝 ✓（不是吃 ✓）*/
    @Override
    public EnumItemInUseAction getItemInUseAction(ItemStack stack, EntityPlayer player) {
        return EnumItemInUseAction.DRINK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public Item getItemProducedOnItemUseFinish() {
        return (emptyItemId > 0 && emptyItemId < Item.itemsList.length) ? Item.itemsList[emptyItemId] : null;
    }

    /** ★ 喝完：记体感 ＋ **返还空容器** ✓ */
    @Override
    public void onItemUseFinish(ItemStack stack, World world, EntityPlayer player) {
        if (player == null || world == null) return;
        if (player.onServer() && !player.inCreativeMode()) {
            CMFood.onEaten(player, this);                       // ★ 进食物体感系统 ✓
            Item empty = getItemProducedOnItemUseFinish();
            player.convertOneOfHeldItem(empty == null ? null : new ItemStack(empty));   // ★ 返还 ✓
            try {
                float pitch = 1.0F + (player.getRNG().nextFloat() - player.getRNG().nextFloat()) * 0.1F;
                world.playSoundAtEntity(player, "random.drink", 0.5F, pitch);
            } catch (Throwable ignored) { }
        }
    }

    // ================= ★ 降温倒计时 =================
    //  ★★ 2026-10-07：三条铁律（NBT 记到期时刻 ✓ 只服务端写 ✓ 用时不动堆叠 ✓）
    //     已经**抽到 CMCool 里**了 ⇒ 这里只负责"到点了换成下一级" ✓（热苹果派/热巧克力奶 共用同一份 ✓）
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (becomesItemId <= 0 || coolTicks <= 0) return;
        if (CMCool.tick(stack, world, entity, coolTicks)) {
            stack.itemID = becomesItemId;                   // 降到下一级 ✓（温水碗 → 水碗 ✓）
            stack.stackSize = 1;
            stack.setItemDamage(0);                         // 条清空 ✓（下一级重新从满开始 ✓）
            stack.setTagCompound(null);                     // ★ 清掉倒计时（下一级自己重新起表 ✓）
        }
    }
}
