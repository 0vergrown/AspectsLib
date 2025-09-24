package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.aether.AetherDensity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Listener for Aether Density updates
 */
public interface IAetherDensityUpdateListener {

    /**
     * Called when density is updated at a position
     */
    void onDensityUpdate(World world, BlockPos pos, AetherDensity oldDensity, AetherDensity newDensity);

    /**
     * Called when biome density is registered or modified
     */
    default void onBiomeDensityUpdate(Identifier biomeId, AetherDensity density) {}

    /**
     * Called when structure density is registered or modified
     */
    default void onStructureDensityUpdate(Identifier structureId, AetherDensity density) {}
}