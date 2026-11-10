package com.example.examplemod.rootimpact.food;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация предметов системы порчи еды (раздел 10 ROOT_IMPACT_SPEC.md).
 * <p>
 * Регистрирует Rotten Food — предмет, заменяющий испорченную еду.
 */
public class FoodItems {

    /** DeferredRegister для предметов мода */
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ExampleMod.MODID);

    /** Rotten Food — испорченная еда */
    public static final DeferredItem<Item> ROTTEN_FOOD = ITEMS.registerItem(
            "rotten_food",
            props -> new RottenFoodItem(props.food(RottenFoodItem.createFoodProperties())),
            new Item.Properties().stacksTo(64)
    );

    /**
     * Регистрирует предметы на шину событий мода.
     * Должен вызываться из конструктора {@link ExampleMod}.
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /**
     * Применяет maxStackSize = 16 к конкретному ItemStack тыквенного пирога.
     * <p>
     * Этот метод НЕ вызывается из системы порчи. Тыквенный пирог — предмет-исключение,
     * который никогда не портится (см. {@link FoodSpoilageRegistry}).
     * Применение максимума стака выполняется отдельным handler'ом
     * ({@code PumpkinPieStackSizeHandler}) — вне цикла проверки порчи.
     * <p>
     * ВАЖНО: НЕ мутирует глобально {@link Items#PUMPKIN_PIE} через
     * {@code modifyDefaultComponentsFrom()}, так как это вызывает конфликт
     * с Sodium (глобальная мутация vanilla-предмета ломает его кэши).
     * <p>
     * Вместо этого компонент {@link DataComponents#MAX_STACK_SIZE} устанавливается
     * на конкретный {@link ItemStack}. Питательность, насыщение и рецепт
     * не изменяются. Предмет остаётся {@code minecraft:pumpkin_pie}.
     *
     * @param stack ItemStack тыквенного пирога
     */
    public static void applyPumpkinPieStackSize(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.PUMPKIN_PIE)) return;
        stack.set(DataComponents.MAX_STACK_SIZE, 16);
    }
}
