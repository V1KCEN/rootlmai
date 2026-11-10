package com.example.examplemod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Замедляет в 2 раза естественный рост всех растений, использующих игровой механизм роста
 * (random tick): культуры, саженцы, тростник, кактус, бамбук, лозу, какао, незер-бородавку,
 * стебли, хорус, сладкие ягоды, мангровые проростки, грибы и растущие растения (кельп/лозы).
 * <p>
 * Единая точка входа — {@link BlockBehaviour.BlockStateBase#randomTick}; вызывается из
 * {@code ServerLevel} для блоков с включённым random tick. Для "растущих" растений
 * пропускается ровно половина random-tick'ов, что даёт рост ровно в 2 раза медленнее ванили.
 * <p>
 * Не затрагивает не-растения (лёд, снег, медь, аметист, farmland, листву и т. п.),
 * а также bone meal (у него отдельная точка обработки) и опыление пчёлами.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class PlantGrowthMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void rootImpact_slowPlantGrowth(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockState state = (BlockState) (Object) this;
        if (isSlowablePlant(state.getBlock()) && random.nextInt(2) == 0) {
            // Пропускаем ровно половину случайных тиков → естественный рост в 2 раза медленнее ванили.
            ci.cancel();
        }
    }

    /**
     * Растения, которые растут через random tick и должны замедляться.
     * CropBlock покрывает все культуры (пшеница, морковь, картофель, свёкла, факельник, кувшинка-цветок).
     * GrowingPlantHeadBlock покрывает кельп и растущие лозы (плачущие/скрученные/светящиеся).
     */
    private static boolean isSlowablePlant(Block block) {
        return block instanceof CropBlock
                || block instanceof SaplingBlock
                || block instanceof StemBlock
                || block instanceof GrowingPlantHeadBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooSaplingBlock
                || block instanceof BambooStalkBlock
                || block instanceof VineBlock
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof MangrovePropaguleBlock
                || block instanceof MushroomBlock;
    }
}