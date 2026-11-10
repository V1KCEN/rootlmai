package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.vanish.VanishJoinLeave;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * VANISH: фильтрация сообщений о входе игрока в игру.
 *
 * <p>Ванильный путь: {@code PlayerList.placeNewPlayer} формирует {@code multiplayer.player.joined}
 * и вызывает {@code broadcastSystemMessage(Component, Function, boolean)}. Последний просто
 * рассылает текст всем подряд — независимо от того, видят ли они вошедшего игрока.
 *
 * <p>Инъекция подменяет только перебор получателей: сообщение уходит лишь тем наблюдателям,
 * которым вошедший игрок виден по матрице VANISH. Вход игрока при этом НЕ отменяется и не ломается —
 * меняется исключительно рассылка текста.
 */
@Mixin(PlayerList.class)
public abstract class VanishPlayerListMixin {

    /**
     * Перехват системной рассылки. {@link VanishJoinLeave#isJoinMessage} отсекает все сообщения,
     * кроме «игрок вошёл», поэтому обычные системные сообщения проходят без изменений.
     */
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;"
            + "Ljava/util/function/Function;Z)V", at = @At("HEAD"), cancellable = true)
    private void rootImpact_vanishFilterSystemMessage(
            Component serverMessage,
            Function<ServerPlayer, Component> playerMessageFactory,
            boolean bypassHiddenChat,
            CallbackInfo ci) {
        if (!VanishJoinLeave.isJoinMessage(serverMessage)) return;

        VanishJoinLeave.broadcastFiltered((PlayerList) (Object) this,
                serverMessage, playerMessageFactory, bypassHiddenChat);
        ci.cancel();
    }
}