package com.example.examplemod.rootimpact.tagri;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Обработчик событий системы тегов предметов ROOT IMPACT.
 * <p>
 * Отвечает за:
 * <ul>
 *   <li>Расход ресурса gentle при получении урона игроком ({@link LivingDamageEvent.Post})</li>
 *   <li>Выпадение предметов с тегом {@code deathdrop} при смерти ({@link LivingDeathEvent})</li>
 *   <li>Отображение строк lore в тулпите предмета ({@link ItemTooltipEvent})</li>
 * </ul>
 * <p>
 * Метод {@link #dropDeathdropItems(ServerPlayer)} выделен отдельно, чтобы в будущем
 * его можно было вызвать из обработчика превращения игрока в призрака без дублирования логики.
 */
public class TagRiHandler {

    /**
     * Обрабатывает получение урона сущностью.
     * Вызывается после применения урона к здоровью ({@link LivingDamageEvent.Post}),
     * поэтому ресурс не расходуется, если урон был отменён.
     * Один тик получения урона = одно событие = одно списание ресурса с каждого gentle-предмета.
     */
    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;

        float damage = event.getNewDamage();
        if (damage <= 0.0F) return; // урон не был фактически применён

        boolean changed = false;
        List<ItemStack> items = player.getInventory().items;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            TagRiComponent component = stack.get(TagRiComponent.TAG_RI.get());
            if (component == null || !component.hasGentleTag()) continue;

            int newUses = component.gentleUses() - 1;

            if (newUses <= 0) {
                // Ресурс исчерпан — предмет исчезает
                playBreakSound(player);
                stack.setCount(0);
                changed = true;
            } else {
                // Уменьшаем ресурс gentle на 1
                TagRiComponent updated = new TagRiComponent(new ArrayList<>(component.tags()), newUses);
                stack.set(TagRiComponent.TAG_RI.get(), updated);
                changed = true;
            }
        }

        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void playBreakSound(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }

    // ------------------------------------------------------------------
    // Смерть игрока — deathdrop
    // ------------------------------------------------------------------

    /**
     * Выбрасывает предметы с тегом {@code deathdrop} из инвентаря игрока на землю
     * и очищает слоты, чтобы ванильный {@code dropAllLoot()} не продублировал их.
     * <p>
     * Этот метод выделен отдельно, чтобы в будущем его можно было вызвать
     * из обработчика превращения игрока в призрака (система жизней/призраков
     * пока не реализована). В этом случае все остальные предметы сохраняются
     * для последующего восстановления, а предметы с deathdrop выпадают сразу.
     *
     * @param player игрок, который умирает (или превращается в призрака)
     * @return true, если были выброшены хотя бы один предмет deathdrop
     */
    public static boolean dropDeathdropItems(ServerPlayer player) {
        var inventory = player.getInventory();
        boolean dropped = false;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            TagRiComponent component = stack.get(TagRiComponent.TAG_RI.get());
            if (component == null || !component.hasTag(TagRiTags.DEATHDROP)) continue;

            // Выбрасываем копию стека в мир (весь стек)
            ItemStack dropCopy = stack.copy();
            player.drop(dropCopy, false);

            // Очищаем слот, чтобы ванильный dropAllLoot() не выбросил предмет повторно
            inventory.setItem(i, ItemStack.EMPTY);
            dropped = true;
        }

        if (dropped) {
            player.inventoryMenu.broadcastChanges();
        }
        return dropped;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;

        dropDeathdropItems(player);
    }

    // ------------------------------------------------------------------
    // Тултип — отображение lore строк тегов
    // ------------------------------------------------------------------

    /**
     * Добавляет строки lore тегов в тултип предмета.
     * Существующие строки предмета не затрагиваются — добавляются только строки ROOT IMPACT.
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        TagRiComponent component = stack.get(TagRiComponent.TAG_RI.get());
        if (component == null || component.tags().isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        Set<String> tags = component.tags();

        if (tags.contains(TagRiTags.DEATHDROP)) {
            tooltip.add(TagRiTags.LORE_DEATHDROP);
        }
        if (tags.contains(TagRiTags.GENTLE_5)) {
            tooltip.add(TagRiTags.LORE_GENTLE_5);
        }
        if (tags.contains(TagRiTags.GENTLE_3)) {
            tooltip.add(TagRiTags.LORE_GENTLE_3);
        }
        if (tags.contains(TagRiTags.GENTLE_1)) {
            tooltip.add(TagRiTags.LORE_GENTLE_1);
        }
    }

    // ------------------------------------------------------------------
    // Объединение стаков — слияние ресурса gentle
    // ------------------------------------------------------------------

    /**
     * При объединении двух стаков с gentle-тегами сохраняет максимальный оставшийся ресурс,
     * чтобы предотвратить дублирование ресурса при слиянии стаков.
     */
    @SubscribeEvent
    public void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack stacked = event.getStackedOnItem();

        TagRiComponent carriedComp = carried.has(TagRiComponent.TAG_RI.get())
                ? carried.get(TagRiComponent.TAG_RI.get()) : null;
        TagRiComponent stackedComp = stacked.has(TagRiComponent.TAG_RI.get())
                ? stacked.get(TagRiComponent.TAG_RI.get()) : null;

        if (carriedComp == null && stackedComp == null) return;

        if (carriedComp != null && stackedComp != null) {
            // Оба стака имеют теги — объединяем, берем max gentleUses
            Set<String> mergedTags = new HashSet<>(carriedComp.tags());
            mergedTags.addAll(stackedComp.tags());
            int maxUses = Math.max(carriedComp.gentleUses(), stackedComp.gentleUses());
            TagRiComponent merged = new TagRiComponent(new ArrayList<>(mergedTags), maxUses);
            stacked.set(TagRiComponent.TAG_RI.get(), merged);
            carried.set(TagRiComponent.TAG_RI.get(), merged);
        } else if (carriedComp != null) {
            stacked.set(TagRiComponent.TAG_RI.get(), carriedComp);
        } else if (stackedComp != null) {
            carried.set(TagRiComponent.TAG_RI.get(), stackedComp);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TagRiCommands.register(event.getDispatcher());
    }
}
