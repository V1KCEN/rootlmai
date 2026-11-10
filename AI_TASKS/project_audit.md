# ROOT IMPACT — АУДИТ ПРОЕКТА (NeoForge 1.21.1)

> Справочник по структуре и системам мода. Создан в ходе первичного аудита.
> Исходный код не изменялся. Build не запускался.

## 1. Общие сведения

- **Платформа:** NeoForge 1.21.1 (`neo_version=21.1.248`), Minecraft `1.21.1`, Java 21, Parchment mappings `2024.11.17`.
- **Инструмент сборки:** NeoGradle userdev `7.1.38`.
- **MODID:** `examplemod`, пакет `com.example.examplemod`.
- **Спецификация:** `ROOT_IMPACT_SPEC.md` (разделы 1–16).
- Текущая задача в `AI_TASKS/current_task.md`.

### Структура пакетов
```
com.example.examplemod
├── ExampleMod.java            — @Mod, точка входа, регистрация хэндлеров
├── ExampleModClient.java      — клиентская точка входа, config-экран
├── Config.java                — демо-конфиг (не используется ROOT IMPACT)
├── RootImpactConfig.java      — основной COMMON-конфиг ROOT IMPACT
├── forge/                     — система кузни (Tier I–III)
├── mixin/                     — 8 Mixin-классов
└── rootimpact/
    ├── PlayerWeightHandler, WeightCalculator, WeightHudOverlay,
    │   WeightPenaltySystem, WeightRegistry        — система веса
    ├── CombatModifierHandler  — бой (урон мечей/топоров)
    ├── AnimalTransportHandler — транспорт (вьючные животные)
    ├── enchant/               — EnchantmentLimiter, AnvilEnchantmentHandler
    ├── repair/                — RepairHandler, RepairLimits, RepairCountComponent
    ├── trade/                 — TradeManager, TradeFilter, TradeRules, FilteredItemListing
    ├── enderchest/            — EnderChestHandler (полное удаление эндер-сундука)
    └── food/                  — FoodSpoilage*, FoodItems, RottenFoodItem и др.
```

### Точки входа и регистрация событий (`ExampleMod.java`)
- Конструктор `@Mod` регистрирует хэндлеры на `NeoForge.EVENT_BUS`:
  `PlayerWeightHandler`, `WeightPenaltySystem`, `AnimalTransportHandler`,
  `CombatModifierHandler`, `RepairHandler`, `TradeManager`, `EnderChestHandler`,
  `FoodSpoilageManager`, `PumpkinPieStackSizeHandler`, `AnvilEnchantmentHandler`.
- На `modEventBus` регистрируются Data Components и предметы:
  `RepairCountComponent.register`, `FoodSpoilageData.register`, `FoodItems.register`.
- Моду `modContainer` регистрируется `RootImpactConfig.SPEC` (COMMON).
- `commonSetup(...)`: `WeightRegistry.init()`, `FoodSpoilageRegistry.init()`, `TradeFilter.apply()`.
- Клиентский HUD веса — `WeightHudOverlay` через `@EventBusSubscriber(CLIENT)`.

### Конфигурация (`RootImpactConfig.java`, COMMON)
| Ключ | Назначение | По умолчанию |
|---|---|---|
| `maxWeight` | макс. вес игрока | 1000.0 |
| `shulkerContentMultiplier` | множитель веса содержимого шалкеров | 0.65 |
| `thresholdSprint` | порог запрета спринта | 0.41 |
| `thresholdSlowness1/2/3` | пороги Slowness I/II/III | 0.66 / 0.81 / 0.96 |
| `swordDamageMultiplier` | множитель урона мечей | 0.5 |
| `axeDamageMultiplier` | множитель урона топоров | 0.4 |
| `hudX/hudY/hudScale` | позиция/масштаб HUD веса | 10 / 10 / 1.0 |

**Примечание:** `Config.java` — пустой демо-конфиг шаблона, не используется системами мода.

---
## 2. Weight System (Система веса) — [РЕАЛИЗОВАНО]

**Классы:**
- `rootimpact/WeightRegistry` — карта `Item → вес`, `init()`, `getWeight()`, `isShulkerBox()`.
- `rootimpact/WeightCalculator` — подсчёт веса инвентаря, рекурсивный разбор шалкеров (×0.65), защита от бесконечной рекурсии по identity.
- `rootimpact/PlayerWeightHandler` — event-based кеш с dirty-флагом (без тиков), пересчёт при изменении инвентаря.
- `rootimpact/WeightPenaltySystem` — наложение штрафов каждые 20 тиков (Slowness I/II/III, Mining Fatigue, блок спринта).
- `rootimpact/WeightHudOverlay` — клиентский HUD (цвет меняется по заполненности).

