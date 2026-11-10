package com.example.examplemod.rootimpact;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик событий для обновления веса игрока.
 * Использует кеш с флагом dirty — вес пересчитывается только при изменении инвентаря.
 * Никаких тиков — только event-based обновление.
 */
public class PlayerWeightHandler {
    private static final Map<UUID, Double> WEIGHT_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DIRTY_FLAGS = new ConcurrentHashMap<>();

    /**
     * Возвращает закешированный вес игрока. Если dirty — пересчитывает.
     */
    public static double getCachedWeight(Player player) {
        UUID uuid = player.getUUID();
        Boolean dirty = DIRTY_FLAGS.get(uuid);
        if (dirty != null && dirty) {
            double weight = WeightCalculator.calculateTotalWeight(player);
            WEIGHT_CACHE.put(uuid, weight);
            DIRTY_FLAGS.put(uuid, false);
            return weight;
        }
        return WEIGHT_CACHE.getOrDefault(uuid, 0.0);
    }

    /**
     * Помечает игрока как dirty — вес будет пересчитан при следующем запросе.
     */
    public static void markDirty(Player player) {
        DIRTY_FLAGS.put(player.getUUID(), true);
    }

    /**
     * Очищает кеш при выходе игрока.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUUID();
        WEIGHT_CACHE.remove(uuid);
        DIRTY_FLAGS.remove(uuid);
    }

    /**
     * Инициализирует кеш при входе игрока.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        markDirty(player);
    }

    /**
     * Изменение инвентаря — основной триггер пересчёта веса.
     * Срабатывает при открытии/закрытии контейнера (сундук, печь, верстак и т.д.).
     * Замена: вместо generic PlayerContainerEvent используем более специфичный
     * PlayerContainerEvent.Close — изменение инвентаря происходит при закрытии,
     * а не при открытии контейнера.
     */
    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        // Игнорируем открытие — вес не меняется от простого открытия
    }

    /**
     * Срабатывает при закрытии контейнера — потенциальное изменение инвентаря.
     * markDirty вызывается, так как игрок мог переместить предметы.
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    /**
     * Срабатывает при подборе предмета с земли.
     * Используется Post-фаза, так как базовый ItemEntityPickupEvent — абстрактный.
     */
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        Player player = event.getPlayer();
        markDirty(player);
    }

    /**
     * Срабатывает при выбрасывании предмета из инвентаря (клавиша Q).
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        markDirty(player);
    }

    /**
     * Срабатывает при взаимодействии с миром (например, установка/разрушение блока
     * предметом из инвентаря).
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    /**
     * Смерть игрока — сбрасываем кеш, т.к. инвентарь мог измениться.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        markDirty(player);
    }

    /**
     * Возврат предметов при возрождении (keepInventory).
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        markDirty(player);
    }

    /**
     * Изменение размеров инвентаря (например, эффект/мод).
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        markDirty(player);
    }
}