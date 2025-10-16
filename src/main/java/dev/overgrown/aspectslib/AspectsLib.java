package dev.overgrown.aspectslib;

import com.google.gson.Gson;
import dev.overgrown.aspectslib.aether.AetherEvents;
import dev.overgrown.aspectslib.aether.AetherManager;
import dev.overgrown.aspectslib.command.AspectDebugCommand;
import dev.overgrown.aspectslib.command.CorruptionCommand;
import dev.overgrown.aspectslib.command.RecipeAspectCommand;
import dev.overgrown.aspectslib.command.TagDumpCommand;
import dev.overgrown.aspectslib.corruption.CorruptionManager;
import dev.overgrown.aspectslib.data.AspectManager;
import dev.overgrown.aspectslib.data.UniversalAspectManager;
import dev.overgrown.aspectslib.recipe.RecipeAspectManager;
import dev.overgrown.aspectslib.registry.ModEntities;
import dev.overgrown.aspectslib.registry.ModItems;
import dev.overgrown.aspectslib.resonance.ResonanceManager;
import dev.overgrown.aspectslib.networking.SyncAspectIdentifierPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AspectsLib implements ModInitializer {
	public static final String MOD_ID = "aspectslib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Gson GSON = new Gson();

	/** Helper for creating namespaced identifiers */
	public static Identifier identifier(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
        ModItems.initialize();
		ModEntities.register();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AspectDebugCommand.register(dispatcher, registryAccess);
			RecipeAspectCommand.register(dispatcher, registryAccess);
			TagDumpCommand.register(dispatcher);
            CorruptionCommand.register(dispatcher, registryAccess);
		});

		// Sync aspect data to players when they join
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			try {
				SyncAspectIdentifierPacket.sendAllData(player);
				AspectsLib.LOGGER.debug("Sent aspect data to player: {}", player.getName().getString());
			} catch (Exception e) {
				AspectsLib.LOGGER.error("Failed to send aspect data to player {}: {}",
						player.getName().getString(), e.getMessage());
			}
		});

		// Initialize and register data managers
		AspectManager aspectManager = new AspectManager();
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(aspectManager);
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new UniversalAspectManager());

		ResourceManagerHelper.get(ResourceType.SERVER_DATA)
				.registerReloadListener(new ResonanceManager());

        // Initialize Aether system
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new AetherManager());
        AetherEvents.initialize();
		
		// Register Recipe Aspect Manager
		ResourceManagerHelper.get(ResourceType.SERVER_DATA)
				.registerReloadListener(new RecipeAspectManager());
		RecipeAspectManager.initialize();

        // Initialize corruption system
        CorruptionManager.initialize();

        LOGGER.info("AspectsLib initialized!");
	}
}