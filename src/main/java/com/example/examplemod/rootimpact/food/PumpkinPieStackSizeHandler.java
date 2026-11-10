package com.example.examplemod.rootimpact.food;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отдельный обработчик для размера стака тыквенного пирога (SPEC: max stack size 16).
 * <p>
 * НЕ связано с порчей еды. Тыквенный пирог согласно SPEC не портится,
 * но должен иметь максимальный размер стака 16.
 * <p>
 * Реализация: при периодическом тике игрока (раз в секунду) для каждого
 * тыквенного пирога в инвентаре устанавливается компонент MAX_STACK_SIZE = 16
 * через {@link FoodItems#applyPumpkinPieStackSize}.
 * ВАЖНО: не мутирует глобально vanilla предмет, только конкретный ItemStack (безопасно для Sodium).
 */
public class PumpkinPieStackSizeHandler {

    /** Интервал применения в тиках (1 секунда) */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        int counter = tickCounters.getOrDefault(uuid, 0) + 1;
        if (counter < CHECK_INTERVAL_TICKS) {
            tickCounters.put(uuid, counter);
            return;
        }
        tickCounters.put(uuid, 0);

        applyToInventory(player);
    }

    private void applyToInventory(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            FoodItems.applyPumpkinPieStackSize(inv.items.get(i));
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            FoodItems.applyPumpkinPieStackSize(inv.armor.get(i));
        }
        FoodItems.applyPumpkinPieStackSize(inv.offhand.get(0));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        tickCounters.remove(event.getEntity().getUUID());
    }
}