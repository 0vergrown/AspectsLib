package dev.overgrown.aspectslib.api.aether;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Listener for corruption events
 */
public interface ICorruptionListener {

    /**
     * Called when corruption starts in a biome
     */
    void onCorruptionStart(World world, Identifier biomeId, BlockPos origin);

    /**
     * Called when corruption spreads to a new position
     */
    void onCorruptionSpread(World world, Identifier biomeId, BlockPos pos, double corruptionLevel);

    /**
     * Called when a biome is purified from corruption
     */
    void onBiomePurified(World world, Identifier biomeId);

    /**
     * Called when corruption reaches critical level in a biome
     */
    default void onCriticalCorruption(World world, Identifier biomeId, double corruptionLevel) {}
}