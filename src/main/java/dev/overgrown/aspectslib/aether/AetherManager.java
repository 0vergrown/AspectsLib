package dev.overgrown.aspectslib.aether;

import com.google.gson.JsonElement;
import dev.overgrown.aspectslib.AspectsLib;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AetherManager extends JsonDataLoader implements IdentifiableResourceReloadListener {
    private static final Map<Identifier, AetherConfig> DIMENSION_CONFIGS = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, AetherChunkData> CHUNK_CACHE = new ConcurrentHashMap<>();
    private static MinecraftServer server;

    public AetherManager() {
        super(AspectsLib.GSON, "aether_config");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        DIMENSION_CONFIGS.clear();
        CHUNK_CACHE.clear();

        prepared.forEach((id, json) -> {
            try {
                AetherConfig config = AspectsLib.GSON.fromJson(json, AetherConfig.class);
                DIMENSION_CONFIGS.put(id, config);
                AspectsLib.LOGGER.debug("Loaded Aether config for dimension: {}", id);
            } catch (Exception e) {
                AspectsLib.LOGGER.error("Failed to load Aether config {}: {}", id, e.getMessage());
            }
        });

        AspectsLib.LOGGER.info("Loaded {} Aether dimension configurations", DIMENSION_CONFIGS.size());
    }

    public static void setServer(MinecraftServer server) {
        AetherManager.server = server;
    }

    public static AetherChunkData getAetherData(World world, ChunkPos chunkPos) {
        if (world.isClient()) {
            return CHUNK_CACHE.computeIfAbsent(chunkPos, pos -> new AetherChunkData(world, pos));
        }

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        return worldState.getOrCreateChunkData(chunkPos, world);
    }

    public static void markAsDeadZone(World world, ChunkPos chunkPos, DeadZoneData data) {
        if (world.isClient()) return;

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        worldState.markAsDeadZone(chunkPos, data);
        CHUNK_CACHE.remove(chunkPos);
    }

    public static void removeDeadZone(World world, ChunkPos chunkPos) {
        if (world.isClient()) return;

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        worldState.removeDeadZone(chunkPos);
    }

    public static boolean isDeadZone(World world, ChunkPos chunkPos) {
        if (world.isClient()) {
            return false;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        return worldState.isDeadZone(chunkPos);
    }

    public static DeadZoneData getDeadZoneData(World world, ChunkPos chunkPos) {
        if (world.isClient()) return null;

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        return worldState.getDeadZoneData(chunkPos);
    }

    private static AetherWorldState getWorldState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();
        return persistentStateManager.getOrCreate(
                AetherWorldState::fromNbt,
                AetherWorldState::new,
                "aspectslib_aether"
        );
    }

    public static Collection<AetherChunkData> getAllChunkData(World world) {
        if (world.isClient()) return CHUNK_CACHE.values();

        ServerWorld serverWorld = (ServerWorld) world;
        AetherWorldState worldState = getWorldState(serverWorld);
        return worldState.getAllChunkData();
    }

    public static void saveAllData() {
        if (server != null) {
            for (ServerWorld world : server.getWorlds()) {
                AetherWorldState worldState = getWorldState(world);
                worldState.markDirty();
            }
        }
    }

    // ========== CONFIGURATION GETTER METHODS ==========

    /**
     * Gets the Aether recovery rate for the specified world
     * @param world The world to get the recovery rate for
     * @return The recovery rate in RU per day
     */
    public static double getRecoveryRate(World world) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        AetherConfig config = DIMENSION_CONFIGS.get(dimensionId);
        return config != null ? config.recoveryRate : 1.0;
    }

    /**
     * Gets the permanent dead zone threshold for the specified world
     * @param world The world to get the threshold for
     * @return The RU threshold for permanent dead zones
     */
    public static int getPermanentDeadZoneThreshold(World world) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        AetherConfig config = DIMENSION_CONFIGS.get(dimensionId);
        return config != null ? config.permanentDeadZoneThreshold : 10000;
    }

    /**
     * Gets the temporary dead zone recovery threshold for the specified world
     * @param world The world to get the threshold for
     * @return The recovery threshold as a percentage (0.0 to 1.0)
     */
    public static double getTemporaryDeadZoneRecoveryThreshold(World world) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        AetherConfig config = DIMENSION_CONFIGS.get(dimensionId);
        return config != null ? config.temporaryDeadZoneRecoveryThreshold : 0.1;
    }

    /**
     * Gets the chunk volume for Aether calculations
     * @param world The world to get the volume for
     * @return The volume of a chunk in cubic meters
     */
    public static int getChunkVolume(World world) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        AetherConfig config = DIMENSION_CONFIGS.get(dimensionId);
        return config != null ? config.chunkVolume : (16 * 16 * 256); // Default chunk volume
    }

    /**
     * Gets the Aether configuration for a specific dimension
     * @param dimensionId The dimension identifier
     * @return The Aether configuration, or null if not found
     */
    public static AetherConfig getDimensionConfig(Identifier dimensionId) {
        return DIMENSION_CONFIGS.get(dimensionId);
    }

    /**
     * Checks if a dimension has custom Aether configuration
     * @param dimensionId The dimension identifier
     * @return True if the dimension has custom configuration
     */
    public static boolean hasDimensionConfig(Identifier dimensionId) {
        return DIMENSION_CONFIGS.containsKey(dimensionId);
    }

    /**
     * Gets all registered dimension configurations
     * @return A map of all dimension configurations
     */
    public static Map<Identifier, AetherConfig> getAllDimensionConfigs() {
        return new ConcurrentHashMap<>(DIMENSION_CONFIGS);
    }

    @Override
    public Identifier getFabricId() {
        return AspectsLib.identifier("aether");
    }

    // Aether configuration per dimension
    public static class AetherConfig {
        public double recoveryRate = 1.0; // RU per day
        public int permanentDeadZoneThreshold = 10000;
        public double temporaryDeadZoneRecoveryThreshold = 0.1; // 10% of capacity
        public int chunkVolume = 16 * 16 * 256; // Default chunk volume

        public AetherConfig() {}

        public AetherConfig(double recoveryRate, int permanentDeadZoneThreshold, double temporaryDeadZoneRecoveryThreshold, int chunkVolume) {
            this.recoveryRate = recoveryRate;
            this.permanentDeadZoneThreshold = permanentDeadZoneThreshold;
            this.temporaryDeadZoneRecoveryThreshold = temporaryDeadZoneRecoveryThreshold;
            this.chunkVolume = chunkVolume;
        }
    }
}