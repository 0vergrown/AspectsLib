package dev.overgrown.aspectslib.api;

import dev.overgrown.aspectslib.data.*;
import dev.overgrown.aspectslib.entity.aura_node.client.AuraNodeVisibilityConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Public API for AspectsLib functionality.
 * <p>
 * Provides:
 * <li>Access to aspect data on items</li>
 * <li>Aspect lookup methods</li>
 * <li>Item-aspect registration</li>
 * </p>
 * <p>
* <br>
 * Usage Example:
 * <pre>{@code
 * // Get aspects from item
 * AspectData data = AspectsAPI.getAspectData(stack);
 * }</pre>
 * </p>
 * <p>
 * <pre>{@code
 * // Add aspect to item
 * AspectsAPI.addAspect(stack, new Identifier("mymod:ignis"), 5);
 * }</pre>
 * </p>
 * <p>
 * <pre>{@code
 * // Register default aspects for item
 * AspectsAPI.registerItemAspect(Items.DIAMOND, new Identifier("aspectslib:vitreus"), 10);
 * }</pre>
 * </p>
 */
public class AspectsAPI {

    /**
     * Gets the aspect data from an ItemStack
     * @param stack The ItemStack to check
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getAspectData(ItemStack stack) {
        return ((IAspectDataProvider) (Object) stack).aspectslib$getAspectData();
    }

    /**
     * Sets the aspect data on an ItemStack
     * @param stack The ItemStack to modify
     * @param data The aspect data to set, or null to clear
     */
    public static void setAspectData(ItemStack stack, @Nullable AspectData data) {
        ((IAspectDataProvider) (Object) stack).aspectslib$setAspectData(data);
    }

    /**
     * Adds aspects to an ItemStack
     * @param stack The ItemStack to modify
     * @param aspectId The identifier of the aspect to add
     * @param amount The amount to add
     * @return true if successful, false if aspect not found
     */
    public static boolean addAspect(ItemStack stack, Identifier aspectId, int amount) {
        Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
        if (aspect == null) {
            return false;
        }

        AspectData currentData = getAspectData(stack);
        AspectData.Builder builder = new AspectData.Builder(currentData);
        builder.add(aspectId, amount);
        setAspectData(stack, builder.build());
        return true;
    }

    /**
     * Adds aspects to an ItemStack by name
     * @param stack The ItemStack to modify
     * @param aspectName The name of the aspect to add
     * @param amount The amount to add
     * @return true if successful, false if aspect not found
     */
    public static boolean addAspectByName(ItemStack stack, String aspectName, int amount) {
        AspectData currentData = getAspectData(stack);
        AspectData.Builder builder = new AspectData.Builder(currentData);
        builder.addByName(aspectName, amount);
        setAspectData(stack, builder.build());
        return true;
    }

    /**
     * Registers default aspects for an items
     * @param item The items to register aspects for
     * @param aspectId The identifier of the aspect
     * @param amount The default amount
     */
    public static void registerItemAspect(Item item, Identifier aspectId, int amount) {
        Identifier itemId = Registries.ITEM.getId(item);
        Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
        
        if (aspect != null) {
            Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
            aspects.put(aspectId, amount);
            ItemAspectRegistry.register(itemId, new AspectData(aspects));
        }
    }

    /**
     * Registers default aspects for an items by name
     * @param item The items to register aspects for
     * @param aspectName The name of the aspect
     * @param amount The default amount
     */
    public static void registerItemAspectByName(Item item, String aspectName, int amount) {
        Identifier itemId = Registries.ITEM.getId(item);
        AspectData.Builder builder = new AspectData.Builder(AspectData.DEFAULT);
        builder.addByName(aspectName, amount);
        ItemAspectRegistry.register(itemId, builder.build());
    }

    /**
     * Registers default aspects for a block
     * @param block The block to register aspects for
     * @param aspectId The identifier of the aspect
     * @param amount The default amount
     */
    public static void registerBlockAspect(Block block, Identifier aspectId, int amount) {
        Identifier blockId = Registries.BLOCK.getId(block);
        Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
        
        if (aspect != null) {
            Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
            aspects.put(aspectId, amount);
            BlockAspectRegistry.register(blockId, new AspectData(aspects));
        }
    }

