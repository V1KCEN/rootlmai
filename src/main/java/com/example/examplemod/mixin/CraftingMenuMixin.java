package com.example.examplemod.mixin;

import com.example.examplemod.forge.ForgeValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин для {@link CraftingMenu}.
 * <p>
 * Перехватывает {@code slotChangedCraftingGrid()} — статический метод,
 * вызываемый при любом изменении сетки крафта (изменение ингредиентов,
 * открытие меню, книга рецептов). После того как рецепт найден и результат
 * помещён в слот результата, проверяет условия кузницы.
 * <p>
 * Также перехватывает {@code quickMoveStack()} для слота 0 (результат),
 * так как shift-click на результате НЕ вызывает slotChangedCraftingGrid.
 * <p>
 * Если условия не выполнены — очищает слот результата или возвращает EMPTY.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    /**
     * Внедряется в конец {@code slotChangedCraftingGrid} после того,
     * как результат уже установлен. Проверяет, можно ли скрафтить
     * предмет в текущей локации.
     * <p>
     * Вызывается при: изменении ингредиентов, открытии меню, книге рецептов,
     * finishPlacingRecipe.
     */
    @Inject(
            method = "slotChangedCraftingGrid",
            at = @At("TAIL")
    )
    private static void onSlotChangedCraftingGrid(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            Level level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            net.minecraft.world.item.crafting.RecipeHolder<?> recipe,
            CallbackInfo ci
    ) {
        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;

        BlockPos craftingTablePos = findNearestCraftingTable(level, player.blockPosition());
        if (craftingTablePos == null) return;

        if (!ForgeValidator.canCraft(level, craftingTablePos, result.getItem())) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }

    /**
     * БЛОКИРУЕТ shift-click на слоте результата (слот 0),
     * если условия кузницы не выполнены.
     * <p>
     * {@code quickMoveStack} — это метод, вызываемый при shift-клике на слоте.
     * Когда игрок shift-кликает на результате крафта (слот 0),
     * этот метод напрямую забирает предмет, НЕ вызывая slotChangedCraftingGrid.
     * <p>
     * Без этой блокировки игрок мог бы:
     * 1. Положить ингредиенты в верстак без кузницы
     * 2. Увидеть результат (он пуст из-за нашего Mixin'а выше)
     * 3. Shift-кликнуть на пустом слоте — не сработает (hasItem = false)
     * 4. Поставить кузницу
     * 5. Убрать и положить ингредиент — результат появится
     * 6. Shift-кликнуть на нём — предмет переместится в инвентарь
     * 7. Убрать кузницу — но предмет уже в инвентаре
     * <p>
     * Но есть ДРУГОЙ сценарий, который наш Mixin на slotChangedCraftingGrid
     * НЕ ловит:
     * 1. Игрок открывает верстак с кузницей
     * 2. Крафтит предмет — результат в слоте
     * 3. Shift-кликает на результат — quickMoveStack забирает его в инвентарь
     * 4. quickMoveStack НЕ вызывает slotChangedCraftingGrid
     * 5. Игрок убирает кузницу — результат уже в инвентаре
     * <p>
     * Для блокировки этого сценария перехватываем quickMoveStack для слота 0.
     */
    @Inject(
            method = "quickMoveStack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onQuickMoveStack(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        // Блокируем только слот результата (индекс 0)
        if (slotIndex != 0) return;

        CraftingMenu menu = (CraftingMenu) (Object) this;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty()) return;

        // Проверяем верстак
        Level level = player.level();
        BlockPos craftingTablePos = findNearestCraftingTable(level, player.blockPosition());
        if (craftingTablePos == null) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (!ForgeValidator.canCraft(level, craftingTablePos, result.getItem())) {
            // Условия не выполнены — блокируем взятие предмета
            // и очищаем результат
            menu.getSlot(0).set(ItemStack.EMPTY);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    /**
     * Ищет верстак в кубическом радиусе 4 блоков от центра.
     */
    private static BlockPos findNearestCraftingTable(Level level, BlockPos center) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(mutable).is(Blocks.CRAFTING_TABLE)) {
                        return mutable.immutable();
                    }
                }
            }
        }
        return null;
    }
}