**События/API:** `PlayerTickEvent.Post`, `PlayerEvent`, `PlayerContainerEvent.Open/Close`, `ItemEntityPickupEvent.Post`, `ItemTossEvent`, `PlayerInteractEvent.*`, `RenderGuiEvent.Post` (клиент).

**Пересечения:** использует `RootImpactConfig`; влияет на `AnimalTransportHandler` (шалкеры и ламы); HUD — клиент.

**Проблемы/наблюдения:**
- В спецификации штрафы: 41–65% → Slowness I; здесь в ветке 0.41–0.65 применяется **блок спринта**, а Slowness I — только с 0.66. `thresholdSprint=0.41` включён в конфиг, хотя «запрет спринта» в SPEC (раздел 1) не прописан. Поведение отличается от спецификации.
- Категория «Оружие/броня/инструменты: вес 0» из SPEC (раздел 1) **не соблюдена** — в `WeightRegistry` оружию/броне назначены ненулевые веса. Фактическая модель веса расходится с SPEC.
- Кеш весов на клиенте использует тот же `PlayerWeightHandler`; на сервере/клиенте могут быть рассинхронизированные dirty-флаги.
- `WeightPenaltySystem`, `PumpkinPieStackSizeHandler`, `FoodSpoilageManager` — три отдельные тик-системы с собственными счётчиками по UUID.

---

## 3. Combat (Бой) — [РЕАЛИЗОВАНО, частично]

**Класс:** `rootimpact/CombatModifierHandler`.

**События/API:** `LivingDamageEvent.Pre`.

**Логика:** только если атакующий — игрок (не Creative/Spectator). Мечи ×0.5, топоры ×0.4 (из конфига). Луки/арбалеты не изменяются (SPEC 6).

**Пересечения:** используется главной рукой `getMainHandItem()`, связан с системой зачарований (т.к. применяется на `Pre`).

**Проблемы/наблюдения:**
- Обильное логирование на каждый удар (`LOGGER.info`) — шум/производительность.
- Умножает итоговое значение урона уже с учётом зачарований — нужно проверить взаимодействие с системой Enchantments.

---

## 4. Repair (Починка) — [РЕАЛИЗОВАНО, частично]

**Классы:**
- `repair/RepairCountComponent` — Data Component `repair_count` (int, NBT+сеть).
- `repair/RepairLimits` — реестр лимитов ремонтов и множителей материала (железо ×1.5, алмаз ×1.25, незерит ×1.25).
- `repair/RepairHandler` — логика наковальни.
- `mixin/RepairItemRecipeMixin` — полное отключение ванильной починки через крафт (2×2 и 3×3).

**События/API:** `AnvilUpdateEvent`, `AnvilRepairEvent`, `ItemTooltipEvent`; Mixin на `RepairItemRecipe.matches`.

**Пересечения:** наковальня используется и `AnvilEnchantmentHandler` (зачарования) — оба обрабатывают `AnvilUpdateEvent`; потенциальный конфликт порядка обработки. Data Component `repair_count` связан с `current_task.md`.

**Проблемы/наблюдения:**
- `AnvilUpdateEvent` обрабатывают И `RepairHandler`, И `AnvilEnchantmentHandler` — две независимые подсистемы на одном событии.
- `current_task.md` требует: Sharpness I+I→II, II+II→II, II+II+Unbreaking I→II+Unbreaking I, не понижать существующие Sharpness III/V, не удалять Mending/Infinity/Flame, не отменять операцию из-за капа. Текущий `AnvilEnchantmentHandler` занижает повышения выше капа, но общая логика наковальни ещё проверяется.

---

## 5. Enchantments (Зачарования) — [РЕАЛИЗОВАНО, частично]

**Классы:**
- `enchant/EnchantmentLimiter` — центральный реестр: `FORBIDDEN` (Mending, Infinity, Thorns, Punch, Fire Aspect, Power) и `LEVEL_CAPS` (Sharpness 2, Protection 2, Projectile 2, Blast 2, FireProt 1, Efficiency 3, Looting 1, Fortune 1, Unbreaking 1).
- `enchant/AnvilEnchantmentHandler` — корректировка при объединении на наковальне.
- `mixin/EnchantmentHelperMixin` — фильтрация `selectEnchantment` (стол зачарований, лут, торговля).
- `mixin/EnchantBookForEmeraldsMixin` — фильтрация книг библиотекаря.

**События/API:** `AnvilUpdateEvent`; Mixin-перехваты `EnchantmentHelper.selectEnchantment`, `VillagerTrades.EnchantBookForEmeralds.getOffer`.

