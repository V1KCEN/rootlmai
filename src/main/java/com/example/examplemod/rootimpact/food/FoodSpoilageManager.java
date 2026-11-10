package com.example.examplemod.rootimpact.food;

import com.example.examplemod.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер порчи еды (раздел 9 ROOT_IMPACT_SPEC.md).
 * <p>
 * Каждый портящийся пищевой ItemStack хранит Data Component
 * {@code created_game_time} — игровое время (в тиках), когда этот
 * конкретный стак был создан (по спецификации: createdGameTime).
 * <p>
 * Время создания выставляется при:
 * <ul>
 *   <li>крафте (PlayerEvent.ItemCraftedEvent)</li>
 *   <li>плавке (PlayerEvent.ItemSmeltedEvent)</li>
 *   <li>подборе готовой еды (ItemEntityPickupEvent.Pre) — если у стака ещё нет createdGameTime</li>
 * </ul>
 * Если у стака уже есть createdGameTime — он НЕ перезаписывается (перемещение,
 * повторный подбор НЕ «омолаживает» еду).
 * <p>
 * Проверка порчи выполняется в {@code PlayerTickEvent.Post} 1 раз в секунду по
 * всем слотам инвентаря игрока. При превышении срока хранения (gameTime >=
 * createdGameTime + shelfLife) весь стак заменяется на Rotten Food (количество
 * сохраняется), timestamp с Rotten Food удаляется (не портится повторно).
 * <p>
 * MERGE: при фактическом объединении двух одинаковых пищевых ItemStack
 * (через {@link ItemStackedOnOtherEvent}) итоговый стак наследует САМОЕ СТАРОЕ
 * значение created_game_time. Никакой глобальной синхронизации таймеров
 * одинаковых предметов в инвентаре НЕТ — только при реальном объединении.
 * <p>
 * Холодные биомы: в биомах с базовой температурой <= 0.0 (снежные/ледяные)
 * эффективный срок хранения умножается на 3 (скорость порчи ×0.33).
 * Используется только игровое время мира (level.getGameTime()).
 * <p>
 * Размеры стаков НЕ изменяются этой системой. Тыквенный/максимальный стек
 * обрабатывается отдельно (вне проверки порчи).
 */
public class FoodSpoilageManager {

    /** Интервал проверки в тиках (20 тиков = 1 секунда) */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** Множитель срока хранения в холодном биоме (скорость порчи ×0.33 → срок ×3) */
    private static final double COLD_BIOME_SHELF_MULTIPLIER = 3.0;

    /** Порог температуры (включительно) для холодного биома */
    private static final float COLD_BIOME_TEMP_THRESHOLD = 0.0F;

