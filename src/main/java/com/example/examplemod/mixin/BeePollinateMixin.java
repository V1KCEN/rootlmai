package com.example.examplemod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Замедляет опыление пчёл не в 3 раза, а примерно в 1.5 раза (раздел о растениях).
 * <p>
 * Рост культур пчёлами происходит в {@code Bee$BeeGrowCropGoal.tick()}:
 * {@code this.random.nextInt(this.adjustedTickDelay(30)) == 0}. Фактический порог
 * {@code adjustedTickDelay(30)} равен 15 (vanilla делит на 2), то есть вероятность
 * роста ≈ 1/15 за тик.
 * <p>
 * Константа 30 заменяется на 45: {@code adjustedTickDelay(45)} = 23, то есть
 * вероятность ≈ 1/23 — примерно в 1.5 раза реже, чем 1/15.
 * <p>
 * Класс цели указан через {@code targets}, так как {@code BeeGrowCropGoal} —
 * package-private внутренний класс (недоступен по ссылке извне пакета).
 */
@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal")
public class BeePollinateMixin {

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 30))
    private int rootImpact_slowBeePollination(int original) {
        return 45;
    }
}