**Пересечения:** с `Trade` (книги/чары торговли), с `Repair` (одна наковальня), с Loot (через `selectEnchantment`).

**Проблемы/наблюдения:**
- `AnvilEnchantmentHandler` НЕ отменяет операцию при капе — просто занижает уровень. Согласуется с 7.4 SPEC.
- Существующие сверхкаповые книги применять разрешено — соответствует 7.5 SPEC.
- `/give` и Creative не ограничиваются (7.10, 7.11) — соблюдено.
- Про `current_task.md`: при `outLevel > maxInput` уровень занижается к капу; при `outLevel <= maxInput` сохраняется. `Sharpness I+I→II` кап=2 разрешён; `Sharpness II+II→III` занижается до II. Требуется проверка этих сценариев.

---
## 6. Trade (Торговля жителей) — [РЕАЛИЗОВАНО, частично]

**Классы:**
- `trade/TradeRules` — центральные правила (алмаз запрещён, снаряжение выше каменного/кольчужного запрещено, железо дорогое: ×3).
- `trade/FilteredItemListing` — обёртка `VillagerTrades.ItemListing`, фильтрует готовый `MerchantOffer` (алмазы, зачарованные предметы, запрещённое снаряжение; удорожает железо).
- `trade/TradeFilter` — оборачивает все сделки из `VillagerTrades.TRADES` (вызывается в `commonSetup`).
- `trade/TradeManager` — зарегистрирован на шине, фактически пустая точка расширения.
- `mixin/VillagerDiscountsMixin` — отключает скидки Hero of the Village и лечение зомби-жителя (`updateSpecialPrices`).
- `mixin/EnchantBookForEmeraldsMixin` — фильтрация книг (см. Enchantments).

**События/API:** `commonSetup → TradeFilter.apply()`; Mixin на `Villager.updateSpecialPrices`.

**Пересечения:** сильно пересекается с Enchantments (зачарованные книги); с `ForgeTier` (запрещает продавать железное снаряжение Tier I). `TradeFilter.apply()` мутирует статическую карту `VillagerTrades.TRADES` — глобальное изменение, критично в мультиплеере/перезагрузке датапаков.

**Проблемы/наблюдения:**
- `TradeManager` фактически не выполняет логики (пустой `onEntityJoinLevel`).
- Мутация общей статической карты `VillagerTrades.TRADES` может конфликтовать с другими модами/перезагрузкой.
- Железо в `TradeRules` «дорогое» (×3), но железная броня/инструменты в `FORBIDDEN_*` — продажа запрещена; согласованность с SPEC (раздел 8) требует уточнения.

---

## 7. Forge System (Кузницы) — [РЕАЛИЗОВАНО]

**Классы:**
- `forge/ForgeTier` — enum Tier I/II/III, радиусы, наборы предметов.
- `forge/ForgeSearch` — поиск/подсчёт блоков в кубическом радиусе, предикаты (наковальня, горячая печь/плавильня, лава/вода).
- `forge/ForgeValidator` — `canCraft()`: Tier I (наковальня+горящая печь+лава), Tier II (наковальня+плавильня+текучая вода+текучая лава), Tier III (только Незер, наковальня+8 источников лавы, Y-радиус 1), исключения-всегда-крафтятся (ведро, ножницы, наковальни).
- `mixin/CraftingMenuMixin` — блокировка результата в верстаке (3×3) через `ForgeValidator.canCraft` (после `slotChangedCraftingGrid` и в `quickMoveStack`).
- `mixin/SmithingMenuMixin` — проверка Tier III при крафте в кузнечном столе (захват Level/pos без `@Shadow`).

**События/API:** Mixin-перехваты `CraftingMenu`, `SmithingMenu`; `ForgeValidator.canCraft`.

**Пересечения:** с `Trade` (запрещены железные вещи Tier I в продаже), с `Enchantments`/`Repair` (наковальня).

**Проблемы/наблюдения:**
- `CraftingMenuMixin.findNearestCraftingTable` — линейный поиск ±4 блока (9×9) на каждую проверку, потенциально затратно.
- Поиск верстака отделён от фактически используемого верстака — может быть неточно при близко стоящих верстаках.
- Tier III требует Незер (SPEC 3 согласовано).

---

## 8. Transport (Транспорт/вьючные животные) — [РЕАЛИЗОВАНО]

