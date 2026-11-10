package com.example.examplemod.rootimpact;

import com.example.examplemod.RootImpactConfig;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Модификатор атаки мечей и топоров (раздел 6 ROOT_IMPACT_SPEC.md).
 * <p>
 * Изменяет реальный атрибут {@link Attributes#ATTACK_DAMAGE} предмета через
 * {@link ItemAttributeModifierEvent}, а не умножает урон в момент нанесения удара.
 * Это гарантирует, что tooltip показывает фактический итоговый урон и что
 * урон не уменьшается дважды (атрибут + прямой множитель в LivingDamageEvent).
 * <p>
 * Логика:
 * <ul>
 *   <li>Мечи — итоговый полный урон атаки × {@code SWORD_DAMAGE_MULTIPLIER}</li>
 *   <li>Топоры — итоговый полный урон атаки × {@code AXE_DAMAGE_MULTIPLIER}</li>
 *   <li>Остальные предметы (включая луки/арбалеты) — не изменяются</li>
 * </ul>
 * ATTACK_SPEED и остальные атрибуты не затрагиваются.
 * <p>
 * Полный урон атаки равен сумме базового урона игрока (обычно 1.0) и модификатора
 * предмета. Чтобы итог после изменения совпал с требуемым (total × multiplier),
 * новый модификатор вычисляется как {@code total * multiplier - playerBase}.
 */
public class CombatModifierHandler {

    /** Базовый урон атаки игрока без оружия (константа vanilla ATTACK_DAMAGE base). */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;

    /** id ванильного модификатора базового урона предмета. */
    private static final net.minecraft.resources.ResourceLocation BASE_ATTACK_DAMAGE_ID = Item.BASE_ATTACK_DAMAGE_ID;

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        double multiplier;
        if (stack.is(ItemTags.SWORDS)) {
            multiplier = RootImpactConfig.SWORD_DAMAGE_MULTIPLIER.get();
        } else if (stack.is(ItemTags.AXES)) {
            multiplier = RootImpactConfig.AXE_DAMAGE_MULTIPLIER.get();
        } else {
            return; // остальные предметы (луки, инструменты и т.д.) не трогаем
        }

        // Ищем vanilla модификатор ATTACK_DAMAGE предмета (id = base_attack_damage),
        // чтобы получить его "чистое" значение из модификатора.
        Double vanillaItemModifier = null;
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)
                    && entry.modifier().id().equals(BASE_ATTACK_DAMAGE_ID)) {
                vanillaItemModifier = entry.modifier().amount();
                break;
            }
        }
        if (vanillaItemModifier == null) return;

        double totalVanilla = PLAYER_BASE_ATTACK_DAMAGE + vanillaItemModifier;
        double newTotal = totalVanilla * multiplier;
        double newModifier = newTotal - PLAYER_BASE_ATTACK_DAMAGE;

        // Заменяем vanilla модификатор ATTACK_DAMAGE на новый (тот же id — атомарная замена).
        event.replaceModifier(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(
                        BASE_ATTACK_DAMAGE_ID,
                        newModifier,
                        AttributeModifier.Operation.ADD_VALUE
                ),
                net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND
        );
    }
}