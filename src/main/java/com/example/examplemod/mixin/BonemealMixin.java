package com.example.examplemod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Делает костную муку в среднем в 3 раза менее эффективной (раздел о растениях).
 * <p>
 * {@link BoneMealItem#applyBonemeal} — единая точка применения костной муки
 * (используется и игроком, и жителем-фермером через {@code BoneMealItem.growCrop}).
 * Если проверка {@link BonemealableBlock#isBonemealSuccess} прошла, добавляем
 * случайный отсев: эффект применяется только в 1/3 случаев.
 * <p>
 * Важно: расход предмета ({@code stack.shrink(1)}) происходит в {@code applyBonemeal}
 * безусловно (при валидной цели), поэтому костная мука всё равно тратится —
 * даже когда эффекта нет (2 из 3 использований).
 * <p>
 * Интерфейс и внешний вид предмета не меняются.
 */
@Mixin(BoneMealItem.class)
public class BonemealMixin {

    @Redirect(
            method = "applyBonemeal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BonemealableBlock;isBonemealSuccess(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
            )
    )
    private static boolean rootImpact_reduceBonemealEffect(
            BonemealableBlock block, Level level, RandomSource random, BlockPos pos, BlockState state) {
        // Сохраняем ванильное условие успеха, но применяем эффект лишь в 1/3 случаев.
        if (!block.isBonemealSuccess(level, random, pos, state)) {
            return false;
        }
        return random.nextInt(3) == 0;
    }
}