**Классы:**
- `rootimpact/AnimalTransportHandler` — устанавливает strength=5 для ламы (рефлексивный вызов `setStrength`), блокирует шалкеры-интеракт c ламой.
- `mixin/AnimalInventoryMixin` — задаёт размер инвентаря осла (1 колонка=4 слота) и мула (2 колонки=7 слотов); ламу не трогает.
- `mixin/HorseInventoryMenuMixin` — заменяет грузовые слоты ламы на слоты с блокировкой шалкеров (`mayPlace`).

**События/API:** `EntityJoinLevelEvent`, `PlayerInteractEvent.EntityInteract`; Mixin на `AbstractChestedHorse`, `HorseInventoryMenu`.

**Пересечения:** с `WeightRegistry.isShulkerBox()` (общая проверка шалкеров).

**Проблемы/наблюдения:**
- SPEC: лама 15 слотов (strength 5), шалкеры запрещены; осёл 3 слота, мул 5 слотов. Здесь осёл=4, мул=7 (формула `columns*3+1`) — **не совпадает с SPEC** (3 и 5).
- Использование рефлексии для `setStrength` — хрупко; корректнее было MiniMixin/AT.

---

## 9. Ender Chest (Эндер-сундук) — [РЕАЛИЗОВАНО]

**Класс:** `rootimpact/enderchest/EnderChestHandler`.

**События/API:** `PlayerInteractEvent.RightClickBlock`, `PlayerEvent.ItemCraftedEvent`, `ItemEntityPickupEvent.Pre`, `ItemTooltipEvent`.

**Логика:** полное удаление: нельзя скрафтить (результат=0), открыть (блок удаляется), подобрать (предмет уничтожается); добавлен предупреждающий тултип.

**Пересечения:** изолирована; не влияет на другие системы.

**Проблемы/наблюдения:** агрессивное поведение (уничтожение блока) соответствует замыслу; явных проблем нет.

---

## 10. Food Spoilage (Порча еды) — [РЕАЛИЗОВАНО, частично]

**Классы:**
- `food/FoodSpoilageData` — Data Component `created_game_time` (long, NBT+сеть).
- `food/FoodSpoilageRegistry` — реестр сроков хранения в днях; исключения (не портятся): сушёная ламинария, мёд, тыквенный пирог.
- `food/FoodSpoilageManager` — порча: setCreatedIfAbsent при крафте/плавке/подборе; тиковая проверка раз в секунду; холодные биомы ×3; merge берёт самое старое время; замена на Rotten Food; тултипы свежести/«Непригодно».
- `food/FoodItems` — регистрация предмета `rotten_food`; `applyPumpkinPieStackSize` (max stack 16) без глобальной мутации vanilla.
- `food/RottenFoodItem` — протухшая еда: Hunger 30с, Confusion 15с, Poison 10с.
- `food/PumpkinPieStackSizeHandler` — отдельная тиковая система применения max stack 16 к тыквенному пирогу.

**События/API:** `PlayerTickEvent.Post`, `PlayerEvent.ItemCraftedEvent/ItemSmeltedEvent`, `ItemEntityPickupEvent.Pre`, `ItemStackedOnOtherEvent`, `PlayerContainerEvent.Open/Close`, `ItemTooltipEvent`, `PlayerEvent.PlayerLoggedOutEvent`.

**Пересечения:** прямых зависимостей от других систем нет; отдельная DataComponent и отдельный предмет.

**Проблемы/наблюдения:**
- SPEC 9: порча «продолжается независимо от загрузки чанка» — здесь проверяется только когда игрок онлайн (тик + открытие контейнера). Если чанк/мир не тикается, проверка не идёт. Возможное расхождение с SPEC.
- Степень свежести в тултипе зависит от биома игрока в момент отображения; при порче биом считается отдельно — возможна небольшая рассинхронизация.

---
## 11. Mixin'ы (сводка)

Файл конфигурации `examplemod.mixins.json` (`required: true`, `compatibilityLevel: JAVA_21`) объявляет 8 mixin:
| Mixin | Целевой класс | Назначение |
|---|---|---|
| `AnimalInventoryMixin` | `AbstractChestedHorse` | размер инвентаря осла/мула |
| `HorseInventoryMenuMixin` | `HorseInventoryMenu` | блок шалкеров в слотах ламы |
| `CraftingMenuMixin` | `CraftingMenu` | проверка кузни при крафте 3×3 |
| `SmithingMenuMixin` | `SmithingMenu` | проверка Tier III в кузнечном столе |
| `RepairItemRecipeMixin` | `RepairItemRecipe` | отключение починки крафтом |
| `VillagerDiscountsMixin` | `Villager` | отключение скидок |
| `EnchantmentHelperMixin` | `EnchantmentHelper` | фильтр `selectEnchantment` |
| `EnchantBookForEmeraldsMixin` | `VillagerTrades.EnchantBookForEmeralds` | фильтр книг библиотекаря |

