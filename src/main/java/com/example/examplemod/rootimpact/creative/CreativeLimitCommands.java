package com.example.examplemod.rootimpact.creative;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Команды системы Creative Limit (только для операторов, уровень прав 2):
 * <pre>
 * /creativelimit on [игроки]          — включить ограничение (без аргументов — себе)
 * /creativelimit off [игроки]         — выключить ограничение
 * /creativelimit allon                — ограничить всех (включая будущие входы)
 * /creativelimit alloff               — снять глобальный режим (персональные записи сохраняются)
 * /creativelimit status               — состояние системы, лимиты и реестры
 * /creativelimit reset &lt;игроки&gt;        — сбросить счётчик выдач из Creative Menu
 * /creativelimit registeritem add &lt;ID...&gt;
 * /creativelimit registeritem delete &lt;ID...&gt;
 * /creativelimit registeritem list
 * /creativelimit registerblock add &lt;ID...&gt;
 * /creativelimit registerblock delete &lt;ID...&gt;
 * /creativelimit registerblock list
 * </pre>
 * Отдельных верхнеуровневых {@code /registeritem} и {@code /registerblock} нет — реестры
 * доступны только как подкоманды {@code /creativelimit}.
 * <p>
 * ID можно писать без namespace ({@code stone} → {@code minecraft:stone}), списком через
 * пробел или запятую; ID проверяются по реальным реестрам предметов/блоков (в т.ч. модовые:
 * {@code modid:item_name}, {@code modid:block_name}).
 * Все данные сохраняются в мир.
 */
public final class CreativeLimitCommands {

    private CreativeLimitCommands() {
    }

    /** Точка входа: регистрация всех команд системы. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("creativelimit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on")
                        .executes(ctx -> setSelf(ctx, true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setPlayers(ctx, true))))
                .then(Commands.literal("off")
                        .executes(ctx -> setSelf(ctx, false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setPlayers(ctx, false))))
                .then(Commands.literal("allon").executes(CreativeLimitCommands::allon))
                .then(Commands.literal("alloff").executes(CreativeLimitCommands::alloff))
                .then(Commands.literal("status").executes(CreativeLimitCommands::status))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(CreativeLimitCommands::resetGrants)))
                .then(registryNode("registeritem", true))
                .then(registryNode("registerblock", false)));
    }

    /** Узел {@code /creativelimit registeritem ...} либо {@code /creativelimit registerblock ...}. */
    private static LiteralArgumentBuilder<CommandSourceStack> registryNode(String literal, boolean item) {
        return Commands.literal(literal)
                .then(Commands.literal("add")
                        .then(Commands.argument("ids", StringArgumentType.greedyString())
                                .executes(ctx -> modifyRegistry(ctx, item, true))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("ids", StringArgumentType.greedyString())
                                .executes(ctx -> modifyRegistry(ctx, item, false))))
                .then(Commands.literal("list").executes(ctx -> listRegistry(ctx, item)));
    }

    // ------------------------------------------------------------------
    // /creativelimit on|off
    // ------------------------------------------------------------------

    private static int setSelf(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        return applyLimit(ctx, List.of(ctx.getSource().getPlayerOrException()), enable);
    }

    private static int setPlayers(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        return applyLimit(ctx, EntityArgument.getPlayers(ctx, "targets"), enable);
    }

    /** Включает/снимает ограничение для списка игроков. */
    private static int applyLimit(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, boolean enable) {
        CreativeLimitState state = CreativeLimitState.get();
        int changed = 0;
        boolean skippedByAllOn = false;

        for (ServerPlayer player : targets) {
            if (!enable && state.isLimitAll()) {
                // глобальный режим allon имеет приоритет над точечным снятием
                skippedByAllOn = true;
                continue;
            }
            state.setLimited(player.getUUID(), enable);
            if (enable) {
                CreativeLimitHandler.markApplied(player);
                if (player.isCreative()) {
                    CreativeLimitPlayerState.apply(player);
                }
            } else {
                CreativeLimitHandler.unmarkApplied(player);
                CreativeLimitEnforcement.clearLedger(player.getUUID());
                CreativeLimitPlayerState.clear(player);
            }
            changed++;
        }

        final int count = changed;
        final boolean skipped = skippedByAllOn;
        final boolean enabled = enable;
        ctx.getSource().sendSuccess(() -> Component.literal("Creative Limit: ограничение "
                + (enabled ? "ВКЛЮЧЕНО" : "СНЯТО") + " для " + count + " игрок(ов)"
                + (skipped ? ". Внимание: включён режим allon — для снятия используйте /creativelimit alloff." : ".")), true);
        return changed;
    }

    // ------------------------------------------------------------------
    // /creativelimit allon|alloff|status|reset
    // ------------------------------------------------------------------

    private static int allon(CommandContext<CommandSourceStack> ctx) {
        CreativeLimitState state = CreativeLimitState.get();
        state.setLimitAll(true);

        List<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            CreativeLimitHandler.markApplied(player);
            if (player.isCreative()) {
                CreativeLimitPlayerState.apply(player);
            }
        }

