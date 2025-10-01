package dev.overgrown.aspectslib.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.overgrown.aspectslib.AspectsLib;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.biome.Biome;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UniversalAspectManager extends JsonDataLoader implements IdentifiableResourceReloadListener {

    private static final Gson GSON = new Gson();

    public UniversalAspectManager() {
        super(GSON, "aspect_assignments");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        ItemAspectRegistry.clear();
        BlockAspectRegistry.clear();
        EntityAspectRegistry.clear();
        BiomeAspectRegistry.clear();

        if (ModRegistries.ASPECTS.isEmpty()) {
            AspectsLib.LOGGER.warn("Aspects map is empty, aspects may not be loaded yet");
            return;
        }

        AspectsLib.LOGGER.info("Processing {} aspect assignment files", prepared.size());
        
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement jsonElement = entry.getValue();
            
            AspectsLib.LOGGER.debug("Processing aspect assignment file: {}", fileId);
            
            if (jsonElement instanceof JsonArray jsonArray) {
                for (JsonElement element : jsonArray) {
                    if (element instanceof JsonObject assignment) {
                        processAssignment(assignment);
                    }
                }
            } else {
                AspectsLib.LOGGER.warn("Aspect assignment file {} is not a JSON array", fileId);
            }
        }
        
        AspectsLib.LOGGER.info("Finished loading universal aspect assignments");
    }

    private void processAssignment(JsonObject assignment) {
        if (!assignment.has("aspects")) {
            AspectsLib.LOGGER.warn("Assignment missing 'aspects' field");
            return;
        }

        Object2IntOpenHashMap<Identifier> aspects = parseAspects(assignment.getAsJsonObject("aspects"));
        if (aspects.isEmpty()) {
            return;
        }

        AspectData aspectData = new AspectData(aspects);

        if (assignment.has("item")) {
            String itemId = JsonHelper.getString(assignment, "item");
            Identifier id = new Identifier(itemId);
            ItemAspectRegistry.register(id, aspectData);
        } else if (assignment.has("item_tag")) {
            String tagId = JsonHelper.getString(assignment, "item_tag");
            if (tagId.startsWith("#")) {
                tagId = tagId.substring(1);
            }
            Identifier tagIdentifier = new Identifier(tagId);
            
            // Register the tag for lazy resolution
            ItemAspectRegistry.registerTag(tagIdentifier, aspectData);
        } else if (assignment.has("block")) {
            String blockId = JsonHelper.getString(assignment, "block");
            BlockAspectRegistry.register(new Identifier(blockId), aspectData);
        } else if (assignment.has("block_tag")) {
            String tagId = JsonHelper.getString(assignment, "block_tag");
            if (tagId.startsWith("#")) {
                tagId = tagId.substring(1);
            }
            BlockAspectRegistry.register(new Identifier(tagId), aspectData);
        } else if (assignment.has("entity")) {
            String entityId = JsonHelper.getString(assignment, "entity");
            EntityAspectRegistry.register(new Identifier(entityId), aspectData);
        } else if (assignment.has("entity_tag")) {
            String tagId = JsonHelper.getString(assignment, "entity_tag");
            if (tagId.startsWith("#")) {
                tagId = tagId.substring(1);
            }
            EntityAspectRegistry.register(new Identifier(tagId), aspectData);
        } else if (assignment.has("biome")) {
            String biomeId = JsonHelper.getString(assignment, "biome");
            Identifier id = new Identifier(biomeId);
            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, id);
            BiomeAspectRegistry.register(biomeKey, aspectData);
        } else if (assignment.has("biome_tag")) {
            String tagId = JsonHelper.getString(assignment, "biome_tag");
            if (tagId.startsWith("#")) {
                tagId = tagId.substring(1);
            }
            BiomeAspectRegistry.register(new Identifier(tagId), aspectData);
        } else {
            AspectsLib.LOGGER.warn("Assignment has no valid target (item, item_tag, block, block_tag, entity, entity_tag, biome, biome_tag)");
        }
    }

    private Object2IntOpenHashMap<Identifier> parseAspects(JsonObject aspectsJson) {
        Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
        
        for (Map.Entry<String, JsonElement> entry : aspectsJson.asMap().entrySet()) {
            String aspectId = entry.getKey();
            JsonElement valueElement = entry.getValue();
            
            if (valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                Identifier aspectIdentifier = new Identifier(aspectId);
                if (ModRegistries.ASPECTS.containsKey(aspectIdentifier)) {
                    int amount = valueElement.getAsInt();
                    aspects.put(aspectIdentifier, amount);
                    AspectsLib.LOGGER.debug("Added aspect {} with amount {}", aspectId, amount);
                } else {
                    AspectsLib.LOGGER.warn("Unknown aspect: {}", aspectId);
                }
            } else {
                AspectsLib.LOGGER.warn("Invalid aspect amount for {}: {}", aspectId, valueElement);
            }
        }
        
        return aspects;
    }

    @Override
    public Identifier getFabricId() {
        return AspectsLib.identifier("universal_aspects");
    }

    @Override
    public Collection<Identifier> getFabricDependencies() {
        return List.of(AspectsLib.identifier("aspects"));
    }
}