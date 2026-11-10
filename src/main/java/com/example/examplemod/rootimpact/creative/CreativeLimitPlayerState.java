package com.example.examplemod.rootimpact.creative;

import com.example.examplemod.RootImpactConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Серверное применение/снятие «меток» Creative Limit на игроке.
 * <p>
 * Что делает ограничение с телом игрока:
 * <ul>
 *   <li>включает «настоящую» уязвимость в Creative — снимает творческую
 *       неуязвимость ({@code abilities.invulnerable}), поэтому урон, мобы и
 *       падения снова работают;</li>
 *   <li>повышает максимальное здоровье на {@code creativeLimitBonusHealth}
 *       (по умолчанию +30 → 50 HP = 25 сердец) через постоянный (transient)
 *       модификатор атрибута {@code max_health};</li>
 *   <li>накладывает бесконечное и неудаляемое свечение ({@code Glowing}),
 *       которое переприменяется каждый тик.</li>
 * </ul>
 * Все значения переприменяются каждый тик, поэтому не теряются после респавна,
 * смены измерения, {@code /effect clear} и молока.
 */
public final class CreativeLimitPlayerState {

    private CreativeLimitPlayerState() {
    }

    /**
     * Применяет состояние ограничения к игроку (вызывается каждый тик).
     */
    public static void apply(ServerPlayer player) {
        applyVulnerability(player);
        applyHealth(player);
        applyGlowing(player);
    }

    /**
     * Возвращает ванильное поведение игрока после снятия ограничения.
     */
    public static void clear(ServerPlayer player) {
        clearHealth(player);
        clearGlowing(player);
        restoreVanillaAbilities(player);
    }

    /**
     * Снимает творческую неуязвимость (только для режима Creative).
     * <p>
     * Ваниль ставит {@code abilities.invulnerable = true} при входе в Creative
     * ({@code GameType.updatePlayerAbilities}), поэтому значение поддерживается
     * принудительно. Клиенту отправляется обновление способностей.
     */
    public static void applyVulnerability(ServerPlayer player) {
        if (!player.isCreative()) return;
        if (player.getAbilities().invulnerable) {
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
        }
    }

    /**
     * Восстанавливает творческую неуязвимость (вызывается при снятии ограничения).
     */
    public static void restoreVanillaAbilities(ServerPlayer player) {
        if (!player.isCreative()) return;
        if (!player.getAbilities().invulnerable) {
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
        }
    }

    /**
     * Повышает максимальное здоровье ограниченного игрока.
     * <p>
     * Используется временный модификатор атрибута: он не пишется в NBT игрока
     * и автоматически исчезает при смерти/респавне, после чего ставится заново.
     */
    public static void applyHealth(ServerPlayer player) {
        double bonus = RootImpactConfig.CREATIVE_LIMIT_BONUS_HEALTH.getAsDouble();
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;

        boolean added = false;
        if (bonus > 0.0D && attribute.getModifier(CreativeLimitMarkers.HEALTH_MODIFIER_ID) == null) {
            attribute.addTransientModifier(new AttributeModifier(
                    CreativeLimitMarkers.HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            added = true;
        }

        if (added) {
            // при включении ограничения игрок получает полное здоровье
            player.setHealth(player.getMaxHealth());
        } else if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Снимает бонус здоровья и удерживает здоровье в новых границах.
     */
    public static void clearHealth(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        if (attribute.getModifier(CreativeLimitMarkers.HEALTH_MODIFIER_ID) == null) return;

        attribute.removeModifier(CreativeLimitMarkers.HEALTH_MODIFIER_ID);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Накладывает бесконечное свечение (метка ограниченного игрока).
     * <p>
     * Эффект переприменяется при любом снятии, поэтому является неудаляемым.
     */
    public static void applyGlowing(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.GLOWING);
        if (current == null || !current.isInfiniteDuration() || current.getAmplifier() != 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }
    }

    /**
     * Снимает свечение, поставленное системой Creative Limit.
     */
    public static void clearGlowing(ServerPlayer player) {
        player.removeEffect(MobEffects.GLOWING);
    }
}
