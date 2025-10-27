package dev.overgrown.aspectslib.corruption;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;

import java.util.*;

public class BiomeRegionDetector {
    private static final int MAX_REGION_SIZE = 1024;
    
    public static Set<ChunkPos> findConnectedBiomeChunks(ServerWorld world, ChunkPos startChunk, Identifier biomeId, int maxRadius) {
        Set<ChunkPos> visited = new HashSet<>();
        Queue<ChunkPos> queue = new LinkedList<>();
        
        queue.add(startChunk);
        visited.add(startChunk);
        
        while (!queue.isEmpty() && visited.size() < MAX_REGION_SIZE) {
            ChunkPos current = queue.poll();
            
            if (Math.abs(current.x - startChunk.x) > maxRadius || 
                Math.abs(current.z - startChunk.z) > maxRadius) {
                continue;
            }
            
            for (ChunkPos neighbor : getAdjacentChunks(current)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                if (!world.getChunkManager().isChunkLoaded(neighbor.x, neighbor.z)) {
                    continue;
                }
                
                Identifier neighborBiomeId = getBiomeIdForChunk(world, neighbor);
                
                if (biomeId.equals(neighborBiomeId)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        return visited;
    }
    
    private static List<ChunkPos> getAdjacentChunks(ChunkPos center) {
        return Arrays.asList(
            new ChunkPos(center.x + 1, center.z),
            new ChunkPos(center.x - 1, center.z),
            new ChunkPos(center.x, center.z + 1),
            new ChunkPos(center.x, center.z - 1)
        );
    }
    
    private static Identifier getBiomeIdForChunk(ServerWorld world, ChunkPos chunkPos) {
        BlockPos centerPos = chunkPos.getStartPos().add(8, 64, 8);
        Biome biome = world.getBiome(centerPos).value();
        return world.getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.BIOME)
                .getId(biome);
    }
}
