package dev.overgrown.aspectslib.recipe;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.api.AspectsAPI;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BlockAspectRegistry;
import dev.overgrown.aspectslib.data.ItemAspectRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RecipeAspectCalculator {
    
    private final RecipeAspectConfig config;
    private final MinecraftServer server;
    private final RecipeManager recipeManager;
    
    private final Map<Identifier, AspectData> calculatedAspects = new ConcurrentHashMap<>();
    private final Map<Identifier, RecipeNode> recipeGraph = new ConcurrentHashMap<>();
    private final Set<Identifier> baseItems = ConcurrentHashMap.newKeySet();
    private final Set<Identifier> processingItems = ConcurrentHashMap.newKeySet();
    private final Map<Identifier, Integer> itemDepths = new ConcurrentHashMap<>();
    
    private static class RecipeNode {
        final Identifier itemId;
        final Set<RecipeEntry> recipes = ConcurrentHashMap.newKeySet();
        final Set<Identifier> dependencies = ConcurrentHashMap.newKeySet();
        final Set<Identifier> dependents = ConcurrentHashMap.newKeySet();
        volatile AspectData cachedAspects = null;
        volatile boolean isProcessing = false;
        volatile boolean isProcessed = false;
        volatile int depth = 0;
        
        RecipeNode(Identifier itemId) {
            this.itemId = itemId;
        }
    }
    
    private static class RecipeEntry {
        final Recipe<?> recipe;
        final List<Identifier> ingredients;
        final Map<Identifier, Integer> ingredientCounts;
        final int outputCount;
        final RecipeType<?> type;
        
        RecipeEntry(Recipe<?> recipe, List<Identifier> ingredients, Map<Identifier, Integer> counts, int outputCount) {
            this.recipe = recipe;
            this.ingredients = ingredients;
            this.ingredientCounts = counts;
            this.outputCount = outputCount;
            this.type = recipe.getType();
        }
    }
    
    public RecipeAspectCalculator(MinecraftServer server) {
        this.config = RecipeAspectConfig.getInstance();
        this.server = server;
        this.recipeManager = server.getRecipeManager();
    }
    
    public void calculateAllAspects() {
        long startTime = System.currentTimeMillis();
        AspectsLib.LOGGER.info("Starting recipe-based aspect calculation...");
        
        clearCalculatedData();
        identifyBaseItems();
        buildRecipeGraph();
        detectAndBreakCycles();
        calculateDepths();
        propagateAspects();
        applyCalculatedAspects();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        String message = String.format("Recipe aspect calculation completed in %d ms. Processed %d items, %d recipes.", 
                duration, calculatedAspects.size(), recipeGraph.size());
        AspectsLib.LOGGER.info(message);
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("§a[AspectsLib] " + message), false);
        }
    }
    
    private void clearCalculatedData() {
        calculatedAspects.clear();
        recipeGraph.clear();
        baseItems.clear();
        processingItems.clear();
        itemDepths.clear();
    }
    
    private void identifyBaseItems() {
        int checked = 0;
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            
            Identifier itemId = Registries.ITEM.getId(item);
            
            // Check ItemAspectRegistry using the new method that includes tags
            AspectData existingAspects = ItemAspectRegistry.getWithTags(itemId);
            
            // Also check block registry for block items
            if ((existingAspects == null || existingAspects.isEmpty()) && item instanceof net.minecraft.item.BlockItem blockItem) {
                Block block = blockItem.getBlock();
                Identifier blockId = Registries.BLOCK.getId(block);
                AspectData blockAspects = BlockAspectRegistry.get(blockId);
                if (blockAspects != null && !blockAspects.isEmpty()) {
                    existingAspects = blockAspects;
                }
            }
            
            checked++;
            
            if (existingAspects != null && !existingAspects.isEmpty()) {
                baseItems.add(itemId);
                calculatedAspects.put(itemId, existingAspects);
                if (itemId.getPath().contains("planks") || itemId.getPath().contains("door") || 
                    itemId.getPath().contains("log") || itemId.getPath().contains("wool")) {
                    AspectsLib.LOGGER.info("Base item {} has aspects: {}", itemId, existingAspects);
                }
            } else if (itemId.getPath().contains("oak_planks")) {
                AspectsLib.LOGGER.warn("Oak planks {} has NO aspects! Registry returned: {}", 
                    itemId, existingAspects);
            }
        }
        
        AspectsLib.LOGGER.info("Identified {} base items with predefined aspects out of {} checked", 
            baseItems.size(), checked);
        
        // Log some specific items we expect to have aspects
        for (String test : new String[]{"minecraft:oak_planks", "minecraft:iron_ingot", "minecraft:diamond"}) {
            Identifier testId = new Identifier(test);
            if (baseItems.contains(testId)) {
                AspectsLib.LOGGER.info("  ✓ {} has aspects", test);
            } else {
                AspectsLib.LOGGER.warn("  ✗ {} has NO aspects!", test);
            }
        }
    }
    
    private void buildRecipeGraph() {
        Collection<Recipe<?>> allRecipes = recipeManager.values();
        
        for (Recipe<?> recipe : allRecipes) {
            if (!isValidRecipe(recipe)) continue;
            
            try {
                ItemStack output = recipe.getOutput(server.getRegistryManager());
                if (output == null || output.isEmpty()) continue;
                
                Identifier outputId = Registries.ITEM.getId(output.getItem());
                RecipeNode node = recipeGraph.computeIfAbsent(outputId, RecipeNode::new);
                
                List<Identifier> ingredientIds = new ArrayList<>();
                Map<Identifier, Integer> ingredientCounts = new HashMap<>();
                
                // Special logging for doors
                if (outputId.getPath().contains("door")) {
                    AspectsLib.LOGGER.info("Processing door recipe: {} -> {}", recipe.getId(), outputId);
                }
                
                if (recipe instanceof ShapedRecipe shaped) {
                    extractIngredientsFromShaped(shaped, ingredientIds, ingredientCounts);
                } else if (recipe instanceof ShapelessRecipe shapeless) {
                    extractIngredientsFromShapeless(shapeless, ingredientIds, ingredientCounts);
                } else if (recipe instanceof SmeltingRecipe || recipe instanceof BlastingRecipe || 
                          recipe instanceof SmokingRecipe || recipe instanceof CampfireCookingRecipe) {
                    extractIngredientsFromCooking(recipe, ingredientIds, ingredientCounts);
                } else if (recipe instanceof StonecuttingRecipe stonecutting) {
                    extractIngredientsFromStonecutting(stonecutting, ingredientIds, ingredientCounts);
                }
                
                // Log what ingredients were found for doors
                if (outputId.getPath().contains("door") && !ingredientIds.isEmpty()) {
                    AspectsLib.LOGGER.info("Door recipe {} ingredients: {}", recipe.getId(), ingredientCounts);
                }
                
                if (!ingredientIds.isEmpty()) {
                    RecipeEntry entry = new RecipeEntry(recipe, ingredientIds, ingredientCounts, output.getCount());
                    node.recipes.add(entry);
                    
                    for (Identifier ingredientId : ingredientIds) {
                        node.dependencies.add(ingredientId);
                        RecipeNode ingredientNode = recipeGraph.computeIfAbsent(ingredientId, RecipeNode::new);
                        ingredientNode.dependents.add(outputId);
                    }
                }
                
            } catch (Exception e) {
                AspectsLib.LOGGER.debug("Error processing recipe {}: {}", recipe.getId(), e.getMessage());
            }
        }
        
        AspectsLib.LOGGER.info("Built recipe graph with {} nodes", recipeGraph.size());
    }
    
    private boolean isValidRecipe(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe || 
               recipe instanceof ShapelessRecipe ||
               recipe instanceof SmeltingRecipe ||
               recipe instanceof BlastingRecipe ||
               recipe instanceof SmokingRecipe ||
               recipe instanceof CampfireCookingRecipe ||
               recipe instanceof StonecuttingRecipe;
    }
    
    private void extractIngredientsFromShaped(ShapedRecipe recipe, List<Identifier> ids, Map<Identifier, Integer> counts) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            extractIngredient(ingredient, ids, counts);
        }
    }
    
    private void extractIngredientsFromShapeless(ShapelessRecipe recipe, List<Identifier> ids, Map<Identifier, Integer> counts) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            extractIngredient(ingredient, ids, counts);
        }
    }
    
    private void extractIngredientsFromCooking(Recipe<?> recipe, List<Identifier> ids, Map<Identifier, Integer> counts) {
        if (recipe instanceof AbstractCookingRecipe cooking) {
            extractIngredient(cooking.getIngredients().get(0), ids, counts);
        }
    }
    
    private void extractIngredientsFromStonecutting(StonecuttingRecipe recipe, List<Identifier> ids, Map<Identifier, Integer> counts) {
        extractIngredient(recipe.getIngredients().get(0), ids, counts);
    }
    
    private void extractIngredient(Ingredient ingredient, List<Identifier> ids, Map<Identifier, Integer> counts) {
        if (ingredient == null || ingredient.isEmpty()) {
            AspectsLib.LOGGER.debug("      Skipping null/empty ingredient");
            return;
        }
        
        ItemStack[] stacks = ingredient.getMatchingStacks();
        AspectsLib.LOGGER.debug("      Ingredient has {} matching stacks", stacks.length);
        
        if (stacks.length == 0) return;
        
        Identifier bestItemId = null;
        double lowestAspectValue = Double.MAX_VALUE;
        boolean hasAnyAspects = false;
        
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            AspectsLib.LOGGER.debug("      Checking option: {}", itemId);
            
            AspectData existingAspects = getItemAspects(itemId);
            
            if (existingAspects != null && !existingAspects.isEmpty()) {
                hasAnyAspects = true;
                double totalValue = existingAspects.calculateTotalRU();
                
                AspectsLib.LOGGER.debug("        Option {} has {} RU", itemId, totalValue);
                
                if (totalValue < lowestAspectValue) {
                    lowestAspectValue = totalValue;
                    bestItemId = itemId;
                }
            } else {
                AspectsLib.LOGGER.debug("        Option {} has NO aspects", itemId);
                if (!hasAnyAspects && bestItemId == null) {
                    bestItemId = itemId;
                }
            }
        }
        
        if (bestItemId != null) {
            ids.add(bestItemId);
            counts.merge(bestItemId, 1, Integer::sum);
            AspectsLib.LOGGER.debug("      Selected ingredient: {}", bestItemId);
        } else if (stacks.length > 0 && stacks[0] != null && !stacks[0].isEmpty()) {
            Identifier itemId = Registries.ITEM.getId(stacks[0].getItem());
            ids.add(itemId);
            counts.merge(itemId, 1, Integer::sum);
            AspectsLib.LOGGER.debug("      Fallback to first stack: {}", itemId);
        } else {
            AspectsLib.LOGGER.warn("      Could not extract any ingredient!");
        }
    }
    
    private AspectData getItemAspects(Identifier itemId) {
        AspectsLib.LOGGER.info("        Getting aspects for {}", itemId);
        
        // Check cache first
        AspectData cached = calculatedAspects.get(itemId);
        AspectsLib.LOGGER.info("          Cached check: cached={}, contains={}", cached, calculatedAspects.containsKey(itemId));
        if (cached != null) {
            AspectsLib.LOGGER.info("          RETURNING CACHED: {}", cached);
            return cached;
        }
        AspectsLib.LOGGER.info("          Not in cache, continuing...");
        
        // Look up using the new method that checks both direct and tag-based mappings
        AspectData registryAspects = ItemAspectRegistry.getWithTags(itemId);
        AspectsLib.LOGGER.info("          ItemAspectRegistry.getWithTags({}) returned: {}", itemId, registryAspects);
        
        if (registryAspects != null && !registryAspects.isEmpty()) {
            AspectsLib.LOGGER.info("          ✓ FOUND ASPECTS in registry for {}: {}", itemId, registryAspects);
            return registryAspects;
        }
        
        // Also check block registry in case it's a block item
        Item item = Registries.ITEM.get(itemId);
        if (item instanceof net.minecraft.item.BlockItem blockItem) {
            Block block = blockItem.getBlock();
            Identifier blockId = Registries.BLOCK.getId(block);
            AspectData blockAspects = BlockAspectRegistry.get(blockId);
            AspectsLib.LOGGER.info("          BlockAspectRegistry.get({}) returned: {}", blockId, blockAspects);
            
            if (blockAspects != null && !blockAspects.isEmpty()) {
                AspectsLib.LOGGER.info("          ✓ FOUND ASPECTS in block registry for {}: {}", blockId, blockAspects);
                return blockAspects;
            }
        }
        
        AspectsLib.LOGGER.info("          NO ASPECTS FOUND - returning DEFAULT for {}", itemId);
        return AspectData.DEFAULT;
    }
    
    private void detectAndBreakCycles() {
        Set<Identifier> visited = new HashSet<>();
        Set<Identifier> recursionStack = new HashSet<>();
        List<List<Identifier>> cycles = new ArrayList<>();
        
        for (Identifier nodeId : recipeGraph.keySet()) {
            if (!visited.contains(nodeId)) {
                detectCyclesDFS(nodeId, visited, recursionStack, new ArrayList<>(), cycles);
            }
        }
        
        if (!cycles.isEmpty()) {
            AspectsLib.LOGGER.info("Detected {} cycles in recipe graph", cycles.size());
            for (List<Identifier> cycle : cycles) {
                breakCycle(cycle);
            }
        }
    }
    
    private boolean detectCyclesDFS(Identifier nodeId, Set<Identifier> visited, Set<Identifier> recursionStack,
                                   List<Identifier> path, List<List<Identifier>> cycles) {
        visited.add(nodeId);
        recursionStack.add(nodeId);
        path.add(nodeId);
        
        RecipeNode node = recipeGraph.get(nodeId);
        if (node != null) {
            for (Identifier dependent : node.dependents) {
                if (!visited.contains(dependent)) {
                    if (detectCyclesDFS(dependent, visited, recursionStack, path, cycles)) {
                        return true;
                    }
                } else if (recursionStack.contains(dependent)) {
                    int cycleStart = path.indexOf(dependent);
                    if (cycleStart != -1) {
                        List<Identifier> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                        cycles.add(cycle);
                    }
                }
            }
        }
        
        path.remove(path.size() - 1);
        recursionStack.remove(nodeId);
        return false;
    }
    
    private void breakCycle(List<Identifier> cycle) {
        Identifier weakestLink = null;
        int minBaseDistance = Integer.MAX_VALUE;
        
        for (Identifier itemId : cycle) {
            int distance = calculateDistanceToBase(itemId, new HashSet<>());
            if (distance < minBaseDistance) {
                minBaseDistance = distance;
                weakestLink = itemId;
            }
        }
        
        if (weakestLink != null) {
            RecipeNode node = recipeGraph.get(weakestLink);
            if (node != null) {
                node.recipes.clear();
                node.dependencies.clear();
                AspectsLib.LOGGER.debug("Broke cycle at item: {}", weakestLink);
            }
        }
    }
    
    private int calculateDistanceToBase(Identifier itemId, Set<Identifier> visited) {
        if (baseItems.contains(itemId)) return 0;
        if (visited.contains(itemId)) return Integer.MAX_VALUE;
        
        visited.add(itemId);
        RecipeNode node = recipeGraph.get(itemId);
        
        if (node == null || node.dependencies.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        
        int minDistance = Integer.MAX_VALUE;
        for (Identifier dep : node.dependencies) {
            int distance = calculateDistanceToBase(dep, new HashSet<>(visited));
            if (distance != Integer.MAX_VALUE) {
                minDistance = Math.min(minDistance, distance + 1);
            }
        }
        
        return minDistance;
    }
    
    private void calculateDepths() {
        Queue<Identifier> queue = new LinkedList<>(baseItems);
        
        for (Identifier baseItem : baseItems) {
            itemDepths.put(baseItem, 0);
        }
        
        while (!queue.isEmpty()) {
            Identifier current = queue.poll();
            RecipeNode node = recipeGraph.get(current);
            
            if (node != null) {
                int currentDepth = itemDepths.getOrDefault(current, 0);
                
                for (Identifier dependent : node.dependents) {
                    int newDepth = currentDepth + 1;
                    Integer existingDepth = itemDepths.get(dependent);
                    
                    if (existingDepth == null || newDepth < existingDepth) {
                        itemDepths.put(dependent, newDepth);
                        if (newDepth < config.getMaxDepth()) {
                            queue.offer(dependent);
                        }
                    }
                }
            }
        }
    }
    
    private void propagateAspects() {
        Map<Integer, List<Identifier>> itemsByDepth = new HashMap<>();
        
        for (Map.Entry<Identifier, Integer> entry : itemDepths.entrySet()) {
            itemsByDepth.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        
        List<Integer> sortedDepths = new ArrayList<>(itemsByDepth.keySet());
        Collections.sort(sortedDepths);
        
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalItems = recipeGraph.size();
        
        for (Integer depth : sortedDepths) {
            List<Identifier> itemsAtDepth = itemsByDepth.get(depth);
            
            for (Identifier itemId : itemsAtDepth) {
                if (!baseItems.contains(itemId)) {
                    calculateItemAspects(itemId);
                    int count = processedCount.incrementAndGet();
                    if (count % 100 == 0) {
                        AspectsLib.LOGGER.debug("Processed {}/{} items", count, totalItems);
                    }
                }
            }
        }
        
        for (Identifier itemId : recipeGraph.keySet()) {
            if (!calculatedAspects.containsKey(itemId)) {
                calculateItemAspects(itemId);
            }
        }
    }
    
    private synchronized AspectData calculateItemAspects(Identifier itemId) {
        AspectData existing = calculatedAspects.get(itemId);
        if (existing != null) {
            return existing;
        }
        
        if (processingItems.contains(itemId)) {
            AspectsLib.LOGGER.debug("Skipping {} - circular dependency detected", itemId);
            return AspectData.DEFAULT;
        }
        
        processingItems.add(itemId);
        
        RecipeNode node = recipeGraph.get(itemId);
        if (node == null || node.recipes.isEmpty()) {
            processingItems.remove(itemId);
            AspectsLib.LOGGER.debug("No recipes found for {}", itemId);
            return AspectData.DEFAULT;
        }
        
        AspectsLib.LOGGER.info("Calculating aspects for {} ({} recipes available)", itemId, node.recipes.size());
        
        AspectData bestAspects = null;
        double bestValue = Double.MAX_VALUE;
        RecipeEntry bestRecipe = null;
        
        for (RecipeEntry recipeEntry : node.recipes) {
            AspectsLib.LOGGER.debug("  Trying recipe {} with {} ingredients", 
                recipeEntry.recipe.getId(), recipeEntry.ingredientCounts.size());
            
            AspectData recipeAspects = calculateRecipeAspects(recipeEntry);
            if (recipeAspects != null && !recipeAspects.isEmpty()) {
                double totalValue = recipeAspects.calculateTotalRU();
                AspectsLib.LOGGER.debug("    Recipe produced {} RU", totalValue);
                
                if (bestAspects == null || totalValue < bestValue) {
                    bestAspects = recipeAspects;
                    bestValue = totalValue;
                    bestRecipe = recipeEntry;
                }
            } else {
                AspectsLib.LOGGER.debug("    Recipe produced no aspects");
            }
        }
        
        if (bestAspects != null) {
            calculatedAspects.put(itemId, bestAspects);
            node.cachedAspects = bestAspects;
            AspectsLib.LOGGER.info("✓ {} calculated with {} total RU from recipe {}", 
                itemId, bestValue, bestRecipe != null ? bestRecipe.recipe.getId() : "unknown");
            
            // Log the actual aspects
            StringBuilder aspectStr = new StringBuilder();
            for (Map.Entry<Identifier, Integer> entry : bestAspects.getMap().entrySet()) {
                if (aspectStr.length() > 0) aspectStr.append(", ");
                aspectStr.append(entry.getKey().getPath()).append(":").append(entry.getValue());
            }
            AspectsLib.LOGGER.debug("    Aspects: {}", aspectStr);
        } else {
            AspectsLib.LOGGER.warn("✗ {} - no valid recipe produced aspects", itemId);
        }
        
        processingItems.remove(itemId);
        return bestAspects != null ? bestAspects : AspectData.DEFAULT;
    }
    
    private AspectData calculateRecipeAspects(RecipeEntry recipeEntry) {
        Object2IntOpenHashMap<Identifier> combinedAspects = new Object2IntOpenHashMap<>();
        
        AspectsLib.LOGGER.info("    === Calculating recipe {} ===", recipeEntry.recipe.getId());
        AspectsLib.LOGGER.info("    Output count: {}, Type: {}", recipeEntry.outputCount, recipeEntry.type);
        AspectsLib.LOGGER.info("    Total ingredients: {}", recipeEntry.ingredientCounts.size());
        
        for (Map.Entry<Identifier, Integer> ingredient : recipeEntry.ingredientCounts.entrySet()) {
            Identifier ingredientId = ingredient.getKey();
            int count = ingredient.getValue();
            
            AspectsLib.LOGGER.info("      Processing ingredient: {}x{}", count, ingredientId);
            
            AspectData ingredientAspects = calculatedAspects.get(ingredientId);
            AspectsLib.LOGGER.info("        From cache: {}", ingredientAspects);
            
            if (ingredientAspects == null) {
                AspectsLib.LOGGER.info("        Not cached, fetching...");
                ingredientAspects = getItemAspects(ingredientId);
                AspectsLib.LOGGER.info("        Fetched: {}", ingredientAspects);
            }
            
            boolean hasAspects = (ingredientAspects != null && !ingredientAspects.isEmpty());
            AspectsLib.LOGGER.info("        Has aspects: {} (null={}, empty={})", hasAspects, 
                ingredientAspects == null, ingredientAspects != null ? ingredientAspects.isEmpty() : "N/A");
            
            if (ingredientAspects != null && !ingredientAspects.isEmpty()) {
                AspectsLib.LOGGER.info("        {}x{} HAS {} aspects", count, ingredientId, 
                    ingredientAspects.getMap().size());
                
                for (Map.Entry<Identifier, Integer> aspectEntry : ingredientAspects.getMap().entrySet()) {
                    int totalAmount = aspectEntry.getValue() * count;
                    int oldValue = combinedAspects.getInt(aspectEntry.getKey());
                    combinedAspects.merge(aspectEntry.getKey(), totalAmount, Integer::sum);
                    int newValue = combinedAspects.getInt(aspectEntry.getKey());
                    AspectsLib.LOGGER.info("          {} : {} * {} = {} (was {}, now {})", 
                        aspectEntry.getKey().getPath(), aspectEntry.getValue(), count, totalAmount, oldValue, newValue);
                }
            } else {
                AspectsLib.LOGGER.warn("        {}x{} has NO ASPECTS!", count, ingredientId);
            }
        }
        
        boolean isEmpty = combinedAspects.isEmpty();
        AspectsLib.LOGGER.info("    Combined aspects empty: {} (size={})", isEmpty, combinedAspects.size());
        
        if (combinedAspects.isEmpty()) {
            AspectsLib.LOGGER.warn("    NO ASPECTS FROM INGREDIENTS! Returning DEFAULT");
            return AspectData.DEFAULT;
        }
        
        AspectsLib.LOGGER.info("    Combined aspects BEFORE loss: {}", combinedAspects);
        
        double lossFactor;
        if (recipeEntry.type == RecipeType.CRAFTING) {
            lossFactor = config.getCraftingLoss();
            AspectsLib.LOGGER.info("    Recipe type: CRAFTING, loss factor: {}", lossFactor);
        } else if (recipeEntry.type == RecipeType.SMELTING || 
                   recipeEntry.type == RecipeType.BLASTING || 
                   recipeEntry.type == RecipeType.SMOKING ||
                   recipeEntry.type == RecipeType.CAMPFIRE_COOKING) {
            lossFactor = config.getSmeltingLoss();
            AspectsLib.LOGGER.info("    Recipe type: SMELTING/COOKING, loss factor: {}", lossFactor);
        } else if (recipeEntry.type == RecipeType.SMITHING) {
            lossFactor = config.getSmithingLoss();
            AspectsLib.LOGGER.info("    Recipe type: SMITHING, loss factor: {}", lossFactor);
        } else if (recipeEntry.type == RecipeType.STONECUTTING) {
            lossFactor = config.getStonecuttingLoss();
            AspectsLib.LOGGER.info("    Recipe type: STONECUTTING, loss factor: {}", lossFactor);
        } else {
            lossFactor = config.getCraftingLoss();
            AspectsLib.LOGGER.info("    Recipe type: OTHER ({}), loss factor: {}", recipeEntry.type, lossFactor);
        }
        
        AspectsLib.LOGGER.info("    Applying loss factor {} and dividing by {} outputs", 
            lossFactor, recipeEntry.outputCount);
        
        for (Identifier aspectId : combinedAspects.keySet()) {
            int originalValue = combinedAspects.getInt(aspectId);
            double calculation = originalValue * lossFactor / recipeEntry.outputCount;
            int adjustedValue = (int) Math.ceil(calculation);
            int finalValue = Math.max(1, adjustedValue);
            combinedAspects.put(aspectId, finalValue);
            AspectsLib.LOGGER.info("      {} : {} * {} / {} = {} -> ceil={} -> max(1)={}", 
                aspectId.getPath(), originalValue, lossFactor, recipeEntry.outputCount, 
                calculation, adjustedValue, finalValue);
        }
        
        AspectsLib.LOGGER.info("    Final combined aspects: {}", combinedAspects);
        
        return new AspectData(combinedAspects);
    }
    
    private void applyCalculatedAspects() {
        int updated = 0;
        
        for (Map.Entry<Identifier, AspectData> entry : calculatedAspects.entrySet()) {
            Identifier itemId = entry.getKey();
            AspectData aspects = entry.getValue();
            
            if (!baseItems.contains(itemId) && aspects != null && !aspects.isEmpty()) {
                ItemAspectRegistry.update(itemId, aspects);
                
                Item item = Registries.ITEM.get(itemId);
                if (item != null && item.getDefaultStack().getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    Identifier blockId = Registries.BLOCK.getId(block);
                    BlockAspectRegistry.update(blockId, aspects);
                }
                
                updated++;
            }
        }
        
        AspectsLib.LOGGER.info("Applied calculated aspects to {} items", updated);
    }
}