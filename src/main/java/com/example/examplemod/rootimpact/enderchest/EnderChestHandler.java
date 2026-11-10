package com.example.examplemod.rootimpact.enderchest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Полное удаление Эндер-сундука из игрового процесса (отдельная механика).
 * <p>
 * Эндер-сундук не должен существовать как игровой объект:
 * <ul>
 *   <li>нельзя скрафтить (результат обнуляется)</li>
 *   <li>нельзя открыть (GUI не открывается, блок уничтожается)</li>
 *   <li>нельзя подобрать (предмет обнуляется)</li>
 *   <li>нельзя использовать (блок заменяется воздухом)</li>
 * </ul>
 * <p>
 * Также добавляет предупреждающий тултип для предмета эндер-сундука,
 * чтобы игрок не тратил ресурсы впустую.
 * <p>
 * Использует только события NeoForge — без Mixin.
 * Работает только при взаимодействии игрока, без тиков и сканирования мира.
 */
public class EnderChestHandler {

    /** Сообщение при попытке использовать эндер-сундук */
    private static final Component ENDER_CHEST_DISABLED =
            Component.literal("Эндер-сундуки отключены модом ROOT IMPACT.");

    /**
     * Блокирует открытие эндер-сундука при правом клике.
     * <p>
     * При попытке открыть эндер-сундук:
     * <ul>
     *   <li>GUI не открывается (событие отменяется)</li>
     *   <li>блок уничтожается (заменяется воздухом)</li>
     *   <li>предмет не выпадает</li>
     *   <li>игрок получает сообщение</li>
     * </ul>
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // Проверяем, является ли блок эндер-сундуком
        if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST)) return;

        Player player = event.getEntity();

        // Отменяем взаимодействие (GUI не откроется)
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);

        // Уничтожаем блок (заменяем воздухом, без дропа)
        level.removeBlock(pos, false);

        // Сообщение игроку
        player.displayClientMessage(ENDER_CHEST_DISABLED, true);
    }

    /**
     * Блокирует крафт эндер-сундука.
     * <p>
     * Если игрок скрафтил эндер-сундук, результат обнуляется.
     * Рецепт существует, но результат не выдаётся.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (crafted.is(Blocks.ENDER_CHEST.asItem())) {
            // Обнуляем результат крафта
            crafted.setCount(0);
            event.getEntity().displayClientMessage(ENDER_CHEST_DISABLED, true);
        }
    }

    /**
     * Блокирует подбор эндер-сундука с земли.
     * <p>
     * Если игрок пытается подобрать эндер-сундук, предмет обнуляется.
     */
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.is(Blocks.ENDER_CHEST.asItem())) {
            // Запрещаем подбор
            event.setCanPickup(TriState.FALSE);
            // Уничтожаем предмет
            event.getItemEntity().discard();
            event.getPlayer().displayClientMessage(ENDER_CHEST_DISABLED, true);
        }
    }

    /**
     * Добавляет предупреждающий тултип для предмета эндер-сундука.
     * <p>
     * Отображается везде, где Minecraft показывает описание предмета:
     * в инвентаре, сундуках, JEI и т.д.
     * <p>
     * Название предмета НЕ изменяется — добавляется только описание.
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(Blocks.ENDER_CHEST.asItem())) return;

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.literal("Вырезан из игры модом ROOT IMPACT.")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        tooltip.add(Component.literal("Не создавайте и не переносите этот предмет —")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("ресурсы будут потеряны.")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
    }
}