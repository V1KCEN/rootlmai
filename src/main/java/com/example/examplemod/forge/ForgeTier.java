package com.example.examplemod.forge;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Уровни кузницы (Tier) согласно ROOT_IMPACT_SPEC.md.
 * <p>
 * Каждый уровень определяет:
 * <ul>
 *   <li>Какие предметы требуют этот уровень для крафта</li>
 *   <li>Радиус проверки (по XZ)</li>
 *   <li>Радиус по Y (для Tier III — 1 блок)</li>
 * </ul>
 */
public enum ForgeTier {

    /** Tier I — Железо. Радиус 3 блока вокруг верстака. */
    TIER_1(3, 3) {
        @Override
        public boolean isRequiredFor(Item item) {
            return TIER_1_ITEMS.contains(item);
        }
    },

    /** Tier II — Алмаз. Радиус 2 блока вокруг верстака. */
    TIER_2(2, 2) {
        @Override
        public boolean isRequiredFor(Item item) {
            return TIER_2_ITEMS.contains(item);
        }
    },

    /** Tier III — Незерит. Радиус 2 блока по XZ, 1 блок по Y вокруг кузнечного стола. */
    TIER_3(2, 1) {
        @Override
        public boolean isRequiredFor(Item item) {
            return TIER_3_ITEMS.contains(item);
        }
    };

    private final int radiusXZ;
    private final int radiusY;

    ForgeTier(int radiusXZ, int radiusY) {
        this.radiusXZ = radiusXZ;
        this.radiusY = radiusY;
    }

    public int getRadiusXZ() { return radiusXZ; }
    public int getRadiusY() { return radiusY; }

    /**
     * Проверяет, требует ли данный предмет этот уровень кузницы.
     */
    public abstract boolean isRequiredFor(Item item);

    // ========== Наборы предметов ==========

    /** Железная броня, инструменты, оружие — Tier I */
    private static final Set<Item> TIER_1_ITEMS = Set.of(
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE
    );

    /** Алмазная броня, инструменты, оружие — Tier II */
    private static final Set<Item> TIER_2_ITEMS = Set.of(
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE
    );

    /** Незеритовое снаряжение — Tier III */
    private static final Set<Item> TIER_3_ITEMS = Set.of(
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE
    );

    /**
     * Определяет, какой уровень кузницы требуется для крафта данного предмета.
     *
     * @return Tier, или null если предмет не требует кузницы
     */
    public static ForgeTier getRequiredTier(Item item) {
        if (item == null) return null;
        for (ForgeTier tier : values()) {
            if (tier.isRequiredFor(item)) {
                return tier;
            }
        }
        return null;
    }
}