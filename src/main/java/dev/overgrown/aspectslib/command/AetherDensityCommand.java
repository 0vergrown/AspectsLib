package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.aether.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.Optional;

public class AetherDensityCommand {
    private static final Identifier VITIUM_ASPECT = AspectsLib.identifier("vitium");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(AspectsLib.MOD_ID + ":aether_density")
                .requires(source -> source.hasPermissionLevel(0))
                .executes(AetherDensityCommand::showUsage)
                .then(CommandManager.literal("report")
                        .executes(AetherDensityCommand::execute))
                .then(CommandManager.literal("list")
                        .executes(AetherDensityCommand::listDensities))
                .then(CommandManager.literal("corrupt")
                        .executes(context -> addCorruption(context, 10.0)) // Default to 10 if no argument provided
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(context -> addCorruption(context, DoubleArgumentType.getDouble(context, "amount"))))));
    }


    private static int showUsage(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String command = AspectsLib.MOD_ID + ":aether_density";

        if (source.getEntity() instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("=== Aether Density Command Usage ===").formatted(Formatting.GOLD));
            player.sendMessage(Text.literal("/" + command + " report - Shows a detailed report of the current aether density").formatted(Formatting.YELLOW));
            player.sendMessage(Text.literal("/" + command + " list - Lists all loaded biome aether densities").formatted(Formatting.YELLOW));
            player.sendMessage(Text.literal("/" + command + " corrupt [<amount>] - Adds vitium corruption to the current biome (default 10)").formatted(Formatting.YELLOW));
        } else {
            source.sendMessage(Text.literal("=== Aether Density Command Usage ===").formatted(Formatting.GOLD));
            source.sendMessage(Text.literal("/" + command + " report - Shows a detailed report of the current aether density").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("/" + command + " list - Lists all loaded biome aether densities").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("/" + command + " corrupt [<amount>] - Adds vitium corruption to the current biome (default 10)").formatted(Formatting.YELLOW));
        }

        return 1;
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (source.getEntity() instanceof ServerPlayerEntity player) {
            World world = player.getWorld();
            BlockPos pos = player.getBlockPos();

            Identifier biomeId = world.getBiome(pos).getKey().orElseThrow().getValue();

            AetherDensity baseDensity = BiomeAetherDensityManager.DENSITY_MAP.get(biomeId);
            AetherDensity density = AetherDensityManager.getDensity(world, pos);

            // Check for structure density
            AetherDensity structureDensity = AetherDensity.EMPTY;
            if (world instanceof ServerWorld serverWorld) {
                structureDensity = getStructureDensityAtPosition(serverWorld, pos);
            }

            Map<Identifier, Double> modifications = DynamicAetherDensityManager.getModifications(biomeId);

            double vitium = density.getDensity(VITIUM_ASPECT);
            double totalOtherAspects = 0.0;

            for (Map.Entry<Identifier, Double> entry : density.getDensities().entrySet()) {
                if (!entry.getKey().equals(VITIUM_ASPECT)) {
                    totalOtherAspects += entry.getValue();
                }
            }

            player.sendMessage(Text.literal("=== Aether Density Report ===").formatted(Formatting.GOLD));
            player.sendMessage(Text.literal("Position: " + pos.toShortString()).formatted(Formatting.GRAY));
            player.sendMessage(Text.literal("Biome: " + biomeId.toString()).formatted(Formatting.YELLOW));
            
            player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
            player.sendMessage(Text.literal("Debug Info:").formatted(Formatting.LIGHT_PURPLE));
            player.sendMessage(Text.literal("  Loaded Biomes: " + BiomeAetherDensityManager.DENSITY_MAP.size()).formatted(Formatting.GRAY));
            player.sendMessage(Text.literal("  Has Base Density: " + (baseDensity != null ? "Yes" : "No")).formatted(Formatting.GRAY));
            
            if (baseDensity != null && !baseDensity.getDensities().isEmpty()) {
                player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
                player.sendMessage(Text.literal("Base Density (from datapack):").formatted(Formatting.AQUA));
                for (Map.Entry<Identifier, Double> entry : baseDensity.getDensities().entrySet()) {
                    player.sendMessage(Text.literal(
                            String.format("  %s: %.2f", entry.getKey().toString(), entry.getValue())
                    ).formatted(Formatting.GRAY));
                }
            } else {
                player.sendMessage(Text.literal("  No base density found in datapack").formatted(Formatting.RED));
            }

            player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
            player.sendMessage(Text.literal("Final Calculated Density:").formatted(Formatting.GREEN));
            
            if (density.getDensities().isEmpty()) {
                player.sendMessage(Text.literal("  No aspects present").formatted(Formatting.GRAY));
            } else {
                for (Map.Entry<Identifier, Double> entry : density.getDensities().entrySet()) {
                    Formatting color = entry.getKey().equals(VITIUM_ASPECT) ? Formatting.RED : Formatting.GREEN;
                    player.sendMessage(Text.literal(
                            String.format("  %s: %.2f", entry.getKey().toString(), entry.getValue())
                    ).formatted(color));
                }
            }

            player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
            player.sendMessage(Text.literal(String.format("Vitium (Corruption): %.2f", vitium)).formatted(Formatting.RED));
            player.sendMessage(Text.literal(String.format("Total Other Aspects: %.2f", totalOtherAspects)).formatted(Formatting.GREEN));

            if (vitium > totalOtherAspects) {
                player.sendMessage(Text.literal("Status: CORRUPTED").formatted(Formatting.DARK_RED));
            } else {
                player.sendMessage(Text.literal("Status: PURE").formatted(Formatting.DARK_GREEN));
            }

            if (modifications != null && !modifications.isEmpty()) {
                player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
                player.sendMessage(Text.literal("Dynamic Modifications:").formatted(Formatting.BLUE));

                for (Map.Entry<Identifier, Double> entry : modifications.entrySet()) {
                    String change = entry.getValue() >= 0 ? "+" : "";
                    player.sendMessage(Text.literal(
                            String.format("  %s: %s%.2f", entry.getKey().toString(), change, entry.getValue())
                    ));
                }
            }

            // Add structure information to report
            if (structureDensity != AetherDensity.EMPTY) {
                player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
                player.sendMessage(Text.literal("Structure Density Override:").formatted(Formatting.LIGHT_PURPLE));
                for (Map.Entry<Identifier, Double> entry : structureDensity.getDensities().entrySet()) {
                    player.sendMessage(Text.literal(
                            String.format("  %s: %.2f", entry.getKey().toString(), entry.getValue())
                    ).formatted(Formatting.LIGHT_PURPLE));
                }
            }
            
            if (BiomeAetherDensityManager.DENSITY_MAP.isEmpty()) {
                player.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
                player.sendMessage(Text.literal("WARNING: No biome densities loaded from datapacks!").formatted(Formatting.DARK_RED));
            }

            return 1;
        }

        source.sendError(Text.literal("This command can only be used by players"));
        return 0;
    }

    private static AetherDensity getStructureDensityAtPosition(ServerWorld world, BlockPos pos) {
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
            // Correct way to get structure entry in Minecraft 1.20.1
            Optional<RegistryEntry.Reference<Structure>> structureEntry = world.getRegistryManager()
                    .get(RegistryKeys.STRUCTURE)
                    .getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, structureId));

            // Use hasKeyAndValue() instead of isEmpty()
            if (structureEntry.isEmpty() || !structureEntry.get().hasKeyAndValue()) {
                return false;
            }

            StructureStart structureStart = world.getStructureAccessor().getStructureAt(pos, structureEntry.get().value());
            return structureStart != null && structureStart.hasChildren() && structureStart.getBoundingBox().contains(pos);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static int listDensities(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendMessage(Text.literal("=== Loaded Biome Aether Densities ===").formatted(Formatting.GOLD));
        
        if (BiomeAetherDensityManager.DENSITY_MAP.isEmpty()) {
            source.sendMessage(Text.literal("No biome densities loaded!").formatted(Formatting.RED));
            source.sendMessage(Text.literal("Check that datapack files exist at:").formatted(Formatting.GRAY));
            source.sendMessage(Text.literal("  data/<namespace>/aether_densities/biome/<biome_path>.json").formatted(Formatting.GRAY));
        } else {
            source.sendMessage(Text.literal("Loaded " + BiomeAetherDensityManager.DENSITY_MAP.size() + " biome densities:").formatted(Formatting.GREEN));
            
            for (Map.Entry<Identifier, AetherDensity> entry : BiomeAetherDensityManager.DENSITY_MAP.entrySet()) {
                source.sendMessage(Text.literal("---").formatted(Formatting.GRAY));
                source.sendMessage(Text.literal("Biome: " + entry.getKey().toString()).formatted(Formatting.YELLOW));
                
                AetherDensity density = entry.getValue();
                if (density.getDensities().isEmpty()) {
                    source.sendMessage(Text.literal("  Empty density").formatted(Formatting.GRAY));
                } else {
                    for (Map.Entry<Identifier, Double> aspectEntry : density.getDensities().entrySet()) {
                        source.sendMessage(Text.literal(
                                String.format("  %s: %.2f", aspectEntry.getKey().toString(), aspectEntry.getValue())
                        ).formatted(Formatting.AQUA));
                    }
                }
            }
        }
        
        return 1;
    }

    private static int addCorruption(CommandContext<ServerCommandSource> context, double amount) {
        ServerCommandSource source = context.getSource();

        if (source.getEntity() instanceof ServerPlayerEntity player) {
            World world = player.getWorld();
            BlockPos pos = player.getBlockPos();
            Identifier biomeId = world.getBiome(pos).getKey().orElseThrow().getValue();

            DynamicAetherDensityManager.addModification(biomeId, VITIUM_ASPECT, amount);

            CorruptionManager.addCorruptionSource(biomeId, pos, 5);
            player.sendMessage(Text.literal("Added " + amount + " vitium corruption to biome: " + biomeId).formatted(Formatting.DARK_RED));
            player.sendMessage(Text.literal("Use /" + AspectsLib.MOD_ID + ":aether_density report to see the current state").formatted(Formatting.GRAY));

            return 1;
        }

        source.sendError(Text.literal("This command can only be used by players"));
        return 0;
    }
}