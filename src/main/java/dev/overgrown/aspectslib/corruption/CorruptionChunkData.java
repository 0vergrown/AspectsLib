package dev.overgrown.aspectslib.corruption;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.Locale;

public class CorruptionChunkData {
    public enum Status {
        PURE,
        TAINTED,
        CORRUPTED,
        REGENERATING;

        public static Status fromOrdinal(int ordinal) {
            Status[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return PURE;
            }
            return values[ordinal];
        }

        public static Status fromString(String name) {
            try {
                return Status.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return PURE;
            }
        }
    }

    private final ChunkPos chunkPos;
    private Status status = Status.PURE;
    private Identifier biomeId;
    private long lastUpdatedTick;
    private long lastCorruptedTick;
    private long lastRegenerationTick;
    private long lastCleanTick;
    private int corruptionEvents;
    private int regenerationEvents;
    private int sculkPlacements;
    private final Object2IntOpenHashMap<Identifier> aspectDeltas = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<Identifier> aetherConsumed = new Object2IntOpenHashMap<>();

    public CorruptionChunkData(ChunkPos chunkPos) {
        this.chunkPos = chunkPos;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public Status getStatus() {
        return status;
    }

    public Identifier getBiomeId() {
        return biomeId;
    }

    public long getLastUpdatedTick() {
        return lastUpdatedTick;
    }

    public long getLastCorruptedTick() {
        return lastCorruptedTick;
    }

    public long getLastRegenerationTick() {
        return lastRegenerationTick;
    }

    public long getLastCleanTick() {
        return lastCleanTick;
    }

    public int getCorruptionEvents() {
        return corruptionEvents;
    }

    public int getRegenerationEvents() {
        return regenerationEvents;
    }

    public int getSculkPlacements() {
        return sculkPlacements;
    }

    public Object2IntOpenHashMap<Identifier> getAspectDeltas() {
        return aspectDeltas;
    }

    public Object2IntOpenHashMap<Identifier> getAetherConsumed() {
        return aetherConsumed;
    }

    public boolean setStatus(Status newStatus, Identifier biomeId, long tick) {
        boolean changed = false;
        if (biomeId != null) {
            this.biomeId = biomeId;
        }

        if (this.status != newStatus) {
            Status previous = this.status;
            this.status = newStatus;
            changed = true;

            if (newStatus == Status.CORRUPTED) {
                corruptionEvents++;
                lastCorruptedTick = tick;
            }

            if (previous == Status.CORRUPTED && newStatus != Status.CORRUPTED) {
                regenerationEvents++;
                lastRegenerationTick = tick;
            }

            if (newStatus == Status.PURE) {
                lastCleanTick = tick;
            }
        }

        lastUpdatedTick = tick;
        return changed;
    }

    public boolean recordAspectDelta(Identifier aspectId, int delta, Identifier biomeId, long tick) {
        if (delta == 0) {
            return false;
        }

        boolean changed = false;
        if (biomeId != null) {
            this.biomeId = biomeId;
        }

        int newAmount = aspectDeltas.getOrDefault(aspectId, 0) + delta;
        if (newAmount == 0) {
            changed = aspectDeltas.containsKey(aspectId);
            aspectDeltas.removeInt(aspectId);
        } else {
            aspectDeltas.put(aspectId, newAmount);
            changed = true;
        }

        lastUpdatedTick = tick;
        return changed;
    }

    public boolean recordAetherConsumption(Identifier aspectId, int amount, Identifier biomeId, long tick) {
        if (amount <= 0) {
            return false;
        }

        if (biomeId != null) {
            this.biomeId = biomeId;
        }

        aetherConsumed.addTo(aspectId, amount);
        lastUpdatedTick = tick;
        return true;
    }

    public boolean recordSculkPlacement(int count, long tick) {
        if (count <= 0) {
            return false;
        }

        sculkPlacements += count;
        lastUpdatedTick = tick;
        return true;
    }

    public boolean isPrunable() {
        return status == Status.PURE
                && aspectDeltas.isEmpty()
                && aetherConsumed.isEmpty()
                && sculkPlacements == 0
                && corruptionEvents == 0
                && regenerationEvents == 0;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("State", status.ordinal());
        if (biomeId != null) {
            nbt.putString("BiomeId", biomeId.toString());
        }
        nbt.putLong("LastUpdated", lastUpdatedTick);
        nbt.putLong("LastCorrupted", lastCorruptedTick);
        nbt.putLong("LastRegeneration", lastRegenerationTick);
        nbt.putLong("LastClean", lastCleanTick);
        nbt.putInt("CorruptionEvents", corruptionEvents);
        nbt.putInt("RegenerationEvents", regenerationEvents);
        nbt.putInt("SculkPlacements", sculkPlacements);

        if (!aspectDeltas.isEmpty()) {
            NbtList aspectList = new NbtList();
            for (Object2IntMap.Entry<Identifier> entry : aspectDeltas.object2IntEntrySet()) {
                NbtCompound aspectNbt = new NbtCompound();
                aspectNbt.putString("Id", entry.getKey().toString());
                aspectNbt.putInt("Delta", entry.getIntValue());
                aspectList.add(aspectNbt);
            }
            nbt.put("AspectDeltas", aspectList);
        }

        if (!aetherConsumed.isEmpty()) {
            NbtList aetherList = new NbtList();
            for (Object2IntMap.Entry<Identifier> entry : aetherConsumed.object2IntEntrySet()) {
                NbtCompound aetherNbt = new NbtCompound();
                aetherNbt.putString("Id", entry.getKey().toString());
                aetherNbt.putInt("Amount", entry.getIntValue());
                aetherList.add(aetherNbt);
            }
            nbt.put("AetherConsumed", aetherList);
        }

        return nbt;
    }

    public static CorruptionChunkData fromNbt(ChunkPos chunkPos, NbtCompound nbt) {
        CorruptionChunkData data = new CorruptionChunkData(chunkPos);
        data.status = Status.fromOrdinal(nbt.getInt("State"));
        if (nbt.contains("BiomeId", NbtElement.STRING_TYPE)) {
            data.biomeId = Identifier.tryParse(nbt.getString("BiomeId"));
        }
        data.lastUpdatedTick = nbt.getLong("LastUpdated");
        data.lastCorruptedTick = nbt.getLong("LastCorrupted");
        data.lastRegenerationTick = nbt.getLong("LastRegeneration");
        data.lastCleanTick = nbt.getLong("LastClean");
        data.corruptionEvents = nbt.getInt("CorruptionEvents");
        data.regenerationEvents = nbt.getInt("RegenerationEvents");
        data.sculkPlacements = nbt.getInt("SculkPlacements");

        if (nbt.contains("AspectDeltas", NbtElement.LIST_TYPE)) {
            NbtList aspectList = nbt.getList("AspectDeltas", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < aspectList.size(); i++) {
                NbtCompound aspectNbt = aspectList.getCompound(i);
                Identifier aspectId = Identifier.tryParse(aspectNbt.getString("Id"));
                int delta = aspectNbt.getInt("Delta");
                if (aspectId != null && delta != 0) {
                    data.aspectDeltas.put(aspectId, delta);
                }
            }
        }

        if (nbt.contains("AetherConsumed", NbtElement.LIST_TYPE)) {
            NbtList aetherList = nbt.getList("AetherConsumed", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < aetherList.size(); i++) {
                NbtCompound aetherNbt = aetherList.getCompound(i);
                Identifier aspectId = Identifier.tryParse(aetherNbt.getString("Id"));
                int amount = aetherNbt.getInt("Amount");
                if (aspectId != null && amount > 0) {
                    data.aetherConsumed.put(aspectId, amount);
                }
            }
        }

        return data;
    }
}
