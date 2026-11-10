package com.example.examplemod.mixin;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.rootimpact.enchant.EnchantmentLimiter;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Единая авторитетная пост-обработка зачарований на выходе наковальни (раздел 7).
 * <p>
 * {@link AnvilUpdateEvent} стреляется ДО вычисления vanilla-результата, поэтому событийный
 * обработчик не может увидеть фактический результат. Этот mixin перехватывает
 * {@link AnvilMenu#createResult()} в {@code TAIL} — после того, как vanilla сформировала
 * результат — и применяет капы/запреты ROOT IMPACT к слоту результата (слот 2).
 * <p>
 * Правила (для каждого зачарования в результате):
 * <ul>
 *   <li>Запрещённое (Mending, Infinity, ...): если оно уже было на одном из входов — сохраняется;
 *       если появилось «с нуля» — удаляется. Операция от этого НЕ отменяется.</li>
 *   <li>Уровень уже существовал на входе (включая сверхлимитный Sharpness III/V) — сохраняется как есть.</li>
 *   <li>Повышение выше капа — принудительно ограничивается капом, но операция не отменяется.</li>
 * </ul>
 * Работает одинаково для обычного клика и SHIFT-click, т.к. результат слот наковальни
 * формируется один раз в {@code createResult()} и далее только переносится игроком.
 */
@Mixin(AnvilMenu.class)
public class AnvilMenuEnchantMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void rootImpact_applyEnchantLimits(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;

        ItemStack left = menu.getSlot(0).getItem();
        ItemStack right = menu.getSlot(1).getItem();
        ItemStack result = menu.getSlot(2).getItem();

        if (result.isEmpty()) return;
        if (!EnchantmentHelper.canStoreEnchantments(result)) return;

        ItemEnchantments outputEnch = EnchantmentHelper.getEnchantmentsForCrafting(result);
        if (outputEnch.isEmpty()) return;

        ItemEnchantments leftEnch = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnch = EnchantmentHelper.getEnchantmentsForCrafting(right);

        boolean changed = false;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(outputEnch);

        for (Map.Entry<Holder<Enchantment>, Integer> e : outputEnch.entrySet()) {
            Holder<Enchantment> holder = e.getKey();
            int outLevel = e.getValue();

            int leftLevel = leftEnch.getLevel(holder);
            int rightLevel = rightEnch.getLevel(holder);
            boolean existedOnInput = leftLevel > 0 || rightLevel > 0;

            // Запрещённые чары: существующие — сохраняем (включая из книг Mending/Infinity/Flame),
            // появившиеся «с нуля» — не добавляем в результат (но операцию не отменяем).
            if (EnchantmentLimiter.isForbidden(holder)) {
                if (!existedOnInput) {
                    mutable.set(holder, 0);
                    changed = true;
                    ExampleMod.LOGGER.info("[EnchantLimiter] Anvil: removed newly created forbidden {}",
                            holder.unwrapKey().orElse(null));
                }
                continue;
            }

            int cap = EnchantmentLimiter.getLevelCap(holder);
            if (cap < 0) continue; // нет лимита

            int maxInput = Math.max(leftLevel, rightLevel);

            // Если уровень уже существовал на входе (в т.ч. сверхлимитный Sharpness III/V) — сохранять.
            // НЕ понижаем существующий сверхкаповый уровень ретроактивно.
            if (outLevel <= maxInput) {
                continue;
            }

            // Повышение (outLevel > maxInput): если превышает кап — ограничиваем капом, операцию не отменяем.
            if (outLevel > cap) {
                mutable.set(holder, cap);
                changed = true;
                ExampleMod.LOGGER.info("[EnchantLimiter] Anvil: prevented upgrade {} {} -> {} (inputs max {})",
                        holder.unwrapKey().orElse(null), outLevel, cap, maxInput);
            }
        }

        if (changed) {
            // Копируем результат и подменяем слот, чтобы клиент корректно пересинхронизировался.
            ItemStack capped = result.copy();
            EnchantmentHelper.setEnchantments(capped, mutable.toImmutable());
            menu.getSlot(2).set(capped);
        }
    }
}