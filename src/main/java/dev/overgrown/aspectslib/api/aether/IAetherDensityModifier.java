package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.aether.AetherDensity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

/**
 * Interface for mods to create custom Aether Density modifiers
 */
public interface IAetherDensityModifier {

    /**
     * Modify the base density at a specific position
     * @param world The world
     * @param pos The position
     * @param baseDensity The base density before modification
     * @return The modified density
     */
    AetherDensity modifyDensity(World world, BlockPos pos, AetherDensity baseDensity);

    /**
     * Get the priority of this modifier (lower numbers execute first)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Check if this modifier should be applied at the given position
     */
    default boolean shouldApply(World world, BlockPos pos) {
        return true;
    }
}