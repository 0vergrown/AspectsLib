package dev.overgrown.aspectslib.api.corruption;

import dev.overgrown.aspectslib.corruption.VitiumCorruptionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKey;

import java.util.Set;

public class VitiumCorruptionAPI {

    /**
     * Add Vitium to a biome, potentially starting corruption
     * @param world The server world
     * @param biomeKey The biome to affect
     * @param amount The amount of Vitium to add
     * @return The new Vitium level in the biome
     */
    public static int addVitiumToBiome(ServerWorld world, RegistryKey<Biome> biomeKey, int amount) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.addVitiumToBiome(biomeKey, amount);
    }

    /**
     * Set the Vitium level in a biome directly
     * @param world The server world
     * @param biomeKey The biome to affect
     * @param amount The exact Vitium amount to set
     * @return The new Vitium level
     */
    public static int setVitiumInBiome(ServerWorld world, RegistryKey<Biome> biomeKey, int amount) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.setVitiumInBiome(biomeKey, amount);
    }

    /**
     * Remove Vitium from a biome
     * @param world The server world
     * @param biomeKey The biome to affect
     * @param amount The amount of Vitium to remove
     * @return The new Vitium level in the biome
     */
    public static int removeVitiumFromBiome(ServerWorld world, RegistryKey<Biome> biomeKey, int amount) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.removeVitiumFromBiome(biomeKey, amount);
    }

    /**
     * Check if a biome is currently corrupted by Vitium
     * @param world The server world
     * @param biomeKey The biome to check
     * @return true if the biome is corrupted
     */
    public static boolean isBiomeCorrupted(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.isBiomeCorrupted(biomeKey);
    }

    /**
     * Get the current Vitium level in a biome
     * @param world The server world
     * @param biomeKey The biome to check
     * @return The amount of Vitium in the biome
     */
    public static int getBiomeVitiumLevel(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.getBiomeVitiumLevel(biomeKey);
    }

    /**
     * Force start corruption in a biome (useful for testing or special events)
     * @param world The server world
     * @param biomeKey The biome to corrupt
     * @param initialVitium The starting Vitium amount
     * @return true if corruption was started successfully
     */
    public static boolean startBiomeCorruption(ServerWorld world, RegistryKey<Biome> biomeKey, int initialVitium) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.startBiomeCorruption(biomeKey, initialVitium);
    }

    /**
     * Get the corruption level of a biome (0.0 to 1.0)
     * @param world The server world
     * @param biomeKey The biome to check
     * @return Corruption level from 0.0 (no corruption) to 1.0 (fully corrupted)
     */
    public static float getCorruptionLevel(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.getCorruptionLevel(biomeKey);
    }

    /**
     * Get all currently corrupted biomes
     * @param world The server world
     * @return Set of all corrupted biomes
     */
    public static Set<RegistryKey<Biome>> getCorruptedBiomes(ServerWorld world) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.getCorruptedBiomes();
    }

    /**
     * Force remove corruption from a biome
     * @param world The server world
     * @param biomeKey The biome to cleanse
     * @return true if corruption was removed, false if biome wasn't corrupted
     */
    public static boolean removeCorruption(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.removeCorruption(biomeKey);
    }

    /**
     * Get the number of corrupted chunks in a biome
     * @param world The server world
     * @param biomeKey The biome to check
     * @return Number of corrupted chunks, or 0 if not corrupted
     */
    public static int getCorruptedChunkCount(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        VitiumCorruptionManager.CorruptionData data = manager.getCorruptionData(biomeKey);
        return data != null ? data.getCorruptedChunks().size() : 0;
    }

    /**
     * Get the Aether level in a corrupted biome
     * @param world The server world
     * @param biomeKey The biome to check
     * @return Aether level, or -1 if biome is not corrupted
     */
    public static int getBiomeAetherLevel(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        VitiumCorruptionManager.CorruptionData data = manager.getCorruptionData(biomeKey);
        return data != null ? data.getAether() : -1;
    }

    /**
     * Check if a biome is in the process of being corrupted
     * @param world The server world
     * @param biomeKey The biome to check
     * @return true if corruption is actively spreading
     */
    public static boolean isCorruptionActive(ServerWorld world, RegistryKey<Biome> biomeKey) {
        VitiumCorruptionManager manager = VitiumCorruptionManager.get(world);
        return manager.isCorruptionActive(biomeKey);
    }
}