package com.example.examplemod.rootimpact.creative;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Общие константы и «метки» системы Creative Limit, доступные и на сервере, и на клиенте.
 * <p>
 * Нужны клиентским Mixin'ам (запрет СКМ): клиент не знает серверное состояние
 * {@link CreativeLimitState}, но видит синхронизированные признаки ограничения —
 * постоянный эффект {@code glowing} (неудаляемое свечение) на игроке в Creative.
 * Итоговая защита всё равно серверная: клиентские проверки лишь не дают ванильному
 * клиенту выполнить запрещённое действие.
 */
public final class CreativeLimitMarkers {

    /** ID модификатора здоровья Creative Limit (синхронизируется на клиент вместе с атрибутами) */
    public static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(com.example.examplemod.ExampleMod.MODID, "creative_limit_health");

    private CreativeLimitMarkers() {
    }

    /**
     * Проверка «метки ограничения» для клиентской стороны: игрок в Creative и имеет
     * постоянное свечение, которое ставит система Creative Limit.
     */
    public static boolean hasMarkers(Player player) {
        return player != null && player.isCreative() && player.hasEffect(MobEffects.GLOWING);
    }
}
