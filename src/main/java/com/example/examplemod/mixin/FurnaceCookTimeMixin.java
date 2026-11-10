package com.example.examplemod.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Замедляет в 3 раза все обычные процессы готовки/плавки furnace-системы.
 * <p>
 * Единая точка — {@code AbstractFurnaceBlockEntity.getTotalCookTime(Level, AbstractFurnaceBlockEntity)},
 * возвращающая время готовки рецепта. Значение умножается на 3, что сохраняет
 * относительную скорость устройств (vanilla-времена рецептов: печь 200, плавильня/коптильня 100):
 * <ul>
 *   <li>Furnace: 10 с → 30 с</li>
 *   <li>Blast Furnace: 5 с → 15 с</li>
 *   <li>Smoker: 5 с → 15 с</li>
 * </ul>
 * {@code BlastFurnaceBlockEntity} и {@code SmokerBlockEntity} не переопределяют этот метод,
 * поэтому миксин покрывает все три устройства одной правкой.
 * <p>
 * Brewing Stand использует отдельный класс ({@code BrewingStandBlockEntity}) и НЕ затрагивается.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class FurnaceCookTimeMixin {

    @Inject(method = "getTotalCookTime", at = @At("RETURN"), cancellable = true)
    private static void rootImpact_slowFurnaceCooking(
            Level level, AbstractFurnaceBlockEntity blockEntity, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() * 3);
    }
}