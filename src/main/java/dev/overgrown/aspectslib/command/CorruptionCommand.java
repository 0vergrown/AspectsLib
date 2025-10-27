package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.corruption.CorruptionAPI;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;

public class CorruptionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("corruption")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(CorruptionCommand::checkCurrentChunk)
                .then(CommandManager.literal("purify")
                        .executes(CorruptionCommand::purifyCurrentChunk))
                .then(CommandManager.literal("force")
                        .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                .executes(CorruptionCommand::forceCorruption)))
        );
    }

    private static int checkCurrentChunk(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        ChunkPos chunkPos = player.getChunkPos();
        
        Biome biome = world.getBiome(player.getBlockPos()).value();
        Identifier biomeId = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        boolean isPure = CorruptionAPI.isChunkPure(world, chunkPos);
        boolean isTainted = CorruptionAPI.isChunkTainted(world, chunkPos);
        boolean isCorrupted = CorruptionAPI.isChunkCorrupted(world, chunkPos);
        int vitiumAmount = CorruptionAPI.getVitiumAmount(world, chunkPos);

        String state;
        if (isCorrupted) {
            state = "§cCorrupted";
        } else if (isTainted) {
            state = "§eTainted";
        } else if (isPure) {
            state = "§aPure";
        } else {
            state = "§7Unknown";
        }

        source.sendFeedback(() -> Text.literal("Chunk: " + chunkPos.toString()), false);
        source.sendFeedback(() -> Text.literal("Biome: " + biomeId.toString()), false);
        source.sendFeedback(() -> Text.literal("State: " + state), false);
        source.sendFeedback(() -> Text.literal("Vitium: " + vitiumAmount), false);

        return 1;
    }

    private static int purifyCurrentChunk(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        ChunkPos chunkPos = player.getChunkPos();
        
        Biome biome = world.getBiome(player.getBlockPos()).value();
        Identifier biomeId = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        int vitiumBefore = CorruptionAPI.getVitiumAmount(world, chunkPos);
        CorruptionAPI.purifyChunk(world, chunkPos);

        int vitiumAfter = CorruptionAPI.getVitiumAmount(world, chunkPos);

        source.sendFeedback(() -> Text.literal("§aPurified chunk region: " + chunkPos + " (biome: " + biomeId + 
                ") - Removed " + (vitiumBefore - vitiumAfter) + " Vitium"), true);

        return 1;
    }

    private static int forceCorruption(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount");

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        ChunkPos chunkPos = player.getChunkPos();
        
        Biome biome = world.getBiome(player.getBlockPos()).value();
        Identifier biomeId = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getId(biome);

        if (biomeId == null) {
            source.sendError(Text.literal("Could not determine current biome"));
            return 0;
        }

        CorruptionAPI.forceCorruption(world, chunkPos, amount);
        source.sendFeedback(() -> Text.literal("§cForced corruption in chunk region: " + chunkPos + " (biome: " + biomeId + ") - Added " + amount + " Vitium"), true);

        return 1;
    }
}