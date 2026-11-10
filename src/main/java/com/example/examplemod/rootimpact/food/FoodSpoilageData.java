package com.example.examplemod.rootimpact.food;

import com.example.examplemod.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация Data Component {@code created_game_time}.
 * <p>
 * Хранит игровое время (в тиках), когда был создан этот конкретный
 * пищевой ItemStack (по спецификации ROOT Impact: createdGameTime).
 * <p>
 * Компонент устанавливается при создании/крафте/подборе еды и проверяется
 * в {@link FoodSpoilageManager}. Сериализуется автоматически (NBT + сеть).
 * <p>
 * Используется только игровое время мира (level.getGameTime()).
 */
public class FoodSpoilageData {

    /** DeferredRegister для Data Components мода */
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleMod.MODID);

    /** Data Component: игровое время (в тиках), когда был создан еда ItemStack */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CREATED_GAME_TIME =
            DATA_COMPONENTS.registerComponentType(
                    "created_game_time",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG)
            );

    /**
     * Регистрирует Data Component на шину событий мода.
     * Должен вызываться из конструктора {@link ExampleMod}.
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}