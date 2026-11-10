package com.example.examplemod.mixin;

import com.example.examplemod.forge.ForgeTier;
import com.example.examplemod.forge.ForgeValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для {@link SmithingMenu}.
 * <p>
 * Проверяет условия Tier III кузницы при создании результата в кузнечном столе.
 * <p>
 * ПОЛНОСТЬЮ БЕЗ @SHADOW. Использует:
 * <ul>
 *   <li>{@code @ModifyArg} на вызов {@code RecipeManager.getRecipesFor()} для получения {@link Level}</li>
 *   <li>{@code @Inject} в конец {@code createResult()} для проверки результата</li>
 *   <li> {@code this.capturedLevel} — обычное поле класса миксина (не @Shadow)</li>
 * </ul>
 * <p>
 * Позиция кузнечного стола определяется сканированием блоков Level
 * вокруг позиции, полученной из локальной переменной {@code access}
 * конструктора {@code SmithingMenu(int, Inventory, ContainerLevelAccess)}.
 */
@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {

    /** Обычное поле класса — не @Shadow. Сохраняет Level из вызова getRecipesFor. */
    private Level capturedLevel;

    /** Позиция кузнечного стола, захваченная из конструктора. Обычное поле — не @Shadow. */
    private BlockPos smithingTablePos;

    /**
     * Конструктор с ContainerLevelAccess. Захватывает позицию стола.
     * Параметр {@code access} — локальная переменная конструктора, не @Shadow.
     */
    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL")
    )
    private void captureTablePos(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                  ContainerLevelAccess access, CallbackInfo ci) {
        access.execute((level, pos) -> {
            this.smithingTablePos = pos.immutable();
        });
    }

    /**
     * Перехватывает аргумент Level в вызове
     * {@code RecipeManager.getRecipesFor(RecipeType, RecipeInput, Level)}.
     * Сохраняет Level для последующей проверки.
     */
    @ModifyArg(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;"
            ),
            index = 2
    )
    private Level captureLevel(Level level) {
        this.capturedLevel = level;
        return level;
    }

    /**
     * Внедряется в конец {@code createResult()} после того,
     * как результат уже установлен. Проверяет условия Tier III.
     */
    @Inject(
            method = "createResult",
            at = @At("TAIL")
    )
    private void onCreateResult(CallbackInfo ci) {
        if (capturedLevel == null || smithingTablePos == null) return;

        SmithingMenu menu = (SmithingMenu) (Object) this;
        ItemStack result = menu.getSlot(3).getItem(); // RESULT_SLOT = 3
        if (result.isEmpty()) return;

        ForgeTier tier = ForgeTier.getRequiredTier(result.getItem());
        if (tier != ForgeTier.TIER_3) return;

        // Проверяем условия Tier III вокруг кузнечного стола
        if (!ForgeValidator.canCraft(capturedLevel, smithingTablePos, result.getItem())) {
            menu.getSlot(3).set(ItemStack.EMPTY);
        }
    }
}