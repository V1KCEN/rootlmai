package com.example.examplemod.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Валидатор условий кузницы.
 * <p>
 * Проверяет, выполнены ли требования для крафта предмета, требующего
 * определённый уровень кузницы.
 * <p>
 * Использует {@link ForgeSearch} для сканирования блоков в радиусе.
 * Не выполняет сканирование по тикам — только по запросу.
 */
public class ForgeValidator {

    /** Предметы, которые всегда можно скрафтить (исключения) */
    private static final Set<Item> ALWAYS_CRAFTABLE = Set.of(
            Items.BUCKET, Items.SHEARS, Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL
    );

    /**
     * Проверяет, можно ли скрафтить данный предмет в данной позиции.
     *
     * @param level  уровень
     * @param pos    позиция верстака (или кузнечного стола для Tier III)
     * @param item   предмет, который пытаемся скрафтить
     * @return true если крафт разрешён
     */
    public static boolean canCraft(Level level, BlockPos pos, Item item) {
        // Исключения — всегда можно
        if (ALWAYS_CRAFTABLE.contains(item)) return true;

        ForgeTier requiredTier = ForgeTier.getRequiredTier(item);
        if (requiredTier == null) return true; // предмет не требует кузницы

        return switch (requiredTier) {
            case TIER_1 -> validateTier1(level, pos);
            case TIER_2 -> validateTier2(level, pos);
            case TIER_3 -> validateTier3(level, pos);
        };
    }

    /**
     * Tier I: радиус 3 блока от верстака.
     * Требуется: наковальня + горящая печь/плавильня + лава (любая).
     */
    private static boolean validateTier1(Level level, BlockPos pos) {
        int r = ForgeTier.TIER_1.getRadiusXZ();
        int ry = ForgeTier.TIER_1.getRadiusY();

        boolean hasAnvil = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_ANVIL);
        boolean hasLitFurnace = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_LIT_FURNACE);
        boolean hasLava = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_LAVA);

        return hasAnvil && hasLitFurnace && hasLava;
    }

    /**
     * Tier II: радиус 2 блока от верстака.
     * Требуется: наковальня + горящая плавильня + текучая вода + текучая лава.
     * Источники воды/лавы НЕ подходят.
     */
    private static boolean validateTier2(Level level, BlockPos pos) {
        int r = ForgeTier.TIER_2.getRadiusXZ();
        int ry = ForgeTier.TIER_2.getRadiusY();

        boolean hasAnvil = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_ANVIL);
        boolean hasLitBlastFurnace = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_LIT_BLAST_FURNACE);
        boolean hasFlowingWater = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_FLOWING_WATER);
        boolean hasFlowingLava = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_FLOWING_LAVA);

        return hasAnvil && hasLitBlastFurnace && hasFlowingWater && hasFlowingLava;
    }

    /**
     * Tier III: радиус 2 блока по XZ, 1 блок по Y от кузнечного стола.
     * Требуется: наковальня + минимум 8 источников лавы.
     * Проверка только в Незере.
     */
    private static boolean validateTier3(Level level, BlockPos pos) {
        // Только в Незере
        if (!level.dimension().location().equals(net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"))) {
            return false;
        }

        int r = ForgeTier.TIER_3.getRadiusXZ();
        int ry = ForgeTier.TIER_3.getRadiusY();

        boolean hasAnvil = ForgeSearch.findBlock(level, pos, r, ry, ForgeSearch.IS_ANVIL);
        int lavaSourceCount = ForgeSearch.countBlocks(level, pos, r, ry, ForgeSearch.IS_LAVA_SOURCE);

        return hasAnvil && lavaSourceCount >= 8;
    }
}