package dev.overgrown.aspectslib.data;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.api.IAspectDataProvider;
import dev.overgrown.aspectslib.codec.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.aspectslib.resonance.ResonanceCalculator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The `AspectData` class is the container for aspect amounts associated with an item and manages the association between items and their aspects. It stores the levels of aspects and provides functionality for NBT serialization, network synchronization, and manipulation. Now uses direct Identifier->int mapping instead of registry entries.
 * <p>
 * Features:
 * <li>NBT serialization</li>
 * <li>Network synchronization</li>
 * <li>Builder pattern for modification</li>
 * </p>
 * <p>
 * Usage:
 * <li>Stored on ItemStack via {@link IAspectDataProvider}</li>
 * <li>Used in tooltip rendering</li>
 * </p>
 */
public class AspectData {

    // Default instance with no aspects
    public static final AspectData DEFAULT = new AspectData(new Object2IntOpenHashMap<>());

    // Codec for serialization and deserialization
    private static Codec<Object2IntOpenHashMap<Identifier>> getInlineCodec() {
        return Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                .xmap(Object2IntOpenHashMap::new, Function.identity());
    }

    public AspectData(Map<Identifier, Integer> aspects) {
        this.aspects = new Object2IntOpenHashMap<>();
        this.aspects.putAll(aspects);
    }

    private static Codec<AspectData> getBaseCodec() {
        return RecordCodecBuilder.create(instance ->
                instance.group(
                        getInlineCodec().fieldOf("aspects").forGetter(component -> component.aspects)
                ).apply(instance, AspectData::new)
        );
    }

    public static final Codec<AspectData> CODEC = CodecUtils.withAlternative(
            CodecUtils.lazy(AspectData::getBaseCodec),
            getInlineCodec().xmap(AspectData::new, data -> data.aspects)
    );

    // Internal storage for aspects and their levels - now uses Identifier instead of RegistryEntry
    private final Object2IntOpenHashMap<Identifier> aspects;

    // Constructor
    public AspectData(Object2IntOpenHashMap<Identifier> aspects) {
        this.aspects = aspects;
    }

    /**
     * Gets the level of the specified aspect by identifier.
     *
     * @param aspectId The aspect identifier to query.
     * @return The level of the aspect, or 0 if not present.
     */
    public int getLevel(Identifier aspectId) {
        return this.aspects.getInt(aspectId);
    }

    /**
     * Gets the level of the specified aspect by name.
     *
     * @param aspectName The aspect name to query.
     * @return The level of the aspect, or 0 if not present.
     */
    public int getLevelByName(String aspectName) {
        Identifier aspectId = AspectManager.NAME_TO_ID.get(aspectName);
        return aspectId != null ? this.aspects.getInt(aspectId) : 0;
    }

    /**
     * Gets the set of aspect identifiers.
     *
     * @return An unmodifiable set of aspect identifiers.
     */
    public Set<Identifier> getAspectIds() {
        return Collections.unmodifiableSet(this.aspects.keySet());
    }

    /**
     * Gets the aspect map with their levels.
     *
     * @return A map of aspect identifiers to their levels.
     */
    public Object2IntOpenHashMap<Identifier> getMap() {
        return this.aspects;
    }

    /**
     * Gets the number of registered aspects.
     *
     * @return The number of aspects.
     */
    public int getSize() {
        return this.aspects.size();
    }

