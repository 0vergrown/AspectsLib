package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.data.AspectData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;

import java.util.Collection;
import java.util.Set;

public class CorruptionAPI {

    /**
     * Checks if a chunk is pure (no Vitium)
     */
    public static boolean isChunkPure(ServerWorld world, ChunkPos chunkPos) {
        return CorruptionManager.isChunkPure(world, chunkPos);
    }

    /**
     * Checks if a chunk is tainted (has Vitium but not corrupted)
     */
    public static boolean isChunkTainted(ServerWorld world, ChunkPos chunkPos) {
        return CorruptionManager.isChunkTainted(world, chunkPos);
    }

    /**
     * Checks if a chunk is corrupted (Vitium dominates)
     */
    public static boolean isChunkCorrupted(ServerWorld world, ChunkPos chunkPos) {
        return CorruptionManager.isChunkCorrupted(world, chunkPos);
    }

    /**
     * Forces a chunk region to become corrupted by adding Vitium
     * @param world The server world
     * @param chunkPos The chunk position
     * @param vitiumAmount The amount of Vitium to add
     */
    public static void forceCorruption(ServerWorld world, ChunkPos chunkPos, int vitiumAmount) {
        // Get the biome for this chunk
        BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
        Biome biome = world.getBiome(centerPos).value();
        Identifier biomeId = world.getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.BIOME)
                .getId(biome);

        if (biomeId == null) {
            AspectsLib.LOGGER.warn("Could not determine biome for chunk {}", chunkPos);
            return;
        }

        // Find the connected region
        Set<ChunkPos> region = BiomeRegionDetector.findConnectedBiomeChunks(world, chunkPos, biomeId, 32);

        Identifier vitiumId = AspectsLib.identifier("vitium");
        CorruptionDataManager.modifyRegionAspects(world, region, biomeId, vitiumId, vitiumAmount);

        AspectsLib.LOGGER.info("Forced corruption on chunk region {} (biome {}) by adding {} Vitium", chunkPos, biomeId, vitiumAmount);
    }

    /**
     * Purifies a chunk region by removing all Vitium
     * @param world The server world
     * @param chunkPos The chunk position
     */
    public static void purifyChunk(ServerWorld world, ChunkPos chunkPos) {
        // Get the biome for this chunk
        BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
        Biome biome = world.getBiome(centerPos).value();
        Identifier biomeId = world.getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.BIOME)
                .getId(biome);

        if (biomeId == null) {
            AspectsLib.LOGGER.warn("Could not determine biome for chunk {}", chunkPos);
            return;
        }

        // Find the connected region
        Set<ChunkPos> region = BiomeRegionDetector.findConnectedBiomeChunks(world, chunkPos, biomeId, 32);

        Identifier vitiumId = AspectsLib.identifier("vitium");

        // Get current Vitium amount for the region
        AspectData currentAspects = CorruptionDataManager.getChunkAspects(world, chunkPos, biomeId);
        int vitiumAmount = currentAspects.getLevel(vitiumId);

        if (vitiumAmount > 0) {
            // Remove all Vitium from the region
            CorruptionDataManager.modifyRegionAspects(world, region, biomeId, vitiumId, -vitiumAmount);
            AspectsLib.LOGGER.info("Purified chunk region {} (biome {}) by removing {} Vitium", chunkPos, biomeId, vitiumAmount);
        } else {
            AspectsLib.LOGGER.info("Chunk region {} (biome {}) has no Vitium to purify", chunkPos, biomeId);
        }
    }

    /**
     * Gets the amount of Vitium in a chunk's region
     */
    public static int getVitiumAmount(ServerWorld world, ChunkPos chunkPos) {
        // Get the biome for this chunk
        BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
        Biome biome = world.getBiome(centerPos).value();
        Identifier biomeId = world.getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.BIOME)
                .getId(biome);

        if (biomeId == null) {
            return 0;
        }

        Identifier vitiumId = AspectsLib.identifier("vitium");
        AspectData aspects = CorruptionDataManager.getChunkAspects(world, chunkPos, biomeId);
        return aspects.getLevel(vitiumId);
    }

    /**
     * Gets stored corruption tracking data for a chunk, if any has been recorded.
     * @param world The server world the chunk belongs to
     * @param chunkPos The chunk position
     * @return The saved corruption data for the chunk, or {@code null} if none exists yet
     */
    public static CorruptionChunkData getChunkData(ServerWorld world, ChunkPos chunkPos) {
        return CorruptionDataManager.getChunkData(world, chunkPos);
    }

    /**
     * Gets a read-only view of all tracked corruption chunks for a world.
     * @param world The server world to query
     * @return Collection of chunk data entries; empty if nothing has been tracked yet
     */
    public static Collection<CorruptionChunkData> getTrackedChunks(ServerWorld world) {
        return CorruptionDataManager.getAll(world);
    }
}
