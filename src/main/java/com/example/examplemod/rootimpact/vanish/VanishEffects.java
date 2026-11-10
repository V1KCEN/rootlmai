package com.example.examplemod.rootimpact.vanish;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * VANISH: фильтрация серверных эффектов, привязанных к позиции игрока.
 *
 * <p>Скрытие самой сущности уже убирает модель, nametag, коллизию, звуки шагов и клиентские
 * частицы. Здесь закрываются оставшиеся <b>серверные</b> эффекты, которые рассылаются всем
 * наблюдателям по координате, а не по сущности:
 * <ul>
 *   <li>{@code ClientboundLevelParticlesPacket} — частицы на позиции игрока;</li>
 *   <li>{@code ClientboundSoundPacket} — звуки, привязанные к сущности игрока.</li>
 * </ul>
 *
 * <p>Правило точечное: эффект отбрасывается только если он возник ВНУТРИ габарита скрытого игрока
 * и наблюдателю, которому этот игрок не виден. Эффекты рядом с игроком (например, взрыв в 10
 * блоках) проходят без изменений — скрывается только присутствие, а не игровой мир.
 */
public final class VanishEffects {

    /** Половина расширения габарита: эффекты «на самом игроке». */
    private static final double EXTRA_RADIUS = 0.6;

    private VanishEffects() {
    }

    /** Находится ли точка внутри тела скрытого от {@code observer} игрока. */
    public static boolean isInsideHiddenPlayer(ServerPlayer observer, double x, double y, double z,
                                               ServerLevel level) {
        if (observer == null || level == null) return false;
        if (!hasHiddenTargets(level)) return false;

        for (Player player : level.players()) {
            if (!(player instanceof ServerPlayer hidden)) continue;
            if (hidden.getUUID().equals(observer.getUUID())) continue;

            VanishState state = VanishState.get();
            if (!state.isVanished(hidden.getUUID())) continue;
            if (state.canSee(observer.getUUID(), hidden.getUUID())) continue;

            if (isInside(hidden, x, y, z)) return true;
        }
        return false;
    }

    /** Является ли сущность скрытой от наблюдателя (для звуков). */
    public static boolean isHiddenEntityFor(ServerPlayer observer, Entity entity) {
        if (observer == null || !(entity instanceof Player hidden)) return false;
        if (hidden.getUUID().equals(observer.getUUID())) return false;

        VanishState state = VanishState.get();
        return state.isVanished(hidden.getUUID())
                && !state.canSee(observer.getUUID(), hidden.getUUID());
    }

    /** Быстрая проверка: есть ли в измерении хоть один скрытый активатор. */
    private static boolean hasHiddenTargets(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (VanishState.get().isVanished(player.getUUID())) return true;
        }
        return false;
    }

    private static boolean isInside(Entity entity, double x, double y, double z) {
        var box = entity.getBoundingBox();
        return x >= box.minX - EXTRA_RADIUS && x <= box.maxX + EXTRA_RADIUS
                && y >= box.minY - EXTRA_RADIUS && y <= box.maxY + EXTRA_RADIUS
                && z >= box.minZ - EXTRA_RADIUS && z <= box.maxZ + EXTRA_RADIUS;
    }
}