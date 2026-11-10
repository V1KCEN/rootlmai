package com.example.examplemod.rootimpact.creative;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверная (авторитетная) проверка правил Creative Limit.
 * <p>
 * Правила для ограниченного игрока в Creative:
 * <ol>
 *   <li>выборка предмета из Creative Menu ограничена общим счётом (по умолчанию 5 выдач):
 *       каждое РЕАЛЬНОЕ получение нового стака = {@code +1} к счётчику. Хотбар НЕ заблокирован —
 *       выдавать можно в любой слот {@code inventoryMenu} (1..45);</li>
 *   <li>предметы из реестра {@code registeritem} не выдаются из Creative Menu
 *       (но уже существующие предметы игрока никогда не удаляются);</li>
 *   <li>СКМ (средняя кнопка мыши): {@code handlePickItem} и pick-block/middle-drag запрещены
 *       полностью;</li>
 *   <li>блоки из реестра {@code registerblock} нельзя ставить (отменяется установка,
 *       предмет не изымается).</li>
 * </ol>
 * <p>
 * <b>Ключевое различие: выдача из Creative Menu ≠ перемещение существующего предмета.</b>
 * Ванильный клиент в Creative для ЛЮБОГО изменения слота инвентаря (в т.ч. обычного
 * перемещения) отправляет {@code ServerboundSetCreativeModeSlotPacket} с новым содержимым
 * слота ({@code CreativeModeInventoryScreen.slotClicked} → {@code handleCreativeModeItemAdd}),
 * поэтому по одному пакету нельзя напрямую отличить выдачу от перемещения. Различение
 * делается через «леджер» {@link MoveLedger}: когда пакет уменьшает/очищает слот,
 * вынутые предметы запоминаются, а когда следующий пакет добавляет такие же предметы,
 * они считаются перемещением (лимит не тратится, предмет не отклоняется). Выдачей считается
 * только ДОБАВЛЕНИЕ предметов, для которых в леджере нет «источника». Леджер привязан к UUID,
 * живёт 40 тиков и всегда опирается на уже применённое сервером уменьшение инвентаря.
 * <p>
 * Метка «Взять извне» ({@link CreativeLimitData}) — ЧИСТО ДЕКОРАТИВНАЯ: она не даёт прав,
 * не снимает ограничения и не влияет на классификацию операций (при сравнении стаков метка
 * игнорируется).
 */
public final class CreativeLimitEnforcement {

    /** Слот {@code -1} в пакете = выбрасывание предмета (не выдача). */
    private static final int DROP_SLOT = -1;

    /** Сколько тиков живёт запись о вынутых из инвентаря предметах. */
    private static final long LEDGER_TTL_TICKS = 40L;

    /** Сообщение при попытке взять предмет из реестра {@code registeritem}. */
    public static final Component MSG_ITEM_REGISTERED = Component.literal(
            "Creative Limit: этот предмет запрещён (registeritem) — выдача из Creative Menu недоступна.");

    /** Сообщение при реальной попытке СКМ/pick-item (только СКМ, не обычные перемещения). */
    public static final Component MSG_PICK_ITEM_BLOCKED = Component.literal(
            "Creative Limit: выдача через СКМ запрещена. Используйте основную часть инвентаря.");

    /** Сообщение при попытке выбросить предмет из Creative Menu (Q/THROW). */
    public static final Component MSG_CREATIVE_DROP_BLOCKED = Component.literal(
            "Creative Limit: выбрасывание предмета из Creative Menu запрещено.");

    /** Леджеры перемещения по UUID игрока. */
    private static final Map<UUID, MoveLedger> LEDGERS = new ConcurrentHashMap<>();

    /** Записи об уменьшении слота инвентаря игрока (для различения Q по своему предмету). */
    private static final Map<UUID, SlotRemoval> LAST_REMOVALS = new ConcurrentHashMap<>();

    /** Отложенные выбросы, ждущие подтверждающего пакета слота в том же тике. */
    private static final Map<UUID, PendingDrop> PENDING_DROPS = new ConcurrentHashMap<>();

    private CreativeLimitEnforcement() {
    }

    // ------------------------------------------------------------------
    // Пакеты Creative Menu
    // ------------------------------------------------------------------

