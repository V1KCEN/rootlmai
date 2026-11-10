package com.example.examplemod.rootimpact.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Централизованные правила торговли (раздел 9 ROOT_IMPACT_SPEC.md).
 * <p>
 * Определяет запрещённые для торговли предметы и политику ценообразования.
 * АЛМАЗ — валюта проекта, полностью запрещён в любой сделке.
 * СНАРЯЖЕНИЕ выше каменного/кольчужного — запрещено.
 * ЖЕЛЕЗО разрешено, но дорого.
 */
public class TradeRules {

    // ========== АЛМАЗ — валюта, полностью запрещён ==========

    /**
     * Проверяет, содержит ли предмет алмаз в любом виде.
     * Алмаз является валютой ROOT IMPACT — не участвует в торговле.
     */
    public static boolean isDiamondRelated(Item item) {
        return item == Items.DIAMOND
                || item == Items.DIAMOND_BLOCK
                || item == Items.DIAMOND_ORE
                || item == Items.DEEPSLATE_DIAMOND_ORE
                || item == Items.DIAMOND_SWORD
                || item == Items.DIAMOND_PICKAXE
                || item == Items.DIAMOND_AXE
                || item == Items.DIAMOND_SHOVEL
                || item == Items.DIAMOND_HOE
                || item == Items.DIAMOND_HELMET
                || item == Items.DIAMOND_CHESTPLATE
                || item == Items.DIAMOND_LEGGINGS
                || item == Items.DIAMOND_BOOTS
                || item == Items.DIAMOND_HORSE_ARMOR;
    }

    // ========== ЗАПРЕЩЁННОЕ СНАРЯЖЕНИЕ (выше каменного/кольчужного) ==========

    /** Запрещённая железная/алмазная/незеритовая броня */
    private static final Set<Item> FORBIDDEN_ARMOR = Set.of(
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
    );

    /** Запрещённые инструменты (железо/алмаз/незерит) */
    private static final Set<Item> FORBIDDEN_TOOLS = Set.of(
            Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE
    );

    /** Запрещённое оружие (железо/алмаз/незерит) */
    private static final Set<Item> FORBIDDEN_WEAPONS = Set.of(
            Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
    );

    /**
     * Проверяет, запрещено ли продавать предмет снаряжения.
     * Разрешается только каменное оружие/инструменты и кольчужная броня.
     */
    public static boolean isForbiddenEquipment(Item item) {
        if (FORBIDDEN_ARMOR.contains(item)) return true;
        if (FORBIDDEN_TOOLS.contains(item)) return true;
        if (FORBIDDEN_WEAPONS.contains(item)) return true;
        return false;
    }

    // ========== ЖЕЛЕЗО (дорогое) ==========

    /** Железный слиток — разрешён, но стоит дороже ванильного */
    public static boolean isIronIngot(Item item) {
        return item == Items.IRON_INGOT;
    }

    /** Множитель цены на железный слиток (дорого, но не нереально) */
    public static final int IRON_PRICE_MULTIPLIER = 3;

    // ========== ПРОЧЕЕ ==========

    /**
     * Проверяет, является ли сделка с зачарованным предметом.
     * Используется для отбраковки зачарованных предметов и не может быть
     * определено до генерации MerchantOffer (см. FilteredItemListing).
     */
    public static boolean isTradeCraftType(net.minecraft.world.entity.npc.VillagerTrades.ItemListing listing) {
        // Зачарованные предметы/книги генерируются специальными типами
        return listing instanceof net.minecraft.world.entity.npc.VillagerTrades.EnchantedItemForEmeralds
                || listing instanceof net.minecraft.world.entity.npc.VillagerTrades.EnchantBookForEmeralds;
    }
}