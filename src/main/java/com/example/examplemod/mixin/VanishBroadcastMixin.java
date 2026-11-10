package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.vanish.VanishVisibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VANISH: индивидуальная видимость игроков на уровне ванильного трекера сущностей.
 *
 * <p><b>Точка вмешательства.</b> {@code Entity.broadcastToPlayer(ServerPlayer)} вызывается трекером
 * {@code ChunkMap.TrackedEntity.updatePlayer} каждый тик для каждой пары «сущность → наблюдатель».
 * Именно здесь ванилла решает, попадёт ли сущность в клиент наблюдателя. Возврат {@code false}
 * исключает наблюдателя из {@code seenBy} и отправляет {@code ClientboundRemoveEntitiesPacket}.
 *
 * <p>Одного этого достаточно для скрытия всех «присутностей» игрока: модели, анимаций, nametag,
 * коллизии, звуков шагов и клиентских частиц — всё это клиент рисует/играет по данным полученной
 * сущности. Урон и снаряды X не затрагиваются: они идут отдельными сущностями и событиями.
 *
 * <p>Проверка выполняется ТОЛЬКО для сущностей игроков и только для ограниченного по UUID
 * наблюдателя, поэтому обычные сущности идти не трогает.
 */
@Mixin(Entity.class)
public abstract class VanishBroadcastMixin {

    /** Индивидуальная проверка видимости для сущностей-игроков. */
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void rootImpact_vanishBroadcastToPlayer(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!VanishVisibility.shouldBroadcast((Entity) (Object) this, player)) {
            cir.setReturnValue(false);
        }
    }
}