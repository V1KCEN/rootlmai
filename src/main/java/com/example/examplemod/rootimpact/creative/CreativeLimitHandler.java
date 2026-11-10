package com.example.examplemod.rootimpact.creative;

import com.example.examplemod.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик системы Creative Limit (сервер, NeoForge EVENT_BUS).
 * <p>
 * Отвечает за:
 * <ul>
 *   <li>загрузку/сохранение состояния из сохранения мира;</li>
 *   <li>per-tick поддержку меток ограничения (здоровье, уязвимость, свечение);</li>
 *   <li>сброс счётчика выдач при выходе из Creative;</li>
 *   <li>запрет установки блоков из реестра {@code registerblock};</li>
 *   <li>декоративную метку «Взять извне»: ей помечаются предметы, вынесенные из инвентаря игрока наружу
 *       ограниченного игрока (подбор с земли, крафт, переплавка, выброс, передача в контейнер).
 *       Метка НЕ даёт прав, НЕ снимает ограничения и НЕ используется как обход (не ставится автоматически: не для всего инвентаря, не каждый тик)
 *       {@code registeritem}/{@code registerblock} — только отображается в tooltip;</li>
 *   <li>регистрацию команд {@code /creativelimit} (включая подкоманды
 *       {@code registeritem}/{@code registerblock}).</li>
 * </ul>
 * Проверки пакетов Creative Menu живут в миксине
 * {@code CreativeLimitCreativeSlotMixin} и вызывают
 * {@link CreativeLimitEnforcement}.
 */
public class CreativeLimitHandler {

    /** Игроки, которым уже применены метки ограничения (нужно для корректного снятия) */
    private static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------
    // Жизненный цикл сервера
    // ------------------------------------------------------------------

