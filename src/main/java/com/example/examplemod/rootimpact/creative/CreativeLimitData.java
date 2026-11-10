package com.example.examplemod.rootimpact.creative;

import com.example.examplemod.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация Data Component {@code taken_outside} («Взять извне») для системы Creative Limit.
 * <p>
 * Компонент — ЧИСТО ДЕКОРАТИВНАЯ метка: он лишь показывает в tooltip, что предмет был
 * взят/перемещён из инвентаря игрока, на котором действует Creative Limit (подбор с земли,
 * крафт, переплавка, выброс, передача в контейнер или другому игроку).
 * <p>
 * Метка НИКОГДА:
 * <ul>
 *   <li>не разрешает получить запрещённый предмет из Creative Menu;</li>
 *   <li>не снимает запреты реестров {@code registeritem}/{@code registerblock};</li>
 *   <li>не отключает ограничения Creative Limit и не меняет поведение предмета.</li>
 * </ul>
 * Компонент сериализуется автоматически (NBT + сеть), поэтому метка:
 * <ul>
 *   <li>сохраняется при разделении стака ({@code ItemStack.split} копирует компоненты)</li>
 *   <li>сохраняется при выбрасывании (компоненты дропа — это компоненты исходного стака)</li>
 *   <li>сохраняется при перемещении между слотами и контейнерами (переносится тот же объект {@link ItemStack})</li>
 *   <li>переживает перезаход игрока и перезапуск сервера</li>
 * </ul>
 */
public class CreativeLimitData {

    /** DeferredRegister для Data Components мода */
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleMod.MODID);

    /** Data Component: «Взять извне» — предмет получен вне Creative Menu */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> TAKEN_OUTSIDE =
            DATA_COMPONENTS.registerComponentType(
                    "taken_outside",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
            );

    /** Текст метки в тултипе предмета */
    public static final String TAKEN_OUTSIDE_LABEL = "Взять извне";

    /**
     * Регистрирует Data Component на шине событий мода.
     * Должен вызываться из конструктора {@link ExampleMod}.
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    /**
     * Проверяет, помечен ли предмет меткой «Взять извне».
     */
    public static boolean isTakenOutside(ItemStack stack) {
        return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(TAKEN_OUTSIDE.get()));
    }

    /**
     * Ставит метку «Взять извне» на предмет (изменяется живой стек).
     */
    public static void markTakenOutside(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.set(TAKEN_OUTSIDE.get(), Boolean.TRUE);
        }
    }
}
