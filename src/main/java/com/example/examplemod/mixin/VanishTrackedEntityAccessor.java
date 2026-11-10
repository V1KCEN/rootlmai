package com.example.examplemod.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * Mixin-доступ к внутреннему трекеру одной сущности {@code ChunkMap.TrackedEntity}.
 *
 * <p>Класс {@code TrackedEntity} — package-private внутренний класс {@code ChunkMap}, поэтому
 * обычный тип в коде мода недоступен; mixin-интерфейс позволяет вызвать его публичный метод.
 *
 * <p>Вызывается ровно ванильный {@code updatePlayer}: он сам решает, отправить ли наблюдателю
 * {@code addPairing} (полный spawn) или {@code removePairing} ({@code ClientboundRemoveEntitiesPacket}).
 * Никакой собственной отправки пакетов — это исключает дубликаты и «призраки».
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface VanishTrackedEntityAccessor {

    /**
     * Пересчитывает, должен ли {@code player} отслеживать сущность, и немедленно приводит
     * клиент в соответствие (спавн или удаление).
     */
    @Invoker("updatePlayer")
    void vanish$updatePlayer(ServerPlayer player);

    /**
     * Множество соединений, которым сущность сейчас реально отправляется.
     *
     * <p>Именно это множество определяет, видит ли клиент сущность: если соединения наблюдателя
     * здесь нет — клиент получил {@code RemoveEntities} и не имеет ни модели, ни nametag,
     * ни коллизии, ни звуков шагов, ни клиентских частиц.
     */
    @Accessor("seenBy")
    Set<Object> vanish$getSeenBy();
}