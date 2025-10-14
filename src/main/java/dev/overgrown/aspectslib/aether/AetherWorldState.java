package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AetherWorldState extends PersistentState {
    private final Map<ChunkPos, AetherChunkData> chunkData = new HashMap<>();
    private final Map<ChunkPos, DeadZoneData> deadZones = new HashMap<>();
    private World world;

    public AetherWorldState() {
        super();
    }

    public AetherChunkData getOrCreateChunkData(ChunkPos chunkPos, World world) {
        this.world = world;
        return chunkData.computeIfAbsent(chunkPos, pos -> {
            AetherChunkData data = new AetherChunkData(world, pos);
            markDirty();
            return data;
        });
    }

    public void markAsDeadZone(ChunkPos chunkPos, DeadZoneData data) {
        chunkData.remove(chunkPos);
        deadZones.put(chunkPos, data);
        markDirty();
    }

    public void removeDeadZone(ChunkPos chunkPos) {
        deadZones.remove(chunkPos);
        markDirty();
    }

    public boolean isDeadZone(ChunkPos chunkPos) {
        return deadZones.containsKey(chunkPos);
    }

    public DeadZoneData getDeadZoneData(ChunkPos chunkPos) {
        return deadZones.get(chunkPos);
    }

    public Collection<AetherChunkData> getAllChunkData() {
        return chunkData.values();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Save chunk data
        NbtList chunkDataList = new NbtList();
        for (Map.Entry<ChunkPos, AetherChunkData> entry : chunkData.entrySet()) {
            if (!entry.getValue().isEmpty()) { // Only save non-empty data
                NbtCompound chunkNbt = new NbtCompound();
                chunkNbt.putLong("Pos", entry.getKey().toLong());
                chunkNbt.put("Data", entry.getValue().toNbt());
                chunkDataList.add(chunkNbt);
            }
        }
        nbt.put("ChunkData", chunkDataList);

        // Save dead zones
        NbtList deadZoneList = new NbtList();
        for (Map.Entry<ChunkPos, DeadZoneData> entry : deadZones.entrySet()) {
            NbtCompound deadZoneNbt = new NbtCompound();
            deadZoneNbt.putLong("Pos", entry.getKey().toLong());
            deadZoneNbt.put("DeadZoneData", entry.getValue().toNbt());
            deadZoneList.add(deadZoneNbt);
        }
        nbt.put("DeadZones", deadZoneList);

        return nbt;
    }

    public static AetherWorldState fromNbt(NbtCompound nbt) {
        AetherWorldState state = new AetherWorldState();

        // Load chunk data
        if (nbt.contains("ChunkData", NbtElement.LIST_TYPE)) {
            NbtList chunkDataList = nbt.getList("ChunkData", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < chunkDataList.size(); i++) {
                NbtCompound chunkNbt = chunkDataList.getCompound(i);
                ChunkPos pos = new ChunkPos(chunkNbt.getLong("Pos"));
                AetherChunkData data = AetherChunkData.fromNbt(chunkNbt.getCompound("Data"));
                state.chunkData.put(pos, data);
            }
        }

        // Load dead zones
        if (nbt.contains("DeadZones", NbtElement.LIST_TYPE)) {
            NbtList deadZoneList = nbt.getList("DeadZones", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < deadZoneList.size(); i++) {
                NbtCompound deadZoneNbt = deadZoneList.getCompound(i);
                ChunkPos pos = new ChunkPos(deadZoneNbt.getLong("Pos"));
                DeadZoneData data = DeadZoneData.fromNbt(deadZoneNbt.getCompound("DeadZoneData"));
                state.deadZones.put(pos, data);
            }
        }

        return state;
    }
}