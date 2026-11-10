package com.example.examplemod.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Predicate;

/**
 * Утилита для поиска блоков в кубическом радиусе вокруг центра.
 * <p>
 * Используется {@link ForgeValidator} для проверки наличия необходимых
 * блоков (наковальня, печь, лава, вода) вокруг верстака или кузнечного стола.
 * <p>
 * Сканирование выполняется только в момент проверки (не по тикам).
 */
public class ForgeSearch {

    /**
     * Проверяет, существует ли хотя бы один блок, удовлетворяющий предикату,
     * в кубическом радиусе {@code radiusXZ} по X/Z и {@code radiusY} по Y.
     *
     * @param level     уровень
     * @param center    центр поиска
     * @param radiusXZ  радиус по X и Z
     * @param radiusY   радиус по Y
     * @param predicate условие для блока
     * @return true если хотя бы один блок найден
     */
    public static boolean findBlock(Level level, BlockPos center, int radiusXZ, int radiusY, Predicate<BlockState> predicate) {
        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // пропускаем центр
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (predicate.test(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Считает количество блоков, удовлетворяющих предикату, в кубическом радиусе.
     *
     * @param level     уровень
     * @param center    центр поиска
     * @param radiusXZ  радиус по X и Z
     * @param radiusY   радиус по Y
     * @param predicate условие для блока
     * @return количество найденных блоков
     */
    public static int countBlocks(Level level, BlockPos center, int radiusXZ, int radiusY, Predicate<BlockState> predicate) {
        int count = 0;
        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (predicate.test(state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ========== Предикаты для конкретных блоков ==========

    /** Любая наковальня (включая повреждённые) */
    public static final Predicate<BlockState> IS_ANVIL = state ->
            state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL);

    /** Горящая печь (любая: обычная, коптильня, плавильня) */
    public static final Predicate<BlockState> IS_LIT_FURNACE = state ->
            state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
                    && (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER));

    /** Горящая печь ИЛИ плавильня (для Tier I — любая горящая) */
    public static final Predicate<BlockState> IS_LIT_FURNACE_OR_BLAST = state ->
            state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
                    && (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER));

    /** Горящая плавильня (только blast furnace — для Tier II) */
    public static final Predicate<BlockState> IS_LIT_BLAST_FURNACE = state ->
            state.is(Blocks.BLAST_FURNACE) && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);

    /** Любая лава (источник или текучая) */
    public static final Predicate<BlockState> IS_LAVA = state ->
            state.is(Blocks.LAVA) || state.is(Blocks.LAVA_CAULDRON);

    /** Только текучая лава (не источник) */
    public static final Predicate<BlockState> IS_FLOWING_LAVA = state ->
            state.is(Blocks.LAVA) && state.getFluidState().isSource() == false;

    /** Только источник лавы */
    public static final Predicate<BlockState> IS_LAVA_SOURCE = state ->
            state.is(Blocks.LAVA) && state.getFluidState().isSource();

    /** Только текучая вода (не источник) */
    public static final Predicate<BlockState> IS_FLOWING_WATER = state ->
            state.is(Blocks.WATER) && state.getFluidState().isSource() == false;

    /** Кузнечный стол */
    public static final Predicate<BlockState> IS_SMITHING_TABLE = state ->
            state.is(Blocks.SMITHING_TABLE);
}