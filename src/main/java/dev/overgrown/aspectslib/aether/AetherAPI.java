package dev.overgrown.aspectslib.aether;

import dev.overgrown.aspectslib.data.AspectData;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Map;

public class AetherAPI {

    /**
     * Checks if a spell can be cast at the given position with the specified aspect costs
     */
    public static boolean canCastSpell(World world, BlockPos pos, AspectData cost) {
        return canCastSpell(world, new ChunkPos(pos), cost);
    }

    /**
     * Checks if a spell can be cast in the given chunk with the specified aspect costs
     */
    public static boolean canCastSpell(World world, ChunkPos chunkPos, AspectData cost) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return false;
        }

        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);
        for (Map.Entry<Identifier, Integer> entry : cost.getMap().entrySet()) {
            if (!aetherData.canHarvest(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Harvests Aether for spell casting. Returns true if successful.
     */
    public static boolean castSpell(World world, BlockPos pos, AspectData cost) {
        return castSpell(world, new ChunkPos(pos), cost);
    }

    /**
     * Harvests Aether for spell casting. Returns true if successful.
     */
    public static boolean castSpell(World world, ChunkPos chunkPos, AspectData cost) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return false;
        }

        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);

        // First, check if we can harvest all required aspects
        for (Map.Entry<Identifier, Integer> entry : cost.getMap().entrySet()) {
            if (!aetherData.canHarvest(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        // Then, harvest all aspects (transactional)
        boolean allHarvested = true;
        for (Map.Entry<Identifier, Integer> entry : cost.getMap().entrySet()) {
            if (!aetherData.harvestAether(entry.getKey(), entry.getValue())) {
                allHarvested = false;
                break;
            }
        }

        return allHarvested;
    }

    /**
     * Gets the current Aether level for a specific aspect at a position
     */
    public static int getAetherLevel(World world, BlockPos pos, Identifier aspectId) {
        return getAetherLevel(world, new ChunkPos(pos), aspectId);
    }

    /**
     * Gets the current Aether level for a specific aspect in a chunk
     */
    public static int getAetherLevel(World world, ChunkPos chunkPos, Identifier aspectId) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return 0;
        }

        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);
        return aetherData.getCurrentAether(aspectId);
    }

    /**
     * Gets the maximum Aether capacity for a specific aspect at a position
     */
    public static int getAetherCapacity(World world, BlockPos pos, Identifier aspectId) {
        return getAetherCapacity(world, new ChunkPos(pos), aspectId);
    }

    /**
     * Gets the maximum Aether capacity for a specific aspect in a chunk
     */
    public static int getAetherCapacity(World world, ChunkPos chunkPos, Identifier aspectId) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return 0;
        }

        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);
        return aetherData.getMaxAether(aspectId);
    }

    /**
     * Gets the Aether percentage (0.0 to 1.0) for a specific aspect at a position
     */
    public static double getAetherPercentage(World world, BlockPos pos, Identifier aspectId) {
        return getAetherPercentage(world, new ChunkPos(pos), aspectId);
    }

    /**
     * Gets the Aether percentage (0.0 to 1.0) for a specific aspect in a chunk
     */
    public static double getAetherPercentage(World world, ChunkPos chunkPos, Identifier aspectId) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return 0.0;
        }

        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);
        return aetherData.getAetherPercentage(aspectId);
    }

    /**
     * Checks if a position is in a dead zone
     */
    public static boolean isDeadZone(World world, BlockPos pos) {
        return isDeadZone(world, new ChunkPos(pos));
    }

    /**
     * Checks if a chunk is a dead zone
     */
    public static boolean isDeadZone(World world, ChunkPos chunkPos) {
        return AetherManager.isDeadZone(world, chunkPos);
    }

    /**
     * Checks if a dead zone is permanent
     */
    public static boolean isPermanentDeadZone(World world, BlockPos pos) {
        return isPermanentDeadZone(world, new ChunkPos(pos));
    }

    /**
     * Checks if a dead zone is permanent
     */
    public static boolean isPermanentDeadZone(World world, ChunkPos chunkPos) {
        DeadZoneData deadZoneData = AetherManager.getDeadZoneData(world, chunkPos);
        return deadZoneData != null && deadZoneData.isPermanent();
    }

    /**
     * Forces recovery of Aether at a position (useful for debugging or admin commands)
     */
    public static void forceRecovery(World world, BlockPos pos) {
        forceRecovery(world, new ChunkPos(pos));
    }

    /**
     * Forces recovery of Aether in a chunk (useful for debugging or admin commands)
     */
    public static void forceRecovery(World world, ChunkPos chunkPos) {
        if (!AetherManager.isDeadZone(world, chunkPos)) {
            AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);
            aetherData.recoverAether();
        }
    }

    /**
     * Creates a temporary dead zone at the specified position
     */
    public static void createTemporaryDeadZone(World world, BlockPos pos) {
        createTemporaryDeadZone(world, new ChunkPos(pos));
    }

    /**
     * Creates a temporary dead zone in the specified chunk
     */
    public static void createTemporaryDeadZone(World world, ChunkPos chunkPos) {
        AetherManager.markAsDeadZone(world, chunkPos, new DeadZoneData(false, world.getTime()));
    }

    /**
     * Creates a permanent dead zone at the specified position
     */
    public static void createPermanentDeadZone(World world, BlockPos pos) {
        createPermanentDeadZone(world, new ChunkPos(pos));
    }

    /**
     * Creates a permanent dead zone in the specified chunk
     */
    public static void createPermanentDeadZone(World world, ChunkPos chunkPos) {
        AetherManager.markAsDeadZone(world, chunkPos, new DeadZoneData(true, world.getTime()));
    }

    /**
     * Removes a dead zone at the specified position
     */
    public static void removeDeadZone(World world, BlockPos pos) {
        removeDeadZone(world, new ChunkPos(pos));
    }

    /**
     * Removes a dead zone in the specified chunk
     */
    public static void removeDeadZone(World world, ChunkPos chunkPos) {
        AetherManager.removeDeadZone(world, chunkPos);
    }
}