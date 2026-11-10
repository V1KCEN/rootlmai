package com.example.examplemod.mixin;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.rootimpact.WeightRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для HorseInventoryMenu.
 * <p>
 * Единственная точка блокировки: Slot.mayPlace(ItemStack).
 * <p>
 * Все vanilla способы помещения предмета в слот проходят через mayPlace():
 * - drag (QUICK_CRAFT): строка 333, 359 AbstractContainerMenu — slot.mayPlace(itemstack)
 * - pickup (PICKUP): строка 429 AbstractContainerMenu — slot7.mayPlace(itemstack10)
 * - shift-click (QUICK_MOVE): строка 401 AbstractContainerMenu → quickMoveStack → moveItemStackTo → slot.mayPlace()
 * - hotbar swap, double click, safeInsert: все вызывают mayPlace()
 * <p>
 * Переопределение mayPlace() в грузовых слотах ламы блокирует все способы одновременно.
 */
@Mixin(HorseInventoryMenu.class)
public class HorseInventoryMenuMixin {

    @Shadow @Final private AbstractHorse horse;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(int containerId, Inventory inventory, Container horseContainer, AbstractHorse horse, int columns, CallbackInfo ci) {
        if (!(horse instanceof Llama)) return;

        HorseInventoryMenu menu = (HorseInventoryMenu) (Object) this;

        // Заменяем грузовые слоты ламы (индексы 2..getInventorySize())
        // на слоты с переопределённым mayPlace(), блокирующим шалкеры
        for (int i = 2; i <= horse.getInventorySize(); i++) {
            Slot originalSlot = menu.slots.get(i);
            final int slotIndex = originalSlot.getSlotIndex();

            Slot blockingSlot = new Slot(horseContainer, slotIndex, originalSlot.x, originalSlot.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !WeightRegistry.isShulkerBox(stack.getItem()) && super.mayPlace(stack);
                }
            };

            menu.slots.set(i, blockingSlot);
        }
    }
}