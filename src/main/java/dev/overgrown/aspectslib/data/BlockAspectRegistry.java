package dev.overgrown.aspectslib.data;

import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Stream;

public class BlockAspectRegistry {

    private static final HashMap<Identifier, AspectData> idToAspect = new HashMap<>();

    public static AspectData register(Identifier id, AspectData aspect) {
        if(idToAspect.containsKey(id)) {
            AspectData existing = idToAspect.get(id);
            existing.addAspect(aspect);
            return aspect;
        }
        idToAspect.put(id, aspect);
        return aspect;
    }

    public static void update(Identifier id, AspectData aspect) {
        if(idToAspect.containsKey(id)) {
            AspectData old = idToAspect.get(id);
            idToAspect.remove(id);
        }
        register(id, aspect);
    }

    protected static void remove(Identifier id) {
        idToAspect.remove(id);
    }

    public static int size() {
        return idToAspect.size();
    }

    public static Stream<Identifier> identifiers() {
        return idToAspect.keySet().stream();
    }

    public static Set<Map.Entry<Identifier, AspectData>> entries() {
        return idToAspect.entrySet();
    }

    public static List<AspectData> values() {
        return idToAspect.values().stream().toList();
    }

    public static AspectData get(Identifier id) {
        AspectData blockData = idToAspect.get(id);
        if (blockData != null) {
            return blockData;
        }
        
        AspectData itemData = ItemAspectRegistry.get(id);
        if (itemData != null && !itemData.isEmpty()) {
            return itemData;
        }
        
        return AspectData.DEFAULT;
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

    public static boolean contains(Identifier id) {
        return idToAspect.containsKey(id);
    }

    public static void clear() {
        idToAspect.clear();
    }

    public static void reset() {
        clear();
    }
}