package dev.overgrown.aspectslib.corruption;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.Collection;

public class CorruptionWorldState extends PersistentState {
    private final Long2ObjectMap<CorruptionChunkData> chunkData = new Long2ObjectOpenHashMap<>();

    public CorruptionChunkData getOrCreate(ChunkPos chunkPos) {
        return chunkData.computeIfAbsent(chunkPos.toLong(), pos -> {
            markDirty();
            return new CorruptionChunkData(chunkPos);
        });
    }

    public CorruptionChunkData get(ChunkPos chunkPos) {
        return chunkData.get(chunkPos.toLong());
    }

    public void pruneIfClean(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        CorruptionChunkData data = chunkData.get(key);
        if (data != null && data.isPrunable()) {
            chunkData.remove(key);
            markDirty();
        }
    }

    public Collection<CorruptionChunkData> getAll() {
        return chunkData.values();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList chunkList = new NbtList();
        for (Long2ObjectMap.Entry<CorruptionChunkData> entry : chunkData.long2ObjectEntrySet()) {
            NbtCompound chunkNbt = entry.getValue().toNbt();
            chunkNbt.putLong("Pos", entry.getLongKey());
            chunkList.add(chunkNbt);
        }
        nbt.put("Chunks", chunkList);
        return nbt;
    }

    public static CorruptionWorldState fromNbt(NbtCompound nbt) {
        CorruptionWorldState state = new CorruptionWorldState();
        if (nbt.contains("Chunks", NbtElement.LIST_TYPE)) {
            NbtList chunkList = nbt.getList("Chunks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < chunkList.size(); i++) {
                NbtCompound chunkNbt = chunkList.getCompound(i);
                ChunkPos chunkPos = new ChunkPos(chunkNbt.getLong("Pos"));
                CorruptionChunkData data = CorruptionChunkData.fromNbt(chunkPos, chunkNbt);
                state.chunkData.put(chunkPos.toLong(), data);
            }
        }
        return state;
    }
}
