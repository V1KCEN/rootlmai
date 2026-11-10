package com.example.examplemod.rootimpact;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashSet;
import java.util.Set;

/**
 * Калькулятор веса инвентаря игрока.
 * Поддерживает рекурсивный подсчёт содержимого шалкеров с коэффициентом 0.65.
 * Защита от бесконечной рекурсии через отслеживание уже обработанных ItemStack по identity.
 */
public class WeightCalculator {

    /**
     * Рассчитывает общий вес инвентаря игрока.
     */
    public static double calculateTotalWeight(Player player) {
        double total = 0.0;
        Set<Integer> visitedShulkers = new HashSet<>();

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            total += calculateStackWeight(player.getInventory().items.get(i), visitedShulkers);
        }

        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            total += calculateStackWeight(player.getInventory().armor.get(i), visitedShulkers);
        }

        total += calculateStackWeight(player.getInventory().offhand.get(0), visitedShulkers);

        return total;
    }

    private static double calculateStackWeight(ItemStack stack, Set<Integer> visitedShulkers) {
        if (stack.isEmpty()) return 0.0;

        double itemWeight = WeightRegistry.getWeight(stack.getItem());
        int count = stack.getCount();
        double total = itemWeight * count;

        if (WeightRegistry.isShulkerBox(stack.getItem())) {
            int identityHash = System.identityHashCode(stack);
            if (!visitedShulkers.contains(identityHash)) {
                visitedShulkers.add(identityHash);
                total += calculateShulkerContent(stack, visitedShulkers);
            }
        }

        return total;
    }

    private static double calculateShulkerContent(ItemStack shulkerStack, Set<Integer> visitedShulkers) {
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return 0.0;

        double contentWeight = 0.0;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack nestedStack = contents.getStackInSlot(i);
            if (nestedStack.isEmpty()) continue;
            contentWeight += calculateStackWeight(nestedStack, visitedShulkers);
        }

        double multiplier = com.example.examplemod.RootImpactConfig.SHULKER_CONTENT_MULTIPLIER.getAsDouble();
        return contentWeight * multiplier;
    }
}