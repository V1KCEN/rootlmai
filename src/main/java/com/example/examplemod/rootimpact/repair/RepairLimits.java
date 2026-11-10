package com.example.examplemod.rootimpact.repair;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Централизованный реестр лимитов ремонтов (раздел 4 ROOT_IMPACT_SPEC.md).
 * <p>
 * Определяет максимальное количество ремонтов для каждого типа предмета.
 * Все лимиты хранятся здесь — никаких магических чисел в других классах.
 * <p>
 * Лимиты:
 * <ul>
 *   <li>Железная броня/оружие/инструменты — 4</li>
 *   <li>Алмазная броня/оружие/инструменты — 2</li>
 *   <li>Незеритовая броня/оружие/инструменты — 2</li>
 *   <li>Луки — 2</li>
 *   <li>Арбалеты — 2</li>
 *   <li>Трезубцы — 2</li>
 * </ul>
 */
public class RepairLimits {

    /** Железная броня — 4 ремонта */
    private static final Set<Item> IRON_ARMOR = Set.of(
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS
    );

    /** Железное оружие — 4 ремонта */
    private static final Set<Item> IRON_WEAPONS = Set.of(
            Items.IRON_SWORD, Items.IRON_AXE
    );

    /** Железные инструменты — 4 ремонта */
    private static final Set<Item> IRON_TOOLS = Set.of(
            Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE
    );

    /** Алмазная броня — 2 ремонта */
    private static final Set<Item> DIAMOND_ARMOR = Set.of(
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
    );

    /** Алмазное оружие — 2 ремонта */
    private static final Set<Item> DIAMOND_WEAPONS = Set.of(
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE
    );

    /** Алмазные инструменты — 2 ремонта */
    private static final Set<Item> DIAMOND_TOOLS = Set.of(
            Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE
    );

    /** Незеритовая броня — 2 ремонта */
    private static final Set<Item> NETHERITE_ARMOR = Set.of(
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
    );

    /** Незеритовое оружие — 2 ремонта */
    private static final Set<Item> NETHERITE_WEAPONS = Set.of(
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE
    );

    /** Незеритовые инструменты — 2 ремонта */
    private static final Set<Item> NETHERITE_TOOLS = Set.of(
            Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE
    );

    /** Луки — 2 ремонта */
    private static final Set<Item> BOWS = Set.of(Items.BOW);

    /** Арбалеты — 2 ремонта */
    private static final Set<Item> CROSSBOWS = Set.of(Items.CROSSBOW);

    /** Трезубцы — 2 ремонта */
    private static final Set<Item> TRIDENTS = Set.of(Items.TRIDENT);

    /**
     * Возвращает максимальное количество ремонтов для предмета.
     *
     * @param item предмет
     * @return лимит ремонтов, или -1 если предмет не ремонтопригоден
     */
    public static int getRepairLimit(Item item) {
        if (item == null) return -1;

        if (IRON_ARMOR.contains(item)) return 4;
        if (IRON_WEAPONS.contains(item)) return 4;
        if (IRON_TOOLS.contains(item)) return 4;

        if (DIAMOND_ARMOR.contains(item)) return 2;
        if (DIAMOND_WEAPONS.contains(item)) return 2;
        if (DIAMOND_TOOLS.contains(item)) return 2;

        if (NETHERITE_ARMOR.contains(item)) return 2;
        if (NETHERITE_WEAPONS.contains(item)) return 2;
        if (NETHERITE_TOOLS.contains(item)) return 2;

        if (BOWS.contains(item)) return 2;
        if (CROSSBOWS.contains(item)) return 2;
        if (TRIDENTS.contains(item)) return 2;

        return -1; // не ремонтопригодный предмет
    }

    /**
     * Возвращает множитель эффективности ремонта для материала.
     * <p>
     * Усиленная починка (раздел 4 ROOT_IMPACT_SPEC.md):
     * <ul>
     *   <li>Железный слиток — ×1.5</li>
     *   <li>Алмаз — ×1.25</li>
     *   <li>Незеритовый слиток — ×1.25</li>
     * </ul>
     *
     * @param material материал для починки
     * @return множитель эффективности (1.0 если материал не даёт усиления)
     */
    public static float getRepairMaterialMultiplier(Item material) {
        if (material == Items.IRON_INGOT) return 1.5F;
        if (material == Items.DIAMOND) return 1.25F;
        if (material == Items.NETHERITE_INGOT) return 1.25F;
        return 1.0F;
    }
}