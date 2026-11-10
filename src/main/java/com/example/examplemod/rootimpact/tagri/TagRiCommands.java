package com.example.examplemod.rootimpact.tagri;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Команды системы тегов предметов {@code /tagri} (только для операторов, уровень прав 2).
 * <pre>
 * /tagri deathdrop — пометить предмет в основной руке тегом «Выпадет при смерти»
 * /tagri gentle5   — пометить тегом прочности 5 срабатываний
 * /tagri gentle3   — пометить тегом прочности 3 срабатывания
 * /tagri gentle1   — пометить тегом прочности 1 срабатывание
 * </pre>
 * <p>
 * Команды применяются к предмету в основной руке. Если рука пуста — выводится сообщение об ошибке.
 * Повторное применение того же тега не создаёт дубликатов. Теги gentle взаимоисключающие:
 * попытка назначить второй gentle-тег выводит ошибку и оставляет исходный тег без изменений.
 */
public final class TagRiCommands {

    private TagRiCommands() {
    }

    /** Точка входа: регистрация всех команд системы. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tagri")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("deathdrop")
                        .executes(ctx -> applyTag(ctx, TagRiTags.DEATHDROP)))
                .then(Commands.literal(TagRiTags.GENTLE_5)
                        .executes(ctx -> applyTag(ctx, TagRiTags.GENTLE_5)))
                .then(Commands.literal(TagRiTags.GENTLE_3)
                        .executes(ctx -> applyTag(ctx, TagRiTags.GENTLE_3)))
                .then(Commands.literal(TagRiTags.GENTLE_1)
                        .executes(ctx -> applyTag(ctx, TagRiTags.GENTLE_1))));
    }

    private static int applyTag(CommandContext<CommandSourceStack> ctx, String tag) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Команду может выполнять только игрок."));
            return 0;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("В основной руке нет предмета."));
            return 0;
        }

        // Проверка конфликта gentle-тегов: нельзя назначить второй gentle на предмет с уже установленным gentle
        if (TagRiTags.GENTLE_TAGS.contains(tag)) {
            if (stack.has(TagRiComponent.TAG_RI.get())) {
                TagRiComponent existing = stack.get(TagRiComponent.TAG_RI.get());
                if (existing.hasGentleTag() && !existing.hasTag(tag)) {
                    source.sendFailure(
                            Component.literal("На предмете уже установлен тег прочности «" + existing.tags() + "». "
                                    + "Снимите его перед назначением другого gentle-тега."));
                    return 0;
                }
            }
        }

        TagRiComponent current = stack.getOrDefault(
                TagRiComponent.TAG_RI.get(),
                new TagRiComponent(new ArrayList<>(), 0)
        );
        Set<String> newTags = new HashSet<>(current.tags());

        if (newTags.contains(tag)) {
            source.sendSuccess(
                    () -> Component.literal("Тег «" + tag + "» уже установлен на этом предмете."),
                    false
            );
            return 0;
        }

        // Для gentle-тегов: убираем старый gentle (если есть), устанавливаем новый с начальным ресурсом
        if (TagRiTags.GENTLE_TAGS.contains(tag)) {
            newTags.removeIf(TagRiTags.GENTLE_TAGS::contains);
            newTags.add(tag);
            TagRiComponent updated = new TagRiComponent(new ArrayList<>(newTags), TagRiTags.gentleUses(tag));
            stack.set(TagRiComponent.TAG_RI.get(), updated);
        } else {
            // deathdrop — просто добавляем тег, ресурс gentle не меняется
            newTags.add(tag);
            TagRiComponent updated = new TagRiComponent(new ArrayList<>(newTags), current.gentleUses());
            stack.set(TagRiComponent.TAG_RI.get(), updated);
        }

        // Уведомляем клиентов об изменении предмета
        player.inventoryMenu.broadcastChanges();

        source.sendSuccess(
                () -> Component.literal("Тег «" + tag + "» применён к предмету в основной руке."),
                true
        );
        return 1;
    }

    /** Регистрация команды через {@link RegisterCommandsEvent} (вызывается из обработчика). */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
}
