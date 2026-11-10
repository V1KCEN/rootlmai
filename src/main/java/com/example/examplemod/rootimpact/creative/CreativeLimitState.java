package com.example.examplemod.rootimpact.creative;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.RootImpactConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Серверное состояние системы Creative Limit (сохраняется в мир).
 * <p>
 * Хранит:
 * <ul>
 *   <li>глобальный флаг «ограничить всех» ({@code /creativelimit allon|alloff})</li>
 *   <li>набор UUID игроков, ограниченных точечно ({@code /creativelimit on <игрок>})</li>
 *   <li>счётчик израсходованных выборок из Creative Menu (по UUID)</li>
 *   <li>реестр запрещённых предметов ({@code /registeritem add|delete})</li>
 *   <li>реестр запрещённых блоков ({@code /registerblock add|delete})</li>
 * </ul>
 * Данные пишутся в сохранение мира (vanilla {@link SavedData}, файл
 * {@code data/examplemod_creative_limit.dat} в папке мира), поэтому переживают
 * перезапуск сервера, выход игрока и смену измерения. Сохранение выполняется
 * автоматически при сохранении уровня (автосейв / остановка сервера) — мутаторы
 * помечают данные «грязными» через {@link #setDirty()}.
 */
public class CreativeLimitState extends SavedData {

    /** Имя данных в хранилище мира */
    public static final String DATA_NAME = ExampleMod.MODID + "_creative_limit";

    /** Текущее (единственное) состояние сервера */
    private static CreativeLimitState instance = new CreativeLimitState();

    /** Глобальный режим: ограничение распространяется на всех игроков */
    private boolean limitAll = false;

    /** Игроки, ограниченные точечно */
    private final Set<UUID> limitedPlayers = new LinkedHashSet<>();

    /** Израсходовано выборок из Creative Menu: UUID → количество */
    private final Map<UUID, Integer> usedGrants = new HashMap<>();

    /** Запрещённые предметы (registeritem) */
    private final Set<ResourceLocation> registeredItems = new LinkedHashSet<>();

    /** Запрещённые блоки (registerblock) */
    private final Set<ResourceLocation> registeredBlocks = new LinkedHashSet<>();

    // ------------------------------------------------------------------
    // Доступ и жизненный цикл
    // ------------------------------------------------------------------

    /** Текущее состояние системы (никогда не {@code null}). */
    public static CreativeLimitState get() {
        return instance;
    }

    /**
     * Загружает (или создаёт) состояние для указанного мира.
     * Вызывается при старте сервера: {@code ServerStartedEvent}.
     */
    public static void attach(ServerLevel overworld) {
        instance = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CreativeLimitState::new, CreativeLimitState::load),
                DATA_NAME
        );
    }


    // ------------------------------------------------------------------
    // Ограничение игроков
    // ------------------------------------------------------------------

    /** Ограничен ли игрок (глобально или точечно). */
    public boolean isLimited(Player player) {
        return isLimited(player.getUUID());
    }

    /** Ограничен ли игрок по UUID. */
    public boolean isLimited(UUID playerId) {
        return this.limitAll || this.limitedPlayers.contains(playerId);
    }

    /** Глобальный режим «ограничить всех». */
    public boolean isLimitAll() {
        return this.limitAll;
    }

    public void setLimitAll(boolean value) {
        this.limitAll = value;
        setDirty();
    }

    /** Точечно включает/снимает ограничение. При снятии сбрасывается счётчик выборок. */
    public void setLimited(UUID playerId, boolean value) {
        if (value) {
            this.limitedPlayers.add(playerId);
        } else {
            this.limitedPlayers.remove(playerId);
            this.usedGrants.remove(playerId);
        }
        setDirty();
    }

    /** Сбрасывает список точечно ограниченных игроков (используется {@code /creativelimit alloff}). */
    public void clearLimitedPlayers() {
        this.limitedPlayers.clear();
        setDirty();
    }

    /** Копия списка точечно ограниченных игроков. */
    public Set<UUID> getLimitedPlayers() {
        return Collections.unmodifiableSet(this.limitedPlayers);
    }

    // ------------------------------------------------------------------
    // Лимит выборок из Creative Menu
    // ------------------------------------------------------------------

    /** Максимальное количество выборок (из конфига, по умолчанию 5). */
    public int getMaxGrants() {
        return RootImpactConfig.CREATIVE_LIMIT_MAX_GRANTS.getAsInt();
    }

    /** Сколько выборок уже израсходовано игроком. */
    public int getUsedGrants(UUID playerId) {
        return this.usedGrants.getOrDefault(playerId, 0);
    }

    /** Сколько выборок осталось игроку. */
    public int getRemainingGrants(UUID playerId) {
        return Math.max(0, getMaxGrants() - getUsedGrants(playerId));
    }

    /**
     * Списывает одну выборку.
     *
     * @return {@code true} — выборка разрешена и списана; {@code false} — лимит исчерпан
     */
    public boolean consumeGrant(UUID playerId) {
        int used = getUsedGrants(playerId);
        if (used >= getMaxGrants()) {
            return false;
        }
        this.usedGrants.put(playerId, used + 1);
        setDirty();
        return true;
    }

    /** Сбрасывает израсходованные выборки игрока (выход из Creative, {@code /creativelimit reset}). */
    public void resetGrants(UUID playerId) {
        if (this.usedGrants.remove(playerId) != null) {
            setDirty();
        }
    }

    /** Сбрасывает счётчики выборок всех игроков. */
    public void resetAllGrants() {
        this.usedGrants.clear();
        setDirty();
    }

    // ------------------------------------------------------------------
    // Реестры запретов (registeritem / registerblock)
    // ------------------------------------------------------------------

    public boolean isItemRegistered(ResourceLocation itemId) {
        return itemId != null && this.registeredItems.contains(itemId);
    }

    public boolean isBlockRegistered(ResourceLocation blockId) {
        return blockId != null && this.registeredBlocks.contains(blockId);
    }

    /** @return {@code true}, если ID добавлен или уже присутствовал */
    public boolean addRegisteredItem(ResourceLocation itemId) {
        if (this.registeredItems.add(itemId)) {
            setDirty();
        }
        return true;
    }

    /** @return {@code true}, если ID был в реестре и удалён */
    public boolean removeRegisteredItem(ResourceLocation itemId) {
        boolean removed = this.registeredItems.remove(itemId);
        if (removed) setDirty();
        return removed;
    }

    /** @return {@code true}, если ID добавлен или уже присутствовал */
    public boolean addRegisteredBlock(ResourceLocation blockId) {
        if (this.registeredBlocks.add(blockId)) {
            setDirty();
        }
        return true;
    }

    /** @return {@code true}, если ID был в реестре и удалён */
    public boolean removeRegisteredBlock(ResourceLocation blockId) {
        boolean removed = this.registeredBlocks.remove(blockId);
        if (removed) setDirty();
        return removed;
    }

    public List<ResourceLocation> getRegisteredItems() {
        return new ArrayList<>(this.registeredItems);
    }

    public List<ResourceLocation> getRegisteredBlocks() {
        return new ArrayList<>(this.registeredBlocks);
    }

    // ------------------------------------------------------------------
    // Сохранение / загрузка
    // ------------------------------------------------------------------

    /** Читает список строк из NBT. */
    private static List<String> readStringList(CompoundTag tag, String key) {
        List<String> result = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    /** Пишет список ID в NBT. */
    private static ListTag writeStringList(Iterable<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    /** Десериализация данных мира. */
    public static CreativeLimitState load(CompoundTag tag, HolderLookup.Provider provider) {
        CreativeLimitState data = new CreativeLimitState();
        data.limitAll = tag.getBoolean("limit_all");

        ListTag limitedList = tag.getList("limited_players", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < limitedList.size(); i++) {
            try {
                data.limitedPlayers.add(NbtUtils.loadUUID(limitedList.get(i)));
            } catch (RuntimeException ignored) {
                // повреждённая запись UUID — пропускаем
            }
        }

        ListTag usedList = tag.getList("used_grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < usedList.size(); i++) {
            CompoundTag entry = usedList.getCompound(i);
            if (!entry.contains("uuid") || !entry.contains("used")) continue;
            try {
                data.usedGrants.put(NbtUtils.loadUUID(entry.get("uuid")), entry.getInt("used"));
            } catch (RuntimeException ignored) {
                // повреждённая запись — пропускаем
            }
        }

        for (String raw : readStringList(tag, "registered_items")) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) data.registeredItems.add(id);
        }
        for (String raw : readStringList(tag, "registered_blocks")) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) data.registeredBlocks.add(id);
        }
        return data;
    }

    /** Сериализация данных мира. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("limit_all", this.limitAll);

        ListTag limitedList = new ListTag();
        for (UUID playerId : this.limitedPlayers) {
            limitedList.add(NbtUtils.createUUID(playerId));
        }
        tag.put("limited_players", limitedList);

        ListTag usedList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : this.usedGrants.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("uuid", NbtUtils.createUUID(entry.getKey()));
            entryTag.putInt("used", entry.getValue());
            usedList.add(entryTag);
        }
        tag.put("used_grants", usedList);

        tag.put("registered_items", writeStringList(this.registeredItems));
        tag.put("registered_blocks", writeStringList(this.registeredBlocks));
        return tag;
    }
}
