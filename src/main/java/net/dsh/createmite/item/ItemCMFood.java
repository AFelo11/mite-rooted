package net.dsh.createmite.item;

import net.dsh.createmite.CMCool;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumItemInUseAction;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.World;

/**
 * ★★ 我们自己的「食物 / 饮品」通用类（2026-10-07 用户拍板的套餐 A ✓）
 *
 *   一件顶四件：**吃或喝（EAT / DRINK ✓）＋ 可选返还容器 ✓ ＋ 可选"放着会自己变" ✓ ＋ MITE 食物值 ✓**
 *
 * 【为什么要有它】ItemBowlDrink 是"饮料专用"（永远是 DRINK ＋ 永远返还碗/桶 ✓），
 *   而苹果派要"吃"、热苹果派要"吃了之后还会凉成苹果派"⇒ 干脆写个通用的 ✓
 *   （饮料那边**没动** ✗ 已经实机验收过的东西不折腾 ✓ CMCool 的倒计时两边共用一份 ✓）
 *
 * 【★ 教训①：能不能吃喝看 isEatable / isDrinkable】
 *   MITE 右键入口 EntityPlayer.checkForIngestion() → canIngest() → item.isIngestable()
 *   = `isEatable || isDrinkable`，而**基类 Item 这两个都是 return false** ✗
 *   ⇒ 不 override 就**右键什么都不发生** ✗（2026-10-07 饮料那批就是这么栽的 ✓）
 *
 * 【★ 教训②：吃东西要走 MITE 自己的路】
 *   ~~EntityPlayer.addFoodValue(Item)~~ ✓ —— 这么调**饥饿和我们的体感一起生效** ✓
 *   （我们的 FoodValueMixin 就挂在它头上 ✓ 不用自己再喊一遍 CMFood.onEaten ✗ 否则记两遍 ✗）
 *
 * 【★ 教训③：饱食度】
 *   · DRINK（饮品）⇒ setAlwaysEdible() ✓ 饱了也能喝 ✓（水/牛奶本来就不挑饿不饿 ✓）
 *   · EAT（食物）⇒ **不设** ✓ 走 MITE 的正常判定（吃饱了吃不下 ✓ 和南瓜派一个规矩 ✓）
 *     ⚠️ 前提是**必须 setFoodValue(...)** ✗ 一个营养值都不给 ⇒ canIngest 永远 false ⇒ 又吃不了 ✗
 *
 * 【★ 教训④：降温三条铁律全在 CMCool 里】见 CMCool 的类注释 ✓ 别再抄一份 ✗
 */
public class ItemCMFood extends CMItem implements net.minecraft.IDamageableItem {

    /** 吃还是喝 ✓ */
    private final EnumItemInUseAction action;
    /** 吃/喝完返还的空容器（0 = 不返还 ✓ 苹果派就没有容器 ✓）*/
    private final int emptyItemId;
    /** 放着会变成什么（热苹果派 → 苹果派 ✓；0 = 永远不变 ✓）*/
    private final int becomesItemId;
    /** 放多少 tick 后变（0 = 永不变 ✓）*/
    private final int coolTicks;

    /**
     * @param action      EnumItemInUseAction.EAT（吃）或 .DRINK（喝）✓
     * @param emptyItemId 返还的空容器 id（0 = 不返还 ✓）
     * @param becomesId   放着会变成的物品 id（0 = 不变 ✓）
     * @param coolMinutes 放多少分钟后变（0 = 不变 ✓）
     */
    public ItemCMFood(int idArg, String unlocalizedName, String textureName, Material material, float difficulty,
                      EnumItemInUseAction action, int emptyItemId, int becomesId, int coolMinutes) {
        super(idArg, material, unlocalizedName, textureName, difficulty);
        this.action = action;
        this.emptyItemId = emptyItemId;
        this.becomesItemId = becomesId;
        this.coolTicks = coolMinutes > 0 ? coolMinutes * CMCool.TICKS_PER_MINUTE : 0;
        if (action == EnumItemInUseAction.DRINK) {
            this.setMaxStackSize(1);                  // 碗/桶不可堆叠 ✓（与 MITE 一致 ✓）
            this.setAlwaysEdible();                   // ★ 饮品：饱了也能喝 ✓
        }
        if (becomesId > 0 && coolTicks > 0) {
            this.setMaxDamage(coolTicks + 1);         // 条 = 倒计时刻度（+1 免得 damage==max ✓）
        }
    }

