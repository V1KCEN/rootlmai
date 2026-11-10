package com.example.examplemod.rootimpact.trade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

/**
 * Обёртка {@link VillagerTrades.ItemListing}, применяющая правила ROOT IMPACT
 * к уже сгенерированному {@link MerchantOffer}.
 * <p>
 * Проверяет результат и стоимость сделки ПОСЛЕ генерации, что позволяет
 * корректно обработать случайные зачарованные предметы и книги.
 * <p>
 * Логика фильтрации:
 * <ul>
 *   <li>Алмаз (в любом виде) — запрещено</li>
 *   <li>Зачарованные предметы/книги — запрещено</li>
 *   <li>Снаряжение выше каменного/кольчужного — запрещено</li>
 *   <li>Железный слиток — дороже (множитель цены)</li>
 * </ul>
 */
public class FilteredItemListing implements VillagerTrades.ItemListing {

    /** Оригинальная ванильная сделка */
    private final VillagerTrades.ItemListing wrapped;

    public FilteredItemListing(VillagerTrades.ItemListing wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Генерирует ванильную сделку, затем применяет правила торговли.
     * Возвращает {@code null} для запрещённых сделок (ванильный код пропускает null).
     */
    @Override
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        MerchantOffer offer = wrapped.getOffer(entity, random);
        if (offer == null) return null;

        // Проверяем результат и стоимость на запрещённое содержимое
        if (isForbiddenOffer(offer)) {
            return null; // сделка запрещена
        }

        // Удорожаем железо
        if (TradeRules.isIronIngot(getResultItem(offer))) {
            return createExpensiveIronOffer(offer);
        }

        return offer;
    }

    /**
     * Проверяет, является ли сделка запрещённой.
     */
    private static boolean isForbiddenOffer(MerchantOffer offer) {
        // Результат сделки
        ItemStack result = offer.getResult();
        if (!result.isEmpty()) {
            if (TradeRules.isDiamondRelated(result.getItem())) return true;
            if (TradeRules.isForbiddenEquipment(result.getItem())) return true;
            if (hasEnchantments(result)) return true;
        }

        // Стоимость A
        ItemStack costA = offer.getCostA();
        if (!costA.isEmpty() && TradeRules.isDiamondRelated(costA.getItem())) return true;

        // Стоимость B
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty() && TradeRules.isDiamondRelated(costB.getItem())) return true;

        return false;
    }

    /**
     * Проверяет, есть ли у предмета зачарования.
     */
    private static boolean hasEnchantments(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments != null && !enchantments.isEmpty();
    }

    /**
     * Возвращает предмет-результат сделки.
     */
    private static Item getResultItem(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        return result.isEmpty() ? null : result.getItem();
    }

    /**
     * Создаёт новый offer с увеличенной ценой железа.
     * <p>
     * Увеличивает количество слитков в стоимости A в {@link TradeRules#IRON_PRICE_MULTIPLIER} раз.
     * Создаётся новый {@link MerchantOffer}, так как costA immutable в ванильном конструкторе.
     */
    private static MerchantOffer createExpensiveIronOffer(MerchantOffer offer) {
        ItemCost costA = offer.getItemCostA();
        int newCount = costA.count() * TradeRules.IRON_PRICE_MULTIPLIER;
        ItemCost newCostA = new ItemCost(costA.item(), newCount, costA.components());

        Optional<ItemCost> costB = offer.getItemCostB();

        return new MerchantOffer(
                newCostA,
                costB,
                offer.getResult().copy(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier()
        );
    }
}