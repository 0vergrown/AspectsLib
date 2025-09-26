package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.aether.*;
import dev.overgrown.aspectslib.AspectsLib;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AetherDensityAPIImpl implements IAetherDensityAPI {

    private final List<IAetherDensityModifier> modifiers = new ArrayList<>();
    private final List<IAetherDensityUpdateListener> updateListeners = new CopyOnWriteArrayList<>();
    private final List<ICorruptionListener> corruptionListeners = new CopyOnWriteArrayList<>();
    private final Map<Identifier, IAetherDensityModifier> registeredModifiers = new HashMap<>();

    @Override
    public AetherDensity getDensity(World world, BlockPos pos) {
        AetherDensity baseDensity = AetherDensityManager.getDensity(world, pos);

        // Apply modifiers in priority order
        List<IAetherDensityModifier> sortedModifiers = new ArrayList<>(modifiers);
        sortedModifiers.sort(Comparator.comparingInt(IAetherDensityModifier::getPriority));

        AetherDensity modifiedDensity = baseDensity;
        for (IAetherDensityModifier modifier : sortedModifiers) {
            if (modifier.shouldApply(world, pos)) {
                modifiedDensity = modifier.modifyDensity(world, pos, modifiedDensity);
            }
        }

        return modifiedDensity;
    }

    @Override
    public AetherDensity getBiomeDensity(Identifier biomeId) {
        return BiomeAetherDensityManager.DENSITY_MAP.getOrDefault(biomeId, AetherDensity.EMPTY);
    }

    @Override
    public AetherDensity getStructureDensity(Identifier structureId) {
        return StructureAetherDensityManager.DENSITY_MAP.getOrDefault(structureId, AetherDensity.EMPTY);
    }

    @Override
    public double getAspectDensity(World world, BlockPos pos, Identifier aspect) {
        return getDensity(world, pos).getDensity(aspect);
    }

    @Override
    public void addDynamicModification(Identifier biomeId, Identifier aspect, double amount) {
        DynamicAetherDensityManager.addModification(biomeId, aspect, amount);
        notifyDensityUpdateListeners(biomeId);
    }

    @Override
    public void setDynamicModification(Identifier biomeId, Identifier aspect, double amount) {
        // First remove any existing modification, then add the new one
        removeDynamicModification(biomeId, aspect);
        addDynamicModification(biomeId, aspect, amount);
    }

    @Override
    public void removeDynamicModification(Identifier biomeId, Identifier aspect) {
        Map<Identifier, Double> modifications = DynamicAetherDensityManager.getModifications(biomeId);
        if (modifications != null) {
            modifications.remove(aspect);
            if (modifications.isEmpty()) {
                DynamicAetherDensityManager.getModificationsMap().remove(biomeId);
            }
        }
        notifyDensityUpdateListeners(biomeId);
    }

    @Override
    public void clearDynamicModifications(Identifier biomeId) {
        DynamicAetherDensityManager.getModificationsMap().remove(biomeId);
        notifyDensityUpdateListeners(biomeId);
    }

    @Override
    public void registerBiomeDensity(Identifier biomeId, AetherDensity density) {
        BiomeAetherDensityManager.DENSITY_MAP.put(biomeId, density);
        notifyDensityUpdateListeners(biomeId);
        AspectsLib.LOGGER.info("Registered custom biome density for {}: {}", biomeId, density.getDensities());
    }

    @Override
    public void registerStructureDensity(Identifier structureId, AetherDensity density) {
        StructureAetherDensityManager.DENSITY_MAP.put(structureId, density);
        notifyDensityUpdateListeners(structureId);
        AspectsLib.LOGGER.info("Registered custom structure density for {}: {}", structureId, density.getDensities());
    }

    @Override
    public void registerAetherModifier(Identifier modifierId, IAetherDensityModifier modifier) {
        if (registeredModifiers.containsKey(modifierId)) {
            AspectsLib.LOGGER.warn("Aether modifier {} is already registered, replacing", modifierId);
        }
        registeredModifiers.put(modifierId, modifier);
        modifiers.add(modifier);
        // Re-sort modifiers
        modifiers.sort(Comparator.comparingInt(IAetherDensityModifier::getPriority));
        AspectsLib.LOGGER.info("Registered aether modifier: {}", modifierId);
    }

    @Override
    public void addCorruptionSource(Identifier biomeId, BlockPos pos, int strength) {
        CorruptionManager.addCorruptionSource(biomeId, pos, strength);

        // Notify corruption listeners
        World world = null; // We don't have world context here, might need to adjust
        for (ICorruptionListener listener : corruptionListeners) {
            listener.onCorruptionStart(world, biomeId, pos);
        }
    }

    @Override
    public double getCorruptionLevel(Identifier biomeId) {
        // Calculate corruption level based on vitium aspect
        AetherDensity density = getBiomeDensity(biomeId);
        Map<Identifier, Double> modifications = DynamicAetherDensityManager.getModifications(biomeId);

        Identifier vitium = AspectsLib.identifier("vitium");
        double vitiumLevel = density.getDensity(vitium) +
                (modifications != null ? modifications.getOrDefault(vitium, 0.0) : 0.0);

        double totalOtherAspects = 0.0;
        for (Map.Entry<Identifier, Double> entry : density.getDensities().entrySet()) {
            if (!entry.getKey().equals(vitium)) {
                totalOtherAspects += entry.getValue();
            }
        }

        if (modifications != null) {
            for (Map.Entry<Identifier, Double> entry : modifications.entrySet()) {
                if (!entry.getKey().equals(vitium)) {
                    totalOtherAspects += entry.getValue();
                }
            }
        }

        return vitiumLevel - totalOtherAspects; // Positive if corrupted, negative if pure
    }

    @Override
    public boolean isBiomeCorrupted(Identifier biomeId) {
        return getCorruptionLevel(biomeId) > 0;
    }

    @Override
    public void registerDensityUpdateListener(IAetherDensityUpdateListener listener) {
        updateListeners.add(listener);
    }

    @Override
    public void registerCorruptionListener(ICorruptionListener listener) {
        corruptionListeners.add(listener);
    }

    private void notifyDensityUpdateListeners(Identifier id) {
        // This is a simplified notification (in practice, it needs more context) - which you won't get because I'm lazy... uwu
        for (IAetherDensityUpdateListener listener : updateListeners) {
            if (BiomeAetherDensityManager.DENSITY_MAP.containsKey(id)) {
                listener.onBiomeDensityUpdate(id, getBiomeDensity(id));
            } else if (StructureAetherDensityManager.DENSITY_MAP.containsKey(id)) {
                listener.onStructureDensityUpdate(id, getStructureDensity(id));
            }
        }
    }

    // Package-private method to get the modifications map for internal use
    static Map<Identifier, Map<Identifier, Double>> getModificationsMap() {
        // Add this method to DynamicAetherDensityManager
        // For now, it's reflection or add the method here (REMEMBER TO DO IT YOU LAZY BUM!!!)
        return Collections.emptyMap(); // Placeholder
    }
}