package dev.overgrown.aspectslib.aether;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class AetherEvents {

    public static void initialize() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            AetherManager.setServer(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Process Aether recovery every 5 seconds (100 ticks) for performance
            if (server.getTicks() % 100 == 0) {
                processAetherRecovery(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AetherManager.saveAllData();
        });
    }

    private static void processAetherRecovery(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            // Only process loaded chunks for performance
            List<WorldChunk> loadedChunks = getLoadedChunks(world);

            for (WorldChunk chunk : loadedChunks) {
                ChunkPos chunkPos = chunk.getPos();
                AetherChunkData chunkData = AetherManager.getAetherData(world, chunkPos);
                chunkData.recoverAether();
            }
        }
    }

    private static List<WorldChunk> getLoadedChunks(ServerWorld world) {
        List<WorldChunk> chunks = new ArrayList<>();
        ServerChunkManager chunkManager = world.getChunkManager();

        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos chunkPos = player.getChunkPos();
            WorldChunk chunk = chunkManager.getWorldChunk(chunkPos.x, chunkPos.z);
            if (chunk != null && !chunks.contains(chunk)) {
                chunks.add(chunk);
            }
        }

        return chunks;
    }
}