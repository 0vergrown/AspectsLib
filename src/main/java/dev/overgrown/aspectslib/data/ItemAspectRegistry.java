package dev.overgrown.aspectslib.data;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.mixin.ItemStackMixin;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Stream;

/**
 * This class pairs AspectData to Identifiers, used in applying custom AspectData to items based on the item's identifier.
 * <p>
 * Responsibilities:
 * <ol type="1">
 * <li>Stores default aspect data for items and tags</li>
 * <li>Provides lookup for item aspects</li>
 * </ol>
 * </p>
 * <p>
 * Usage:
 * <li>Populated by {@link CustomItemTagManager} from datapacks</li>
 * <li>Accessed by {@link ItemStackMixin} during aspect initialization</li>
 * </p>
 * <br>
 * Example datapack entry (data/aspectslib/tags/items/aspects.json):
 * <pre>{@code
 * {
 *   "replace": false,
 *   "values": [
 *     {
 *       "id": "minecraft:diamond",
 *       "aspects": {
 *         "aspectslib:terra": 10,
 *         "aspectslib:vitreus": 5
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 */
public class ItemAspectRegistry {

    /**
     * Internal storage for the mappings between Identifiers and AspectData
     */
    private static final HashMap<Identifier, AspectData> idToAspect = new HashMap<>();
    
    /**
     * Storage for tag-based aspects that will be resolved when tags are available
     */
    private static final HashMap<Identifier, AspectData> tagToAspect = new HashMap<>();

    /**
     * Registers a new association between an `Identifier` and an `AspectData`.
     *
     * @param id     The `Identifier` of the items to which the aspect will be applied.
     * @param aspect The `AspectData` to associate with the items.
     * @return The registered `AspectData`.
     */
    public static AspectData register(Identifier id, AspectData aspect) {
        idToAspect.put(id, aspect);
        return aspect;
    }

    /**
     * Updates an existing association between an `Identifier` and an `AspectData`. If the `Identifier` is already
     * registered, the old `AspectData` is removed and replaced with the new one.
     *
     * @param id     The `Identifier` of the items to update.
     * @param aspect The new `AspectData` to associate with the items.
     */
    public static void update(Identifier id, AspectData aspect) {
        if(idToAspect.containsKey(id)) {
            AspectData old = idToAspect.get(id);
            idToAspect.remove(id);
        }
        register(id, aspect);
    }

    /**
     * Removes the association for the specified `Identifier`.
     *
     * @param id The `Identifier` of the items to remove from the registry.
     */
    protected static void remove(Identifier id) {
        idToAspect.remove(id);
    }

    /**
     * Returns the number of registered items-aspect associations.
     *
     * @return The number of entries in the registry.
     */
    public static int size() {
        return idToAspect.size();
    }

    /**
     * Provides a stream of all registered `Identifier` objects.
     *
     * @return A stream of `Identifier` objects.
     */
    public static Stream<Identifier> identifiers() {
        return idToAspect.keySet().stream();
    }

    /**
     * Provides a set of all registered entries (key-value pairs) in the registry.
     *
     * @return A set of `Map.Entry` objects representing the registered associations.
     */
    public static Set<Map.Entry<Identifier, AspectData>> entries() {
        return idToAspect.entrySet();
    }

    /**
     * Provides a list of all registered `AspectData` objects.
     *
     * @return A list of `AspectData` objects.
     */
    public static List<AspectData> values() {
        return idToAspect.values().stream().toList();
    }

    /**
     * Retrieves the `AspectData` associated with the specified `Identifier`.
     * This method now checks both direct mappings and tag-based mappings.
     *
     * @param id The `Identifier` of the items to look up.
     * @return The associated `AspectData`, or DEFAULT if not found.
     */
    public static AspectData get(Identifier id) {
        // First check direct mappings
        AspectData direct = idToAspect.get(id);
        if (direct != null) {
            return direct;
        }
        
        // Then check tag-based mappings
        Item item = Registries.ITEM.get(id);
        if (item != null && item != Items.AIR) {
            boolean debugLog = id.getPath().contains("oak_planks") || id.getPath().contains("oak_door");
            if (debugLog && !tagToAspect.isEmpty()) {
                AspectsLib.LOGGER.debug("Checking tags for {}, {} tags registered", id, tagToAspect.size());
            }
            
            for (Map.Entry<Identifier, AspectData> tagEntry : tagToAspect.entrySet()) {
                TagKey<Item> itemTag = TagKey.of(RegistryKeys.ITEM, tagEntry.getKey());
                try {
                    if (item.getDefaultStack().isIn(itemTag)) {
                        if (debugLog) {
                            AspectsLib.LOGGER.info("Item {} matched tag {}, returning aspects: {}", 
                                id, tagEntry.getKey(), tagEntry.getValue());
                        }
                        return tagEntry.getValue();
                    }
                } catch (Exception e) {
                    // Tag might not be loaded yet, skip it
                    if (debugLog) {
                        AspectsLib.LOGGER.debug("Failed to check tag {} for item {}: {}", 
                            tagEntry.getKey(), id, e.getMessage());
                    }
                }
            }
            
            if (debugLog && !tagToAspect.isEmpty()) {
                AspectsLib.LOGGER.debug("No tags matched for {}", id);
            }
        }
        
        return AspectData.DEFAULT;
    }
    
    /**
     * Registers tag-based aspects that will be applied to items with the given tag.
     * 
     * @param tagId The tag identifier (without the # prefix)
     * @param aspect The AspectData to associate with items having this tag
     */
    public static void registerTag(Identifier tagId, AspectData aspect) {
        tagToAspect.put(tagId, aspect);
        AspectsLib.LOGGER.debug("Registered tag aspects for tag {}: {} aspects", tagId, aspect.getMap().size());
    }
    
    /**
     * Gets aspects for an item, checking both direct mappings and tag-based mappings.
     * This is now just an alias for get() since get() handles both cases.
     * 
     * @param itemId The item identifier
     * @return The associated AspectData, or DEFAULT if not found
     */
    public static AspectData getWithTags(Identifier itemId) {
        return get(itemId);
    }

    /**
     * Retrieves the `Identifier` associated with the specified `AspectData`.
     *
     * @param aspect The `AspectData` to look up.
     * @return The associated `Identifier`.
     * @throws IllegalArgumentException If the `AspectData` is not registered.
     */
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

    /**
     * Checks if the registry contains an association for the specified `Identifier`.
     *
     * @param id The `Identifier` to check.
     * @return `true` if the `Identifier` is registered, otherwise `false`.
     */
    public static boolean contains(Identifier id) {
        return idToAspect.containsKey(id);
    }

    /**
     * Clears all registered associations from the registry.
     */
    public static void clear() {
        idToAspect.clear();
        tagToAspect.clear();
    }

    /**
     * Resets the registry by clearing all registered associations. This is an alias for {@link #clear()}.
     */
    public static void reset() {
        clear();
    }
    
    /**
     * Gets the number of registered tags.
     */
    public static int tagSize() {
        return tagToAspect.size();
    }
    
    /**
     * Gets all registered tag identifiers.
     */
    public static Set<Identifier> getRegisteredTags() {
        return new HashSet<>(tagToAspect.keySet());
    }
}