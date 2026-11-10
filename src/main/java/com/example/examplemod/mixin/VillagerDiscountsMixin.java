package com.example.examplemod.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для {@link Villager}.
 * <p>
 * Полностью отключает скидки жителей (раздел 9 ROOT_IMPACT_SPEC.md):
 * <ul>
 *   <li>Скидки Hero of the Village</li>
 *   <li>Скидки после лечения зомби-жителя</li>
 * </ul>
 * <p>
 * Обе скидки применяются в private методе {@code updateSpecialPrices(Player)},
 * который вызывается при открытии торгового GUI. Нет NeoForge события,
 * срабатывающего в этот момент, поэтому необходим Mixin.
 * <p>
 * {@code @Inject} в HEAD с {@code cancellable = true} полностью отключает
 * применение любых скидок, не затрагивая базовые цены и остальную логику жителей.
 * <p>
 * НЕ использует {@code @Shadow} — не зависит от внутренних полей класса.
 */
@Mixin(Villager.class)
public class VillagerDiscountsMixin {

    /**
     * Отключает применение скидок при открытии торгового GUI.
     * <p>
     * {@code updateSpecialPrices(Player)} — private метод, вызывается при
     * открытии торгового экрана. Отмена в HEAD предотвращает применение
     * скидок Hero of the Village и скидок от лечения зомби-жителя.
     */
    @Inject(
            method = "updateSpecialPrices",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableVillagerDiscounts(Player player, CallbackInfo ci) {
        ci.cancel();
    }
}