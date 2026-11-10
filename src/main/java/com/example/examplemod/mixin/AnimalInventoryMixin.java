package com.example.examplemod.mixin;

import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин для изменения размера инвентаря вьючных животных.
 * <p>
 * В Minecraft 1.21.1 размер инвентаря AbstractChestedHorse определяется:
 * - getInventoryColumns() — количество колонок GUI (используется в HorseInventoryMenu и ClientboundHorseScreenOpenPacket)
 * - getInventorySize() — количество слотов контейнера (формула: columns * 3 + 1)
 * <p>
 * Оба метода должны быть согласованы, иначе GUI будет пытаться обратиться к несуществующим слотам.
 * <p>
 * Лама: не трогается — слоты задаются через strength (3*strength + 1).
 * Осёл: 1 колонка = 4 слота (3 грузовых + 1 седло).
 * Мул: 2 колонки = 7 слотов (6 грузовых + 1 седло, ближайшее к 5 по спецификации).
 */
@Mixin(AbstractChestedHorse.class)
public class AnimalInventoryMixin {

    @Inject(method = "getInventoryColumns", at = @At("HEAD"), cancellable = true)
    private void onGetInventoryColumns(CallbackInfoReturnable<Integer> cir) {
        AbstractChestedHorse self = (AbstractChestedHorse) (Object) this;
        String className = self.getClass().getSimpleName();

        if ("Donkey".equals(className)) {
            cir.setReturnValue(1); // 1 колонка = 4 слота (3 грузовых)
        } else if ("Mule".equals(className)) {
            cir.setReturnValue(2); // 2 колонки = 7 слотов (6 грузовых)
        }
        // Llama: не трогаем — слоты задаются через strength
    }

    @Inject(method = "getInventorySize", at = @At("HEAD"), cancellable = true)
    private void onGetInventorySize(CallbackInfoReturnable<Integer> cir) {
        AbstractChestedHorse self = (AbstractChestedHorse) (Object) this;
        String className = self.getClass().getSimpleName();

        if ("Donkey".equals(className)) {
            cir.setReturnValue(4); // 1*3+1 = 4
        } else if ("Mule".equals(className)) {
            cir.setReturnValue(7); // 2*3+1 = 7
        }
        // Llama: не трогаем — слоты задаются через strength
    }
}