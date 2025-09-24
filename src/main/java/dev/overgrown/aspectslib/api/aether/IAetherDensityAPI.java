package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.aether.AetherDensity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Main API for interacting with Aether Density system.
 * Allows other mods to query, modify, and register densities.
 */
public interface IAetherDensityAPI {

    // Query methods
    AetherDensity getDensity(World world, BlockPos pos);
    AetherDensity getBiomeDensity(Identifier biomeId);
    AetherDensity getStructureDensity(Identifier structureId);
    double getAspectDensity(World world, BlockPos pos, Identifier aspect);

    // Modification methods
    void addDynamicModification(Identifier biomeId, Identifier aspect, double amount);
    void setDynamicModification(Identifier biomeId, Identifier aspect, double amount);
    void removeDynamicModification(Identifier biomeId, Identifier aspect);
    void clearDynamicModifications(Identifier biomeId);

    // Registration methods for other mods
    void registerBiomeDensity(Identifier biomeId, AetherDensity density);
    void registerStructureDensity(Identifier structureId, AetherDensity density);
    void registerAetherModifier(Identifier modifierId, IAetherDensityModifier modifier);

    // Corruption system
    void addCorruptionSource(Identifier biomeId, BlockPos pos, int strength);
    double getCorruptionLevel(Identifier biomeId);
    boolean isBiomeCorrupted(Identifier biomeId);

    // Event registration
    void registerDensityUpdateListener(IAetherDensityUpdateListener listener);
    void registerCorruptionListener(ICorruptionListener listener);
}