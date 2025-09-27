package dev.overgrown.aspectslib.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.overgrown.aspectslib.AspectsLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RecipeAspectConfig {
    
    private static final Path CONFIG_PATH = Paths.get("config", "aspectslib", "recipe_aspects.json");
    
    private boolean enabled = true;
    private double craftingLoss = 0.8;
    private double smeltingLoss = 0.9;
    private double smithingLoss = 0.95;
    private double stonecuttingLoss = 1.0;
    private int maxDepth = 20;
    private int parallelThreads = 4;
    private boolean preferLowestValueIngredient = true;
    private boolean debugLogging = false;
    
    private static RecipeAspectConfig INSTANCE;
    
    public static RecipeAspectConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RecipeAspectConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }
    
    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = Files.readString(CONFIG_PATH);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                
                if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
                if (json.has("craftingLoss")) craftingLoss = json.get("craftingLoss").getAsDouble();
                if (json.has("smeltingLoss")) smeltingLoss = json.get("smeltingLoss").getAsDouble();
                if (json.has("smithingLoss")) smithingLoss = json.get("smithingLoss").getAsDouble();
                if (json.has("stonecuttingLoss")) stonecuttingLoss = json.get("stonecuttingLoss").getAsDouble();
                if (json.has("maxDepth")) maxDepth = json.get("maxDepth").getAsInt();
                if (json.has("parallelThreads")) parallelThreads = json.get("parallelThreads").getAsInt();
                if (json.has("preferLowestValueIngredient")) preferLowestValueIngredient = json.get("preferLowestValueIngredient").getAsBoolean();
                if (json.has("debugLogging")) debugLogging = json.get("debugLogging").getAsBoolean();
                
                AspectsLib.LOGGER.info("Loaded recipe aspect configuration");
            } catch (IOException e) {
                AspectsLib.LOGGER.error("Failed to load recipe aspect config", e);
                save();
            }
        } else {
            save();
        }
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("craftingLoss", craftingLoss);
            json.addProperty("smeltingLoss", smeltingLoss);
            json.addProperty("smithingLoss", smithingLoss);
            json.addProperty("stonecuttingLoss", stonecuttingLoss);
            json.addProperty("maxDepth", maxDepth);
            json.addProperty("parallelThreads", parallelThreads);
            json.addProperty("preferLowestValueIngredient", preferLowestValueIngredient);
            json.addProperty("debugLogging", debugLogging);
            
            String jsonString = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(json);
            
            Files.writeString(CONFIG_PATH, jsonString);
            AspectsLib.LOGGER.info("Saved recipe aspect configuration");
        } catch (IOException e) {
            AspectsLib.LOGGER.error("Failed to save recipe aspect config", e);
        }
    }
    
    public boolean isEnabled() { return enabled; }
    public double getCraftingLoss() { return craftingLoss; }
    public double getSmeltingLoss() { return smeltingLoss; }
    public double getSmithingLoss() { return smithingLoss; }
    public double getStonecuttingLoss() { return stonecuttingLoss; }
    public int getMaxDepth() { return maxDepth; }
    public int getParallelThreads() { return parallelThreads; }
    public boolean isPreferLowestValueIngredient() { return preferLowestValueIngredient; }
    public boolean isDebugLogging() { return debugLogging; }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        save();
    }
}