    /**
     * Проверяет пакет изменения слота инвентаря в Creative
     * ({@code ServerboundSetCreativeModeSlotPacket}).
     * <p>
     * Вызывается из миксина в самом начале {@code handleSetCreativeModeSlot}.
     *
     * @param player  игрок
     * @param slotNum номер слота {@code inventoryMenu} ({@code -1} — дроп предмета)
     * @param stack   новое содержимое слота
     * @return {@code true} — пакет нужно отменить (это была запрещённая выдача из Creative Menu)
     */
    public static boolean handleCreativeSlot(ServerPlayer player, int slotNum, ItemStack stack) {
        CreativeLimitState state = CreativeLimitState.get();
        if (!state.isLimited(player)) return false;
        if (!player.gameMode.isCreative()) return false;   // правила Creative работают только в Creative

        long now = player.serverLevel().getGameTime();

        if (slotNum == DROP_SLOT) {
            // Q/THROW из Creative Menu. Валиный путь:
            //   CreativeModeInventoryScreen.slotClicked() -> player.drop(...) на клиенте +
            //   MultiPlayerGameMode.handleCreativeModeItemDrop(stack) ->
            //   ServerboundSetCreativeModeSlotPacket(-1, stack) ->
            //   ServerGamePacketListenerImpl.handleSetCreativeModeSlot() -> "flag = slotNum < 0"
            //   -> player.drop(itemstack, true).
            // slotNum = -1 отправляет ТОЛЬКО handleCreativeModeItemDrop, то есть creative-выброс.
            //
            // Различение «выброс из creative-меню» и «Q по своему предмету» (требование B)
            // делается по СОДЕРЖИМОМУ инвентаря, а не по порядку пакетов, потому что клиент шлёт
            // пару «-1 + пакет слота» в двух разных порядках:
            //   * обычная вкладка creative: слот (строка 321) приходит РАНЬШЕ, чем -1 (строка 328);
            //   * вкладка "Inventory": -1 (строка 245) приходит РАНЬШЕ, чем слот (строка 246).
            // Q по creative-слоту (строки 287-288) не сопровождается пакетом слота вовсе.
            //
            // Проверять «есть ли такой предмет в инвентаре» НЕЛЬЗЯ: тогда повторный Q по creative-меню
            // для уже имеющегося предмета дал бы бесконечное размножение в мире. Единственный надёжный
            // признак Q по своему предмету — сервер уже принял пакет слота, который УМЕНЬШИЛ слот игрока.
            //
            // Порядок «-1 раньше слота»: выброс откладываем и ждём подтверждающий пакет слота.
            SlotRemoval removal = LAST_REMOVALS.remove(player.getUUID());
            if (removal != null && removal.tick() == now
                    && ItemStack.isSameItemSameComponents(removal.item(), stack)
                    && removal.item().getCount() == stack.getCount()) {
                return false;                       // Q по уже имеющемуся предмету — ванильное поведение
            }
            PENDING_DROPS.put(player.getUUID(), new PendingDrop(stack, now));
            return true;                            // выброс ждёт подтверждения (см. confirmPendingDrop)
        }
        if (slotNum < 1 || slotNum > 45) return false;     // служебные слоты меню

        ItemStack current = player.inventoryMenu.getSlot(slotNum).getItem();
        // Метка «Взять извне» декоративная: при сравнении стаков её игнорируем.
        ItemStack existing = stripMarker(current);
        ItemStack incoming = stripMarker(stack);

        MoveLedger ledger = ledgerFor(player);
        ledger.expire(player);

        if (incoming.isEmpty()) {
            // Слот очищается: предметы уходят из инвентаря (перемещение/выброс) — запоминаем источник.
            if (!existing.isEmpty()) {
                ledger.remember(existing);
                // Q по своему предмету: клиент пришлёт следом пакет -1 с выброшенной частью.
                rememberRemoval(player, now, existing, existing.getCount());
            }
            confirmPendingDrop(player, now);
            return false;
        }

        boolean sameKind = ItemStack.isSameItemSameComponents(existing, incoming);
        int added = incoming.getCount() - (sameKind ? existing.getCount() : 0);

        // Вытесненные предметы (замена содержимого слота) тоже становятся «источником».
        if (!existing.isEmpty() && !sameKind) {
            ledger.remember(existing);
        }

        if (added <= 0) {
            // Слот уменьшился (в т.ч. Q по своему предмету: клиент уже снял часть и прислал остаток).
            // Запоминаем, ЧТО и СКОЛЬКО ушло из инвентаря, чтобы следующий пакет -1 можно было
            // отличить от выброса предмета, взятого из creative-меню.
            if (sameKind && existing.getCount() > incoming.getCount()) {
                rememberRemoval(player, now, existing, existing.getCount() - incoming.getCount());
            }
            confirmPendingDrop(player, now);
            return false;   // уменьшение количества/очистка — это не выдача
        }

        // Часть добавленного могла прийти из другого слота этого же инвентаря.
        int moved = ledger.absorb(incoming.getItem(), added);
        if (added - moved <= 0) {
            return false;   // обычное перемещение существующего предмета: лимит не тратится
        }

        // Реальная выдача из Creative Menu.
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(incoming.getItem());
        if (state.isItemRegistered(itemId)) {
            reject(player, MSG_ITEM_REGISTERED);
            return true;
        }

        if (!state.consumeGrant(player.getUUID())) {
            reject(player, limitReachedMessage(player));
            return true;
        }

        notifyGrants(player);
        return false;
    }

