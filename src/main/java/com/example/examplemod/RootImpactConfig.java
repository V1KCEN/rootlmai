package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class RootImpactConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Максимальный вес игрока
    public static final ModConfigSpec.DoubleValue MAX_WEIGHT = BUILDER
            .comment("Максимальный вес, который может нести игрок (по умолчанию 1000)")
            .defineInRange("maxWeight", 1000.0, 1.0, 100000.0);

    // Множитель веса содержимого шалкера (0.65 = -35%)
    public static final ModConfigSpec.DoubleValue SHULKER_CONTENT_MULTIPLIER = BUILDER
            .comment("Множитель веса содержимого шалкера (0.65 = -35%)")
            .defineInRange("shulkerContentMultiplier", 0.65, 0.0, 1.0);

    // Пороги штрафов (в процентах от maxWeight)
    public static final ModConfigSpec.DoubleValue THRESHOLD_SPRINT = BUILDER
            .comment("Порог запрета спринта (в процентах от maxWeight)")
            .defineInRange("thresholdSprint", 0.41, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THRESHOLD_SLOWNESS_1 = BUILDER
            .comment("Порог Slowness I (в процентах от maxWeight)")
            .defineInRange("thresholdSlowness1", 0.66, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THRESHOLD_SLOWNESS_2 = BUILDER
            .comment("Порог Slowness II (в процентах от maxWeight)")
            .defineInRange("thresholdSlowness2", 0.81, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THRESHOLD_SLOWNESS_3 = BUILDER
            .comment("Порог Slowness III + Mining Fatigue I (в процентах от maxWeight)")
            .defineInRange("thresholdSlowness3", 0.96, 0.0, 1.0);

    // Модификаторы урона (раздел 6)
    public static final ModConfigSpec.DoubleValue SWORD_DAMAGE_MULTIPLIER = BUILDER
            .comment("Множитель урона мечей (0.5 = -50%)")
            .defineInRange("swordDamageMultiplier", 0.5, 0.01, 1.0);

    public static final ModConfigSpec.DoubleValue AXE_DAMAGE_MULTIPLIER = BUILDER
            .comment("Множитель урона топоров (0.4 = -60%)")
            .defineInRange("axeDamageMultiplier", 0.4, 0.01, 1.0);

    // HUD (раздел 1)
    public static final ModConfigSpec.IntValue HUD_X = BUILDER
            .comment("Позиция HUD веса по X (от правого края экрана)")
            .defineInRange("hudX", 10, 0, 5000);

    public static final ModConfigSpec.IntValue HUD_Y = BUILDER
            .comment("Позиция HUD веса по Y (от верхнего края экрана)")
            .defineInRange("hudY", 10, 0, 5000);

    public static final ModConfigSpec.DoubleValue HUD_SCALE = BUILDER
            .comment("Масштаб HUD веса (1.0 = 100%)")
            .defineInRange("hudScale", 1.0, 0.25, 4.0);

    // Creative Limit
    public static final ModConfigSpec.IntValue CREATIVE_LIMIT_MAX_GRANTS = BUILDER
            .comment("Максимальное количество выборок предметов из Creative Menu для ограниченного игрока (по умолчанию 5)")
            .defineInRange("creativeLimitMaxGrants", 5, 0, 100000);

    public static final ModConfigSpec.DoubleValue CREATIVE_LIMIT_BONUS_HEALTH = BUILDER
            .comment("Бонус к максимальному здоровью ограниченного игрока в Creative (по умолчанию 30.0, т.е. 50 HP = 25 сердец)")
            .defineInRange("creativeLimitBonusHealth", 30.0, 0.0, 1024.0);

    static final ModConfigSpec SPEC = BUILDER.build();
}