package dev.overgrown.aspectslib.aether;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.overgrown.aspectslib.AspectsLib;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.HashMap;
import java.util.Map;

public class StructureAetherDensityManager extends JsonDataLoader implements IdentifiableResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final Map<Identifier, AetherDensity> DENSITY_MAP = new HashMap<>();

    public StructureAetherDensityManager() {
        super(GSON, "aether_densities/structure");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        DENSITY_MAP.clear();
        AspectsLib.LOGGER.info("Starting to load structure aether densities from {} files", prepared.size());

        prepared.forEach((resourceId, json) -> {
            try {
                AspectsLib.LOGGER.debug("Processing resource: {}", resourceId);

                String path = resourceId.getPath();

                if (path.endsWith(".json")) {
                    path = path.substring(0, path.length() - 5);
                }

                // Extract structure ID from path (remove the "aether_densities/structure/" prefix if present)
                String structurePath = path;
                if (path.startsWith("aether_densities/structure/")) {
                    structurePath = path.substring("aether_densities/structure/".length());
                }

                Identifier structureId;

                if (structurePath.contains("/")) {
                    String[] parts = structurePath.split("/");
                    if (parts.length >= 2) {
                        String namespace = parts[0];
                        String structureName = String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                        structureId = new Identifier(namespace, structureName);
                    } else {
                        structureId = new Identifier(resourceId.getNamespace(), structurePath);
                    }
                } else {
                    structureId = new Identifier(resourceId.getNamespace(), structurePath);
                }

                AspectsLib.LOGGER.info("Loading aether density for structure: {}", structureId);

                JsonObject jsonObj = json.getAsJsonObject();

                JsonObject valuesObj = null;
                if (jsonObj.has("values")) {
                    valuesObj = jsonObj.getAsJsonObject("values");
                } else {
                    valuesObj = jsonObj;
                }

                AetherDensity density = AetherDensity.fromJson(valuesObj);
                DENSITY_MAP.put(structureId, density);

                AspectsLib.LOGGER.info("Successfully loaded {} aspects for structure {}: {}",
                        density.getDensities().size(), structureId, density.getDensities());

            } catch (Exception e) {
                AspectsLib.LOGGER.error("Error loading structure aether density from {}: {}", resourceId, e.getMessage(), e);
            }
        });

        AspectsLib.LOGGER.info("Completed loading {} structure aether densities", DENSITY_MAP.size());
    }

    @Override
    public Identifier getFabricId() {
        return AspectsLib.identifier("structure_aether_density");
    }
}