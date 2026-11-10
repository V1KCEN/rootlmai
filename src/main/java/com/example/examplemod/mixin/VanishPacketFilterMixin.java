package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.vanish.VanishEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VANISH: точечная фильтрация исходящих пакетов конкретного клиента.
 *
 * <p>Скрытие самой сущности уже убирает модель, nametag, коллизию, шаги и клиентские частицы.
 * Здесь закрывается последний путь утечки «присутствия» — <b>звуки на позиции игрока</b>
 * (выстрел, удар и т.п.), которые ванилла рассылает по координате, а не по сущности.
 *
 * <p>Проверка точечная: пакет отбрасывается только для того получателя, которому скрытый игрок
 * не виден, и только если координаты звука попадают внутрь его габарита. Все остальные пакеты и
 * звуки в других местах проходят без изменений, поэтому урон, снаряды и мировой звук не затрагиваются.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class VanishPacketFilterMixin {

    /** Фильтр только для звуков; остальные пакеты не трогаем. */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void rootImpact_vanishFilterSound(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundSoundPacket sound)) return;

        // Фактический объект — ServerGamePacketListenerImpl, у которого есть поле player.
        if (!((Object) this instanceof ServerGamePacketListenerImpl listener)) return;
        ServerPlayer receiver = listener.player;
        if (receiver == null) return;

        VanishSoundPacketAccessor accessor = (VanishSoundPacketAccessor) sound;
        if (VanishEffects.isInsideHiddenPlayer(receiver,
                accessor.vanish$getX() + 0.5,
                accessor.vanish$getY() + 0.5,
                accessor.vanish$getZ() + 0.5,
                receiver.serverLevel())) {
            ci.cancel();
        }
    }
}