package com.example.examplemod.rootimpact;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;

/**
 * Обработчик логики транспорта.
 * Регулирует количество слотов и ограничения на шалкеры у вьючных животных.
 * <p>
 * Лама: 15 слотов (strength = 5, устанавливается при появлении в мире),
 * шалкеры запрещены (проверка при взаимодействии + миксин HorseInventoryMenuMixin).
 * Осёл: 3 слота (через миксин AnimalInventoryMixin).
 * Мул: 5 слотов (через миксин AnimalInventoryMixin).
 */
public class AnimalTransportHandler {

    private static boolean reflectionWarningLogged = false;

    /**
     * Устанавливает strength = 5 для ламы при её появлении в мире.
     * Вызывается один раз за жизнь сущности.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Llama llama)) return;
        if (llama.level().isClientSide) return;

        int currentStrength = llama.getStrength();
        if (currentStrength >= 5) return;

        try {
            Method setStrength = Llama.class.getDeclaredMethod("setStrength", int.class);
            setStrength.setAccessible(true);
            setStrength.invoke(llama, 5);
            ExampleMod.LOGGER.debug("AnimalTransportHandler: установлен strength=5 для ламы");
        } catch (Exception e) {
            if (!reflectionWarningLogged) {
                ExampleMod.LOGGER.warn("AnimalTransportHandler: не удалось вызвать setStrength", e);
                reflectionWarningLogged = true;
            }
        }
    }

    /**
     * Запрещает помещать шалкеры в ламу при прямом взаимодействии (правый клик с шалкером в руке).
     * Дополнительная защита — миксин HorseInventoryMenuMixin блокирует shift-click в контейнере.
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Llama)) return;

        Player player = event.getEntity();
        ItemStack heldItem = player.getItemInHand(event.getHand());

        if (WeightRegistry.isShulkerBox(heldItem.getItem())) {
            event.setCanceled(true);
        }
    }
}