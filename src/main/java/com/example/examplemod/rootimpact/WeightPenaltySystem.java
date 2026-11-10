package com.example.examplemod.rootimpact;

import com.example.examplemod.RootImpactConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система наложения штрафов на основе текущего веса игрока.
 * Пороги из ROOT_IMPACT_SPEC.md (не меняются):
 * 0-40%   — без штрафов
 * 41-65%  — запрет спринта
 * 66-80%  — Slowness II
 * 81-95%  — Slowness III
 * 96-100%+ — Slowness IV + Mining Fatigue II
 * <p>
 * Сила штрафов усилена примерно в 1.5 раза относительно исходных значений (уровни эффектов
 * подняты так, чтобы величина замедления выросла примерно в полтора раза): было
 * Slowness I/II/III + Mining Fatigue I, стало Slowness II/III/IV + Mining Fatigue II.
 * Пороги и сама структура штрафов оставлены прежними, новые типы дебаффов не добавлены.
 * <p>
 * Штрафы применяются раз в секунду (каждые 20 тиков) через счётчик тактов.
 */
public class WeightPenaltySystem {

    /** Интервал применения штрафов в тактах (20 тиков = 1 секунда) */
    private static final int APPLY_INTERVAL_TICKS = 20;

    /** Счётчик тактов для каждого UUID игрока. Сбрасывается при достижении APPLY_INTERVAL_TICKS. */
    private static final Map<UUID, Integer> TICK_COUNTERS = new ConcurrentHashMap<>();

    private static final Holder<MobEffect> SLOWNESS = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("minecraft:slowness")).orElseThrow();
    private static final Holder<MobEffect> DIG_SLOWDOWN = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("minecraft:mining_fatigue")).orElseThrow();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (player.isCreative() || player.isSpectator()) return;

        UUID uuid = player.getUUID();

        // Инкрементируем счётчик тактов для игрока
        int counter = TICK_COUNTERS.getOrDefault(uuid, 0) + 1;

        if (counter < APPLY_INTERVAL_TICKS) {
            // Сохраняем счётчик и выходим — штрафы ещё не пора применять
            TICK_COUNTERS.put(uuid, counter);
            return;
        }

        // Сбрасываем счётчик и применяем штрафы
        TICK_COUNTERS.put(uuid, 0);
        applyPenalties(player);
    }

    private void applyPenalties(Player player) {
        double currentWeight = PlayerWeightHandler.getCachedWeight(player);
        double maxWeight = RootImpactConfig.MAX_WEIGHT.getAsDouble();
        double ratio = currentWeight / maxWeight;

        if (ratio <= 0.40) {
            clearPenalties(player);
            return;
        }

        if (ratio <= 0.65) {
            clearSlownessEffects(player);
            applySprintBlock(player);
            return;
        }

        if (ratio <= 0.80) {
            applyEffect(player, SLOWNESS, 1);
            return;
        }

        if (ratio <= 0.95) {
            applyEffect(player, SLOWNESS, 2);
            return;
        }

        applyEffect(player, SLOWNESS, 3);
        applyEffect(player, DIG_SLOWDOWN, 1);
    }

    /**
     * Применяет эффект к игроку, если его ещё нет или уровень/длительность не совпадают.
     *
     * @param player        целевой игрок
     * @param effectHolder  тип эффекта
     * @param amplifier     уровень эффекта (0 = I, 1 = II, ...)
     */
    private void applyEffect(Player player, Holder<MobEffect> effectHolder, int amplifier) {
        MobEffectInstance current = player.getEffect(effectHolder);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > 100) {
            return;
        }
        player.addEffect(new MobEffectInstance(effectHolder, 120, amplifier, false, false, false));
    }

    /**
     * Снимает все штрафные эффекты (Slowness и Mining Fatigue).
     */
    private void clearPenalties(Player player) {
        player.removeEffect(SLOWNESS);
        player.removeEffect(DIG_SLOWDOWN);
    }

    /**
     * Снимает эффекты замедления (используется в диапазоне "запрет спринта").
     */
    private void clearSlownessEffects(Player player) {
        player.removeEffect(SLOWNESS);
        player.removeEffect(DIG_SLOWDOWN);
    }

    /**
     * Блокирует спринт игрока, если он активен.
     */
    private void applySprintBlock(Player player) {
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}