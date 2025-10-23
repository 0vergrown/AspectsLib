package dev.overgrown.aspectslib.data;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.*;
import java.util.stream.Stream;

public class BiomeAspectRegistry {

    private static final HashMap<RegistryKey<Biome>, AspectData> keyToAspect = new HashMap<>();
    private static final HashMap<Identifier, AspectData> idToAspect = new HashMap<>();

    public static AspectData register(RegistryKey<Biome> key, AspectData aspect) {
        if(keyToAspect.containsKey(key)) {
            AspectData existing = keyToAspect.get(key);
            existing.addAspect(aspect);
            return aspect;
        }
        keyToAspect.put(key, aspect);
        return aspect;
    }

    public static AspectData register(Identifier id, AspectData aspect) {
        if(idToAspect.containsKey(id)) {
            AspectData existing = idToAspect.get(id);
            existing.addAspect(aspect);
            return aspect;
        }
        idToAspect.put(id, aspect);
        return aspect;
    }

    public static void update(RegistryKey<Biome> key, AspectData aspect) {
        if(keyToAspect.containsKey(key)) {
            AspectData old = keyToAspect.get(key);
            keyToAspect.remove(key);
        }
        register(key, aspect);
    }

    public static void update(Identifier id, AspectData aspect) {
        if(idToAspect.containsKey(id)) {
            AspectData old = idToAspect.get(id);
            idToAspect.remove(id);
        }
        register(id, aspect);
    }

    protected static void remove(RegistryKey<Biome> key) {
        keyToAspect.remove(key);
    }

    protected static void remove(Identifier id) {
        idToAspect.remove(id);
    }

    public static int size() {
        return keyToAspect.size() + idToAspect.size();
    }

    public static Stream<RegistryKey<Biome>> biomeKeys() {
        return keyToAspect.keySet().stream();
    }

    public static Stream<Identifier> identifiers() {
        return idToAspect.keySet().stream();
    }

    public static Set<Map.Entry<RegistryKey<Biome>, AspectData>> biomeEntries() {
        return keyToAspect.entrySet();
    }

    public static Set<Map.Entry<Identifier, AspectData>> entries() {
        return idToAspect.entrySet();
    }

    public static List<AspectData> values() {
        List<AspectData> combined = new ArrayList<>();
        combined.addAll(keyToAspect.values());
        combined.addAll(idToAspect.values());
        return combined;
    }

    public static AspectData get(RegistryKey<Biome> key) {
        // First check direct key mappings
        AspectData keyData = keyToAspect.get(key);
        if (keyData != null && !keyData.isEmpty()) {
            return keyData;
        }

        // Then check identifier mappings
        Identifier biomeId = key.getValue();
        AspectData idData = idToAspect.get(biomeId);
        if (idData != null && !idData.isEmpty()) {
            return idData;
        }

        return AspectData.DEFAULT;
    }


    public static AspectData get(Identifier id) {
        AspectData data = idToAspect.get(id);
        if (data != null && !data.isEmpty()) {
            return data;
        }

        // Also check if there's a key mapping for this identifier
        for (Map.Entry<RegistryKey<Biome>, AspectData> entry : keyToAspect.entrySet()) {
            if (entry.getKey().getValue().equals(id)) {
                return entry.getValue();
            }
        }

        return AspectData.DEFAULT;
    }

    public static RegistryKey<Biome> getKey(AspectData aspect) {
        Optional<Map.Entry<RegistryKey<Biome>, AspectData>> entryOptional = keyToAspect.entrySet().stream()
                .filter((aspectEntry) -> Objects.equals(aspectEntry.getValue(), aspect))
                .findFirst();
        if(entryOptional.isPresent()) {
            return entryOptional.get().getKey();
        } else {
            throw new IllegalArgumentException("Could not get registry key from aspect data '" + aspect.toString() + "', as it was not registered!");
        }
    }

    public static Identifier getId(AspectData aspect) {
        Optional<Map.Entry<Identifier, AspectData>> entryOptional = idToAspect.entrySet().stream()
                .filter((aspectEntry) -> Objects.equals(aspectEntry.getValue(), aspect))
                .findFirst();
        if(entryOptional.isPresent()) {
            return entryOptional.get().getKey();
        } else {
            throw new IllegalArgumentException("Could not get identifier from aspect data '" + aspect.toString() + "', as it was not registered!");
        }
    }

    public static boolean contains(RegistryKey<Biome> key) {
        return keyToAspect.containsKey(key) || idToAspect.containsKey(key.getValue());
    }

    public static boolean contains(Identifier id) {
        return idToAspect.containsKey(id);
    }

    public static void clear() {
        keyToAspect.clear();
        idToAspect.clear();
    }

    public static void reset() {
        clear();
    }
}