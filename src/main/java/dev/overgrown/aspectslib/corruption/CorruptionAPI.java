package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectModifier;
import net.minecraft.util.Identifier;

public class CorruptionAPI {

    /**
     * Gets the corruption state of a biome
     * @param biomeId The biome identifier
     * @return 0 = Pure, 1 = Tainted, 2 = Corrupted
     */
    public static int getBiomeCorruptionState(Identifier biomeId) {
        return CorruptionManager.getCorruptionState(biomeId);
    }

    /**
     * Checks if a biome is pure (no Vitium)
     */
    public static boolean isBiomePure(Identifier biomeId) {
        return CorruptionManager.isBiomePure(biomeId);
    }

    /**
     * Checks if a biome is tainted (has Vitium but not corrupted)
     */
    public static boolean isBiomeTainted(Identifier biomeId) {
        return CorruptionManager.isBiomeTainted(biomeId);
    }

    /**
     * Checks if a biome is corrupted (Vitium dominates)
     */
    public static boolean isBiomeCorrupted(Identifier biomeId) {
        return CorruptionManager.isBiomeCorrupted(biomeId);
    }

    /**
     * Forces a biome to become corrupted by adding Vitium
     * @param biomeId The biome identifier
     * @param vitiumAmount The amount of Vitium to add
     */
    public static void forceCorruption(Identifier biomeId, int vitiumAmount) {
        Identifier vitiumId = AspectsLib.identifier("vitium");
        BiomeAspectModifier.addBiomeModification(biomeId, vitiumId, vitiumAmount);

        AspectsLib.LOGGER.info("Forced corruption on biome {} by adding {} Vitium", biomeId, vitiumAmount);
    }

    /**
     * Purifies a biome by removing all Vitium
     * @param biomeId The biome identifier
     */
    public static void purifyBiome(Identifier biomeId) {
        Identifier vitiumId = AspectsLib.identifier("vitium");

        // Get COMBINED aspects to get the actual current Vitium amount
        AspectData currentAspects = BiomeAspectModifier.getCombinedBiomeAspects(biomeId);
        int vitiumAmount = currentAspects.getLevel(vitiumId);

        if (vitiumAmount > 0) {
            // Remove all Vitium
            BiomeAspectModifier.addBiomeModification(biomeId, vitiumId, -vitiumAmount);
            AspectsLib.LOGGER.info("Purified biome {} by removing {} Vitium", biomeId, vitiumAmount);
        } else {
            AspectsLib.LOGGER.info("Biome {} has no Vitium to purify", biomeId);
        }
    }

    /**
     * Gets the amount of Vitium in a biome
     */
    public static int getVitiumAmount(Identifier biomeId) {
        Identifier vitiumId = AspectsLib.identifier("vitium");
        // Use combined aspects
        AspectData aspects = BiomeAspectModifier.getCombinedBiomeAspects(biomeId);
        return aspects.getLevel(vitiumId);
    }
}