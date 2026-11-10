package com.example.examplemod.rootimpact.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

import java.util.Map;

/**
 * Применяет правила торговли ROOT IMPACT к {@link VillagerTrades.TRADES}.
 * <p>
 * Оборачивает каждую ванильную сделку в {@link FilteredItemListing},
 * которая проверяет сгенерированный {@code MerchantOffer} на запрещённое
 * содержимое (алмазы, зачарования, снаряжение выше лимита) и удорожает железо.
 * <p>
 * Вызывается один раз при инициализации мода (в commonSetup).
 * Не требует Mixin — модифицирует содержимое статической карты TRADES.
 */
public class TradeFilter {

    /**
     * Применяет фильтрацию ко всем профессиям жителей.
     * <p>
     * Для каждой профессии и каждого уровня заменяет массив {@code ItemListing[]}
     * на массив обёрток {@code FilteredItemListing}.
     */
    public static void apply() {
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry
                : VillagerTrades.TRADES.entrySet()) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> tradesByLevel = entry.getValue();
            for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> levelEntry : tradesByLevel.int2ObjectEntrySet()) {
                VillagerTrades.ItemListing[] listings = levelEntry.getValue();
                if (listings == null || listings.length == 0) continue;

                // Оборачиваем каждую сделку в фильтр
                VillagerTrades.ItemListing[] filtered = new VillagerTrades.ItemListing[listings.length];
                for (int i = 0; i < listings.length; i++) {
                    filtered[i] = new FilteredItemListing(listings[i]);
                }
                levelEntry.setValue(filtered);
            }
        }
    }
}