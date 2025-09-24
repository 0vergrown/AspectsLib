package dev.overgrown.aspectslib.aether;

import dev.overgrown.aspectslib.AspectsLib;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AetherDensityManager {
    public static AetherDensity getDensity(World world, BlockPos pos) {
        RegistryEntry<net.minecraft.world.biome.Biome> biomeEntry = world.getBiome(pos);
        Identifier biomeId = null;

        if (biomeEntry.getKey().isPresent()) {
            biomeId = biomeEntry.getKey().get().getValue();
        } else if (world instanceof ServerWorld serverWorld) {
            biomeId = serverWorld.getRegistryManager()
                    .get(RegistryKeys.BIOME)
                    .getId(biomeEntry.value());
        }

        AspectsLib.LOGGER.debug("Getting density for biome: {} at position {}", biomeId, pos);

        // Start with biome density as the base
        AetherDensity baseDensity = biomeId != null ?
                BiomeAetherDensityManager.DENSITY_MAP.getOrDefault(biomeId, AetherDensity.EMPTY) :
                AetherDensity.EMPTY;

        if (baseDensity == AetherDensity.EMPTY && biomeId != null) {
            AspectsLib.LOGGER.debug("No base density found for biome: {}", biomeId);
        } else if (baseDensity != AetherDensity.EMPTY) {
            AspectsLib.LOGGER.debug("Found base density for biome {}: {}", biomeId, baseDensity.getDensities());
        }

        // Create a copy of the base density that we can modify
        Map<Identifier, Double> finalDensities = new HashMap<>(baseDensity.getDensities());

        // Apply structure densities as ADDITIVE "pockets" - they add to biome aspects
        if (world instanceof ServerWorld serverWorld) {
            AetherDensity structureDensity = getStructureDensity(serverWorld, pos);
            if (structureDensity != AetherDensity.EMPTY) {
                AspectsLib.LOGGER.debug("Found structure density at {}: {}", pos, structureDensity.getDensities());

                // Add structure aspects to the base biome aspects
                for (Map.Entry<Identifier, Double> structureAspect : structureDensity.getDensities().entrySet()) {
                    Identifier aspectId = structureAspect.getKey();
                    double structureAmount = structureAspect.getValue();

                    // Add structure aspect to existing biome aspect (or create new entry)
                    finalDensities.merge(aspectId, structureAmount, Double::sum);
                    AspectsLib.LOGGER.debug("Added {} RU of {} from structure to biome density", structureAmount, aspectId);
                }
            }
        }

        // Apply dynamic modifications (like corruption) to the combined density
        if (biomeId != null) {
            Map<Identifier, Double> dynamicMods = DynamicAetherDensityManager.getModifications(biomeId);
            if (dynamicMods != null && !dynamicMods.isEmpty()) {
                for (Map.Entry<Identifier, Double> entry : dynamicMods.entrySet()) {
                    finalDensities.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }

        return new AetherDensity(finalDensities);
    }

    private static AetherDensity getStructureDensity(ServerWorld world, BlockPos pos) {
        // Check if position is inside any structure that has a density defined
        for (Map.Entry<Identifier, AetherDensity> entry : StructureAetherDensityManager.DENSITY_MAP.entrySet()) {
            Identifier structureId = entry.getKey();
            AetherDensity structureDensity = entry.getValue();

            if (isInsideStructure(world, pos, structureId)) {
                return structureDensity;
            }
        }

        return AetherDensity.EMPTY;
    }

    private static boolean isInsideStructure(ServerWorld world, BlockPos pos, Identifier structureId) {
        try {
            // Get the structure from registry using the correct method
            Optional<RegistryEntry.Reference<Structure>> structureEntry = world.getRegistryManager()
                    .get(RegistryKeys.STRUCTURE)
                    .getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, structureId));

            // Check if the structure entry exists and has a value
            if (structureEntry.isEmpty() || !structureEntry.get().hasKeyAndValue()) {
                return false;
            }

            // Check if the position is inside this structure
            StructureStart structureStart = world.getStructureAccessor().getStructureAt(pos, structureEntry.get().value());
            if (structureStart != null && structureStart.hasChildren()) {
                return structureStart.getBoundingBox().contains(pos);
            }
        } catch (Exception e) {
            AspectsLib.LOGGER.warn("Error checking structure {} at {}: {}", structureId, pos, e.getMessage());
        }

        return false;
    }
}