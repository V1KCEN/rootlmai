package com.example.examplemod.mixin;

import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин для {@link RepairItemRecipe}.
 * <p>
 * Полностью отключает ванильную починку через крафт:
 * <ul>
 *   <li>сетка крафта игрока (2×2)</li>
 *   <li>верстак (3×3)</li>
 * </ul>
 * <p>
 * {@code RepairItemRecipe.matches()} возвращает {@code false} всегда,
 * поэтому рецепт объединения двух одинаковых предметов никогда не совпадает
 * и результат не появляется.
 * <p>
 * Единственный способ починки — наковальня (см. {@code RepairHandler}).
 */
@Mixin(RepairItemRecipe.class)
public class RepairItemRecipeMixin {

    /**
     * Блокирует совпадение рецепта ремонта через крафт.
     * <p>
     * Возвращает {@code false} всегда, чтобы рецепт никогда не совпадал
     * ни в 2×2, ни в 3×3 сетке.
     */
    @Inject(
            method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blockCraftingRepair(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    /**
     * Дублирующая блокировка для базовой сигнатуры {@code matches(RecipeInput, Level)}.
     * Покрывает все сценарии вызова рецепта, включая 2×2 сетку игрока,
     * чтобы исключить возможность обхода.
     */
    @Inject(
            method = "matches(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blockCraftingRepairBase(RecipeInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