    /**
     * Запрещает «pick item» средней кнопкой мыши для ограниченного игрока в Creative.
     * <p>
     * Это ванильная часть СКМ-поведения — она отключается полностью, и только здесь
     * показывается сообщение про СКМ (обычные перемещения и выдачи сообщение не вызывают).
     *
     * @return {@code true} — пакет нужно отменить
     */
    public static boolean handlePickItem(ServerPlayer player) {
        CreativeLimitState state = CreativeLimitState.get();
        if (!state.isLimited(player)) return false;
        if (!player.gameMode.isCreative()) return false;
        player.displayClientMessage(MSG_PICK_ITEM_BLOCKED, true);
        return true;
    }

    /** Копия стака без декоративной метки «Взять извне» (для корректного сравнения). */
    private static ItemStack stripMarker(ItemStack stack) {
        if (stack.isEmpty() || !CreativeLimitData.isTakenOutside(stack)) return stack;
        ItemStack copy = stack.copy();
        copy.remove(CreativeLimitData.TAKEN_OUTSIDE.get());
        return copy;
    }

    /** Леджер игрока (создаётся при первом обращении). */
    private static MoveLedger ledgerFor(ServerPlayer player) {
        return LEDGERS.computeIfAbsent(player.getUUID(), id -> new MoveLedger());
    }

    /** Забывает состояние леджера игрока (выход из игры, снятие ограничения). */
    public static void clearLedger(UUID playerId) {
        if (playerId != null) {
            LEDGERS.remove(playerId);
            LAST_REMOVALS.remove(playerId);
            PENDING_DROPS.remove(playerId);
        }
    }

    /** Запись об отложенном выбросе, ожидающем подтверждающего пакета слота. */
    private record PendingDrop(ItemStack stack, long tick) {
    }

    /**
     * Записывает, что из слота игрока ушло {@code amount} предметов {@code item} (уменьшение слота).
     * Используется только для различения «Q по своему предмету» и «выброс из creative-меню»:
     * настоящая выдача сюда не попадает, потому что она увеличивает количество, а не уменьшает.
     */
    private static void rememberRemoval(ServerPlayer player, long tick, ItemStack item, int amount) {
        LAST_REMOVALS.put(player.getUUID(), new SlotRemoval(item.copyWithCount(amount), tick));
    }

    /**
     * Подтверждает отложенный выброс, если слот игрока только что уменьшился тем же предметом.
     * <p>
     * Вызывается сразу после фиксации уменьшения слота. Если игрок уменьшил слот тем же предметом,
     * отложенный выброс — это ванильный Q по собственному предмету, и мы выполняем его сами
     * (пакет с {@code slotNum = -1} был отменён). Если такого уменьшения не было, отложенного
     * выброса не существует: предмет был из creative-меню, и выброс остаётся отменённым.
     *
     * @param tick тик обработки пакета слота
     */
    private static void confirmPendingDrop(ServerPlayer player, long tick) {
        PendingDrop pending = PENDING_DROPS.get(player.getUUID());
        if (pending == null) return;

        SlotRemoval removal = LAST_REMOVALS.get(player.getUUID());
        if (removal == null || removal.tick() != tick
                || !ItemStack.isSameItemSameComponents(removal.item(), pending.stack())
                || pending.stack().getCount() > removal.item().getCount()) {
            return;   // подтверждения нет: выброс остаётся отменённым (creative-меню)
        }

        PENDING_DROPS.remove(player.getUUID());
        LAST_REMOVALS.remove(player.getUUID());
        // Ровно то, что сделала бы ваниллю при slotNum = -1.
        player.drop(pending.stack(), true);
    }

    /**
     * Истекает отложенные выбросы, не подтверждённые пакетом слота в тот же тик.
     * Вызывается каждый тик игрока: если подтверждения не было (выброс из creative-меню),
     * сообщаем о запрете. Предмет в мир не выбрасывается.
     */
    public static void expirePendingDrops(ServerPlayer player) {
        PendingDrop pending = PENDING_DROPS.remove(player.getUUID());
        if (pending == null) return;
        reject(player, MSG_CREATIVE_DROP_BLOCKED);
    }