        final int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Creative Limit: режим «ограничить всех» ВКЛЮЧЁН (сейчас игроков: " + count
                        + "; все новые входы тоже ограничены)."), true);
        return count;
    }

    private static int alloff(CommandContext<CommandSourceStack> ctx) {
        CreativeLimitState state = CreativeLimitState.get();
        state.setLimitAll(false);
        // Персональные UUID и счётчики выдач НЕ очищаются: allon вернёт ограничения этим же игрокам.

        List<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
        int stillLimited = 0;
        for (ServerPlayer player : players) {
            if (state.isLimited(player)) {
                stillLimited++;   // игрок ограничен персонально — состояние сохраняем
            } else {
                CreativeLimitHandler.unmarkApplied(player);
                CreativeLimitEnforcement.clearLedger(player.getUUID());
                CreativeLimitPlayerState.clear(player);
            }
        }

        final int count = players.size();
        final int kept = stillLimited;
        final int personal = state.getLimitedPlayers().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Creative Limit: режим «ограничить всех» ВЫКЛЮЧЕН. Персональные записи сохранены ("
                        + personal + "); из " + count + " игрок(ов) онлайн персонально ограничены остались "
                        + kept + "."), true);
        return count;
    }

    private static int resetGrants(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CreativeLimitState state = CreativeLimitState.get();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            state.resetGrants(player.getUUID());
        }

        final int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Creative Limit: счётчик выборок сброшен у " + count + " игрок(ов)."), true);
        return count;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CreativeLimitState state = CreativeLimitState.get();
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.literal("=== Creative Limit ==="), false);
        source.sendSuccess(() -> Component.literal("Global: " + (state.isLimitAll()
                ? "ALLON — ограничены все игроки (включая будущие входы)"
                : "выключен (действуют только персональные записи)")), false);
        source.sendSuccess(() -> Component.literal("Персонально ограничено UUID: "
                + state.getLimitedPlayers().size()), false);
        source.sendSuccess(() -> Component.literal("Creative cap: " + state.getMaxGrants()
                + " (конфиг creativeLimitMaxGrants)"), false);
        source.sendSuccess(() -> Component.literal("Bonus HP: +"
                + com.example.examplemod.RootImpactConfig.CREATIVE_LIMIT_BONUS_HEALTH.getAsDouble()
                + " (конфиг creativeLimitBonusHealth)"), false);
        source.sendSuccess(() -> Component.literal("registeritem (" + state.getRegisteredItems().size() + "): "
                + joinIds(state.getRegisteredItems())), false);
        source.sendSuccess(() -> Component.literal("registerblock (" + state.getRegisteredBlocks().size() + "): "
                + joinIds(state.getRegisteredBlocks())), false);

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            final String line = " - " + player.getGameProfile().getName()
                    + ": ограничения " + (state.isLimited(player) ? "ON" : "OFF")
                    + ", Creative cap: " + state.getUsedGrants(player.getUUID()) + "/" + state.getMaxGrants()
                    + ", режим: " + player.gameMode.getGameModeForPlayer().getName()
                    + (player.isCreative() ? " (сейчас в Creative)" : " (не в Creative)");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // /registeritem | /registerblock
    // ------------------------------------------------------------------

    /** Добавляет/удаляет ID в реестре предметов или блоков. */
    private static int modifyRegistry(CommandContext<CommandSourceStack> ctx, boolean item, boolean add) {
        CreativeLimitState state = CreativeLimitState.get();
        String raw = StringArgumentType.getString(ctx, "ids");
        String registryName = item ? "registeritem" : "registerblock";
        String kind = item ? "предметов" : "блоков";

        List<String> done = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) continue;
            ResourceLocation id = item ? CreativeLimitIds.asItemId(token) : CreativeLimitIds.asBlockId(token);
            if (id == null) {
                problems.add("«" + token + "» — неизвестный ID " + (item ? "предмета" : "блока"));
                continue;
            }
            if (add) {
                if (item) {
                    state.addRegisteredItem(id);
                } else {
                    state.addRegisteredBlock(id);
                }
                done.add(id.toString());
            } else {
                boolean removed = item ? state.removeRegisteredItem(id) : state.removeRegisteredBlock(id);
                if (removed) {
                    done.add(id.toString());
                } else {
                    problems.add("«" + id + "» — нет в реестре");
                }
            }
        }

        int size = item ? state.getRegisteredItems().size() : state.getRegisteredBlocks().size();
        StringBuilder message = new StringBuilder();
        message.append(registryName).append(": ");
        if (done.isEmpty()) {
            message.append("ничего не изменено.");
        } else {
            message.append(add ? "добавлено " : "удалено ").append(done.size()).append(" ID ").append(kind)
                    .append(": ").append(String.join(", ", done)).append(".");
        }
        message.append(" Всего в реестре: ").append(size).append(".");
        if (!problems.isEmpty()) {
            message.append(" Проблемы: ").append(String.join("; ", problems))
                    .append(". Формат: /creativelimit ").append(registryName)
                    .append(" add stone | minecraft:stone | modid:path");
        }

        final String text = message.toString();
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(text), true);
        } else {
            ctx.getSource().sendFailure(Component.literal(text));
        }
        return done.size();
    }

    /** Показывает содержимое реестра. */
    private static int listRegistry(CommandContext<CommandSourceStack> ctx, boolean item) {
        CreativeLimitState state = CreativeLimitState.get();
        List<ResourceLocation> ids = item ? state.getRegisteredItems() : state.getRegisteredBlocks();
        final String text = "creativelimit " + (item ? "registeritem" : "registerblock")
                + " (" + ids.size() + "): " + joinIds(ids);
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return ids.size();
    }

    /** Строка со списком ID для вывода. */
    private static String joinIds(List<ResourceLocation> ids) {
        if (ids.isEmpty()) return "—";
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation id : ids) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(id);
        }
        return builder.toString();
    }
}
