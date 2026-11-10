package com.example.examplemod.rootimpact.vanish;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collection;

/**
 * Серверная логика VANISH: индивидуальная видимость, сброс целей мобов, события игроков.
 *
 * <p><b>Почему это не «обычная невидимость».</b> Состояние хранит матрицу «наблюдатель → может
 * видеть активатора», поэтому скрытие строго индивидуально: Y может не видеть X, при этом Z видит X.
 * Никакой {@code setInvisible(true)} не используется — невидимость применяется через фильтрацию
 * трекинга и пакетов на стороне сервера (см. {@link VanishVisibility}).
 */
public class VanishHandler {

    /** Сообщение об изменении видимости */
    private static final Component MSG = Component.literal("[Vanish] ");
/** Загружает сохранённое состояние VANISH из мира. */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        VanishState.attach(event.getServer().overworld());
        com.example.examplemod.ExampleMod.LOGGER.info(
                "Root Impact: VANISH — состояние загружено (активаторов с активным vanish: {}).",
                VanishState.get().getVanishedTargets().size());
    }

    /** Сохраняет состояние при остановке сервера. */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        event.getServer().overworld().getDataStorage().save();
    }

    /** Регистрирует команды {@code /vanish}. */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        VanishCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------
    // Серверные точки
    // ------------------------------------------------------------------

    /**
     * Мобы не должны начинать агриться на скрытого (vanish) игрока.
     * <p>
     * Событие {@link LivingChangeTargetEvent} отменяемое и срабатывает ДО установки цели,
     * поэтому новых целей не будет. Остальные мобы продолжают работать с другими игроками
     * как обычно — правило действует только на скрытых игроков.
     */
    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (!(newTarget instanceof ServerPlayer player)) return;
        if (!VanishState.get().isVanished(player.getUUID())) return;

        event.setCanceled(true);
    }

    /**
     * Каждый тик: уже выбранная цель сбрасывается, а скрытый игрок не выбирается целью.
     * <p>
     * {@link LivingChangeTargetEvent} закрывает только новые назначения, поэтому здесь
     * дополнительно снимаем текущую цель у мобов рядом и чистим «злобу» (anger) у
     * {@link NeutralMob}, чтобы зверь действительно «забыл» игрока, а не просто сменил цель
     * и тут же её вернул.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VanishState state = VanishState.get();
        if (!state.isVanished(player.getUUID())) return;
        if (player.tickCount % 5 != 0) return;   // 4 раза в секунду достаточно

        Collection<Mob> mobs = player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(16.0));

        for (Mob mob : mobs) {
            LivingEntity target = mob.getTarget();
            if (target == player) {
                mob.setTarget(null);
            }
            if (mob instanceof NeutralMob neutral) {
                // Злоба указывает по UUID и тоже должна быть снята.
                neutral.setPersistentAngerTarget(null);
                neutral.setRemainingPersistentAngerTime(0);
            }
            // Мобы, помнящие скрытого игрока как последнего обидчика, должны его забыть.
            if (mob.getLastHurtByMob() == player) {
                mob.setLastHurtByMob(null);
            }
        }
    }

    /** При смене измерения трекинг сбрасывается — сообщаем системе обновить видимость. */
    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VanishVisibility.refreshNearby(player);
    }

    /** При входе игрока его сущность должна отправиться только тем, кому он видим. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VanishVisibility.refreshNearby(player);
    }

    /** Выход игрока: он больше не наблюдатель, поэтому чистим временные записи о нём. */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VanishVisibility.forgetObserver(player.getUUID());
    }

    /** Аккуратная вспомогательная проверка для команд. */
    public static void report(ServerPlayer source, String message) {
        source.sendSystemMessage(MSG.copy().append(Component.literal(message).withStyle(ChatFormatting.GRAY)));
    }
}