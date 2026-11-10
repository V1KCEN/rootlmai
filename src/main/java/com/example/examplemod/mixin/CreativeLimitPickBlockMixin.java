package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.creative.CreativeLimitMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Клиентский запрет СКМ в мире: pick-block выключен для ограниченного игрока.
 * <p>
 * Ванильный {@code Minecraft.pickBlock()} в Creative подменяет предмет в хотбаре и
 * отправляет серверу {@code ServerboundSetCreativeModeSlotPacket} со слотом
 * {@code 36 + selected}. Сервер такую выдачу отклоняет (см.
 * {@code CreativeLimitEnforcement}), а этот миксин не даёт ванильному клиенту
 * вообще ничего получить — без «мигающих» предметов до синхронизации.
 * <p>
 * Клиент определяет ограничение по метке системы ({@link CreativeLimitMarkers}).
 */
@Mixin(Minecraft.class)
public class CreativeLimitPickBlockMixin {

    /** pick-block (средняя кнопка мыши) в мире. */
    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void rootImpact_creativeLimitPickBlock(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        LocalPlayer player = minecraft.player;
        if (player == null || !CreativeLimitMarkers.hasMarkers(player)) return;

        player.displayClientMessage(Component.literal(
                "Creative Limit: выбор блока средней кнопкой мыши запрещён."), true);
        ci.cancel();
    }
}
