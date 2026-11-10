package com.example.examplemod.mixin;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к координатам пакета звука {@link ClientboundSoundPacket}.
 *
 * <p>Нужен, чтобы определить, что звук возник на позиции скрытого игрока. Значения только
 * читаются, поведение пакета не меняется.
 */
@Mixin(ClientboundSoundPacket.class)
public interface VanishSoundPacketAccessor {

    @Accessor("x")
    int vanish$getX();

    @Accessor("y")
    int vanish$getY();

    @Accessor("z")
    int vanish$getZ();
}