package dev.overgrown.aspectslib.recipe;

import dev.overgrown.aspectslib.AspectsLib;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RecipeAspectManager implements IdentifiableResourceReloadListener {
    
    private static RecipeAspectManager INSTANCE;
    private MinecraftServer server;
    private RecipeAspectCalculator calculator;
    private final RecipeAspectConfig config;
    
    public RecipeAspectManager() {
        INSTANCE = this;
        this.config = RecipeAspectConfig.getInstance();
    }
    
    public static RecipeAspectManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RecipeAspectManager();
        }
        return INSTANCE;
    }
    
    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server != null) {
            this.calculator = new RecipeAspectCalculator(server);
        }
    }
    
    public void setEnabled(boolean enabled) {
        config.setEnabled(enabled);
        AspectsLib.LOGGER.info("Recipe aspect calculation {}", enabled ? "enabled" : "disabled");
    }
    
    public void recalculateAspects() {
        if (server == null || calculator == null) {
            AspectsLib.LOGGER.warn("Cannot calculate recipe aspects: server not initialized");
            return;
        }
        
        if (!config.isEnabled()) {
            AspectsLib.LOGGER.info("Recipe aspect calculation is disabled");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                calculator.calculateAllAspects();
            } catch (Exception e) {
                AspectsLib.LOGGER.error("Failed to calculate recipe aspects", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
                                          Profiler prepareProfiler, Profiler applyProfiler,
                                          Executor prepareExecutor, Executor applyExecutor) {
        return synchronizer.whenPrepared(null).thenRunAsync(() -> {
            applyProfiler.startTick();
            applyProfiler.push("aspectslib:recipe_aspects");
            
            if (server != null && config.isEnabled()) {
                AspectsLib.LOGGER.info("Triggering recipe aspect recalculation after resource reload");
                recalculateAspects();
            }
            
            applyProfiler.pop();
            applyProfiler.endTick();
        }, applyExecutor);
    }
    
    @Override
    public Identifier getFabricId() {
        return AspectsLib.identifier("recipe_aspects");
    }
    
    @Override
    public Collection<Identifier> getFabricDependencies() {
        // Make sure we load AFTER all aspect data is loaded
        return List.of(
            AspectsLib.identifier("aspects"),
            AspectsLib.identifier("universal_aspects"),
            new Identifier("minecraft", "recipes"),
            new Identifier("minecraft", "tags/items")  // Wait for item tags to load
        );
    }
    
    public static void initialize() {
        RecipeAspectManager manager = getInstance();
        
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            manager.setServer(server);
            AspectsLib.LOGGER.info("Recipe aspect manager initialized with server");
            
            // Schedule calculation to happen after all data is loaded
            server.execute(() -> {
                // Small delay to ensure all systems are ready
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                AspectsLib.LOGGER.info("Starting delayed recipe aspect calculation...");
                manager.recalculateAspects();
            });
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            manager.setServer(null);
        });
    }
}