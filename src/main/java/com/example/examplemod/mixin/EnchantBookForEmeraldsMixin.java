package com.example.examplemod.mixin;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.rootimpact.enchant.EnchantmentLimiter;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехватывает {@link VillagerTrades.EnchantBookForEmeralds#getOffer} — точку,
 * где библиотекарь генерирует зачарованные книги НАПРЯМУЮ через
 * {@link EnchantedBookItem#createForEnchantment}, минуя {@code selectEnchantment}.
 * <p>
 * ROOT IMPACT: запрещённые чары (Mending, Infinity, Thorns, Punch, Fire Aspect, Power)
 * не предлагаются; уровни выше капа занижаются до капа.
 * Если после фильтрации книга недопустима — возвращается обычная книга (не пустая сделка).
 */
@Mixin(VillagerTrades.EnchantBookForEmeralds.class)
public class EnchantBookForEmeraldsMixin {

    @Inject(method = "getOffer", at = @At("RETURN"), cancellable = true)
    private void rootImpact_filterLibrarianBook(
            Entity trader,
            RandomSource random,
            CallbackInfoReturnable<MerchantOffer> cir
    ) {
        MerchantOffer offer = cir.getReturnValue();
        if (offer == null) return;

        ItemStack result = offer.getResult();
        if (result.isEmpty() || !result.is(Items.ENCHANTED_BOOK)) return;

        // Извлекаем зачарования из книги
        var enchantments = result.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);

        boolean changed = false;
        net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutable =
                new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);

        for (java.util.Map.Entry<Holder<Enchantment>, Integer> e : enchantments.entrySet()) {
            Holder<Enchantment> holder = e.getKey();
            int level = e.getValue();

            // Запрещённые — заменяем книгу на обычную (не создаём запрещённый чар)
            if (EnchantmentLimiter.isForbidden(holder)) {
                ExampleMod.LOGGER.info("[EnchantLimiter] Librarian: removed forbidden {} from book offer",
                        holder.unwrapKey().orElse(null));
                cir.setReturnValue(new MerchantOffer(
                        offer.getItemCostA(),
                        offer.getItemCostB(),
                        new ItemStack(Items.BOOK),
                        offer.getMaxUses(),
                        offer.getXp(),
                        offer.getPriceMultiplier()
                ));
                return;
            }

            // Занижаем уровень до капа
            int cap = EnchantmentLimiter.getLevelCap(holder);
            if (cap >= 0 && level > cap) {
                mutable.set(holder, cap);
                changed = true;
                ExampleMod.LOGGER.info("[EnchantLimiter] Librarian: capped {} {} -> {}",
                        holder.unwrapKey().orElse(null), level, cap);
            }
        }

        if (changed) {
            ItemStack newBook = new ItemStack(Items.ENCHANTED_BOOK);
            newBook.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            cir.setReturnValue(new MerchantOffer(
                    offer.getItemCostA(),
                    offer.getItemCostB(),
                    newBook,
                    offer.getMaxUses(),
                    offer.getXp(),
                    offer.getPriceMultiplier()
            ));
        }
    }
}