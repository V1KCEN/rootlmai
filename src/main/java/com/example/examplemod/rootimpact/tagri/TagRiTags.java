package com.example.examplemod.rootimpact.tagri;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.Set;

/**
 * Константы тегов системы тегов предметов ROOT IMPACT ({@code /tagri})
 * и соответствующие им строки отображения в lore.
 * <p>
 * Теги хранятся в Data Component предмета. Dieser класс содержит только
 * константы и вспомогательные методы — никакой логики.
 */
public final class TagRiTags {

    private TagRiTags() {
    }

    /** Тег: предмет выпадает из инвентаря при смерти владельца */
    public static final String DEATHDROP = "deathdrop";

    /** Теги прочности gentle (взаимно исключающие) */
    public static final String GENTLE_5 = "gentle5";
    public static final String GENTLE_3 = "gentle3";
    public static final String GENTLE_1 = "gentle1";

    /** Неизменяемый набор всех gentle-тегов */
    public static final Set<String> GENTLE_TAGS = Set.of(GENTLE_5, GENTLE_3, GENTLE_1);

    /**
     * Возвращает начальное значение ресурса gentle для указанного тега.
     *
     * @param tag название тега gentle
     * @return количество срабатываний (5, 3 или 1), или 0 для неизвестного тега
     */
    public static int gentleUses(String tag) {
        return switch (tag) {
            case GENTLE_5 -> 5;
            case GENTLE_3 -> 3;
            case GENTLE_1 -> 1;
            default -> 0;
        };
    }

    /** Строка lore для тега {@link #DEATHDROP} */
    public static final Component LORE_DEATHDROP =
            Component.literal("Не уйдет с владельцем")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true));

    /** Строка lore для тега {@link #GENTLE_5} */
    public static final Component LORE_GENTLE_5 =
            Component.literal("Хрупкий предмет")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true));

    /** Строка lore для тега {@link #GENTLE_3} */
    public static final Component LORE_GENTLE_3 =
            Component.literal("Очень хрупкий предмет")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true));

    /** Строка lore для тега {@link #GENTLE_1} */
    public static final Component LORE_GENTLE_1 =
            Component.literal("Подобен нежному цветку")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true));

    /**
     * Получить компонент lore по названию тега.
     *
     * @param tag название тега
     * @return компонент lore, или пустой компонент для неизвестного тега
     */
    public static Component loreForTag(String tag) {
        return switch (tag) {
            case DEATHDROP -> LORE_DEATHDROP;
            case GENTLE_5 -> LORE_GENTLE_5;
            case GENTLE_3 -> LORE_GENTLE_3;
            case GENTLE_1 -> LORE_GENTLE_1;
            default -> Component.literal(tag);
        };
    }
}
