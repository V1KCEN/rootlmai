package com.example.examplemod.rootimpact.vanish;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Ядро VANISH: индивидуальная видимость игрока и синхронизация entity-трекинга.
 *
 * <p><b>Как это работает (серверно, без клиентских проверок).</b>
 * Ванильный трекер {@code ChunkMap.TrackedEntity.updatePlayer} каждый тик решает, должен ли
 * наблюдатель получать обновления сущности, вызывая {@link Entity#broadcastToPlayer(ServerPlayer)}.
 * Если метод возвращает {@code false}, наблюдатель исключается из {@code seenBy}, и в его клиент
 * уходит {@code ClientboundRemoveEntitiesPacket}. Один этот механизм закрывает всё, что является
 * «присутствием» игрока на клиенте:
 * <ul>
 *   <li>модель и анимации — entity вообще не приходит в клиент;</li>
 *   <li>nametag — рисуется вместе с entity;</li>
 *   <li>collision — клиент не знает о сущности и не сталкивается;</li>
 *   <li>звуки шагов — проигрываются клиентом по данным полученной entity;</li>
 *   <li>частицы entity (например, от зелий/enchant) — клиент их не создаёт.</li>
 * </ul>
 *
 * <p>Урон, снаряды и стрелы X при этом остаются: они являются отдельными сущностями/событиями
 * урона и проверяются по другим правилам, поэтому скрытие на них не влияет.
 *
 * <p><b>Восстановление видимости.</b> Когда право возвращается, трекер снова вызывает
 * {@code broadcastToPlayer}, он возвращает {@code true}, и ванильный {@code addPairing} полностью
 * отправляет клиенту spawn-данные сущности. Дубликатов и «призраков» не возникает, переподключение
 * игроку не требуется.
 */
public final class VanishVisibility {

    private VanishVisibility() {
    }

    /**
     * Главная проверка: должен ли {@code observer} получать сущность {@code entity}.
     * Вызывается из mixin на {@code Entity.broadcastToPlayer}.
     *
     * <p>Возвращает {@code true} для всех сущностей, кроме скрытых игроков, поэтому предметы,
     * мобы, стрелы и снаряды отображаются всегда — скрывается только присутствие игрока.
     */
    public static boolean shouldBroadcast(Entity entity, ServerPlayer observer) {
        if (observer == null) return true;
        if (entity == null) return true;

        // Скрываем только сущности игроков; всё остальное (снаряды X и т.п.) не трогаем.
        if (!(entity instanceof Player hidden)) return true;

        // Наблюдатель сам всегда видит и скрываемого, и себя.
        if (hidden.getUUID().equals(observer.getUUID())) return true;

        return VanishState.get().canSee(observer.getUUID(), hidden.getUUID());
    }

    /**
     * Видит ли наблюдатель скрываемого игрока (вспомогательный метод для команд и тестов).
     */
    public static boolean canSee(UUID observer, UUID target) {
        return VanishState.get().canSee(observer, target);
    }

    /**
     * Пересчитывает трекинг сущности для всех наблюдателей в её измерении.
     *
     * <p>Нужен после смены видимости, чтобы клиент немедленно получил (или потерял) сущность,
     * а не ждал следующего естественного перехода трекера.
     */
    public static void refreshNearby(ServerPlayer subject) {
        if (subject == null) return;
        for (ServerPlayer observer : subject.serverLevel().players()) {
            if (observer.getUUID().equals(subject.getUUID())) continue;
            VanishTracker.updateFor(subject, observer);
        }
    }

    /**
     * Забывает наблюдателя, который вышел: временные права во время all больше не нужны.
     */
    public static void forgetObserver(UUID observer) {
        VanishState.get().forgetObserver(observer);
    }
}