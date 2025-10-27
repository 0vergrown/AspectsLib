package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.aether.AetherChunkData;
import dev.overgrown.aspectslib.aether.AetherManager;
import dev.overgrown.aspectslib.aether.DeadZoneData;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.*;

public class CorruptionManager {
    public static final Identifier VITIUM_ID = AspectsLib.identifier("vitium");
    private static final Random RANDOM = new Random();

    private record AspectConsumptionResult(Identifier aspectId, int previousAmount,
                                           int newAmount, int vitiumBefore, int vitiumAfter) {
    }

    // Configuration
    private static final int CORRUPTION_CHECK_INTERVAL = 200; // 10 seconds
    private static final int ASPECT_CONSUMPTION_INTERVAL = 400; // 20 seconds
    private static final int AETHER_CONSUMPTION_INTERVAL = 1200; // 60 seconds
    private static final int SCULK_SPREAD_CHANCE = 20; // 20% chance per check
    private static final int MAX_SCULK_PER_CHUNK = 64;
    private static final double PERMANENT_DEAD_ZONE_CHANCE = 0.1; // 10%
    private static final int MAX_REGION_RADIUS = 32; // Maximum radius for region detection

    public static void initialize() {
        ServerTickEvents.START_SERVER_TICK.register(CorruptionManager::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        long time = server.getOverworld().getTime();

        // Only check every CORRUPTION_CHECK_INTERVAL ticks for performance
        if (time % CORRUPTION_CHECK_INTERVAL != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            processWorldCorruption(world, time);
        }
    }

    private static void processWorldCorruption(ServerWorld world, long currentTime) {
        // Get all loaded chunks
        Set<ChunkPos> loadedChunks = getLoadedChunks(world);
        Set<ChunkPos> processedChunks = new HashSet<>();

        // Process each unprocessed chunk and its connected region
        for (ChunkPos chunkPos : loadedChunks) {
            if (processedChunks.contains(chunkPos)) {
                continue;
            }

            // Get the biome for this chunk
            BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
            Biome biome = world.getBiome(centerPos).value();
            Identifier biomeId = world.getRegistryManager()
                    .get(net.minecraft.registry.RegistryKeys.BIOME)
                    .getId(biome);

            if (biomeId == null) {
                continue;
            }

            // Find all connected chunks of the same biome (region)
            Set<ChunkPos> region = BiomeRegionDetector.findConnectedBiomeChunks(
                world, chunkPos, biomeId, MAX_REGION_RADIUS);

            // Mark all chunks in this region as processed
            processedChunks.addAll(region);

            // Process corruption for this region
            processRegionCorruption(world, biomeId, region, chunkPos, currentTime);
        }
    }

    private static void processRegionCorruption(ServerWorld world, Identifier biomeId,
                                                Set<ChunkPos> region,
                                                ChunkPos representativeChunk,
                                                long currentTime) {
        // Get the region's aspects (modified by corruption effects)
        AspectData currentRegionAspects = CorruptionDataManager.getChunkAspects(world, representativeChunk, biomeId);

        // Check current Vitium amount
        int currentVitiumAmount = currentRegionAspects.getLevel(VITIUM_ID);

        if (currentVitiumAmount == 0) {
            updateRegionStatuses(world, region, biomeId, CorruptionChunkData.Status.PURE, currentTime);
            return;
        }

        // Calculate total of OTHER aspects (excluding Vitium) from the CURRENT region data
        int currentTotalOtherAspects = calculateTotalOtherAspects(currentRegionAspects);

        // For the base comparison, we should also use current aspects to be consistent
        // But we need to exclude Vitium from the base calculation
        int baseTotalForComparison = currentTotalOtherAspects;

        // Log the aspects
        if (AspectsLib.LOGGER.isDebugEnabled()) {
            AspectsLib.LOGGER.debug("Region {} aspects - Vitium: {}, Other aspects total: {}",
                    biomeId, currentVitiumAmount, baseTotalForComparison);
            for (Identifier aspectId : currentRegionAspects.getAspectIds()) {
                int amount = currentRegionAspects.getLevel(aspectId);
                if (!aspectId.equals(VITIUM_ID)) {
                    AspectsLib.LOGGER.debug("  {}: {}", aspectId, amount);
                }
            }
        }

        // Corruption occurs when Vitium is GREATER THAN the total of other aspects
        // So for 15 total other aspects, you need 16 or more Vitium to corrupt
        if (currentVitiumAmount > baseTotalForComparison) {
            updateRegionStatuses(world, region, biomeId, CorruptionChunkData.Status.CORRUPTED, currentTime);
            
            AspectsLib.LOGGER.info("Region {} became corrupted! Vitium: {} > Other aspects total: {}",
                    biomeId, currentVitiumAmount, baseTotalForComparison);

            // Process corruption effects for this region's chunks
            processRegionCorruptionEffects(world, region, biomeId, representativeChunk, currentRegionAspects, currentTime);

            // Check if only Vitium remains - start consuming aether
            if (currentTotalOtherAspects == 0 && currentVitiumAmount > 0) {
                if (currentTime % AETHER_CONSUMPTION_INTERVAL == 0) {
                    processAetherConsumption(world, region, biomeId, currentTime);
                }
            }
        } else {
            updateRegionStatuses(world, region, biomeId, CorruptionChunkData.Status.TAINTED, currentTime);

            AspectsLib.LOGGER.debug("Region {} is tainted. Vitium: {} <= Other aspects total: {} (needs to be > {} to corrupt)",
                    biomeId, currentVitiumAmount, baseTotalForComparison, baseTotalForComparison);
        }
    }

    private static void updateRegionStatuses(ServerWorld world, Set<ChunkPos> region,
                                             Identifier biomeId, CorruptionChunkData.Status status, long tick) {
        for (ChunkPos chunkPos : region) {
            CorruptionDataManager.updateChunkStatus(world, chunkPos, biomeId, status, tick);
        }
    }

    private static int calculateTotalOtherAspects(AspectData aspects) {
        int total = 0;
        for (Identifier aspectId : aspects.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ID)) {
                total += aspects.getLevel(aspectId);
            }
        }
        return total;
    }

    private static void processRegionCorruptionEffects(ServerWorld world, Set<ChunkPos> region,
                                                       Identifier biomeId, ChunkPos representativeChunk, AspectData currentAspects,
                                                       long currentTime) {
        // Spread sculk in random chunks
        if (RANDOM.nextInt(100) < SCULK_SPREAD_CHANCE) {
            List<ChunkPos> regionList = new ArrayList<>(region);
            ChunkPos randomChunk = regionList.get(RANDOM.nextInt(regionList.size()));
            spreadSculk(world, randomChunk, currentTime);
        }

        // Consume aspects ONCE per region (not per chunk!)
        if (currentTime % ASPECT_CONSUMPTION_INTERVAL == 0) {
            consumeRegionAspects(world, region, biomeId, currentAspects).ifPresent(result -> {
                CorruptionDataManager.recordAspectDelta(world, representativeChunk, biomeId, result.aspectId(), -1, currentTime);
                int vitiumDelta = result.vitiumAfter() - result.vitiumBefore();
                if (vitiumDelta != 0) {
                    CorruptionDataManager.recordAspectDelta(world, representativeChunk, biomeId, VITIUM_ID, vitiumDelta, currentTime);
                }
            });
        }
    }

    private static void spreadSculk(ServerWorld world, ChunkPos chunkPos, long currentTime) {
        int sculkCount = 0;

        for (int i = 0; i < 3; i++) { // Try 3 times to place sculk
            if (sculkCount >= MAX_SCULK_PER_CHUNK) break;

            int x = chunkPos.getStartX() + RANDOM.nextInt(16);
            int z = chunkPos.getStartZ() + RANDOM.nextInt(16);
            BlockPos pos = findSurfacePosition(world, new BlockPos(x, 0, z));

            if (pos != null && world.getBlockState(pos).isAir() &&
                    world.getBlockState(pos.down()).isOpaque()) {
                world.setBlockState(pos, Blocks.SCULK.getDefaultState());
                sculkCount++;
                CorruptionDataManager.recordSculkPlacement(world, chunkPos, 1, currentTime);

                // Play sculk spread sound effect
                world.playSound(
                        null, // player - null means all nearby players will hear it
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        net.minecraft.sound.SoundEvents.BLOCK_SCULK_SPREAD, // The sculk spread sound
                        net.minecraft.sound.SoundCategory.BLOCKS,
                        1.0f, // volume
                        0.8f + RANDOM.nextFloat() * 0.4f // pitch variation (0.8 to 1.2)
                );

                AspectsLib.LOGGER.debug("Placed sculk at {} in chunk {}", pos, chunkPos);
            }
        }
    }

    private static BlockPos findSurfacePosition(World world, BlockPos pos) {
        for (int y = world.getTopY(); y >= world.getBottomY(); y--) {
            BlockPos currentPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (world.getBlockState(currentPos).isOpaque()) {
                return currentPos.up();
            }
        }
        return null;
    }

    private static Optional<AspectConsumptionResult> consumeRegionAspects(ServerWorld world, Set<ChunkPos> region, Identifier biomeId, AspectData currentAspects) {
        List<Identifier> nonVitiumAspects = new ArrayList<>();

        // Find all non-Vitium aspects with positive amounts
        for (Identifier aspectId : currentAspects.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ID) && currentAspects.getLevel(aspectId) > 0) {
                nonVitiumAspects.add(aspectId);
            }
        }

        if (nonVitiumAspects.isEmpty()) {
            AspectsLib.LOGGER.debug("No non-Vitium aspects left to consume in region {}", biomeId);
            return Optional.empty();
        }

        // Pick a random aspect to consume
        Identifier targetAspect = nonVitiumAspects.get(RANDOM.nextInt(nonVitiumAspects.size()));
        int currentAmount = currentAspects.getLevel(targetAspect);

        if (currentAmount > 0) {
            // Reduce target aspect by 1, increase Vitium by 1 across the region
            int previousVitiumAmount = currentAspects.getLevel(VITIUM_ID);
            CorruptionDataManager.modifyRegionAspects(world, region, biomeId, targetAspect, -1);
            CorruptionDataManager.modifyRegionAspects(world, region, biomeId, VITIUM_ID, 1);

            // Get the updated aspects to verify the change
            ChunkPos representativeChunk = region.iterator().next();
            AspectData updatedAspects = CorruptionDataManager.getChunkAspects(world, representativeChunk, biomeId);
            int newAmount = updatedAspects.getLevel(targetAspect);
            int newVitiumAmount = updatedAspects.getLevel(VITIUM_ID);

            AspectsLib.LOGGER.info("Vitium consumed 1 {} from region {}. {}: {} -> {}, Vitium: {} -> {}",
                    targetAspect, biomeId, targetAspect, currentAmount, newAmount,
                    previousVitiumAmount, newVitiumAmount);

            // If aspect reaches 0, log it
            if (newAmount <= 0) {
                AspectsLib.LOGGER.info("Aspect {} completely consumed in region {}! Moving to next aspect.",
                        targetAspect, biomeId);
            }

            return Optional.of(new AspectConsumptionResult(targetAspect, currentAmount, newAmount, previousVitiumAmount, newVitiumAmount));
        }

        return Optional.empty();
    }

    private static void processAetherConsumption(ServerWorld world, Set<ChunkPos> region,
                                                 Identifier biomeId, long currentTime) {
        // Process aether consumption for a random chunk in the region
        List<ChunkPos> regionList = new ArrayList<>(region);
        ChunkPos targetChunk = regionList.get(RANDOM.nextInt(regionList.size()));
        
        AetherChunkData aetherData = AetherManager.getAetherData(world, targetChunk);

        // Calculate total aether remaining in the chunk
        int totalAether = 0;
        for (Identifier aspectId : aetherData.getAspectIds()) {
            totalAether += aetherData.getCurrentAether(aspectId);
        }

        if (totalAether > 0) {
            // Find an aspect with aether to consume (prioritize non-Vitium aspects)
            Identifier targetAspect = null;
            for (Identifier aspectId : aetherData.getAspectIds()) {
                if (aetherData.getCurrentAether(aspectId) > 0) {
                    targetAspect = aspectId;
                    if (!aspectId.equals(VITIUM_ID)) {
                        break;
                    }
                }
            }

            if (targetAspect != null) {
                // Consume 1 point of aether, increase Vitium aspect by 1 across the region
                if (aetherData.harvestAether(targetAspect, 1)) {
                    CorruptionDataManager.modifyRegionAspects(world, region, biomeId, VITIUM_ID, 1);
                    CorruptionDataManager.recordAetherConsumption(world, targetChunk, biomeId, targetAspect, 1, currentTime);
                    CorruptionDataManager.recordAspectDelta(world, targetChunk, biomeId, VITIUM_ID, 1, currentTime);

                    AspectsLib.LOGGER.info("Consumed 1 {} Aether from chunk {}, total aether remaining: {}",
                            targetAspect, targetChunk, totalAether - 1);
                }
            }
        } else {
            // All aether depleted - create dead zone (only once per chunk)
            if (!AetherManager.isDeadZone(world, targetChunk)) {
                boolean permanent = RANDOM.nextDouble() < PERMANENT_DEAD_ZONE_CHANCE;
                DeadZoneData deadZoneData = new DeadZoneData(permanent, world.getTime());
                AetherManager.markAsDeadZone(world, targetChunk, deadZoneData);

                // Erase all aspects from the region
                eraseRegionAspects(world, region, biomeId);
                CorruptionDataManager.updateChunkStatus(world, targetChunk, biomeId, CorruptionChunkData.Status.REGENERATING, currentTime);

                AspectsLib.LOGGER.info("Created {} dead zone at {} in region {}",
                        permanent ? "permanent" : "temporary", targetChunk, biomeId);
            }
        }
    }

    private static void eraseRegionAspects(ServerWorld world, Set<ChunkPos> region, Identifier biomeId) {
        // Get current aspects and set all to 0 across the region
        ChunkPos representativeChunk = region.iterator().next();
        AspectData currentAspects = CorruptionDataManager.getChunkAspects(world, representativeChunk, biomeId);
        for (Identifier aspectId : currentAspects.getAspectIds()) {
            int currentAmount = currentAspects.getLevel(aspectId);
            if (currentAmount > 0) {
                CorruptionDataManager.modifyRegionAspects(world, region, biomeId, aspectId, -currentAmount);
            }
        }

        AspectsLib.LOGGER.info("Erased all aspects from region {}", biomeId);
    }


    private static Set<ChunkPos> getLoadedChunks(ServerWorld world) {
        Set<ChunkPos> loadedChunks = new HashSet<>();

        // Collect chunks around players (within view distance)
        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos playerChunk = player.getChunkPos();
            int viewDistance = world.getServer().getPlayerManager().getViewDistance();

            // Add chunks in a radius around each player
            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                    // Check if chunk is actually loaded
                    if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                        loadedChunks.add(chunkPos);
                    }
                }
            }
        }

        return loadedChunks;
    }

    // Chunk-based API access
    public static boolean isChunkCorrupted(ServerWorld world, ChunkPos chunkPos) {
        CorruptionChunkData data = CorruptionDataManager.getChunkData(world, chunkPos);
        return data != null && data.getStatus() == CorruptionChunkData.Status.CORRUPTED;
    }

    public static boolean isChunkTainted(ServerWorld world, ChunkPos chunkPos) {
        CorruptionChunkData data = CorruptionDataManager.getChunkData(world, chunkPos);
        return data != null && data.getStatus() == CorruptionChunkData.Status.TAINTED;
    }

    public static boolean isChunkPure(ServerWorld world, ChunkPos chunkPos) {
        CorruptionChunkData data = CorruptionDataManager.getChunkData(world, chunkPos);
        return data == null || data.getStatus() == CorruptionChunkData.Status.PURE;
    }
}