    /** Счётчик тиков для каждого игрока */
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    /**
     * Периодическая проверка инвентаря (раз в секунду).
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        int counter = tickCounters.getOrDefault(uuid, 0) + 1;
        if (counter < CHECK_INTERVAL_TICKS) {
            tickCounters.put(uuid, counter);
            return;
        }
        tickCounters.put(uuid, 0);

        checkInventory(player);
    }

    /**
     * Крафт: новый созданный ItemStack получает createdGameTime (если еда и ещё нет времени).
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getCrafting();
        if (stack.isEmpty() || !FoodSpoilageRegistry.isPerishable(stack.getItem())) return;
        setCreatedIfAbsent(stack, player.level().getGameTime());
    }

    /**
     * Плавка (печь/коптильня/плавильня): готовый продукт получает createdGameTime.
     */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getSmelting();
        if (stack.isEmpty() || !FoodSpoilageRegistry.isPerishable(stack.getItem())) return;
        setCreatedIfAbsent(stack, player.level().getGameTime());
    }

    /**
     * Подбор еды: устанавливает createdGameTime только если его ещё нет (не «омолаживает»).
     */
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty() || !FoodSpoilageRegistry.isPerishable(stack.getItem())) return;

        boolean hadCreated = stack.has(FoodSpoilageData.CREATED_GAME_TIME.get());
        setCreatedIfAbsent(stack, player.level().getGameTime());

        // Логируем только при СОЗДАНИИ (не при обычном переносе) — для диагностики
        if (!hadCreated) {
            ExampleMod.LOGGER.info(
                    "[FoodSpoilage] CREATED item={} count={} gameTime={} created={} shelfLifeTicks={}",
                    stack.getItem(), stack.getCount(),
                    player.level().getGameTime(),
                    stack.getOrDefault(FoodSpoilageData.CREATED_GAME_TIME.get(), -1L),
                    getEffectiveShelfFor(player, stack.getItem())
            );
        }
    }

    /**
     * MERGE: при фактическом объединении двух одинаковых пищевых ItemStack
     * итоговый стак наследует САМОЕ СТАРОЕ created_game_time.
     * <p>
     * Срабатывает при клике предметом на другой в слоте (инвентарь/контейнер).
     * Если оба стака — одна и та же еда и оба имеют created_game_time,
     * то в слот записывается минимальное (старое) значение.
     */
    @SubscribeEvent
    public void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) return;

        ItemStack carried = event.getCarriedItem();
        ItemStack stackedOn = event.getStackedOnItem();

        if (carried.isEmpty() || stackedOn.isEmpty()) return;
        if (carried.getItem() != stackedOn.getItem()) return;
        if (!FoodSpoilageRegistry.isPerishable(carried.getItem())) return;

        Long carriedCreated = carried.get(FoodSpoilageData.CREATED_GAME_TIME.get());
        Long stackedOnCreated = stackedOn.get(FoodSpoilageData.CREATED_GAME_TIME.get());
        if (carriedCreated == null || stackedOnCreated == null) return;

        long oldest = Math.min(carriedCreated, stackedOnCreated);

        // Записываем самое старое время в слот (результат объединения)
        ItemStack slotStack = event.getSlot().getItem();
        if (!slotStack.isEmpty() && slotStack.getItem() == carried.getItem()) {
            slotStack.set(FoodSpoilageData.CREATED_GAME_TIME.get(), oldest);
            ExampleMod.LOGGER.info(
                    "[FoodSpoilage] MERGE item={} firstTimestamp={} secondTimestamp={} resultTimestamp={}",
                    carried.getItem(), stackedOnCreated, carriedCreated, oldest
            );
        }
    }

    /**
     * Обрабатывает содержимое открываемого контейнера (для порчи еды в контейнере).
     */
    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        long gameTime = player.level().getGameTime();
        boolean cold = isColdBiome(player);
        processContainerSlots(player, event.getContainer().slots, gameTime, cold);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        long gameTime = player.level().getGameTime();
        boolean cold = isColdBiome(player);
        processContainerSlots(player, event.getContainer().slots, gameTime, cold);
    }

    /**
     * Проходит по слотам меню, собирает стаки из мира и обрабатывает порчу.
     */
    private void processContainerSlots(Player player, List<net.minecraft.world.inventory.Slot> slots,
                                       long gameTime, boolean cold) {
        if (slots == null) return;
        for (net.minecraft.world.inventory.Slot slot : slots) {
            if (slot == null || slot.container == player.getInventory()) continue;
            ItemStack original = slot.getItem();
            if (original == null || original.isEmpty()) continue;
            if (!FoodSpoilageRegistry.isPerishable(original.getItem())) continue;

            ItemStack result = checkAndSpoil(player, original, gameTime, cold);
            if (result != original) {
                slot.set(result);
            }
        }
    }

    /**
     * Проходит по инвентарю игрока: проверяет порчу и заменяет на Rotten Food.
     */
    private void checkInventory(Player player) {
        Inventory inv = player.getInventory();
        long gameTime = player.level().getGameTime();
        boolean cold = isColdBiome(player);

        // Основные слоты (36)
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack original = inv.items.get(i);
            ItemStack result = checkAndSpoil(player, original, gameTime, cold);
            if (result != original) {
                inv.setItem(i, result);
            }
        }
        // Броня
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack original = inv.armor.get(i);
            ItemStack result = checkAndSpoil(player, original, gameTime, cold);
            if (result != original) {
                inv.setItem(i, result);
            }
        }
        // Оффхэнд
        ItemStack off = inv.offhand.get(0);
        ItemStack offResult = checkAndSpoil(player, off, gameTime, cold);
        if (offResult != off) {
            inv.setItem(40, offResult);
        }
    }

    /**
     * Проверяет один стак: инициализирует created при отсутствии, при истечении
     * заменяет на Rotten Food (кол-во сохраняется, Rotten не портится повторно).
     */
    private ItemStack checkAndSpoil(Player player, ItemStack stack, long gameTime, boolean cold) {
        if (stack.isEmpty() || !FoodSpoilageRegistry.isPerishable(stack.getItem())) return stack;

        Long created = stack.get(FoodSpoilageData.CREATED_GAME_TIME.get());
        if (created == null) {
            // Еда без времени (старый мир/новый предмет без инициализации) — инициализируем
            setCreatedIfAbsent(stack, gameTime);
            ExampleMod.LOGGER.info(
                    "[FoodSpoilage] CREATED item={} count={} gameTime={} created={} shelfLifeTicks={}",
                    stack.getItem(), stack.getCount(), gameTime,
                    stack.getOrDefault(FoodSpoilageData.CREATED_GAME_TIME.get(), -1L),
                    getEffectiveShelfFor(player, stack.getItem())
            );
            return stack;
        }

        long shelfLife;
        if (cold) {
            long base = FoodSpoilageRegistry.getShelfLifeTicks(stack.getItem());
            if (base < 0) return stack;
            shelfLife = Math.round(base * COLD_BIOME_SHELF_MULTIPLIER);
        } else {
            shelfLife = FoodSpoilageRegistry.getShelfLifeTicks(stack.getItem());
            if (shelfLife < 0) return stack;
        }

        long spoilTime = created + shelfLife;
        if (gameTime >= spoilTime) {
            int count = stack.getCount();
            ItemStack rotten = new ItemStack(FoodItems.ROTTEN_FOOD.get(), count);
            rotten.applyComponents(stack.getComponents());
            rotten.remove(FoodSpoilageData.CREATED_GAME_TIME.get());

            ExampleMod.LOGGER.info(
                    "[FoodSpoilage] SPOILED item={} count={} gameTime={} spoilTime={}",
                    stack.getItem(), count, gameTime, spoilTime
            );
            return rotten;
        }
        return stack;
    }

    /**
     * Возвращает эффективный срок хранения в тиках с учётом холодного биома.
     */
    private long getEffectiveShelfFor(Player player, Item item) {
        long base = FoodSpoilageRegistry.getShelfLifeTicks(item);
        if (base < 0) return -1;
        if (isColdBiome(player)) {
            return Math.round(base * COLD_BIOME_SHELF_MULTIPLIER);
        }
        return base;
    }

    /**
     * Определяет, находится ли игрок в холодном биоме (базовая температура <= 0.0).
     */
    private boolean isColdBiome(Player player) {
        Biome biome = player.level().getBiome(player.blockPosition()).value();
        return biome.getBaseTemperature() <= COLD_BIOME_TEMP_THRESHOLD;
    }

    /**
     * Устанавливает createdGameTime, если его ещё нет.
     */
    private void setCreatedIfAbsent(ItemStack stack, long gameTime) {
        if (stack.has(FoodSpoilageData.CREATED_GAME_TIME.get())) return;
        stack.set(FoodSpoilageData.CREATED_GAME_TIME.get(), gameTime);
    }

    /**
     * Tooltip.
     * <p>
     * Для Гнили — отдельная подпись «Непригодно к употреблению» (тёмно-красная).
     * Для портящейся еды — степень свежести с цветовой индикацией.
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Гниль: отдельная подпись
        if (stack.is(FoodItems.ROTTEN_FOOD.get())) {
            MutableComponent inedible = Component.literal("Непригодно к употреблению")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED));
            event.getToolTip().add(inedible);
            return;
        }

        if (!FoodSpoilageRegistry.isPerishable(stack.getItem())) return;

        Player player = event.getEntity();
        if (player == null) return;

        Long created = stack.get(FoodSpoilageData.CREATED_GAME_TIME.get());
        if (created == null) return;

        long gameTime = player.level().getGameTime();
        long shelfLife = getEffectiveShelfFor(player, stack.getItem());
        if (shelfLife < 0) return;

        long age = Math.max(0, gameTime - created);
        double ratio = (double) age / (double) shelfLife;

        // При ratio >= 1.0 еда уже заменена на Гниль обработчиком порчи.
        if (ratio >= 1.0) {
            return;
        }

        String label;
        ChatFormatting color;
        if (ratio < 0.25) {
            label = "Очень свежее";
            color = ChatFormatting.GREEN;
        } else if (ratio < 0.50) {
            label = "Свежее";
            color = ChatFormatting.DARK_GREEN;
        } else if (ratio < 0.70) {
            label = "Нормальное";
            color = ChatFormatting.YELLOW;
        } else if (ratio < 0.85) {
            label = "Начинает портиться";
            color = ChatFormatting.GOLD;
        } else {
            label = "Скоро испортится";
            color = ChatFormatting.DARK_RED;
        }

        MutableComponent freshness = Component.literal(label).setStyle(Style.EMPTY.withColor(color));
        event.getToolTip().add(freshness);
    }

    /**
     * Очищает счётчик при выходе.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        tickCounters.remove(event.getEntity().getUUID());
    }
}