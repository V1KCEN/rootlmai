package com.example.examplemod.rootimpact.tagri;

import com.example.examplemod.ExampleMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data Component {@code tag_ri} для системы тегов предметов ROOT IMPACT.
 * <p>
 * Хранит набор тегов предмета ({@code deathdrop}, {@code gentle5}, {@code gentle3}, {@code gentle1})
 * и оставшийся ресурс прочности gentle ({@code gentleUses}).
 * <p>
 * Компонент сериализуется через {@link #CODEC} (NBT, сохранение мира) и
 * {@link #STREAM_CODEC} (сеть). Благодаря этому теги сохраняются при:
 * <ul>
 *   <li>перемещении предмета между слотами, инвентарями, сундуками, шалкерами;</li>
 *   <li>разделении и объединении стаков;</li>
 *   <li>перезаходе игрока и перезапуске сервера.</li>
 * </ul>
 * Сравнение компонентов ({@link #equals}/{@link #hashCode}) используется игрой
 * для определения стакования: предметы с разным набором тегов не будут стакаться.
 */
public class TagRiComponent {

    /** DeferredRegister для Data Components мода */
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleMod.MODID);

    /** Codec для сохранения в NBT (мир, сундуки, инвентари, перезапуск сервера) */
    public static final Codec<TagRiComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.listOf()
                            .fieldOf("tags")
                            .forGetter(comp -> new ArrayList<>(comp.tags)),
                    Codec.INT.fieldOf("gentle_uses").forGetter(TagRiComponent::gentleUses)
            ).apply(instance, TagRiComponent::new)
    );

    /** StreamCodec для синхронизации по сети (клиент <-> сервер) */
    public static final StreamCodec<FriendlyByteBuf, TagRiComponent> STREAM_CODEC = StreamCodec.of(
            (buf, component) -> {
                buf.writeVarInt(component.tags.size());
                for (String tag : component.tags) {
                    buf.writeUtf(tag);
                }
                buf.writeVarInt(component.gentleUses);
            },
            buf -> {
                int tagCount = buf.readVarInt();
                Set<String> tags = new HashSet<>();
                for (int i = 0; i < tagCount; i++) {
                    tags.add(buf.readUtf(Short.MAX_VALUE));
                }
                int gentleUses = buf.readVarInt();
                return new TagRiComponent(new ArrayList<>(tags), gentleUses);
            }
    );

    /** Регистрация Data Component {@code tag_ri} */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TagRiComponent>> TAG_RI =
            DATA_COMPONENTS.registerComponentType(
                    "tag_ri",
                    builder -> builder
                            .persistent(CODEC)
                            .networkSynchronized(STREAM_CODEC)
            );

    private final Set<String> tags;
    private final int gentleUses;

    /**
     * Создаёт новый компонент тегов.
     *
     * @param tags      список тегов (не-null, копируется во множество)
     * @param gentleUses оставшийся ресурс gentle (0 если gentle-тег не установлен)
     */
    public TagRiComponent(List<String> tags, int gentleUses) {
        this.tags = tags != null ? Set.copyOf(tags) : Set.of();
        this.gentleUses = gentleUses;
    }

    /** {@return неизменяемый набор тегов предмета} */
    public Set<String> tags() {
        return tags;
    }

    /** {@return оставшийся ресурс gentle (количество срабатываний)} */
    public int gentleUses() {
        return gentleUses;
    }

    /** {@return true, если предмет имеет указанный тег} */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** {@return true, если предмет имеет любой gentle-тег} */
    public boolean hasGentleTag() {
        return tags.contains(TagRiTags.GENTLE_5)
                || tags.contains(TagRiTags.GENTLE_3)
                || tags.contains(TagRiTags.GENTLE_1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagRiComponent that)) return false;
        return gentleUses == that.gentleUses && Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, gentleUses);
    }

    @Override
    public String toString() {
        return "TagRiComponent{tags=" + tags + ", gentleUses=" + gentleUses + "}";
    }

    /**
     * Регистрирует Data Component на шине событий мода.
     * Должен вызываться из конструктора {@link ExampleMod}.
     *
     * @param modEventBus шина событий мода
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