    /**
     * Checks if there are no aspects registered.
     *
     * @return `true` if no aspects are registered, otherwise `false`.
     */
    public boolean isEmpty() {
        return this.aspects.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof AspectData other) {
            return this.aspects.equals(other.aspects);
        }
        return false;
    }

    @Override
    public String toString() {
        return "AspectData{aspects=" + this.aspects + "}";
    }

    /**
     * Adds aspects from another AspectData to this one.
     */
    public AspectData addAspect(AspectData aspectData) {
        for (Object2IntMap.Entry<Identifier> aspectIntegerEntry : aspectData.aspects.object2IntEntrySet()) {
            this.aspects.put(aspectIntegerEntry.getKey(), this.aspects.getOrDefault(aspectIntegerEntry.getKey(), 0) + aspectIntegerEntry.getIntValue());
        }
        return this;
    }

    /**
     * Writes this AspectData to NBT
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(AspectsLib.LOGGER::error)
                .ifPresent(element -> nbt.put("AspectData", element));
        return nbt;
    }

    /**
     * Reads AspectData from NBT
     */
    public static AspectData fromNbt(NbtCompound nbt) {
        if (nbt.contains("AspectData", NbtElement.COMPOUND_TYPE)) {
            return CODEC.parse(NbtOps.INSTANCE, nbt.get("AspectData"))
                    .resultOrPartial(AspectsLib.LOGGER::error)
                    .orElse(DEFAULT);
        }
        return DEFAULT;
    }

    /**
     * Writes this AspectData to a packet buffer for network sync
     */
    public void toPacket(PacketByteBuf buf) {
        buf.writeVarInt(aspects.size());
        for (Object2IntMap.Entry<Identifier> entry : aspects.object2IntEntrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeVarInt(entry.getIntValue());
        }
    }

    /**
     * Reads AspectData from a packet buffer
     */
    public static AspectData fromPacket(PacketByteBuf buf) {
        int size = buf.readVarInt();
        Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
        for (int i = 0; i < size; i++) {
            Identifier aspectId = buf.readIdentifier();
            int amount = buf.readVarInt();
            aspects.put(aspectId, amount);
        }
        return new AspectData(aspects);
    }

    /**
     * Builder class for creating `AspectData` instances.
     */
    public static class Builder {
        private final Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();

        public Builder(AspectData data) {
            this.aspects.putAll(data.aspects);
        }

        /**
         * Sets the level of an aspect by identifier.
         *
         * @param aspectId The aspect identifier to set.
         * @param level  The level to set.
         */
        public void set(Identifier aspectId, int level) {
            if (level <= 0) {
                this.aspects.removeInt(aspectId);
            } else {
                this.aspects.put(aspectId, level);
            }
        }

        /**
         * Sets the level of an aspect by name.
         *
         * @param aspectName The aspect name to set.
         * @param level  The level to set.
         */
        public void setByName(String aspectName, int level) {
            Identifier aspectId = AspectManager.NAME_TO_ID.get(aspectName);
            if (aspectId != null) {
                set(aspectId, level);
            }
        }

        /**
         * Adds a level to an aspect by identifier.
         *
         * @param aspectId The aspect identifier to add.
         * @param level  The level to add.
         */
        public void add(Identifier aspectId, int level) {
            if (level > 0) {
                this.aspects.merge(aspectId, level, Integer::sum);
            }
        }

        /**
         * Adds a level to an aspect by name.
         *
         * @param aspectName The aspect name to add.
         * @param level  The level to add.
         */
        public void addByName(String aspectName, int level) {
            Identifier aspectId = AspectManager.NAME_TO_ID.get(aspectName);
            if (aspectId != null) {
                this.aspects.put(aspectId, this.aspects.getOrDefault(aspectId, 0) + level);
            }
        }

        /**
         * Removes aspects that match the given predicate.
         *
         * @param predicate The predicate to test.
         */
        public void remove(Predicate<Identifier> predicate) {
            this.aspects.keySet().removeIf(predicate);
        }

        /**
         * Builds the `AspectData`.
         *
         * @return The constructed `AspectData`.
         */
        public AspectData build() {
            return new AspectData(this.aspects);
        }
    }

    /**
     * Calculates the Resonance Units (RU).
     */
    public double calculateTotalRU() {
        double total = 0;
        for (Object2IntMap.Entry<Identifier> entry : aspects.object2IntEntrySet()) {
            total += entry.getIntValue();
        }
        return total;
    }

    // Add resonance calculation
    public ResonanceCalculator.ResonanceResult calculateResonance() {
        return ResonanceCalculator.calculate(this);
    }
}