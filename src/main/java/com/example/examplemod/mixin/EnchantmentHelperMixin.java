package com.example.examplemod.mixin;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.rootimpact.enchant.EnchantmentLimiter;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

/**
 * Перехватывает {@link EnchantmentHelper#selectEnchantment} — центральную точку,
 * через которую выбираются зачарования в чародейском столе, у жителей и в loot.
 * <p>
 * Правила ROOT IMPACT:
 * <ul>
 *   <li>Полностью запрещённые (Mending, Infinity, Thorns, Punch, Fire Aspect, Power) удаляются из списка.</li>
 *   <li>Уровни выше капа (Sharpness II, Protection II, Projectile Protection II, Blast Protection II,
 *       Fire Protection I, Efficiency III, Looting I, Fortune I) занижаются до капа.</li>
 * </ul>
 * Уже существующие зачарованные предметы/книги этот метод не затрагивает —
 * он вызывается только для создания НОВЫХ зачарований.
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {

    @Inject(method = "selectEnchantment", at = @At("RETURN"), cancellable = true)
    private static void rootImpact_filterEnchantments(
            RandomSource random,
            ItemStack stack,
            int level,
            Stream<Holder<Enchantment>> possibleEnchantments,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        List<EnchantmentInstance> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) {
            return;
        }

        boolean changed = false;
        ListIterator<EnchantmentInstance> it = list.listIterator();
        while (it.hasNext()) {
            EnchantmentInstance instance = it.next();
            Holder<Enchantment> holder = instance.enchantment;

            // Запрещённые — удаляем полностью
            if (EnchantmentLimiter.isForbidden(holder)) {
                it.remove();
                changed = true;
                ExampleMod.LOGGER.info("[EnchantLimiter] Removed forbidden enchantment {} from selection",
                        holder.unwrapKey().orElse(null));
                continue;
            }

            // Занижаем уровни до капа
            int cap = EnchantmentLimiter.getLevelCap(holder);
            if (cap >= 0 && instance.level > cap) {
                it.set(new EnchantmentInstance(holder, cap));
                changed = true;
                ExampleMod.LOGGER.info("[EnchantLimiter] Capped enchantment {} level {} -> {}",
                        holder.unwrapKey().orElse(null), instance.level, cap);
            }
        }

        // Список изменился — возвращаем новый (Java collections обновил тот же объект)
        if (changed) {
            cir.setReturnValue(list);
        }
    }
}