    /**
     * Забывает сведения об уменьшении слота, если они не использованы в текущем тике.
     * Нужно, чтобы запись от давнего перемещения не могла подтвердить выброс позже.
     */
    public static void expireRemovals(ServerPlayer player) {
        SlotRemoval removal = LAST_REMOVALS.get(player.getUUID());
        if (removal != null && removal.tick() != player.serverLevel().getGameTime()) {
            LAST_REMOVALS.remove(player.getUUID());
        }
    }

    /**
     * Запись об уменьшении слота инвентаря игрока.
     *
     * @param item   предмет (с количеством = сколько ушло)
     * @param tick   тик, в котором обработан пакет слота
     */
    private record SlotRemoval(ItemStack item, long tick) {
    }

    // ------------------------------------------------------------------
    // Установка блоков (registerblock)
    // ------------------------------------------------------------------

    /**
     * Блокирует установку зарегистрированных блоков только ограниченным игроком в Creative.
     * <p>
     * В Survival и ванильному Creative размещение блоков не ограничивается — registerblock
     * — это ограничение Creative Limit, а не глобальный запрет для Survival.
     * <p>
     * Отмена {@link BlockEvent.EntityPlaceEvent} откатывает установку блока; предмет
     * (уже существующий у игрока) при этом НЕ изымается — registerblock запрещает только
     * установку. Метка «Взять извне» исключением больше не является.
     */
    public static void handleBlockPlace(ServerPlayer player, BlockEvent.EntityPlaceEvent event) {
        CreativeLimitState state = CreativeLimitState.get();
        if (!state.isLimited(player)) return;
        if (!player.gameMode.isCreative()) return; // правила Creative работают только в Creative

        BlockState placed = event.getPlacedBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        if (!state.isBlockRegistered(blockId)) return;

        event.setCanceled(true);
        player.displayClientMessage(Component.literal(
                "Creative Limit: блок «" + blockId + "» запрещён (registerblock)."), true);
    }

    // ------------------------------------------------------------------
    // Сообщения и отклонение
    // ------------------------------------------------------------------

    /** Отклоняет операцию: сообщение игроку + принудительная синхронизация инвентаря. */
    private static void reject(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        // Клиент уже применил изменение локально — возвращаем серверное состояние,
        // чтобы «фантомный» предмет из Creative Menu исчез.
        player.inventoryMenu.sendAllDataToRemote();
    }

    /** Сообщение о достигнутом лимите выдач. */
    private static Component limitReachedMessage(ServerPlayer player) {
        CreativeLimitState state = CreativeLimitState.get();
        return Component.literal("Creative grants: " + state.getUsedGrants(player.getUUID()) + "/"
                + state.getMaxGrants() + " — лимит выдач из Creative Menu исчерпан.");
    }

    /** Информирует игрока о счётчике выдач. */
    private static void notifyGrants(ServerPlayer player) {
        CreativeLimitState state = CreativeLimitState.get();
        player.displayClientMessage(Component.literal("Creative grants: "
                + state.getUsedGrants(player.getUUID()) + "/" + state.getMaxGrants()), true);
    }

    // ------------------------------------------------------------------
    // Леджер «вынутых» предметов (различает перемещение и выдачу)
    // ------------------------------------------------------------------

    /**
     * Помнит предметы, вынутые из инвентаря ограниченного игрока последними пакетами.
     * Если следующий пакет добавляет такие же предметы — это перемещение существующего
     * предмета, а не выдача из Creative Menu.
     */
    private static final class MoveLedger {

        /** Предмет → количество, вынутое из инвентаря */
        private final Map<Item, Integer> removed = new HashMap<>();

        /** Игровое время последнего изменения */
        private long lastChange = Long.MIN_VALUE;

        /** Сбрасывает устаревшие записи (клиент шлёт «источник» и «приёмник» подряд). */
        void expire(ServerPlayer player) {
            long now = player.serverLevel().getGameTime();
            if (this.lastChange != Long.MIN_VALUE && now - this.lastChange > LEDGER_TTL_TICKS) {
                this.removed.clear();
            }
            this.lastChange = now;
        }

        /** Запоминает вынутый из инвентаря стак. */
        void remember(ItemStack stack) {
            if (stack.isEmpty()) return;
            this.removed.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        /**
         * Пытается объяснить добавление предметов перемещением.
         *
         * @return сколько предметов удалось отнести к перемещению (не больше {@code count})
         */
        int absorb(Item item, int count) {
            Integer available = this.removed.get(item);
            if (available == null || available <= 0) return 0;
            int moved = Math.min(count, available);
            int left = available - moved;
            if (left > 0) {
                this.removed.put(item, left);
            } else {
                this.removed.remove(item);
            }
            return moved;
        }
    }
}
