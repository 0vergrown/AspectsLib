package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.overgrown.aspectslib.api.corruption.VitiumCorruptionAPI;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.Set;

public class VitiumCorruptionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("vitium")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                .suggests((context, builder) -> {
                                    ServerWorld world = context.getSource().getWorld();
                                    world.getRegistryManager().get(RegistryKeys.BIOME).getKeys().forEach(key -> builder.suggest(key.getValue().toString()));
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            ServerWorld world = source.getWorld();

                                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                            int newLevel = VitiumCorruptionAPI.addVitiumToBiome(world, biomeKey, amount);

                                            source.sendFeedback(() -> Text.literal("Added " + amount + " Vitium to biome: " + biomeId + " (New level: " + newLevel + ")"), true);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> {
                                            ServerWorld world = context.getSource().getWorld();
                                            world.getRegistryManager().get(RegistryKeys.BIOME).getKeys().forEach(key -> builder.suggest(key.getValue().toString()));
                                            return builder.buildFuture();
                                        })
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    ServerCommandSource source = context.getSource();
                                                    Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                                    ServerWorld world = source.getWorld();

                                                    RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                                    int newLevel = VitiumCorruptionAPI.setVitiumInBiome(world, biomeKey, amount);

                                                    source.sendFeedback(() -> Text.literal("Set Vitium in biome " + biomeId + " to " + amount + " (New level: " + newLevel + ")"), true);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                                .suggests((context, builder) -> {
                                                    ServerWorld world = context.getSource().getWorld();
                                                    world.getRegistryManager().get(RegistryKeys.BIOME).getKeys().forEach(key -> builder.suggest(key.getValue().toString()));
                                                    return builder.buildFuture();
                                                })
                                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> {
                                                            ServerCommandSource source = context.getSource();
                                                            Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                                            ServerWorld world = source.getWorld();

                                                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                                            int newLevel = VitiumCorruptionAPI.removeVitiumFromBiome(world, biomeKey, amount);

                                                            source.sendFeedback(() -> Text.literal("Removed " + amount + " Vitium from biome: " + biomeId + " (New level: " + newLevel + ")"), true);
                                                            return 1;
                                                        })))
                                        .then(CommandManager.literal("check")
                                                .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                                        .suggests((context, builder) -> {
                                                            ServerWorld world = context.getSource().getWorld();
                                                            world.getRegistryManager().get(RegistryKeys.BIOME).getKeys().forEach(key -> builder.suggest(key.getValue().toString()));
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            ServerCommandSource source = context.getSource();
                                                            Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                                            ServerWorld world = source.getWorld();

                                                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                                            boolean isCorrupted = VitiumCorruptionAPI.isBiomeCorrupted(world, biomeKey);
                                                            int vitiumLevel = VitiumCorruptionAPI.getBiomeVitiumLevel(world, biomeKey);
                                                            float corruptionLevel = VitiumCorruptionAPI.getCorruptionLevel(world, biomeKey);
                                                            int aetherLevel = VitiumCorruptionAPI.getBiomeAetherLevel(world, biomeKey);
                                                            int corruptedChunks = VitiumCorruptionAPI.getCorruptedChunkCount(world, biomeKey);
                                                            boolean isActive = VitiumCorruptionAPI.isCorruptionActive(world, biomeKey);

                                                            source.sendFeedback(() -> Text.literal("Biome " + biomeId + ":"), true);
                                                            source.sendFeedback(() -> Text.literal("  Vitium Level: " + vitiumLevel), true);
                                                            source.sendFeedback(() -> Text.literal("  Corruption Level: " + String.format("%.2f", corruptionLevel * 100) + "%"), true);
                                                            source.sendFeedback(() -> Text.literal("  Corrupted: " + isCorrupted), true);
                                                            source.sendFeedback(() -> Text.literal("  Active Corruption: " + isActive), true);
                                                            source.sendFeedback(() -> Text.literal("  Aether Level: " + (aetherLevel != -1 ? aetherLevel : "N/A")), true);
                                                            source.sendFeedback(() -> Text.literal("  Corrupted Chunks: " + corruptedChunks), true);
                                                            return 1;
                                                        }))
                                                .then(CommandManager.literal("start")
                                                        .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                                                .suggests((context, builder) -> {
                                                                    ServerWorld world = context.getSource().getWorld();
                                                                    world.getRegistryManager().get(RegistryKeys.BIOME).getKeys().forEach(key -> builder.suggest(key.getValue().toString()));
                                                                    return builder.buildFuture();
                                                                })
                                                                .then(CommandManager.argument("vitium", IntegerArgumentType.integer(16))
                                                                        .executes(context -> {
                                                                            ServerCommandSource source = context.getSource();
                                                                            Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                                                            int vitium = IntegerArgumentType.getInteger(context, "vitium");
                                                                            ServerWorld world = source.getWorld();

                                                                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                                                            boolean success = VitiumCorruptionAPI.startBiomeCorruption(world, biomeKey, vitium);

                                                                            if (success) {
                                                                                source.sendFeedback(() -> Text.literal("Started corruption in biome: " + biomeId + " with " + vitium + " Vitium"), true);
                                                                            } else {
                                                                                source.sendFeedback(() -> Text.literal("Failed to start corruption in biome: " + biomeId), true);
                                                                            }
                                                                            return success ? 1 : 0;
                                                                        })))
                                                        .then(CommandManager.literal("cleanse")
                                                                .then(CommandManager.argument("biome", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                                                        .suggests((context, builder) -> {
                                                                            ServerWorld world = context.getSource().getWorld();
                                                                            // Only suggest corrupted biomes for cleansing
                                                                            VitiumCorruptionAPI.getCorruptedBiomes(world).forEach(key -> builder.suggest(key.getValue().toString()));
                                                                            return builder.buildFuture();
                                                                        })
                                                                        .executes(context -> {
                                                                            ServerCommandSource source = context.getSource();
                                                                            Identifier biomeId = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "biome");
                                                                            ServerWorld world = source.getWorld();

                                                                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                                                                            boolean removed = VitiumCorruptionAPI.removeCorruption(world, biomeKey);

                                                                            if (removed) {
                                                                                source.sendFeedback(() -> Text.literal("Cleansed corruption from biome: " + biomeId), true);
                                                                            } else {
                                                                                source.sendFeedback(() -> Text.literal("Biome " + biomeId + " was not corrupted"), true);
                                                                            }
                                                                            return removed ? 1 : 0;
                                                                        })))
                                                        .then(CommandManager.literal("list")
                                                                .executes(context -> {
                                                                    ServerCommandSource source = context.getSource();
                                                                    ServerWorld world = source.getWorld();

                                                                    Set<RegistryKey<Biome>> corruptedBiomes = VitiumCorruptionAPI.getCorruptedBiomes(world);

                                                                    if (corruptedBiomes.isEmpty()) {
                                                                        source.sendFeedback(() -> Text.literal("No corrupted biomes"), true);
                                                                    } else {
                                                                        source.sendFeedback(() -> Text.literal("Corrupted biomes (" + corruptedBiomes.size() + "):"), true);
                                                                        for (RegistryKey<Biome> biomeKey : corruptedBiomes) {
                                                                            int vitiumLevel = VitiumCorruptionAPI.getBiomeVitiumLevel(world, biomeKey);
                                                                            float corruptionLevel = VitiumCorruptionAPI.getCorruptionLevel(world, biomeKey);
                                                                            source.sendFeedback(() -> Text.literal("  " + biomeKey.getValue() + " - Vitium: " + vitiumLevel + ", Corruption: " + String.format("%.1f", corruptionLevel * 100) + "%"), true);
                                                                        }
                                                                    }
                                                                    return 1;
                                                                }))))))));
    }
}