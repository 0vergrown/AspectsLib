package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.aether.DeadZone;
import dev.overgrown.aspectslib.aether.DeadZoneManager;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import dev.overgrown.aspectslib.resonance.ResonanceCalculator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Map;
import java.util.Optional;

/**
 * Public API for Aether manipulation and spellcasting
 */
public class AetherAPI {

    /**
     * Gets the Aether density (in RU/m³) for a specific aspect in the given biome
     */
    public static double getAetherDensity(Identifier biomeId, Identifier aspectId) {
        // Use biome aspects as Aether densities
        var aspectData = BiomeAspectRegistry.get(biomeId);
        if (aspectData != null && !aspectData.isEmpty()) {
            return aspectData.getLevel(aspectId);
        }
        return 0.0;
    }

    /**
     * Gets the total Aether density for a biome (sum of all aspects)
     */
    public static double getTotalAetherDensity(Identifier biomeId) {
        var aspectData = BiomeAspectRegistry.get(biomeId);
        return aspectData != null ? aspectData.calculateTotalRU() : 0.0;
    }

    /**
     * Calculates available Aether for spellcasting at a specific location
     * considering biome density and dead zones
     */
    public static double getAvailableAether(World world, BlockPos pos, Identifier aspectId) {
        if (!(world instanceof ServerWorld serverWorld)) return 0.0;

        ChunkPos chunkPos = new ChunkPos(pos);
        Biome biome = world.getBiome(pos).value();
        Identifier biomeId = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) return 0.0;

        // Get base density from biome
        double baseDensity = getAetherDensity(biomeId, aspectId);

        // Apply dead zone multiplier
        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        double multiplier = manager.getAetherMultiplier(chunkPos);

        return baseDensity * multiplier;
    }

    /**
     * Checks if a location is in a dead zone
     */
    public static boolean isInDeadZone(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return false;

        ChunkPos chunkPos = new ChunkPos(pos);
        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        return manager.isDeadZone(chunkPos);
    }

    /**
     * Gets the Aether efficiency multiplier for a location (1.0 = normal, 0.0 = no magic)
     */
    public static double getAetherEfficiency(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return 1.0;

        ChunkPos chunkPos = new ChunkPos(pos);
        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        return manager.getAetherMultiplier(chunkPos);
    }

    /**
     * Creates a temporary dead zone at the specified chunk
     */
    public static void createTemporaryDeadZone(World world, ChunkPos pos, double drainAmount) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        // 1 day recovery time = 24000 ticks
        manager.addTemporaryDeadZone(pos, drainAmount, 24000L);
    }

    /**
     * Creates a permanent dead zone at the specified chunk
     * WARNING: This is irreversible!
     */
    public static void createPermanentDeadZone(World world, ChunkPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        manager.addPermanentDeadZone(pos);
    }

    /**
     * Gets information about a dead zone at the specified chunk
     */
    public static Optional<DeadZone> getDeadZoneInfo(World world, ChunkPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return Optional.empty();

        DeadZoneManager manager = DeadZoneManager.get(serverWorld);
        return Optional.ofNullable(manager.getDeadZone(pos));
    }

    /**
     * Calculates spell cost considering resonance between aspects
     */
    public static double calculateSpellCost(Map<Identifier, Integer> aspectCosts) {
        // Use Resonance calculator
        var resonanceResult = ResonanceCalculator.calculate(new dev.overgrown.aspectslib.data.AspectData(
                new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<>(aspectCosts)
        ));

        // Total cost = base cost + barrier cost, modified by amplification
        double baseCost = aspectCosts.values().stream().mapToDouble(Integer::doubleValue).sum();
        return (baseCost + resonanceResult.barrierCost()) * resonanceResult.amplificationFactor();
    }

    /**
     * Checks if a player can cast a spell with the given aspect costs
     */
    public static boolean canCastSpell(PlayerEntity player, Map<Identifier, Integer> aspectCosts, World world, BlockPos pos) {
        // Check if in dead zone with no Aether
        if (getAetherEfficiency(world, pos) <= 0) return false;

        // Calculate actual cost considering resonance
        double actualCost = calculateSpellCost(aspectCosts);

        // For now, we'll assume players have infinite Aether
        // In your spell system, you might want to track player Aether storage
        return actualCost >= 0;
    }
}