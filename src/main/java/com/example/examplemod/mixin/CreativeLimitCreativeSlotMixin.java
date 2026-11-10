package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.creative.CreativeLimitEnforcement;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Серверный контроль системы Creative Limit над ванильными путями Creative Menu.
 * <p>
 * Ваниль не имеет события на выдачу предмета из Creative Menu, поэтому правила
 * применяются здесь — в единственной серверной точке, куда попадают все такие выдачи:
 * <ul>
 *   <li>{@code handleSetCreativeModeSlot} — выдача/дроп предмета из Creative Menu
 *       (в т.ч. pick-block средней кнопкой мыши: клиент отправляет слот
 *       {@code 36 + selected});</li>
 *   <li>{@code handlePickItem} — ванильный «выбор предмета» (pick item) СКМ.</li>
 * </ul>
 * Проверки и решения принимает {@link CreativeLimitEnforcement}; при нарушении пакет
 * отменяется, а клиенту уходит реальное состояние инвентаря (без «фантомных» предметов).
 * <p>
 * Инъекция стоит в HEAD, поэтому дополнительно проверяется поток: если пакет пришёл
 * не в главном потоке сервера, ванильный {@code PacketUtils.ensureRunningOnSameThread}
 * сам перепланирует его, и правило применится один раз — уже в главном потоке.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class CreativeLimitCreativeSlotMixin {

    /** Игрок соединения (поле {@code public ServerPlayer player} в ванили) */
    @Shadow
    public ServerPlayer player;

    /** Выдача предмета из Creative Menu. */
    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void rootImpact_creativeLimitSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (!runningOnServerThread()) return;
        if (CreativeLimitEnforcement.handleCreativeSlot(this.player, packet.slotNum(), packet.itemStack())) {
            ci.cancel();
        }
    }

    /** Ванильный «pick item» средней кнопкой мыши. */
    @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void rootImpact_creativeLimitPickItem(ServerboundPickItemPacket packet, CallbackInfo ci) {
        if (!runningOnServerThread()) return;
        if (CreativeLimitEnforcement.handlePickItem(this.player)) {
            ci.cancel();
        }
    }

    /** Проверка потока: вне главного потока обработку отдаём ванильному перепланированию. */
    private boolean runningOnServerThread() {
        MinecraftServer server = this.player.getServer();
        return server == null || server.isSameThread();
    }
}