    /**
     * Registers default aspects for an entity type
     * @param entityType The entity type to register aspects for
     * @param aspectId The identifier of the aspect
     * @param amount The default amount
     */
    public static void registerEntityAspect(EntityType<?> entityType, Identifier aspectId, int amount) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(entityType);
        Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
        
        if (aspect != null) {
            Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
            aspects.put(aspectId, amount);
            EntityAspectRegistry.register(entityId, new AspectData(aspects));
        }
    }

    /**
     * Registers default aspects for a biome
     * @param biomeKey The registry key of the biome to register aspects for
     * @param aspectId The identifier of the aspect
     * @param amount The default amount
     */
    public static void registerBiomeAspect(RegistryKey<Biome> biomeKey, Identifier aspectId, int amount) {
        Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
        
        if (aspect != null) {
            Object2IntOpenHashMap<Identifier> aspects = new Object2IntOpenHashMap<>();
            aspects.put(aspectId, amount);
            BiomeAspectRegistry.register(biomeKey, new AspectData(aspects));
        }
    }

    /**
     * Gets aspect data for an item by its identifier (includes tag-based aspects)
     * @param itemId The identifier of the item
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getItemAspectData(Identifier itemId) {
        return ItemAspectRegistry.get(itemId);
    }

    /**
     * Gets aspect data for an item (includes tag-based aspects)
     * @param item The item
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getItemAspectData(Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        return ItemAspectRegistry.get(itemId);
    }

    /**
     * Gets aspect data for a block
     * @param blockId The identifier of the block
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getBlockAspectData(Identifier blockId) {
        return BlockAspectRegistry.get(blockId);
    }

    /**
     * Gets aspect data for an entity type
     * @param entityId The identifier of the entity type
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getEntityAspectData(Identifier entityId) {
        return EntityAspectRegistry.get(entityId);
    }

    /**
     * Gets aspect data for a biome
     * @param biomeKey The registry key of the biome
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getBiomeAspectData(RegistryKey<Biome> biomeKey) {
        return BiomeAspectModifier.getCombinedBiomeAspects(biomeKey.getValue());
    }

    /**
     * Gets aspect data for a biome by identifier
     * @param biomeId The identifier of the biome
     * @return The aspect data, or AspectData.DEFAULT if none
     */
    public static AspectData getBiomeAspectData(Identifier biomeId) {
        return BiomeAspectModifier.getCombinedBiomeAspects(biomeId);
    }

    /**
     * Gets an aspect by its identifier
     * @param aspectId The identifier of the aspect
     * @return The aspect, or empty if not found
     */
    public static Optional<Aspect> getAspect(Identifier aspectId) {
        return Optional.ofNullable(ModRegistries.ASPECTS.get(aspectId));
    }

    /**
     * Gets an aspect by its name
     * @param aspectName The name of the aspect
     * @return The aspect, or empty if not found
     */
    public static Optional<Aspect> getAspectByName(String aspectName) {
        Identifier aspectId = AspectManager.NAME_TO_ID.get(aspectName);
        return aspectId != null ? getAspect(aspectId) : Optional.empty();
    }

    /**
     * Creates a new AspectData builder
     * @return A new builder instance
     */
    public static AspectData.Builder createAspectDataBuilder() {
        return new AspectData.Builder(AspectData.DEFAULT);
    }

    /**
     * Gets all loaded aspects
     * @return A map of all loaded aspects
     */
    public static java.util.Map<Identifier, Aspect> getAllAspects() {
        return java.util.Collections.unmodifiableMap(ModRegistries.ASPECTS);
    }

    /**
     * Adds a condition for when aura nodes should be fully visible.
     * @param condition A predicate that takes a PlayerEntity and boolean indicating if the node has aspects
     */
    public static void addAuraNodeVisibilityCondition(BiPredicate<PlayerEntity, Boolean> condition) {
        AuraNodeVisibilityConfig.addVisibilityCondition(condition);
    }

    /**
     * Sets whether aura nodes should always be fully visible.
     * @param alwaysShow true to always show nodes, false to use conditions
     */
    public static void setAuraNodesAlwaysVisible(boolean alwaysShow) {
        AuraNodeVisibilityConfig.setAlwaysShow(alwaysShow);
    }
}