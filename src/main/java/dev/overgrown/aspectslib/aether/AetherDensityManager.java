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
        AspectsLib.LOGGER.debug("Available biome densities: {}", BiomeAetherDensityManager.DENSITY_MAP.keySet());

        // Start with biome density
        AetherDensity density = biomeId != null ?
                BiomeAetherDensityManager.DENSITY_MAP.getOrDefault(biomeId, AetherDensity.EMPTY) :
                AetherDensity.EMPTY;

        if (density == AetherDensity.EMPTY && biomeId != null) {
            AspectsLib.LOGGER.debug("No base density found for biome: {}", biomeId);
        } else if (density != AetherDensity.EMPTY) {
            AspectsLib.LOGGER.debug("Found base density for biome {}: {}", biomeId, density.getDensities());
        }

        // Apply structure densities as "pockets" - they override biome densities
        if (world instanceof ServerWorld serverWorld) {
            AetherDensity structureDensity = getStructureDensity(serverWorld, pos);
            if (structureDensity != AetherDensity.EMPTY) {
                AspectsLib.LOGGER.debug("Found structure density at {}: {}", pos, structureDensity.getDensities());
                // Structure densities create isolated pockets that override biome densities
                density = structureDensity;
            }
        }

        // Apply dynamic modifications (like corruption)
        if (biomeId != null) {
            Map<Identifier, Double> dynamicMods = DynamicAetherDensityManager.getModifications(biomeId);
            if (dynamicMods != null && !dynamicMods.isEmpty()) {
                Map<Identifier, Double> finalDensities = new HashMap<>(density.getDensities());

                for (Map.Entry<Identifier, Double> entry : dynamicMods.entrySet()) {
                    finalDensities.merge(entry.getKey(), entry.getValue(), Double::sum);
                }

                density = new AetherDensity(finalDensities);
            }
        }

        return density;
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