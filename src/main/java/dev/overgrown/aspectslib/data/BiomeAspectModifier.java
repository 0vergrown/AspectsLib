package dev.overgrown.aspectslib.data;

import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;

public class BiomeAspectModifier {
    private static final Map<Identifier, AspectData> biomeModifications = new HashMap<>();

    public static void addBiomeModification(Identifier biomeId, Identifier aspectId, int amount) {
        AspectData current = biomeModifications.getOrDefault(biomeId, AspectData.DEFAULT);
        AspectData.Builder builder = new AspectData.Builder(current);
        builder.add(aspectId, amount);
        biomeModifications.put(biomeId, builder.build());
    }

    public static void drainAllAspects(Identifier biomeId, int amount) {
        // Get current biome aspects from registry
        AspectData currentBiomeAspects = BiomeAspectRegistry.get(biomeId);
        if (!currentBiomeAspects.isEmpty()) {
            AspectData.Builder builder = new AspectData.Builder(currentBiomeAspects);
            // Reduce all aspects by specified amount
            for (Identifier aspectId : currentBiomeAspects.getAspectIds()) {
                int currentAmount = currentBiomeAspects.getLevel(aspectId);
                if (currentAmount > 0) {
                    builder.set(aspectId, Math.max(0, currentAmount - amount));
                }
            }
            // Update the registry - you'll need public update methods for this
            BiomeAspectRegistry.update(biomeId, builder.build());
        }
    }

    public static AspectData getModifiedBiomeAspects(Identifier biomeId, AspectData original) {
        AspectData modification = biomeModifications.get(biomeId);
        if (modification != null && !modification.isEmpty()) {
            AspectData.Builder builder = new AspectData.Builder(original);
            for (Identifier aspectId : modification.getAspectIds()) {
                builder.add(aspectId, modification.getLevel(aspectId));
            }
            return builder.build();
        }
        return original;
    }

    public static void clearModifications() {
        biomeModifications.clear();
    }

    // Helper method to get combined aspects (original + modifications)
    public static AspectData getCombinedBiomeAspects(Identifier biomeId) {
        AspectData original = BiomeAspectRegistry.get(biomeId);
        return getModifiedBiomeAspects(biomeId, original);
    }
}