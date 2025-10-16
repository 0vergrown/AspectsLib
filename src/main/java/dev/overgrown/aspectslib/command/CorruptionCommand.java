package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.corruption.CorruptionAPI;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

public class CorruptionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("corruption")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(CorruptionCommand::checkCurrentBiome)
                .then(CommandManager.literal("purify")
                        .executes(CorruptionCommand::purifyCurrentBiome))
                .then(CommandManager.literal("force")
                        .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                .executes(CorruptionCommand::forceCorruption)))
        );
    }

    private static int checkCurrentBiome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
        Identifier biomeId = player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        int corruptionState = CorruptionAPI.getBiomeCorruptionState(biomeId);
        int vitiumAmount = CorruptionAPI.getVitiumAmount(biomeId);

        String state;
        switch (corruptionState) {
            case 0 -> state = "§aPure";
            case 1 -> state = "§eTainted";
            case 2 -> state = "§cCorrupted";
            default -> state = "§7Unknown";
        }

        source.sendFeedback(() -> Text.literal("Biome: " + biomeId.toString()), false);
        source.sendFeedback(() -> Text.literal("State: " + state), false);
        source.sendFeedback(() -> Text.literal("Vitium: " + vitiumAmount), false);

        return 1;
    }

    private static int purifyCurrentBiome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
        Identifier biomeId = player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        int vitiumBefore = CorruptionAPI.getVitiumAmount(biomeId);
        CorruptionAPI.purifyBiome(biomeId);

        // Apply modifications to registry to make them permanent
        dev.overgrown.aspectslib.data.BiomeAspectModifier.applyModificationsToRegistry();

        int vitiumAfter = CorruptionAPI.getVitiumAmount(biomeId);

        source.sendFeedback(() -> Text.literal("§aPurified biome: " + biomeId +
                " (Removed " + (vitiumBefore - vitiumAfter) + " Vitium)"), true);

        return 1;
    }

    private static int forceCorruption(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount");

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
        Identifier biomeId = player.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        CorruptionAPI.forceCorruption(biomeId, amount);
        source.sendFeedback(() -> Text.literal("§cForced corruption in biome: " + biomeId + " (+" + amount + " Vitium)"), true);

        return 1;
    }
}