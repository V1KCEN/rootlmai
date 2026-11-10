package com.example.examplemod.rootimpact.vanish;

import com.example.examplemod.mixin.VanishChunkMapAccessor;
import com.example.examplemod.mixin.VanishTrackedEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Тонкая обёртка над ванильным трекером сущностей для НЕМЕДЛЕННОГО обновления видимости.
 *
 * <p>Обычно пересчёт делает сам {@code ChunkMap} каждый тик, но после команды игроку не нужно
 * ждать: здесь мы принудительно вызываем уже существующий ванильный пересчёт «видит ли наблюдатель
 * сущность». Если ответ изменился, в клиент немедленно уходит корректный {@code RemoveEntities}
 * или полный spawn-пакет — без дубликатов и без переподключения игрока.
 */
public final class VanishTracker {

    private VanishTracker() {
    }

    /**
     * Пересчитывает отправку сущности {@code subject} наблюдателю {@code observer}.
     * Безопасно ничего не делает, если трекер недоступен (например, сущность выгружена).
     */
    public static void updateFor(Entity subject, ServerPlayer observer) {
        if (subject == null || observer == null) return;
        if (subject.level() != observer.level()) return;
        if (subject.isRemoved()) return;

        ServerLevel level = observer.serverLevel();
        VanishChunkMapAccessor map = (VanishChunkMapAccessor) level.getChunkSource().chunkMap;
        Object tracked = map.vanish$getEntityMap().get(subject.getId());
        if (tracked == null) return;   // сущность не отслеживается — пересчитывать нечего

        ((VanishTrackedEntityAccessor) tracked).vanish$updatePlayer(observer);
    }

    /** Пересчитывает трекинг для всех наблюдателей в измерении сущности. */
    public static void updateForAll(Entity subject) {
        if (!(subject.level() instanceof ServerLevel level)) return;
        for (ServerPlayer observer : level.players()) {
            if (observer == subject) continue;
            updateFor(subject, observer);
        }
    }

    /**
     * Диагностика: отправляется ли сущность наблюдателю прямо сейчас.
     *
     * <p>Возвращает {@code true}, если соединение наблюдателя присутствует в множестве
     * {@code seenBy} трекера, то есть клиент реально имеет эту сущность. Если {@code false} —
     * клиент получил удаление и не видит игрока (нет модели, nametag, коллизии, шагов и частиц).
     * Если сущность не отслеживается вообще (игрок вне радиуса), возвращается {@code true},
     * чтобы не выдавать «скрытость» там, где игрок и так не в зоне видимости.
     */
    public static boolean isTrackedBy(Entity subject, ServerPlayer observer) {
        if (subject == null || observer == null) return false;
        if (subject.level() != observer.level()) return false;

        ServerLevel level = observer.serverLevel();
        VanishChunkMapAccessor map = (VanishChunkMapAccessor) level.getChunkSource().chunkMap;
        Object tracked = map.vanish$getEntityMap().get(subject.getId());
        if (tracked == null) return true;   // вне зоны отслеживания

        VanishTrackedEntityAccessor accessor = (VanishTrackedEntityAccessor) tracked;
        return accessor.vanish$getSeenBy().contains(observer.connection);
    }
}