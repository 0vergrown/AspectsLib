package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

public class ChunkAspectStorage extends PersistentState {
    private final Long2ObjectMap<AspectData> chunkAspects = new Long2ObjectOpenHashMap<>();

    public AspectData getChunkAspects(ChunkPos chunkPos, Identifier biomeId) {
        long key = chunkPos.toLong();
        AspectData stored = chunkAspects.get(key);
        
        if (stored != null) {
            return stored;
        }
        
        return BiomeAspectRegistry.get(biomeId);
    }

    public void setChunkAspects(ChunkPos chunkPos, AspectData aspects) {
        chunkAspects.put(chunkPos.toLong(), aspects);
        markDirty();
    }

    public void modifyChunkAspect(ChunkPos chunkPos, Identifier biomeId, Identifier aspectId, int delta) {
        if (delta == 0) return;
        
        long key = chunkPos.toLong();
        AspectData current = chunkAspects.get(key);
        
        if (current == null) {
            current = BiomeAspectRegistry.get(biomeId);
        }
        
        AspectData.Builder builder = new AspectData.Builder(current);
        builder.add(aspectId, delta);
        
        chunkAspects.put(key, builder.build());
        markDirty();
    }

    public void removeChunkAspects(ChunkPos chunkPos) {
        if (chunkAspects.remove(chunkPos.toLong()) != null) {
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList chunkList = new NbtList();
        
        for (Long2ObjectMap.Entry<AspectData> entry : chunkAspects.long2ObjectEntrySet()) {
            NbtCompound chunkNbt = new NbtCompound();
            chunkNbt.putLong("Pos", entry.getLongKey());
            
            AspectData aspects = entry.getValue();
            NbtList aspectList = new NbtList();
            for (Identifier aspectId : aspects.getAspectIds()) {
                int amount = aspects.getLevel(aspectId);
                if (amount > 0) {
                    NbtCompound aspectNbt = new NbtCompound();
                    aspectNbt.putString("Id", aspectId.toString());
                    aspectNbt.putInt("Amount", amount);
                    aspectList.add(aspectNbt);
                }
            }
            chunkNbt.put("Aspects", aspectList);
            chunkList.add(chunkNbt);
        }
        
        nbt.put("Chunks", chunkList);
        return nbt;
    }

    public static ChunkAspectStorage fromNbt(NbtCompound nbt) {
        ChunkAspectStorage storage = new ChunkAspectStorage();
        
        if (nbt.contains("Chunks", NbtElement.LIST_TYPE)) {
            NbtList chunkList = nbt.getList("Chunks", NbtElement.COMPOUND_TYPE);
            
            for (int i = 0; i < chunkList.size(); i++) {
                NbtCompound chunkNbt = chunkList.getCompound(i);
                long pos = chunkNbt.getLong("Pos");
                
                Object2IntOpenHashMap<Identifier> aspectMap = new Object2IntOpenHashMap<>();
                
                if (chunkNbt.contains("Aspects", NbtElement.LIST_TYPE)) {
                    NbtList aspectList = chunkNbt.getList("Aspects", NbtElement.COMPOUND_TYPE);
                    
                    for (int j = 0; j < aspectList.size(); j++) {
                        NbtCompound aspectNbt = aspectList.getCompound(j);
                        Identifier aspectId = Identifier.tryParse(aspectNbt.getString("Id"));
                        int amount = aspectNbt.getInt("Amount");
                        
                        if (aspectId != null && amount > 0) {
                            aspectMap.put(aspectId, amount);
                        }
                    }
                }
                
                if (!aspectMap.isEmpty()) {
                    storage.chunkAspects.put(pos, new AspectData(aspectMap));
                }
            }
        }
        
        return storage;
    }
}
