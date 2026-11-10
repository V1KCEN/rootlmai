package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.vanish.VanishEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VANISH: серверные частицы не выдают присутствие скрытого игрока.
 *
 * <p>Ванильная точка: {@code ServerLevel.sendParticles(ServerPlayer, boolean, double, double, double, Packet)}
 * — приватный метод, который решает, отправить ли пакет частиц конкретному игроку. Инъекция
 * отменяет отправку, только если частицы возникли внутри тела скрытого от получателя игрока.
 *
 * <p>Частицы в других местах (взрывы, эффекты блоков, другие игроки) проходят без изменений.
 */
@Mixin(ServerLevel.class)
public abstract class VanishParticleMixin {

    /** Фильтрация частиц по позиции. */
    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDD"
            + "Lnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"), cancellable = true)
    private void rootImpact_vanishFilterParticles(
            ServerPlayer player,
            boolean longDistance,
            double posX,
            double posY,
            double posZ,
            Packet<?> packet,
            CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (VanishEffects.isInsideHiddenPlayer(player, posX, posY, posZ, level)) {
            cir.setReturnValue(false);
        }
    }
}