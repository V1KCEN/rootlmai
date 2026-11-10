package com.example.examplemod.rootimpact.vanish;

import com.example.examplemod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent-состояние системы VANISH: индивидуальная видимость игроков.
 *
 * <p><b>Модель данных.</b> Для каждого «активатора» (скрываемого игрока) хранится:
 * <ul>
 *   <li>{@code overrides} — точечные права: наблюдатель UUID → {@code true} (видит) / {@code false}
 *       (не видит). По умолчанию (отсутствии записи) наблюдатель ВИДИТ активатора;</li>
 *   <li>{@code allActive} — включён ли массовый vanish;</li>
 *   <li>{@code snapshot} — точный набор наблюдателей, которым активатор был НЕ виден
 *       в момент включения массового vanish. Используется для точного восстановления;</li>
 *   <li>{@code tempOverrides} — временные точечные права, заданные ВО ВРЕМЯ активного all.
 *       Они не трогают {@code overrides} и исчезают при снятии all, после чего
 *       восстанавливается именно snapshot.</li>
 * </ul>
 *
 * <p>Эффективная видимость: {@code себя видит всегда} → иначе
 * {@code allActive ? tempOverrides (по умолчанию false) : overrides (по умолчанию true)}.
 *
 * <p>Данные пишутся в мир ({@code examplemod_vanish.dat}) и переживают выход/вход, смерть,
 * смену измерения и перезапуск сервера.
 */
public class VanishState extends SavedData {

    /** Имя данных в хранилище мира */
    public static final String DATA_NAME = ExampleMod.MODID + "_vanish";

    private static VanishState instance = new VanishState();

    /** UUID активатора → его состояние видимости */
    private final Map<UUID, Entry> entries = new HashMap<>();

    public VanishState() {
    }

    // ------------------------------------------------------------------
    // Жизненный цикл
    // ------------------------------------------------------------------

    public static VanishState get() {
        return instance;
    }

    /** Загружает (или создаёт) состояние для указанного мира. */
    public static void attach(ServerLevel overworld) {
        instance = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VanishState::new, VanishState::load),
                DATA_NAME
        );
    }

    // ------------------------------------------------------------------
// Запросы видимости
// ------------------------------------------------------------------

    /** Видит ли наблюдатель {@code observer} активатора {@code target}. */
    public boolean canSee(UUID observer, UUID target) {
        if (observer.equals(target)) return true;   // себя игрок всегда видит

        Entry entry = this.entries.get(target);
        if (entry == null) return true;             // по умолчанию все видят

        return entry.allActive ? entry.isTemporarilyVisibleTo(observer)
                               : entry.isVisibleTo(observer);
    }

    /** Видит ли наблюдатель (серверный игрок) активатора. */
    public boolean canSee(ServerPlayer observer, ServerPlayer target) {
        return canSee(observer.getUUID(), target.getUUID());
    }

    /** Активен ли vanish для активатора (all или есть хотя бы один скрытый наблюдатель). */
    public boolean isVanished(UUID target) {
        Entry entry = this.entries.get(target);
        if (entry == null) return false;
        if (entry.allActive) return true;
        return entry.overrides.containsValue(Boolean.FALSE);
    }

    public boolean isAllActive(UUID target) {
        Entry entry = this.entries.get(target);
        return entry != null && entry.allActive;
    }

    /** Есть ли точечные (не all) настройки видимости у активатора. */
    public boolean hasOverrides(UUID target) {
        Entry entry = this.entries.get(target);
        return entry != null && !entry.overrides.isEmpty();
    }

    /** UUID активаторов с активным vanish (для диагностики/статуса). */
    public Set<UUID> getVanishedTargets() {
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID target : this.entries.keySet()) {
            if (isVanished(target)) result.add(target);
        }
        return Collections.unmodifiableSet(result);
    }

    // ------------------------------------------------------------------
