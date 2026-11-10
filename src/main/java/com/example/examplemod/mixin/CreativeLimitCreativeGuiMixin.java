package com.example.examplemod.mixin;

import com.example.examplemod.rootimpact.creative.CreativeLimitMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Клиентский запрет СКМ в Creative Menu.
 * <p>
 * Ванильные СКМ-операции в творческом инвентаре:
 * <ul>
 *   <li>{@code ClickType.CLONE} — средний клик копирует предмет на курсор
 *       (проверка СКМ целиком на стороне клиента, сервер о ней не знает);</li>
 *   <li>{@code ClickType.QUICK_CRAFT} с типом 2 — middle-drag (распределение
 *       копий предмета по слотам).</li>
 * </ul>
 * Оба случая проходят через {@code CreativeModeInventoryScreen.slotClicked},
 * поэтому для ограниченного игрока вызов отменяется.
 * <p>
 * Клиент определяет ограничение по метке системы ({@link CreativeLimitMarkers}).
 */
/**
 * Q/THROW по Creative Menu.
 *
 * <p>Серверная защита стоит в {@code CreativeLimitEnforcement.handleCreativeSlot}
 * ({@code ServerboundSetCreativeModeSlotPacket} со слотом {@code -1}), этот mixin — только UX-слой:
 * он отменяет выброс предмета, взятого из creative-меню, до отправки пакета, чтобы не было
 * визуального «дропа» на клиенте.
 *
 * <p>Важно: Q по слоту ИНВЕНТАРЯ ИГРОКА не отменяется. Такой слот — обёртка
 * {@code SlotWrapper} над слотом игрока, тогда как creative-слот принадлежит
 * {@code SimpleContainer} меню ({@code CreativeModeInventoryScreen.CONTAINER}).
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeLimitCreativeGuiMixin {

    /** Средняя кнопка мыши в творческом инвентаре. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void rootImpact_creativeLimitMiddleClick(Slot slot, int slotId, int mouseButton, ClickType type, CallbackInfo ci) {
        if (!isMiddleButton(mouseButton, type)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !CreativeLimitMarkers.hasMarkers(player)) return;

        player.displayClientMessage(Component.literal(
                "Creative Limit: получение/копирование предметов средней кнопкой мыши запрещено."), true);
        ci.cancel();
    }

    /** Q/THROW по предмету creative-меню — запрещено (выброс в мир = обход cap/registeritem). */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void rootImpact_creativeLimitCreativeThrow(Slot slot, int slotId, int mouseButton, ClickType type, CallbackInfo ci) {
        if (type != ClickType.THROW) return;
        if (!isCreativeMenuSlot(slot)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !CreativeLimitMarkers.hasMarkers(player)) return;

        player.displayClientMessage(Component.literal(
                "Creative Limit: выбрасывание предмета из Creative Menu запрещено."), true);
        ci.cancel();
    }

    /**
     * Является ли слот слотом creative-меню (а не слотом инвентаря игрока).
     *
     * <p>Creative-слоты принадлежат {@code SimpleContainer} на 45 ячеек
     * ({@code CreativeModeInventoryScreen.CONTAINER}), а слоты инвентаря игрока приходят
     * обёрнутыми в {@code SlotWrapper} и принадлежат {@code Inventory}. Проверяем именно
     * контейнер, а не класс слота, чтобы не зависеть от видимости внутренних классов.
     */
    private static boolean isCreativeMenuSlot(Slot slot) {
        return slot != null && slot.container instanceof SimpleContainer;
    }

    /**
     * Определяет среднюю кнопку мыши.
     * <p>
     * У обычного клика {@code mouseButton} равен кнопке (2 = СКМ), у drag-операций
     * кнопка упакована вместе с заголовком: {@code getQuickcraftType(button)}
     * возвращает тип перетаскивания (2 — средняя кнопка).
     */
    private static boolean isMiddleButton(int mouseButton, ClickType type) {
        if (type == ClickType.QUICK_CRAFT) {
            return AbstractContainerMenu.getQuickcraftType(mouseButton) == 2;
        }
        return mouseButton == 2;
    }
}