    // ---- ★ 教训①：这两个不写就"打不开嘴" ✗ ----
    @Override
    public boolean isEatable(int subtype) {
        return action == EnumItemInUseAction.EAT;
    }

    @Override
    public boolean isDrinkable(int subtype) {
        return action == EnumItemInUseAction.DRINK;
    }

    // ---- 耐久条（只有"会自己变"的那两件才有条 ✓ 但接口必须实现 ✓ MITE 才肯画 ✗）----
    @Override
    public int getNumComponentsForDurability() {
        return 1;
    }

    @Override
    public int getRepairCost() {
        return 0;
    }

    @Override
    public EnumItemInUseAction getItemInUseAction(ItemStack stack, EntityPlayer player) {
        return action;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public Item getItemProducedOnItemUseFinish() {
        return (emptyItemId > 0 && emptyItemId < Item.itemsList.length) ? Item.itemsList[emptyItemId] : null;
    }

    /**
     * ★ 吃完 / 喝完：喂饱（走 MITE 的路 ✓）＋ **扣掉一个** ✓ ＋ 返还容器（如果有 ✓）
     *
     * ★★ 2026-10-07 run317 实测「**苹果派吃了不消耗**」✗ ⇒ 真凶就在这里 ✓（反汇编实证 ✓）：
     *   · `EntityPlayer.onItemUseFinish()` 只做两件事：调 `ItemStack.onItemUseFinish(...)` ＋ 记统计 ✓
     *   · `ItemStack.onItemUseFinish(...)` 也只是转发给 `Item.onItemUseFinish(...)` ✓
     *   · ⇒ **两边都没有一句 `stackSize--`** ✗✗ —— MITE 里「吃掉一个」**完全由 Item 自己负责** ✓
     *   · 原版 / MITE 的基类写法就是这一句（见 Item.onItemUseFinish 反汇编 ✓）：
     *       `player.convertOneOfHeldItem(产物 == null ? null : new ItemStack(产物))`
     *     ★ 传 **null 就是"不返还任何东西"⇒ 等价于减掉一个** ✓（碗类传碗 ⇒ 等于换个碗 ✓）
     *   ⇒ 第一版只在**有容器**时才调 ⇒ 苹果派吃完原样留着 ✗（用户一眼看出来 ✓）
     *   ⇒ 现在**无条件调** ✓ 有容器给容器 ✓ 没容器给 null（= 吃掉）✓
     */
    @Override
    public void onItemUseFinish(ItemStack stack, World world, EntityPlayer player) {
        if (player == null || world == null) return;
        if (player.onServer() && !player.inCreativeMode()) {
            player.addFoodValue(this);                    // ★ 饥饿 ＋ 体感（mixin）一起生效 ✓
            Item empty = getItemProducedOnItemUseFinish();
            player.convertOneOfHeldItem(empty == null ? null : new ItemStack(empty));   // ★ 没容器 = 吃掉 ✓
        }
    }

    /** ★ 放着自己变凉（热苹果派 → 苹果派 ✓ 热巧克力奶 → 巧克力奶 ✓）*/
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (becomesItemId <= 0 || coolTicks <= 0) return;
        if (CMCool.tick(stack, world, entity, coolTicks)) {
            stack.itemID = becomesItemId;                 // 降到下一级 ✓
            stack.stackSize = 1;
            stack.setItemDamage(0);                       // 条清空 ✓（下一级重新起表 ✓）
            stack.setTagCompound(null);                   // ★ 清掉倒计时 ✓
        }
    }
}