    /** Загружает сохранённое состояние Creative Limit из мира. */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CreativeLimitState.attach(event.getServer().overworld());
        CreativeLimitState state = CreativeLimitState.get();
        ExampleMod.LOGGER.info(
                "Root Impact: Creative Limit — состояние загружено (ограничить всех: {}, игроков точечно: {}, предметов в registeritem: {}, блоков в registerblock: {}).",
                state.isLimitAll(), state.getLimitedPlayers().size(),
                state.getRegisteredItems().size(), state.getRegisteredBlocks().size());
    }

    /** Дополнительное сохранение при остановке сервера. */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        event.getServer().overworld().getDataStorage().save();
        ExampleMod.LOGGER.info("Root Impact: Creative Limit — состояние сохранено.");
    }

    // ------------------------------------------------------------------
    // Игроки
    // ------------------------------------------------------------------

    /** При входе игрока сразу применяем ограничение, если оно на нём. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;
        if (!player.isCreative()) return;                 // метки живут только в Creative
        MARKED.add(player.getUUID());
        CreativeLimitPlayerState.apply(player);
    }

    /** При выходе снимаем признак применения (состояние в мире сохраняется). */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MARKED.remove(player.getUUID());
        CreativeLimitEnforcement.clearLedger(player.getUUID());
    }

    /** Каждый тик поддерживаем здоровье/уязвимость/свечение ограниченного игрока. */
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (CreativeLimitState.get().isLimited(player) && player.isCreative()) {
            MARKED.add(player.getUUID());
            CreativeLimitPlayerState.apply(player);
        } else if (MARKED.remove(player.getUUID())) {
            CreativeLimitPlayerState.clear(player);
        }


        // Выброс из Creative Menu, не подтверждённый уменьшением слота в тот же тик (то есть Q по
        // предмету creative-меню), окончательно отклоняется: в мир ничего не падает.
        CreativeLimitEnforcement.expirePendingDrops(player);
        CreativeLimitEnforcement.expireRemovals(player);
    }

    /** Смена режима игры: выход из Creative сбрасывает израсходованный лимит выборок. */
    @SubscribeEvent
    public void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;

        if (event.getNewGameMode() == GameType.CREATIVE) {
            MARKED.add(player.getUUID());
            CreativeLimitPlayerState.apply(player);
        } else {
            // Выход из Creative — лимит выборок начинается заново
            CreativeLimitState.get().resetGrants(player.getUUID());
            CreativeLimitPlayerState.restoreVanillaAbilities(player);
        }
    }

    /** Запрет установки блоков из реестра {@code registerblock}. */
    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CreativeLimitEnforcement.handleBlockPlace(player, event);
    }

    // ------------------------------------------------------------------
    // Декоративная метка «Взять извне»
    // ------------------------------------------------------------------

    /**
     * Метка ставится ТОЛЬКО по фактическим событиям происхождения предмета
     * (подбор, крафт, переплавка, выброс), а не периодически и не всему инвентарю:
     * предмет, просто лежащий в инвентаре ограниченного игрока, метки НЕ получает.
     * Метка ничего не разрешает и не запрещает — она лишь декоративная и сохраняется
     * при разделении стака, перемещении между слотами, складывании в контейнеры,
     * передаче другому игроку, перезаходе и перезапуске сервера (Data Component сериализуется).
     */

    /**
     * Снимок количества предметов в инвентаре перед подбором (по UUID).
     * <p>
     * Нужен, чтобы пометить меткой «Взять извне» РОВНО те стеки, куда реально лёг
     * подобранный предмет. Пометить все совпадающие стеки нельзя: тогда предмет,
     * ранее выданный из Creative Menu, получил бы метку, хотя его происхождение — Creative.
     */
    private static final Map<UUID, int[]> PICKUP_BEFORE = new ConcurrentHashMap<>();

    /** Запоминает содержимое инвентаря перед подбором предмета. */
    @SubscribeEvent
    public void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;

        Inventory inventory = player.getInventory();
        int[] before = new int[inventory.getContainerSize()];
        for (int i = 0; i < before.length; i++) {
            ItemStack stack = inventory.getItem(i);
            before[i] = stack.isEmpty() ? 0 : stack.getCount();
        }
        PICKUP_BEFORE.put(player.getUUID(), before);
    }

    /**
     * Подобранный с земли предмет считается полученным вне Creative Menu и получает
     * декоративную метку «Взять извне».
     * <p>
     * Метка ставится ТОЛЬКО в слоты, количество которых увеличилось (куда реально лёг
     * предмет). Остальные стеки инвентаря метку не получают.
     */
    @SubscribeEvent
    public void onItemPickupPost(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        int[] before = PICKUP_BEFORE.remove(player.getUUID());
        if (before == null) return;
        if (!CreativeLimitState.get().isLimited(player)) return;

        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < Math.min(before.length, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.getCount() <= before[i]) continue;
            if (CreativeLimitData.isTakenOutside(stack)) continue;
            CreativeLimitData.markTakenOutside(stack);
            changed = true;
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** Скрафченный предмет считается полученным вне Creative Menu. */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;
        CreativeLimitData.markTakenOutside(event.getCrafting());
    }

    /** Выплавленный предмет считается полученным вне Creative Menu. */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;
        CreativeLimitData.markTakenOutside(event.getSmelting());
    }

    /**
     * Выброшенный ограниченным игроком предмет уходит в мир с декоративной меткой
     * «Взять извне», чтобы её увидел получатель (другой игрок, воронка, контейнер).
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!CreativeLimitState.get().isLimited(player)) return;
        CreativeLimitData.markTakenOutside(event.getEntity().getItem());
    }

    /** Тултип метки «Взять извне». */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!CreativeLimitData.isTakenOutside(event.getItemStack())) return;
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.literal(CreativeLimitData.TAKEN_OUTSIDE_LABEL)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)));
    }

    // ------------------------------------------------------------------
    // Команды
    // ------------------------------------------------------------------

    /** Регистрирует команды системы. */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CreativeLimitCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------
    // Вспомогательное (используется командами)
    // ------------------------------------------------------------------

    /** Помечает игрока как «метки применены» (чтобы снятие состояния было корректным). */
    public static void markApplied(Player player) {
        MARKED.add(player.getUUID());
    }

    /** Снимает признак применения меток. */
    public static void unmarkApplied(Player player) {
        MARKED.remove(player.getUUID());
    }
}
