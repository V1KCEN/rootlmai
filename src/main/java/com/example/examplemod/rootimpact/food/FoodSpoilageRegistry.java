package com.example.examplemod.rootimpact.food;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Реестр сроков хранения продуктов (раздел 9 ROOT_IMPACT_SPEC.md).
 * <p>
 * Определяет срок хранения (в игровых днях) для каждого продукта.
 * Сроки строго соответствуют ROOT_IMPACT_SPEC.md:
 * <ul>
 *   <li>Яблоко - 2 игровых дня (48000 тиков)</li>
 *   <li>Сырое мясо - 2 игровых дня</li>
 *   <li>Приготовленное мясо - 3 игровых дня</li>
 *   <li>Рыба - 2.5 игровых дня</li>
 *   <li>Ягоды - 3 игровых дня</li>
 *   <li>Овощи - 4 игровых дня</li>
 *   <li>Хлеб и хлебобулочные - 5 игровых дней</li>
 *   <li>Готовые блюда (рагу, супы, жареная картошка и т.п.) - 6 игровых дней</li>
 * </ul>
 * <p>
 * Исключения (никогда не портятся) по SPEC: сушёная ламинария, мёд, тыквенный пирог.
 * <p>
 * Продукты, не зарегистрированные здесь, НЕ портятся.
 * <p>
 * Яблоко (minecraft:apple) портится за 2 игровых дня (2 x 24000 = 48000 game ticks).
 */
public class FoodSpoilageRegistry {

    /** Карта: предмет -> срок хранения в игровых днях */
    private static final Map<Item, Integer> SHELF_LIFE_DAYS = new HashMap<>();

    /** Количество тиков в одном игровом дне (20 тиков/сек x 60 сек x 20 мин) */
    public static final long TICKS_PER_DAY = 24000L;

    /** Рыба - 2.5 дня: добавляем полдня = 12000 тиков */
    private static final long FISH_HALF_DAY_TICKS = TICKS_PER_DAY / 2;

    private FoodSpoilageRegistry() {
    }

    /**
     * Регистрирует срок хранения продукта.
     *
     * @param item продукт
     * @param days срок хранения в игровых днях
     */
    public static void register(Item item, int days) {
        SHELF_LIFE_DAYS.put(item, days);
    }

    /**
     * Возвращает срок хранения продукта в тиках.
     *
     * @param item продукт
     * @return срок хранения в тиках, или -1 если продукт не портится
     */
    public static long getShelfLifeTicks(Item item) {
        Integer days = SHELF_LIFE_DAYS.get(item);
        if (days == null) return -1;

        long base = (long) days * TICKS_PER_DAY;
        // Рыба - 2.5 дня: добавляем полдня
        if (item == Items.COD || item == Items.SALMON || item == Items.COOKED_COD
                || item == Items.COOKED_SALMON || item == Items.PUFFERFISH || item == Items.TROPICAL_FISH) {
            return base + FISH_HALF_DAY_TICKS;
        }
        return base;
    }

    /**
     * Проверяет, портится ли продукт.
     */
    public static boolean isPerishable(Item item) {
        return SHELF_LIFE_DAYS.containsKey(item);
    }

    /**
     * Инициализирует реестр сроками хранения из манифеста (ROOT_IMPACT_SPEC.md).
     * Вызывается один раз при инициализации мода.
     */
    public static void init() {

        // === Яблоко - 2 игровых дня (48000 тиков) ===
        register(Items.APPLE, 2);

        // === Сырое мясо - 2 игровых дня ===
        register(Items.BEEF, 2);
        register(Items.PORKCHOP, 2);
        register(Items.CHICKEN, 2);
        register(Items.MUTTON, 2);
        register(Items.RABBIT, 2);

        // === Приготовленное мясо - 3 игровых дня ===
        register(Items.COOKED_BEEF, 3);
        register(Items.COOKED_PORKCHOP, 3);
        register(Items.COOKED_CHICKEN, 3);
        register(Items.COOKED_MUTTON, 3);
        register(Items.COOKED_RABBIT, 3);

        // === Рыба - 2.5 игровых дня ===
        register(Items.COD, 2);
        register(Items.SALMON, 2);
        register(Items.COOKED_COD, 2);
        register(Items.COOKED_SALMON, 2);
        register(Items.PUFFERFISH, 2);
        register(Items.TROPICAL_FISH, 2);

        // === Ягоды - 3 игровых дня ===
        register(Items.SWEET_BERRIES, 3);
        register(Items.GLOW_BERRIES, 3);

        // === Овощи - 4 игровых дня ===
        register(Items.POTATO, 4);
        register(Items.CARROT, 4);
        register(Items.BEETROOT, 4);

        // === Хлеб и печенье - 5 игровых дней ===
        register(Items.BREAD, 5);
        register(Items.COOKIE, 5);

        // === Готовые блюда - 6 игровых дней ===
        register(Items.MUSHROOM_STEW, 6);
        register(Items.BEETROOT_SOUP, 6);
        register(Items.RABBIT_STEW, 6);
        register(Items.SUSPICIOUS_STEW, 6);
        register(Items.BAKED_POTATO, 6);
        register(Items.CAKE, 6);

        // === НЕ портятся (исключения из SPEC) ===
        // DRIED_KELP (сушёная ламинария) - не регистрируем
        // HONEY_BOTTLE (мёд) - не регистрируем
        // PUMPKIN_PIE (тыквенный пирог) - не регистрируем
    }
}