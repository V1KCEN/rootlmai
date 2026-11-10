package com.example.examplemod.rootimpact.vanish;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.function.Function;

/**
 * VANISH: фильтрация системных сообщений о входе/выходе игроков.
 *
 * <p>Ванильные сообщения {@code multiplayer.player.joined} и {@code multiplayer.player.left}
 * содержат только <i>отображаемое имя</i> игрока, а не его UUID, поэтому субъекта сообщения мы
 * определяем по имени среди игроков онлайн. Это совпадает с тем, что видит и сам игрок в чате,
 * поэтому подмена невозможна.
 *
 * <p>Точечное правило: сообщение о входе/выходе игрока отправляется наблюдателю только тогда,
 * когда этот игрок ему виден по матрице VANISH. Всем остальным сообщение уходит как обычно.
 */
public final class VanishJoinLeave {

    private static final String KEY_JOINED = "multiplayer.player.joined";
    private static final String KEY_JOINED_RENAMED = "multiplayer.player.joined.renamed";
    private static final String KEY_LEFT = "multiplayer.player.left";

    private VanishJoinLeave() {
    }

    /** Является ли сообщение сообщением о входе игрока. */
    public static boolean isJoinMessage(Component message) {
        return keyOf(message) != null;
    }

    /** Извлекает translatable-ключ сообщения (или {@code null}). */
    private static String keyOf(Component message) {
        if (message == null) return null;
        if (!(message.getContents() instanceof TranslatableContents translatable)) return null;

        String key = translatable.getKey();
        if (KEY_JOINED.equals(key) || KEY_JOINED_RENAMED.equals(key) || KEY_LEFT.equals(key)) {
            return key;
        }
        return null;
    }

    /**
     * Рассылает сообщение о входе/выходе с учётом индивидуальной видимости.
     *
     * @param playerList            список игроков (нужен для перебора наблюдателей)
     * @param serverMessage         исходное сообщение (как в ванилле)
     * @param playerMessageFactory  фабрика текста для конкретного получателя
     * @param bypassHiddenChat      флаг чат-фильтра (без изменений)
     */
    public static void broadcastFiltered(PlayerList playerList,
                                          Component serverMessage,
                                          Function<ServerPlayer, Component> playerMessageFactory,
                                          boolean bypassHiddenChat) {
        ServerPlayer subject = subjectOf(playerList, serverMessage);
        List<ServerPlayer> recipients = playerList.getPlayers();

        for (ServerPlayer receiver : recipients) {
            // Субъекта самого не фильтруем: он и так получит только свои данные.
            if (subject != null && !subject.getUUID().equals(receiver.getUUID())
                    && !VanishState.get().canSee(receiver.getUUID(), subject.getUUID())) {
                continue;   // наблюдателю, которому игрок скрыт, сообщение не отправляется
            }

            Component text = playerMessageFactory.apply(receiver);
            if (text != null) {
                receiver.sendSystemMessage(text, bypassHiddenChat);
            }
        }
    }

    /**
     * Определяет игрока, о котором это сообщение, по отображаемому имени.
     *
     * <p>Ванил формирует сообщение как {@code translatable("multiplayer.player.left",
     * player.getDisplayName())}, поэтому первый аргумент — это имя субъекта.
     */
    private static ServerPlayer subjectOf(PlayerList playerList, Component message) {
        if (!(message.getContents() instanceof TranslatableContents translatable)) return null;

        Object[] args = translatable.getArgs();
        if (args.length == 0) return null;
        // Аргументы транслитерации — компоненты (в нашем случае отображаемое имя субъекта).
        if (!(args[0] instanceof Component nameComponent)) return null;

        String name = nameComponent.getString();
        if (name.isEmpty()) return null;

        for (ServerPlayer candidate : playerList.getPlayers()) {
            if (candidate.getDisplayName().getString().equals(name)) {
                return candidate;
            }
        }
        return null;
    }
}