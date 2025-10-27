package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.data.AspectData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentStateManager;

import java.util.Collection;
import java.util.List;

public final class CorruptionDataManager {
    private static final String CORRUPTION_STATE_KEY = "aspectslib_corruption";
    private static final String CHUNK_ASPECT_STORAGE_KEY = "aspectslib_chunk_aspects";

    private CorruptionDataManager() {
    }

    public static CorruptionWorldState getWorldState(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(
                CorruptionWorldState::fromNbt,
                CorruptionWorldState::new,
                CORRUPTION_STATE_KEY
        );
    }

    public static void updateChunkStatus(ServerWorld world, ChunkPos chunkPos, Identifier biomeId,
                                         CorruptionChunkData.Status status, long tick) {
        CorruptionWorldState state = getWorldState(world);
        CorruptionChunkData data = state.getOrCreate(chunkPos);
        boolean changed = data.setStatus(status, biomeId, tick);
        if (changed) {
            state.markDirty();
        }
        if (data.isPrunable()) {
            state.pruneIfClean(chunkPos);
        }
    }

    public static void recordAspectDelta(ServerWorld world, ChunkPos chunkPos, Identifier biomeId,
                                         Identifier aspectId, int delta, long tick) {
        if (delta == 0) {
            return;
        }

        CorruptionWorldState state = getWorldState(world);
        CorruptionChunkData data = state.getOrCreate(chunkPos);
        boolean changed = data.recordAspectDelta(aspectId, delta, biomeId, tick);
        if (changed) {
            state.markDirty();
        }
        if (data.isPrunable()) {
            state.pruneIfClean(chunkPos);
        }
    }

    public static void recordBulkAspectDelta(ServerWorld world, Collection<ChunkPos> chunkPositions,
                                             Identifier biomeId, Identifier aspectId, int delta, long tick) {
        for (ChunkPos chunkPos : chunkPositions) {
            recordAspectDelta(world, chunkPos, biomeId, aspectId, delta, tick);
        }
    }

    public static void recordAetherConsumption(ServerWorld world, ChunkPos chunkPos, Identifier biomeId,
                                               Identifier aspectId, int amount, long tick) {
        if (amount <= 0) {
            return;
        }

        CorruptionWorldState state = getWorldState(world);
        CorruptionChunkData data = state.getOrCreate(chunkPos);
        boolean changed = data.recordAetherConsumption(aspectId, amount, biomeId, tick);
        if (changed) {
            state.markDirty();
        }
    }

    public static void recordSculkPlacement(ServerWorld world, ChunkPos chunkPos, int count, long tick) {
        if (count <= 0) {
            return;
        }

        CorruptionWorldState state = getWorldState(world);
        CorruptionChunkData data = state.getOrCreate(chunkPos);
        boolean changed = data.recordSculkPlacement(count, tick);
        if (changed) {
            state.markDirty();
        }
    }

    public static Collection<CorruptionChunkData> getAll(ServerWorld world) {
        return List.copyOf(getWorldState(world).getAll());
    }

    public static CorruptionChunkData getChunkData(ServerWorld world, ChunkPos chunkPos) {
        return getWorldState(world).get(chunkPos);
    }

    public static ChunkAspectStorage getChunkAspectStorage(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(
                ChunkAspectStorage::fromNbt,
                ChunkAspectStorage::new,
                CHUNK_ASPECT_STORAGE_KEY
        );
    }

    public static AspectData getChunkAspects(ServerWorld world, ChunkPos chunkPos, Identifier biomeId) {
        return getChunkAspectStorage(world).getChunkAspects(chunkPos, biomeId);
    }

    public static void modifyChunkAspect(ServerWorld world, ChunkPos chunkPos, Identifier biomeId, 
                                        Identifier aspectId, int delta) {
        getChunkAspectStorage(world).modifyChunkAspect(chunkPos, biomeId, aspectId, delta);
    }

    public static void modifyRegionAspects(ServerWorld world, Collection<ChunkPos> region, Identifier biomeId,
                                          Identifier aspectId, int delta) {
        ChunkAspectStorage storage = getChunkAspectStorage(world);
        for (ChunkPos chunkPos : region) {
            storage.modifyChunkAspect(chunkPos, biomeId, aspectId, delta);
        }
    }
}
