package com.example.examplemod.rootimpact.repair;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Обработчик системы починки (раздел 4 ROOT_IMPACT_SPEC.md).
 * <p>
 * Реализует:
 * <ul>
 *   <li>Ограничение ремонтов через {@code repair_count} (Data Component)</li>
 *   <li>Запрет объединения двух одинаковых предметов в наковальне</li>
 *   <li>Разрешение починки материалом, переименования, переноса зачарований</li>
 *   <li>Усиленная починка материалом (железо ×1.5, алмаз ×1.25, незерит ×1.25)</li>
 *   <li>Увеличение {@code repair_count} на 1 за каждый успешный ремонт материалом</li>
 *   <li>Сообщение "Материал истощен, починка невозможна!" при достижении лимита</li>
 *   <li>Тултип "Осталось ремонтов: X"</li>
 * </ul>
 * <p>
 * Использует {@link AnvilUpdateEvent} для проверки/блокировки результата и применения
 * усиленной починки, и {@link ItemTooltipEvent} для отображения оставшихся ремонтов.
 * Работает только при взаимодействии с наковальней — без тиков и сканирования.
 */
public class RepairHandler {

    /** Сообщение при достижении лимита ремонтов */
    private static final Component REPAIR_LIMIT_MESSAGE =
            Component.literal("Материал истощен, починка невозможна!");

    /** Префикс тултипа оставшихся ремонтов */
    private static final String REMAINING_REPAIRS_PREFIX = "Осталось ремонтов: ";

    /**
     * Обрабатывает обновление наковальни.
     * <p>
     * Вызывается при каждом изменении слотов наковальни.
     * Проверяет:
     * <ul>
     *   <li>Если это объединение двух одинаковых предметов — блокирует результат</li>
     *   <li>Если это починка материалом и лимит достигнут — блокирует результат + сообщение</li>
     *   <li>Если это починка материалом — применяет усиленную починку</li>
     *   <li>Если это переименование или перенос зачарований — разрешает (vanilla)</li>
     * </ul>
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Пустой левый слот — нечего обрабатывать
        if (left.isEmpty()) return;

        // === Запрет объединения двух одинаковых предметов ===
        // Блокируем ТОЛЬКО чистый ремонт прочности: два одинаковых повреждаемых
        // предмета без операции зачарований. Если объединение выполняется ради
        // зачарований (правая часть вносит чары) — даём vanilla сформировать результат,
        // а последующая пост-обработка (AnvilMenuEnchantMixin) применяет капы ROOT IMPACT.
        if (!right.isEmpty() && isSameItemCombination(left, right)
                && !isEnchantOperation(right)) {
            event.setOutput(ItemStack.EMPTY);
            event.setCanceled(true);
            return;
        }

        // === Починка материалом ===
        if (!right.isEmpty() && isRepairMaterial(left, right)) {
            int repairCount = getRepairCount(left);
            int limit = RepairLimits.getRepairLimit(left.getItem());

            // Если предмет не ремонтопригоден — не вмешиваемся
            if (limit < 0) return;

            // Если лимит достигнут — блокируем результат и сообщаем
            if (repairCount >= limit) {
                event.setOutput(ItemStack.EMPTY);
                event.setCanceled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().displayClientMessage(REPAIR_LIMIT_MESSAGE, true);
                }
                return;
            }

            // Применяем усиленную починку материалом
            applyEnhancedRepair(event, left, right);
        }
        // Переименование и перенос зачарований — vanilla, не вмешиваемся
    }

    /**
     * Применяет усиленную починку материалом.
     * <p>
     * Копирует левый предмет, вычисляет усиленное восстановление прочности
     * (vanilla восстановление × множитель материала) и устанавливает результат.
     * <p>
     * {@code repair_count} увеличивается на 1 прямо в выходном стаке. Это гарантирует,
     * что и обычный клик (результат берётся из resultSlots), и SHIFT-click
     * (копия resultSlots через moveItemStackTo) получают одинаковый инкремент — ровно 1.
     * При переименовании/переносе зачарований (не ремонт материалом) метод не вызывается,
     * поэтому {@code repair_count} там не изменяется.
     */
    private static void applyEnhancedRepair(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        // Копируем левый предмет как основу результата
        ItemStack output = left.copy();

        // Количество материалов (правый слот)
        int materialCount = right.getCount();

        // Ванильное восстановление прочности за один материал: maxDamage / 4
        int maxDamage = output.getMaxDamage();
        int currentDamage = output.getDamageValue();
        int vanillaRestorePerMaterial = Math.min(currentDamage, maxDamage / 4);

        // Множитель усиленной починки для материала
        float multiplier = RepairLimits.getRepairMaterialMultiplier(right.getItem());

        // Усиленное восстановление прочности
        int enhancedRestore = (int) (vanillaRestorePerMaterial * materialCount * multiplier);

        // Новая прочность (не ниже 0)
        int newDamage = Math.max(0, currentDamage - enhancedRestore);
        output.setDamageValue(newDamage);

        // Запекаем увеличенный repair_count прямо в выходной стак.
        // Это тот самый ItemStack, который реально получит игрок и при обычном клике
        // (результат из resultSlots), и при SHIFT-click (копия resultSlots через moveItemStackTo).
        // Инкремент НЕ выполняется в AnvilRepairEvent: при SHIFT-click этот стак уже пуст
        // после переноса в инвентарь, поэтому на реальном предмете инкремент бы терялся.
        int newRepairCount = getRepairCount(left) + 1;
        setRepairCount(output, newRepairCount);

        // Устанавливаем результат и стоимость
        event.setOutput(output);
        event.setMaterialCost(materialCount);
        // Стоимость: базовая 1 + repair_count (как в ванильной наковальне)
        event.setCost(1L + getRepairCount(left));
    }

