package com.example.examplemod.rootimpact.enchant;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Map;
import java.util.Set;

/**
 * Центральный реестр ограничений зачарований ROOT IMPACT (раздел 7).
 * <p>
 * Полностью запрещённые (нельзя получить обычным способом):
 * <ul>
 *   <li>Mending</li>
 *   <li>Infinity</li>
 *   <li>Thorns</li>
 *   <li>Punch</li>
 *   <li>Fire Aspect</li>
 *   <li>Power</li>
 *   <li>Flame</li>
 * </ul>
 * Максимальные уровни ROOT IMPACT:
 * <ul>
 *   <li>Sharpness: 2</li>
 *   <li>Protection: 2</li>
 *   <li>Projectile Protection: 2</li>
 *   <li>Blast Protection: 2</li>
 *   <li>Fire Protection: 1</li>
 *   <li>Efficiency: 3</li>
 *   <li>Looting: 1</li>
 *   <li>Fortune: 1</li>
 *   <li>Unbreaking: 1</li>
 * </ul>
 * Существующие зачарованные предметы/книги НЕ изменяются, НЕ понижаются, НЕ очищаются.
 */
public class EnchantmentLimiter {

    /** Запрещённые зачарования (ключи реестра) — единый источник истины */
    public static final Set<ResourceKey<Enchantment>> FORBIDDEN = Set.of(
            Enchantments.MENDING,
            Enchantments.INFINITY,
            Enchantments.THORNS,
            Enchantments.PUNCH,
            Enchantments.FIRE_ASPECT,
            Enchantments.POWER,
            Enchantments.FLAME
    );

    /** Максимальные уровни (ключ -> кап) */
    public static final Map<ResourceKey<Enchantment>, Integer> LEVEL_CAPS = Map.of(
            Enchantments.SHARPNESS, 2,
            Enchantments.PROTECTION, 2,
            Enchantments.PROJECTILE_PROTECTION, 2,
            Enchantments.BLAST_PROTECTION, 2,
            Enchantments.FIRE_PROTECTION, 1,
            Enchantments.EFFICIENCY, 3,
            Enchantments.LOOTING, 1,
            Enchantments.FORTUNE, 1,
            Enchantments.UNBREAKING, 1
    );

    private EnchantmentLimiter() {
    }

    /**
     * Запрещено ли зачарование (нельзя создать новое обычным способом).
     * Использует единый {@link #FORBIDDEN} set.
     */
    public static boolean isForbidden(Holder<Enchantment> holder) {
        if (holder == null) return false;
        return holder.unwrapKey().map(FORBIDDEN::contains).orElse(false);
    }

    /**
     * Возвращает кап уровня зачарования (или -1 если лимита нет).
     */
    public static int getLevelCap(Holder<Enchantment> holder) {
        if (holder == null) return -1;
        return holder.unwrapKey().map(LEVEL_CAPS::get).orElse(-1);
    }

    /**
     * Проверяет: превышает ли уровень кап.
     */
    public static boolean exceedsCap(Holder<Enchantment> holder, int level) {
        int cap = getLevelCap(holder);
        return cap >= 0 && level > cap;
    }
}