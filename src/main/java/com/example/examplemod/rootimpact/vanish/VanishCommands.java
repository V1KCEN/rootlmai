package com.example.examplemod.rootimpact.vanish;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Команды системы VANISH (уровень прав 2 — только OP):
 *
 * <pre>
 * /vanish &lt;активатор&gt; on &lt;наблюдатель&gt;   — наблюдатель начинает видеть активатора
 * /vanish &lt;активатор&gt; off &lt;наблюдатель&gt;  — наблюдатель перестаёт видеть активатора
 * /vanish &lt;активатор&gt; all                    — переключить массовый vanish
 * /vanish &lt;активатор&gt;                       — то же, что all (переключение)
 * /vanish &lt;активатор&gt; reset                 — полностью сбросить vanish активатора
 * /vanish clearall                              — сбросить vanish всех игроков
 * /vanish status                                — состояние системы
 * </pre>
 *
 * <p>Поддерживаются ник и стандартные селекторы (в т.ч. {@code @a}, {@code @p}).
 *
 * <p>Смысл {@code on}/{@code off}: {@code /vanish X on Y} — Y <b>начинает видеть</b> X,
 * {@code /vanish X off Y} — Y <b>перестаёт видеть</b> X.
 */
public final class VanishCommands {

    private static final String PREFIX = "[Vanish] ";

    private VanishCommands() {
    }

    /** Точка входа: регистрация команд. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vanish")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(VanishCommands::status))
                .then(Commands.literal("clearall").executes(VanishCommands::clearAll))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> toggleAll(ctx, single(ctx, "target")))
                        .then(Commands.literal("all")
                                .executes(ctx -> toggleAll(ctx, single(ctx, "target"))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx, single(ctx, "target"))))
                        .then(Commands.literal("on")
                                .then(Commands.argument("observers", EntityArgument.players())
                                        .executes(ctx -> setVisibility(ctx, true))))
                        .then(Commands.literal("off")
                                .then(Commands.argument("observers", EntityArgument.players())
                                        .executes(ctx -> setVisibility(ctx, false))))));
    }

    // ------------------------------------------------------------------
// Обработчики
// ------------------------------------------------------------------

    /** {@code /vanish X on|off Y...} */
    private static int setVisibility(CommandContext<CommandSourceStack> ctx, boolean visible)
            throws CommandSyntaxException {
        ServerPlayer target = single(ctx, "target");
        Collection<ServerPlayer> observers = EntityArgument.getPlayers(ctx, "observers");

        VanishState state = VanishState.get();
        int changed = 0;
        for (ServerPlayer observer : observers) {
            if (observer.getUUID().equals(target.getUUID())) continue;   // себя скрыть нельзя

            state.setVisibility(target.getUUID(), observer.getUUID(), visible);
            changed++;
        }

        // Немедленно обновляем трекинг: скрытым — удаление, видимым — полный spawn.
        VanishTracker.updateForAll(target);

        String verb = visible ? "теперь видит" : "больше не видит";
        boolean temporary = state.isAllActive(target.getUUID());
        send(ctx, "Активатор " + target.getGameProfile().getName() + ": наблюдатель "
                + (observers.size() == 1 ? names(observers) : observers.size() + " игрок(ов)") + " " + verb
                + (temporary ? " (временно, действует all)" : "") + ". Всего изменено: " + changed);
        return changed;
    }

    /** {@code /vanish X} и {@code /vanish X all} — переключение массового vanish. */
    private static int toggleAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        VanishState state = VanishState.get();
        boolean nowAll = state.toggleAll(target.getUUID());

        // Трекинг пересчитывается для всех наблюдателей в измерении.
        VanishTracker.updateForAll(target);

        if (nowAll) {
            send(ctx, "Массовый vanish ВКЛЮЧЁН для " + target.getGameProfile().getName()
                    + ": его не видит никто, кроме него самого. Снимок видимости сохранён.");
        } else {
            send(ctx, "Массовый vanish ВЫКЛЮЧЕН для " + target.getGameProfile().getName()
                    + ": восстановлено состояние, которое было до включения all.");
        }
        return 1;
    }

    /** {@code /vanish X reset} — полный сброс. */
    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        VanishState.get().clearTarget(target.getUUID());
        VanishTracker.updateForAll(target);
        send(ctx, "Vanish полностью сброшен для " + target.getGameProfile().getName()
                + " (виден всем).");
        return 1;
    }

    /** {@code /vanish clearall} */
    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        VanishState.get().clearAll();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            VanishTracker.updateForAll(player);
        }
        send(ctx, "Все настройки vanish удалены.");
        return 1;
    }

    /** {@code /vanish status} */
    private static int status(CommandContext<CommandSourceStack> ctx) {
        VanishState state = VanishState.get();
        var online = ctx.getSource().getServer().getPlayerList().getPlayers();

        send(ctx, "Состояние VANISH:");
        for (ServerPlayer target : online) {
            boolean vanished = state.isVanished(target.getUUID());
            if (!vanished && !state.hasOverrides(target.getUUID())) continue;

            StringBuilder line = new StringBuilder("  - ")
                    .append(target.getGameProfile().getName()).append(": ")
                    .append(state.isAllActive(target.getUUID())
                            ? "массовый vanish активен (скрыт от всех)"
                            : "точечные настройки (all выключен)");

            for (ServerPlayer observer : online) {
                if (observer.getUUID().equals(target.getUUID())) continue;
                boolean sees = state.canSee(observer.getUUID(), target.getUUID());
                line.append("; ").append(observer.getGameProfile().getName())
                        .append(sees ? " видит" : " НЕ видит");
            }
            ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("  Всего активаторов с активным vanish: "
                + state.getVanishedTargets().size()), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Вспомогательное
    // ------------------------------------------------------------------

    /** Одиночный игрок из аргумента (поддерживаются ник и селекторы). */
    private static ServerPlayer single(CommandContext<CommandSourceStack> ctx, String arg)
            throws CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, arg);
    }

    private static String names(Collection<ServerPlayer> players) {
        StringBuilder sb = new StringBuilder();
        for (ServerPlayer player : players) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(player.getGameProfile().getName());
        }
        return sb.toString();
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + message).withStyle(ChatFormatting.AQUA), false);
    }
}