// Изменения
// ------------------------------------------------------------------

    /**
     * Точечное изменение видимости: {@code /vanish X on|off Y}.
     * <p>
     * Если массовый vanish активен, изменение ВРЕМЕННОЕ (идёт в {@code tempOverrides}) и не
     * трогает {@code overrides} — после снятия all восстановится snapshot. Иначе изменение
     * постоянное и попадает в {@code overrides}.
     */
    public void setVisibility(UUID target, UUID observer, boolean visible) {
        Entry entry = entryFor(target);
        if (entry.allActive) {
            entry.tempOverrides.put(observer, visible);
        } else {
            entry.overrides.put(observer, visible);
        }
        setDirty();
    }

    /**
     * Переключение массового vanish для активатора ({@code /vanish X} и {@code /vanish X all}).
     * <p>
     * При ВКЛЮЧЕНИИ сначала фиксируется точный снимок текущей видимости (наблюдатели, которым
     * активатор не был виден). При ВЫКЛЮЧЕНИИ {@code overrides} восстанавливается из снимка,
     * временные изменения отбрасываются. Снимок НЕ удаляется — он остаётся сохранённым.
     *
     * @return {@code true}, если массовый vanish включился; {@code false}, если выключился
     */
    public boolean toggleAll(UUID target) {
        Entry entry = entryFor(target);
        if (!entry.allActive) {
            // Фиксируем точный текущий статус: кто НЕ видел активатора.
            entry.snapshot = new LinkedHashSet<>();
            for (Map.Entry<UUID, Boolean> pair : entry.overrides.entrySet()) {
                if (!pair.getValue()) entry.snapshot.add(pair.getKey());
            }
            entry.allActive = true;
            entry.tempOverrides.clear();
        } else {
            // Восстанавливаем состояние, которое было ДО первого all.
            entry.overrides.clear();
            for (UUID observer : entry.snapshot) {
                entry.overrides.put(observer, Boolean.FALSE);
            }
            entry.allActive = false;
            entry.tempOverrides.clear();
            // entry.snapshot намеренно сохраняется.
        }
        setDirty();
        return entry.allActive;
    }

    /** Полностью сбрасывает vanish активатора (и снимок, и точечные настройки). */
    public void clearTarget(UUID target) {
        if (this.entries.remove(target) != null) setDirty();
    }

    /** Удаляет все настройки vanish (например, при полном сбросе системы). */
    public void clearAll() {
        if (!this.entries.isEmpty()) {
            this.entries.clear();
            setDirty();
        }
    }

    /**
     * Забывает наблюдателя, который вышел с сервера.
     * <p>
     * Постоянные (точечные) настройки НЕ удаляются: по задумлению они переживают перезаход.
     * Удаляются только временные права, заданные во время активного all, — они относятся
     * к конкретной сессии наблюдателя и не должны «протухать» молча.
     */
    public void forgetObserver(UUID observer) {
        boolean changed = false;
        for (Entry entry : this.entries.values()) {
            if (entry.allActive && entry.tempOverrides.remove(observer) != null) {
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    private Entry entryFor(UUID target) {
        return this.entries.computeIfAbsent(target, id -> new Entry());
    }

    // ------------------------------------------------------------------
// Сохранение
// ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> pair : this.entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("target", pair.getKey());
            pair.getValue().save(entryTag);
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }

    public static VanishState load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        VanishState state = new VanishState();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.hasUUID("target")) continue;
            UUID target = entryTag.getUUID("target");
            Entry entry = new Entry();
            entry.load(entryTag);
            state.entries.put(target, entry);
        }
        return state;
    }

    /** Состояние одного активатора. */
    private static final class Entry {

        /** Наблюдатель → видит ли (true) / не видит (false). Отсутствие = видит. */
        private final Map<UUID, Boolean> overrides = new HashMap<>();

        /** Активен ли массовый vanish. */
        private boolean allActive;

        /** Снимок: наблюдатели, которым активатор НЕ был виден до включения all. */
        private Set<UUID> snapshot = new LinkedHashSet<>();

        /** Временные точечные права, заданные во время активного all. */
        private final Map<UUID, Boolean> tempOverrides = new HashMap<>();

        boolean isVisibleTo(UUID observer) {
            return this.overrides.getOrDefault(observer, Boolean.TRUE);
        }

        boolean isTemporarilyVisibleTo(UUID observer) {
            // Во время all по умолчанию активатор скрыт от всех; временное on делает видимым.
            return this.tempOverrides.getOrDefault(observer, Boolean.FALSE);
        }

        void save(CompoundTag tag) {
            tag.putBoolean("all", this.allActive);
            tag.put("overrides", saveMap(this.overrides));
            tag.put("snapshot", saveSet(this.snapshot));
            tag.put("temp", saveMap(this.tempOverrides));
        }

        void load(CompoundTag tag) {
            this.allActive = tag.getBoolean("all");
            loadMap(tag.getCompound("overrides"), this.overrides);
            this.snapshot = new LinkedHashSet<>();
            ListTag snapshotList = tag.getList("snapshot", Tag.TAG_STRING);
            for (int i = 0; i < snapshotList.size(); i++) {
                try {
                    this.snapshot.add(UUID.fromString(snapshotList.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // повреждённая запись — пропускаем
                }
            }
            loadMap(tag.getCompound("temp"), this.tempOverrides);
        }

        private static CompoundTag saveMap(Map<UUID, Boolean> map) {
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<UUID, Boolean> pair : map.entrySet()) {
                tag.putBoolean(pair.getKey().toString(), pair.getValue());
            }
            return tag;
        }

        private static ListTag saveSet(Set<UUID> set) {
            ListTag list = new ListTag();
            for (UUID id : set) {
                list.add(StringTag.valueOf(id.toString()));
            }
            return list;
        }

        private static void loadMap(CompoundTag tag, Map<UUID, Boolean> target) {
            for (String key : tag.getAllKeys()) {
                try {
                    target.put(UUID.fromString(key), tag.getBoolean(key));
                } catch (IllegalArgumentException ignored) {
                    // некорректный UUID — пропускаем
                }
            }
        }
    }
}