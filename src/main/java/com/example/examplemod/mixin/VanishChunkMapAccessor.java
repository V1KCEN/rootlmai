package com.example.examplemod.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin-доступ к карте отслеживаемых сущностей {@link ChunkMap}.
 *
 * <p><b>Зачем.</b> Ванильный {@code ChunkMap.TrackedEntity.updatePlayer(ServerPlayer)} — единственное
 * место, где решается, должен ли наблюдатель получать spawn/move-данные сущности. Система VANISH
 * не меняет это поведение: mixin даёт только возможность НАЙТИ нужный трекер, чтобы после команды
 * принудительно вызвать уже существующий ванильный пересчёт — тогда клиент сразу получает
 * верную картину, без переподключения игрока.
 *
 * <p>Собственной отправки пакетов здесь нет: спавн и удаление сущности всегда делает ванилля,
 * поэтому дубликаты сущностей, «призраки» и поломанные UUID исключены.
 *
 * <p>Тип значения намеренно {@code Object}: внутренний класс {@code ChunkMap.TrackedEntity}
 * package-private и недоступен из кода мода, поэтому обращение идёт через отдельный
 * mixin-интерфейс {@link VanishTrackedEntityAccessor}.
 */
@Mixin(ChunkMap.class)
public interface VanishChunkMapAccessor {

    /** Карта «id сущности → трекер». */
    @Accessor("entityMap")
    Int2ObjectMap<Object> vanish$getEntityMap();
}