package com.example.examplemod.rootimpact.food;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * Предмет Rotten Food (раздел 10 ROOT_IMPACT_SPEC.md).
 * <p>
 * Заменяет испорченную еду. Нельзя приготовить, нельзя использовать
 * как ингредиент. При съедании накладывает:
 * <ul>
 *   <li>Голод — 30 секунд</li>
 *   <li>Тошнота — 15 секунд</li>
 *   <li>Отравление — 10 секунд</li>
 * </ul>
 * Питательность: 0, насыщение: 0.
 */
public class RottenFoodItem extends Item {

    public RottenFoodItem(Properties properties) {
        super(properties);
    }

    /**
     * Создаёт FoodProperties для Rotten Food.
     * Питательность 0, насыщение 0, эффекты при съедании.
     */
    public static FoodProperties createFoodProperties() {
        return new FoodProperties.Builder()
                .nutrition(0)
                .saturationModifier(0.0F)
                // alwaysEdible() — позволяет съесть «Гниль» даже при полностью заполненной шкале голода
                .alwaysEdible()
                .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 600, 0), 1.0F)      // 30 сек голод
                .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 300, 0), 1.0F)   // 15 сек тошнота
                .effect(() -> new MobEffectInstance(MobEffects.POISON, 200, 0), 1.0F)      // 10 сек отравление
                .build();
    }
}