Все mixin в секции `mixins` (общие, не client/server). `required: true` — при сбое применения любого mixin мод не запустится.

---

## 12. Реализовано / Частично / Отсутствует

### Полностью реализовано
- Weight System (учёт веса, кеш, штрафы, HUD, шалкеры ×0.65).
- Кузни Forge System (Tier I–III) с миксинами крафта/кузнечного стола.
- Ender Chest (полное удаление).
- Транспорт вьючных животных (лама/осёл/мул, блок шалкеров у ламы).
- Регистрации: Data Components (`repair_count`, `created_game_time`), предмет `rotten_food`.
- Mixin-инфраструктура (8 миксинов объявлены и реализованы).

### Частично реализовано / с расхождениями
- **Repair:** починка крафтом отключена, ремонт на наковальне + лимиты + DataComponent реализованы; логика наковальни проверяется под `current_task.md`.
- **Enchantments:** стол зачарований/лут/торговля фильтруются, наковальня занижает кап, запрещённые не создаются; требуется верификация сценариев `current_task.md`.
- **Trade:** фильтрация и отключение скидок работают; `TradeManager` — пустая точка; глобальная мутация `TRADES`.
- **Food Spoilage:** порча, холодные биомы, merge, Rotten Food реализованы; порча «офлайн/незагруженный чанк» не гарантируется; рассинхрон биома в тултипе.
- **Combat:** модификатор урона есть; избыточное логирование; взаимодействие с чарами требует проверки.
- **Weight:** категория «оружие/броня/инструменты = 0» SPEC не соблюдена; штраф «Slowness I на 41–65%» из SPEC заменён на блок спринта.

### Отсутствует (нет классов/реализации в `src`)
- Loot Tables фильтрация (раздел 5 и 7.7 SPEC) — реализована только через общий `selectEnchantment`, специализированной фильтрации структур/лута нет.
- Мир и респавн животных (раздел 10 SPEC) — не реализовано.
- Тотем/дроп с призывателя, durability bar (раздел 11) — не реализовано.
- Кастомные предметы/зелья из разделов 12–13 (Жидкая Смерть, Зелье дома, маски, кастомные предметы) — не реализовано.
- Маски/анонимность, скины, мультиплеерная имитация (разделы 14–15) — не реализовано.
- Какие-либо команды мода — отсутствуют.

---

## 13. Пересечения и зависимости систем

- **Наковальня (Anvil)** = точка пересечения `Repair` и `Enchantments` (оба слушают `AnvilUpdateEvent`) — ключевой риск конфликта и предмет `current_task.md`.
- **Наковальня также участвует в Forge System** (нужна для Tier I–III).
- **Weight ↔ Transport/Inventory** — общий признак шалкера через `WeightRegistry.isShulkerBox()`; лам и ослов затрагивает и вес, и транспорт.
- **Enchantments ↔ Trade ↔ Loot** — общие точки генерации (`selectEnchantment`, книги библиотекаря).
- **Trade ↔ ForgeTier** — запрет продажи железного снаряжения, пересекающего Tier I.
- **Конфиг RootImpactConfig** — используется Weight, Combat, HUD.
- Отдельные тик-системы независимы: `WeightPenaltySystem`, `FoodSpoilageManager`, `PumpkinPieStackSizeHandler`.

---

## 14. Известные проблемы (для будущих задач, НЕ исправлены)

1. **Weight/SPEC:** оружие/броня/инструменты должны весить 0, но в `WeightRegistry` веса > 0. Штраф 41–65% в SPEC = Slowness I, в коде = блок спринта.
2. **Transport/SPEC:** осёл по SPEC 3 слота (код: 4), мул 5 (код: 7).
3. **Anvil:** два слушателя `AnvilUpdateEvent` (Repair + Enchant) — риск неожиданных взаимодействий; сценарии `current_task.md` требуют верификации.
4. **Combat:** `LOGGER.info` на каждый удар (производительность/шум).
5. **FoodSpoilage:** порча не работает, если мир/чанк игрока не тикается (SPEC требует независимости от загрузки чанка); биом в тултипе может отличаться от биома при порче.
6. **Trade:** глобальная мутация `VillagerTrades.TRADES`; `TradeManager` пуст.
7. **Mixin `required:true`:** любая поломка сигнатур при обновлении MC/NeoForge полностью вырубит мод.
8. **`Config.java`** — неиспользуемый демо-конфиг шаблона.

---

*Аудит проведён: структура проекта, 38 Java-классов, 4 ресурсных файла, 2 build-конфига, спецификация ROOT_IMPACT_SPEC.md.*