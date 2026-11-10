package com.example.examplemod.rootimpact.trade;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Менеджер торговли жителей (раздел 9 ROOT_IMPACT_SPEC.md).
 * <p>
 * Отвечает за применение правил торговли к жителям.
 * <p>
 * Фильтрация сделок выполняется через {@link TradeFilter#apply()} при инициализации мода.
 * Отключение скидок (Hero of the Village, лечение зомби-жителя) выполняется
 * через {@code VillagerDiscountsMixin} на {@code Villager.updateSpecialPrices()}.
 * <p>
 * Этот класс регистрирует обработчики событий, связанные с торговлей.
 */
public class TradeManager {

    /**
     * Обрабатывает появление жителя в мире.
     * <p>
     * В текущей реализации не требуется дополнительных действий при спавне,
     * так как фильтрация сделок выполняется глобально через {@link TradeFilter},
     * а скидки отключаются через Mixin.
     * <p>
     * Метод оставлен как точка расширения для будущих механик торговли.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Точка расширения для будущих механик торговли
    }
}