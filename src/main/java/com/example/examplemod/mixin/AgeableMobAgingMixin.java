package com.example.examplemod.mixin;

import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ROOT IMPACT: замедляет ровно в 3 раза взросление детёнышей и breeding cooldown
 * для ВСЕХ {@link AgeableMob} — корова, свинья, курица, овца, лошадь, осёл, мул, лама,
 * лиса, черепаха, аксолотль, волк, кошка, панда, кролик, коза, верблюд, пчела,
 * странник, а также жители (Villager / WanderingTrader).
 * <p>
 * Единственная тиковая точка изменения возраста в vanilla 1.21.1 — {@code AgeableMob.aiStep()}
 * (проверено по всем вызовам {@code setAge}/{@code ageUp} в исходниках):
 * <pre>
 * } else if (this.isAlive()) {
 *     int i = this.getAge();
 *     if (i &lt; 0) this.setAge(++i);        // детёныш взрослеет
 *     else if (i &gt; 0) this.setAge(--i);   // идёт breeding cooldown
 * }
 * </pre>
 * Оба вызова {@code setAge} перенаправляются, и изменение применяется только каждое 3-е обращение
 * к изменению возраста (счётчик {@code rootImpactAgeTickCounter}). Величина изменения за тик
 * остаётся ванильной (1), но число тиковых изменений падает втрое:
 * <ul>
 *   <li>взросление детёныша: {@code BABY_START_AGE} -24000 → 72000 тиков естественного роста;</li>
 *   <li>breeding cooldown: vanilla {@code setAge(6000)} в
 *       {@code Animal.spawnChildFromBreeding}/{@code finalizeSpawnChildFromBreeding},
 *       {@code Fox}, {@code Turtle}, {@code VillagerMakeLove} — обратный отсчёт идёт 6000 → 18000
 *       тиков. Само стартовое значение 6000 не меняется, удлиняется именно отсчёт.</li>
 * </ul>
 * <p>
 * Кормление детёнышей остаётся ПОЛНОСТЬЮ ванильным: оно идёт через
 * {@code AgeableMob.ageUp(int, boolean)} (в {@code Animal.mobInteract}, {@code Panda},
 * {@code Sheep.ate}, {@code Camel} и т. д.), а {@code setAge} внутри {@code ageUp} вызывается
 * из тела {@code ageUp}, а не из {@code aiStep} — перенаправление (которое действует только на
 * места вызова внутри {@code aiStep}) его не видит. Поэтому эффективность кормления не делится на 3.
 * <p>
 * Головастик — единственная «детская» форма, которая НЕ {@code AgeableMob}
 * ({@code Tadpole extends AbstractFish}), у него своя механика и своя константа
 * {@code ticksToBeFrog}; её замедление сделано отдельно в {@code ExampleMod.commonSetup()}.
 */
@Mixin(AgeableMob.class)
public class AgeableMobAgingMixin {

    /**
     * ROOT IMPACT: собственный счётчик фактических обращений к ванильному изменению возраста.
     * <p>
     * ВАЖНО: раньше гейт был построен на {@code self.tickCount % 3 == 0}. {@code Entity.tickCount}
     * инкрементируется НЕ в {@code aiStep}, а серверным планировщиком тика сущностей —
     * {@code ServerLevel.tickNonPassenger()} ({@code p_entity.tickCount++} перед {@code tick()});
     * {@code aiStep()} лишь вызывается из {@code LivingEntity.tick()} → {@code this.aiStep()}.
     * То есть гейт зависел от внешней для {@code aiStep} переменной: если в каком-то рантайме/
     * конфигурации {@code tickCount} не растёт ровно на 1 за тик (или стоит на значении, кратном 3),
     * условие {@code % 3 == 0} выполняется на каждом тике — и замедление «исчезает» (скорость
     * становится ванильной). Именно поэтому замедление могло работать в одном окружении и
     * не работать в другом.
     * <p>
     * Теперь считаем сами вызовы изменения возраста внутри {@code aiStep}: на каждый тик жизни
     * детёныша (или каждый тик обратного отсчёта cooldown) приходится ровно одно обращение,
     * поэтому «1 изменение на 3 обращения» = «1 изменение возраста за 3 игровых тика»
     * независимо от {@code tickCount} и от планировщика тиков.
     */
    @Unique
    private int rootImpactAgeTickCounter;

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AgeableMob;setAge(I)V"
            )
    )
    private void rootImpact_slowAging(AgeableMob self, int newAge) {
        // Применяем только каждое 3-е изменение: взросление и breeding cooldown ×3 медленнее.
        if (++this.rootImpactAgeTickCounter % 3 == 0) {
            self.setAge(newAge);
        }
    }
}
