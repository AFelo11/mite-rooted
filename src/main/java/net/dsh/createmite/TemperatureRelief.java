package net.dsh.createmite;

import net.minecraft.EntityPlayer;

/**
 * ★★ 体温"缓解源"接口（用户 2026-10-01 裁定 ✓）
 *
 * 【为什么要这个接口】用户对"持续 5 分钟就必定掉到 34.5 以下 / 必定超过 38"这条规则选了
 *   **B 方案**（改成"越待越快地往极端逼"，**不是**硬性钳死 ✓），同时要求
 *   「A 也要留接口【后面可能会添加一些附属功能或者功能方块】」✓
 *   ⇒ 于是把"怎么算缓解"抽成这个接口 ✓：**本体只提供"火堆取暖"一个默认实现** ✓，
 *     以后附属加"火堆 / 暖气片 / 空调方块 / 温泉"之类，只要注册一个实现即可 ✓（不用改本体 ✓）。
 *
 * 【注册】{@link CMBodyTemp#registerRelief(TemperatureRelief)} ✓
 *   （注册进去的源**每 tick**都会被问到，所以实现里别做重活 ✗，要扫描就自己缓存 ✓）
 */
public interface TemperatureRelief {

    /**
     * 直接修正**目标体温**（℃ ✓）—— 返回原值表示"我不管" ✓。
     *
     * @param player 玩家
     * @param target 现在算出来的目标体温（℃ ✓）
     * @param cold   true = 当前是"冷"（目标低于正常体温 ✓）
     */
    float adjustTarget(EntityPlayer player, float target, boolean cold);

    /**
     * 缓解强度（0 ~ 1 ✓）：**1 = 完全缓解** ⇒ 会清空"持续暴露"计时 ✓（也就是掐掉加速 ✓）。
     * 返回 0 表示没有任何缓解作用 ✓。
     */
    float reliefStrength(EntityPlayer player, boolean cold);
}
