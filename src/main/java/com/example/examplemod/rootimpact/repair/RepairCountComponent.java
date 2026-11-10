package com.example.examplemod.rootimpact.repair;

import com.example.examplemod.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация Data Component {@code repair_count}.
 * <p>
 * Хранит количество успешных ремонтов предмета через наковальню.
 * Тип: {@code int}.
 * <p>
 * Используется системой ограничения ремонтов (раздел 4 ROOT_IMPACT_SPEC.md).
 * Сериализуется автоматически через Codec (NBT) и StreamCodec (сеть).
 */
public class RepairCountComponent {

    /** DeferredRegister для Data Components мода */
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleMod.MODID);

    /** Data Component: количество ремонтов предмета */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REPAIR_COUNT =
            DATA_COMPONENTS.registerComponentType(
                    "repair_count",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    /**
     * Регистрирует Data Component на шину событий мода.
     * Должен вызываться из конструктора {@link ExampleMod}.
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}