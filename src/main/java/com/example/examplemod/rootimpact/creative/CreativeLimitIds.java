package com.example.examplemod.rootimpact.creative;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Разбор и проверка ID для реестров {@code registeritem} / {@code registerblock}.
 * <p>
 * Правила разбора:
 * <ul>
 *   <li>{@code stone} → {@code minecraft:stone} (пустой namespace = minecraft)</li>
 *   <li>{@code minecraft:stone}, {@code create:cogwheel} — как есть</li>
 *   <li>регистр приводится к нижнему (ID регистри в нижнем регистре)</li>
 *   <li>ID проверяется по реальным реестрам предметов/блоков,
 *       поэтому опечатки и несуществующие ID отсекаются сразу</li>
 * </ul>
 * Модовые предметы/блоки поддерживаются (любой зарегистрированный ID).
 */
public final class CreativeLimitIds {

    private CreativeLimitIds() {
    }

    /**
     * Приводит «сырой» ввод к каноничному виду ID: нижний регистр + namespace по умолчанию.
     *
     * @param raw строка из команды
     * @return нормализованная строка либо {@code null}, если ввод пуст
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        return id;
    }

    /**
     * Разбирает и проверяет ID предмета.
     *
     * @return ID предмета либо {@code null}, если предмет не зарегистрирован
     */
    public static ResourceLocation asItemId(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(normalize(raw) == null ? "" : normalize(raw));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        return id;
    }

    /**
     * Разбирает и проверяет ID блока.
     *
     * @return ID блока либо {@code null}, если блок не зарегистрирован
     */
    public static ResourceLocation asBlockId(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(normalize(raw) == null ? "" : normalize(raw));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
        return id;
    }
}
