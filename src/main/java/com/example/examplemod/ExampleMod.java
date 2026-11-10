package com.example.examplemod;

import com.example.examplemod.rootimpact.AnimalTransportHandler;
import com.example.examplemod.rootimpact.CombatModifierHandler;
import com.example.examplemod.rootimpact.PlayerWeightHandler;
import com.example.examplemod.rootimpact.WeightPenaltySystem;
import com.example.examplemod.rootimpact.WeightRegistry;
import com.example.examplemod.rootimpact.creative.CreativeLimitData;
import com.example.examplemod.rootimpact.creative.CreativeLimitHandler;
import com.example.examplemod.rootimpact.enderchest.EnderChestHandler;
import com.example.examplemod.rootimpact.food.FoodItems;
import com.example.examplemod.rootimpact.food.FoodSpoilageData;
import com.example.examplemod.rootimpact.food.FoodSpoilageManager;
import com.example.examplemod.rootimpact.food.FoodSpoilageRegistry;
import com.example.examplemod.rootimpact.food.PumpkinPieStackSizeHandler;
import com.example.examplemod.rootimpact.repair.RepairCountComponent;
import com.example.examplemod.rootimpact.repair.RepairHandler;
import com.example.examplemod.rootimpact.tagri.TagRiComponent;
import com.example.examplemod.rootimpact.tagri.TagRiHandler;
import com.example.examplemod.rootimpact.trade.TradeFilter;
import com.example.examplemod.rootimpact.trade.TradeManager;
import com.example.examplemod.rootimpact.vanish.VanishHandler;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.animal.frog.Tadpole;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Root Impact: конструктор мода — начало инициализации.");

        modEventBus.addListener(this::commonSetup);

        // Регистрируем обработчики системы веса на NeoForge EVENT_BUS
        LOGGER.info("Root Impact: регистрация PlayerWeightHandler...");
        NeoForge.EVENT_BUS.register(new PlayerWeightHandler());
        LOGGER.info("Root Impact: PlayerWeightHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация WeightPenaltySystem...");
        NeoForge.EVENT_BUS.register(new WeightPenaltySystem());
        LOGGER.info("Root Impact: WeightPenaltySystem зарегистрирован.");

        LOGGER.info("Root Impact: регистрация AnimalTransportHandler...");
        NeoForge.EVENT_BUS.register(new AnimalTransportHandler());
        LOGGER.info("Root Impact: AnimalTransportHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация CombatModifierHandler...");
        NeoForge.EVENT_BUS.register(new CombatModifierHandler());
        LOGGER.info("Root Impact: CombatModifierHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация RepairHandler...");
        NeoForge.EVENT_BUS.register(new RepairHandler());
        LOGGER.info("Root Impact: RepairHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация TradeManager...");
        NeoForge.EVENT_BUS.register(new TradeManager());
        LOGGER.info("Root Impact: TradeManager зарегистрирован.");

        LOGGER.info("Root Impact: регистрация EnderChestHandler...");
        NeoForge.EVENT_BUS.register(new EnderChestHandler());
        LOGGER.info("Root Impact: EnderChestHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация FoodSpoilageManager...");
        NeoForge.EVENT_BUS.register(new FoodSpoilageManager());
        LOGGER.info("Root Impact: FoodSpoilageManager зарегистрирован.");

        LOGGER.info("Root Impact: регистрация PumpkinPieStackSizeHandler...");
        NeoForge.EVENT_BUS.register(new PumpkinPieStackSizeHandler());
        LOGGER.info("Root Impact: PumpkinPieStackSizeHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация CreativeLimitHandler...");
        NeoForge.EVENT_BUS.register(new CreativeLimitHandler());
        LOGGER.info("Root Impact: CreativeLimitHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация VanishHandler...");
        NeoForge.EVENT_BUS.register(new VanishHandler());
        LOGGER.info("Root Impact: VanishHandler зарегистрирован.");

        LOGGER.info("Root Impact: регистрация TagRiHandler...");
        NeoForge.EVENT_BUS.register(new TagRiHandler());
        LOGGER.info("Root Impact: TagRiHandler зарегистрирован.");

        // Регистрируем Data Components/reggistry на шине событий мода
        LOGGER.info("Root Impact: регистрация RepairCountComponent...");
        RepairCountComponent.register(modEventBus);
        LOGGER.info("Root Impact: RepairCountComponent зарегистрирован.");

        LOGGER.info("Root Impact: регистрация FoodSpoilageData...");
        FoodSpoilageData.register(modEventBus);
        LOGGER.info("Root Impact: FoodSpoilageData зарегистрирован.");

        LOGGER.info("Root Impact: регистрация CreativeLimitData...");
        CreativeLimitData.register(modEventBus);
        LOGGER.info("Root Impact: CreativeLimitData зарегистрирован.");

        LOGGER.info("Root Impact: регистрация TagRiComponent...");
        TagRiComponent.register(modEventBus);
        LOGGER.info("Root Impact: TagRiComponent зарегистрирован.");

        LOGGER.info("Root Impact: регистрация FoodItems...");
        FoodItems.register(modEventBus);
        LOGGER.info("Root Impact: FoodItems зарегистрирован.");

        // Регистрируем конфиг Root Impact
        LOGGER.info("Root Impact: регистрация конфига...");
        modContainer.registerConfig(ModConfig.Type.COMMON, RootImpactConfig.SPEC);
        LOGGER.info("Root Impact: конфиг зарегистрирован.");

        LOGGER.info("Root Impact: конструктор мода — инициализация завершена.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Root Impact: commonSetup — начало.");

        // ROOT IMPACT: головастик — единственная «детская» форма, которая НЕ наследуется от
        // AgeableMob (Tadpole extends AbstractFish extends WaterAnimal extends PathfinderMob),
        // поэтому AgeableMobAgingMixin его не покрывает. У головастика своя механика возраста
        // (Tadpole.aiStep -> setAge(this.age + 1) каждый тик) и своя ванильная константа
        // ticksToBeFrog = 24000 тиков, которая и определяет превращение в лягушку.
        // Утраиваем порог: 24000 -> 72000, то есть естественное взросление ровно в 3 раза медленнее.
        // Сам счётчик Age и кормление (ageUp -> getSpeedUpSecondsWhenFeeding) остаются ванильными.
        Tadpole.ticksToBeFrog = Tadpole.ticksToBeFrog * 3;
        LOGGER.info("Root Impact: Tadpole.ticksToBeFrog = {} (взросление головастика в 3 раза медленнее).", Tadpole.ticksToBeFrog);

        // Подтверждение активной механики возраста (полезно для проверки, что запущена именно эта сборка).
        LOGGER.info("Root Impact: замедление возраста АКТИВНО — взросление детёнышей и breeding cooldown ×3 медленнее (миксин AgeableMobAgingMixin, 1 изменение возраста за 3 тика).");

        // Инициализируем реестр весов
        WeightRegistry.init();
        LOGGER.info("Root Impact: система веса инициализирована.");

        // Инициализируем реестр сроков хранения еды
        FoodSpoilageRegistry.init();
        LOGGER.info("Root Impact: реестр сроков хранения еды инициализирован.");

        // Применяем правила торговли к жителям
        LOGGER.info("Root Impact: применение правил торговли...");
        TradeFilter.apply();
        LOGGER.info("Root Impact: правила торговли применены.");

        LOGGER.info("Root Impact: commonSetup — завершён.");
    }
}