    /**
     * Добавляет тултип "Осталось ремонтов: X" для ремонтопригодных предметов.
     * <p>
     * X = лимит − repair_count.
     * Если предмет нельзя ремонтировать — строка не отображается.
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        int limit = RepairLimits.getRepairLimit(stack.getItem());
        if (limit < 0) return; // предмет не ремонтопригоден

        int repairCount = getRepairCount(stack);
        int remaining = limit - repairCount;

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.literal(REMAINING_REPAIRS_PREFIX + remaining));
    }

    /**
     * Проверяет, является ли комбинация объединением двух одинаковых предметов.
     * <p>
     * Два одинаковых предмета (например, два меча) — это ремонт объединением,
     * который запрещён. Материал (слиток) и книга — не одинаковые предметы.
     */
    private static boolean isSameItemCombination(ItemStack left, ItemStack right) {
        // Оба предмета должны быть одинаковыми
        if (left.getItem() != right.getItem()) return false;

        // Оба должны быть ремонтопригодными (иметь прочность)
        if (!left.isDamageableItem() || !right.isDamageableItem()) return false;

        // Книга зачарований — не предмет для объединения
        if (right.is(net.minecraft.tags.ItemTags.LECTERN_BOOKS)) return false;

        return true;
    }

    /**
     * Определяет, является ли объединение двух одинаковых предметов операцией зачарований,
     * а не чистым ремонтом прочности.
     * <p>
     * Операция считается операцией зачарований, если правая часть вносит какие-либо
     * зачарования в результат: это либо зачарованная книга (STORED_ENCHANTMENTS),
     * либо зачарованный инструмент/оружие/броня (компонент ENCHANTMENTS).
     * <p>
     * Если правая часть не содержит зачарований — это чистый ремонт прочности,
     * который запрещён ROOT IMPACT Repair System.
     */
    private static boolean isEnchantOperation(ItemStack right) {
        // Зачарованная книга — всегда операция зачарований.
        if (right.has(DataComponents.STORED_ENCHANTMENTS)) return true;
        // Зачарованный предмет (инструмент/оружие/броня).
        return !EnchantmentHelper.getEnchantmentsForCrafting(right).isEmpty();
    }

    /**
     * Проверяет, является ли правый слот материалом для починки левого.
     * <p>
     * Использует vanilla {@code Item.isValidRepairItem()}.
     */
    private static boolean isRepairMaterial(ItemStack left, ItemStack right) {
        return left.getItem().isValidRepairItem(left, right);
    }

    /**
     * Возвращает текущий {@code repair_count} предмета.
     */
    private static int getRepairCount(ItemStack stack) {
        return stack.getOrDefault(RepairCountComponent.REPAIR_COUNT.get(), 0);
    }

    /**
     * Устанавливает {@code repair_count} предмета.
     */
    private static void setRepairCount(ItemStack stack, int count) {
        stack.set(RepairCountComponent.REPAIR_COUNT.get(), count);
    }
}