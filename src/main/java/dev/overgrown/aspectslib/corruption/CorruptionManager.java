package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.aether.AetherChunkData;
import dev.overgrown.aspectslib.aether.AetherManager;
import dev.overgrown.aspectslib.aether.DeadZoneData;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectModifier;
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
    private static final Map<Identifier, CorruptionState> CORRUPTION_STATES = new HashMap<>();
    private static final Random RANDOM = new Random();

    // Configuration
    private static final int CORRUPTION_CHECK_INTERVAL = 200; // 10 seconds
    private static final int ASPECT_CONSUMPTION_INTERVAL = 400; // 20 seconds
    private static final int AETHER_CONSUMPTION_INTERVAL = 1200; // 60 seconds
    private static final int SCULK_SPREAD_CHANCE = 20; // 20% chance per check
    private static final int MAX_SCULK_PER_CHUNK = 64;
    private static final double PERMANENT_DEAD_ZONE_CHANCE = 0.1; // 10%

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

        // Group chunks by biome to process each biome only once
        Map<Identifier, List<ChunkPos>> chunksByBiome = new HashMap<>();

        for (ChunkPos chunkPos : loadedChunks) {
            BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
            Biome biome = world.getBiome(centerPos).value();
            Identifier biomeId = world.getRegistryManager()
                    .get(net.minecraft.registry.RegistryKeys.BIOME)
                    .getId(biome);

            if (biomeId != null) {
                chunksByBiome.computeIfAbsent(biomeId, k -> new ArrayList<>()).add(chunkPos);
            }
        }

        // Process each biome only once
        for (Map.Entry<Identifier, List<ChunkPos>> entry : chunksByBiome.entrySet()) {
            Identifier biomeId = entry.getKey();
            List<ChunkPos> biomesChunks = entry.getValue();

            // Pick a representative chunk for this biome
            ChunkPos representativeChunk = biomesChunks.get(0);

            processBiomeCorruption(world, biomeId, biomesChunks, representativeChunk, currentTime);
        }

        // Clean up old corruption states
        CORRUPTION_STATES.entrySet().removeIf(stateEntry ->
                !chunksByBiome.containsKey(stateEntry.getKey()) &&
                        stateEntry.getValue().lastSeen + 6000 < world.getTime()
        );
    }

    private static void processBiomeCorruption(ServerWorld world, Identifier biomeId,
                                               List<ChunkPos> biomesChunks,
                                               ChunkPos representativeChunk,
                                               long currentTime) {
        // Get the base biome aspects (from the original biome data file)
        AspectData baseBiomeAspects = BiomeAspectRegistry.get(biomeId);

        // Get combined aspects (original + modifications from the modifier)
        AspectData currentBiomeAspects = BiomeAspectModifier.getCombinedBiomeAspects(biomeId);

        // Check current Vitium amount
        int currentVitiumAmount = currentBiomeAspects.getLevel(VITIUM_ID);

        if (currentVitiumAmount == 0) {
            // Pure biome - no Vitium, clear corruption state
            updateCorruptionState(biomeId, CorruptionState.PURE, world.getTime());
            return;
        }

        // Calculate total of OTHER aspects (excluding Vitium) from the BASE biome
        int baseTotalOtherAspects = calculateTotalOtherAspects(baseBiomeAspects);

        // Calculate current total of other aspects
        int currentTotalOtherAspects = calculateTotalOtherAspects(currentBiomeAspects);

        // Corruption occurs when Vitium is GREATER THAN (not equal to) the ORIGINAL total
        // So for 15 total aspects, you need 16 or more Vitium to corrupt
        if (currentVitiumAmount > baseTotalOtherAspects) {
            // Corrupted biome
            CorruptionState state = CORRUPTION_STATES.computeIfAbsent(biomeId,
                    id -> new CorruptionState(CorruptionState.CORRUPTED, world.getTime()));

            if (state.state != CorruptionState.CORRUPTED) {
                state.state = CorruptionState.CORRUPTED;
                AspectsLib.LOGGER.info("Biome {} became corrupted! Vitium: {} > Base aspects total: {} (Current other aspects: {})",
                        biomeId, currentVitiumAmount, baseTotalOtherAspects, currentTotalOtherAspects);
            }
            state.lastSeen = world.getTime();

            // Process corruption effects for this biome's chunks
            processCorruption(world, biomesChunks, biomeId, currentBiomeAspects, currentTime);

            // Check if only Vitium remains - start consuming aether
            if (currentTotalOtherAspects == 0 && currentVitiumAmount > 0) {
                if (currentTime % AETHER_CONSUMPTION_INTERVAL == 0) {
                    processAetherConsumption(world, representativeChunk, biomeId, currentTime);
                }
            }
        } else {
            // Tainted biome (has Vitium but not enough to corrupt)
            updateCorruptionState(biomeId, CorruptionState.TAINTED, world.getTime());

            AspectsLib.LOGGER.debug("Biome {} is tainted. Vitium: {} <= Base aspects total: {} (needs to be > {} to corrupt)",
                    biomeId, currentVitiumAmount, baseTotalOtherAspects, baseTotalOtherAspects);
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

    private static void processCorruption(ServerWorld world, List<ChunkPos> biomesChunks,
                                          Identifier biomeId, AspectData currentAspects,
                                          long currentTime) {
        // Spread sculk in random chunks
        if (RANDOM.nextInt(100) < SCULK_SPREAD_CHANCE) {
            ChunkPos randomChunk = biomesChunks.get(RANDOM.nextInt(biomesChunks.size()));
            spreadSculk(world, randomChunk);
        }

        // Consume aspects ONCE per biome (not per chunk!)
        if (currentTime % ASPECT_CONSUMPTION_INTERVAL == 0) {
            consumeAspects(biomeId, currentAspects);
        }
    }

    private static void spreadSculk(ServerWorld world, ChunkPos chunkPos) {
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

    private static void consumeAspects(Identifier biomeId, AspectData currentAspects) {
        List<Identifier> nonVitiumAspects = new ArrayList<>();

        // Find all non-Vitium aspects with positive amounts
        for (Identifier aspectId : currentAspects.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ID) && currentAspects.getLevel(aspectId) > 0) {
                nonVitiumAspects.add(aspectId);
            }
        }

        if (nonVitiumAspects.isEmpty()) {
            AspectsLib.LOGGER.debug("No non-Vitium aspects left to consume in biome {}", biomeId);
            return;
        }

        // Pick a random aspect to consume
        Identifier targetAspect = nonVitiumAspects.get(RANDOM.nextInt(nonVitiumAspects.size()));
        int currentAmount = currentAspects.getLevel(targetAspect);

        if (currentAmount > 0) {
            // Reduce target aspect by 1, increase Vitium by 1
            BiomeAspectModifier.addBiomeModification(biomeId, targetAspect, -1);
            BiomeAspectModifier.addBiomeModification(biomeId, VITIUM_ID, 1);

            // Apply modifications to the registry so they persist and are visible
            BiomeAspectModifier.applyModificationsToRegistry();

            int newAmount = currentAmount - 1;
            int newVitiumAmount = currentAspects.getLevel(VITIUM_ID) + 1;

            AspectsLib.LOGGER.info("Vitium consumed 1 {} from biome {}. {}: {} -> {}, Vitium: {} -> {}",
                    targetAspect, biomeId, targetAspect, currentAmount, newAmount,
                    currentAspects.getLevel(VITIUM_ID), newVitiumAmount);

            // If aspect reaches 0, log it
            if (newAmount <= 0) {
                AspectsLib.LOGGER.info("Aspect {} completely consumed in biome {}! Moving to next aspect.",
                        targetAspect, biomeId);
            }
        }
    }

    private static void processAetherConsumption(ServerWorld world, ChunkPos chunkPos,
                                                 Identifier biomeId, long currentTime) {
        AetherChunkData aetherData = AetherManager.getAetherData(world, chunkPos);

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
                // Consume 1 point of aether, increase Vitium aspect by 1
                if (aetherData.harvestAether(targetAspect, 1)) {
                    BiomeAspectModifier.addBiomeModification(biomeId, VITIUM_ID, 1);
                    // Apply modifications to registry
                    BiomeAspectModifier.applyModificationsToRegistry();

                    AspectsLib.LOGGER.info("Consumed 1 {} Aether from chunk {}, total aether remaining: {}",
                            targetAspect, chunkPos, totalAether - 1);
                }
            }
        } else {
            // All aether depleted - create dead zone (only once per chunk)
            if (!AetherManager.isDeadZone(world, chunkPos)) {
                boolean permanent = RANDOM.nextDouble() < PERMANENT_DEAD_ZONE_CHANCE;
                DeadZoneData deadZoneData = new DeadZoneData(permanent, world.getTime());
                AetherManager.markAsDeadZone(world, chunkPos, deadZoneData);

                // Erase all aspects from the biome
                eraseAllAspects(biomeId);

                AspectsLib.LOGGER.info("Created {} dead zone at {} in biome {}",
                        permanent ? "permanent" : "temporary", chunkPos, biomeId);
            }
        }
    }

    private static void eraseAllAspects(Identifier biomeId) {
        // Get COMBINED aspects and set all to 0
        AspectData currentAspects = BiomeAspectModifier.getCombinedBiomeAspects(biomeId);
        for (Identifier aspectId : currentAspects.getAspectIds()) {
            int currentAmount = currentAspects.getLevel(aspectId);
            if (currentAmount > 0) {
                BiomeAspectModifier.addBiomeModification(biomeId, aspectId, -currentAmount);
            }
        }
        // Apply the erasure to registry
        BiomeAspectModifier.applyModificationsToRegistry();

        AspectsLib.LOGGER.info("Erased all aspects from biome {}", biomeId);
    }

    private static void updateCorruptionState(Identifier biomeId, int state, long time) {
        CorruptionState currentState = CORRUPTION_STATES.get(biomeId);
        if (currentState == null || currentState.state != state) {
            CORRUPTION_STATES.put(biomeId, new CorruptionState(state, time));
        } else {
            currentState.lastSeen = time;
        }
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

    // Getters for API access
    public static int getCorruptionState(Identifier biomeId) {
        CorruptionState state = CORRUPTION_STATES.get(biomeId);
        return state != null ? state.state : CorruptionState.PURE;
    }

    public static boolean isBiomeCorrupted(Identifier biomeId) {
        return getCorruptionState(biomeId) == CorruptionState.CORRUPTED;
    }

    public static boolean isBiomeTainted(Identifier biomeId) {
        return getCorruptionState(biomeId) == CorruptionState.TAINTED;
    }

    public static boolean isBiomePure(Identifier biomeId) {
        return getCorruptionState(biomeId) == CorruptionState.PURE;
    }

    // Corruption state tracking
    private static class CorruptionState {
        static final int PURE = 0;
        static final int TAINTED = 1;
        static final int CORRUPTED = 2;

        int state;
        long lastSeen;

        CorruptionState(int state, long lastSeen) {
            this.state = state;
            this.lastSeen = lastSeen;
        }
    }
}