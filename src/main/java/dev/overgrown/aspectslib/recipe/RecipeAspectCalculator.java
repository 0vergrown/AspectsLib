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
            
            // Check ItemAspectRegistry (includes tag-based aspects)
            AspectData existingAspects = ItemAspectRegistry.get(itemId);
            
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
            }
        }
        
        AspectsLib.LOGGER.info("Identified {} base items with predefined aspects out of {} checked", 
            baseItems.size(), checked);
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
            return;
        }
        
        ItemStack[] stacks = ingredient.getMatchingStacks();
        if (stacks.length == 0) return;
        
        Identifier bestItemId = null;
        double lowestAspectValue = Double.MAX_VALUE;
        boolean hasAnyAspects = false;
        
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            AspectData existingAspects = getItemAspects(itemId);
            
            if (existingAspects != null && !existingAspects.isEmpty()) {
                hasAnyAspects = true;
                double totalValue = existingAspects.calculateTotalRU();
                
                if (totalValue < lowestAspectValue) {
                    lowestAspectValue = totalValue;
                    bestItemId = itemId;
                }
            } else {
                if (!hasAnyAspects && bestItemId == null) {
                    bestItemId = itemId;
                }
            }
        }
        
        if (bestItemId != null) {
            ids.add(bestItemId);
            counts.merge(bestItemId, 1, Integer::sum);
        } else if (stacks.length > 0 && stacks[0] != null && !stacks[0].isEmpty()) {
            Identifier itemId = Registries.ITEM.getId(stacks[0].getItem());
            ids.add(itemId);
            counts.merge(itemId, 1, Integer::sum);
        }
    }
    
    private AspectData getItemAspects(Identifier itemId) {
        // Check cache first
        AspectData cached = calculatedAspects.get(itemId);
        if (cached != null) {
            return cached;
        }
        
        // Look up using the method that checks both direct and tag-based mappings
        AspectData registryAspects = ItemAspectRegistry.get(itemId);
        if (registryAspects != null && !registryAspects.isEmpty()) {
            return registryAspects;
        }
        
        // Also check block registry in case it's a block item
        Item item = Registries.ITEM.get(itemId);
        if (item instanceof net.minecraft.item.BlockItem blockItem) {
            Block block = blockItem.getBlock();
            Identifier blockId = Registries.BLOCK.getId(block);
            AspectData blockAspects = BlockAspectRegistry.get(blockId);
            
            if (blockAspects != null && !blockAspects.isEmpty()) {
                return blockAspects;
            }
        }
        
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
            return AspectData.DEFAULT;
        }
        
        processingItems.add(itemId);
        
        RecipeNode node = recipeGraph.get(itemId);
        if (node == null || node.recipes.isEmpty()) {
            processingItems.remove(itemId);
            return AspectData.DEFAULT;
        }
        
        AspectData bestAspects = null;
        double bestValue = Double.MAX_VALUE;
        RecipeEntry bestRecipe = null;
        
        for (RecipeEntry recipeEntry : node.recipes) {
            AspectData recipeAspects = calculateRecipeAspects(recipeEntry);
            if (recipeAspects != null && !recipeAspects.isEmpty()) {
                double totalValue = recipeAspects.calculateTotalRU();
                
                if (bestAspects == null || totalValue < bestValue) {
                    bestAspects = recipeAspects;
                    bestValue = totalValue;
                    bestRecipe = recipeEntry;
                }
            }
        }
        
        if (bestAspects != null) {
            calculatedAspects.put(itemId, bestAspects);
            node.cachedAspects = bestAspects;
        }
        
        processingItems.remove(itemId);
        return bestAspects != null ? bestAspects : AspectData.DEFAULT;
    }
    
    private AspectData calculateRecipeAspects(RecipeEntry recipeEntry) {
        Object2IntOpenHashMap<Identifier> combinedAspects = new Object2IntOpenHashMap<>();
        
        for (Map.Entry<Identifier, Integer> ingredient : recipeEntry.ingredientCounts.entrySet()) {
            Identifier ingredientId = ingredient.getKey();
            int count = ingredient.getValue();
            
            AspectData ingredientAspects = calculatedAspects.get(ingredientId);
            if (ingredientAspects == null) {
                ingredientAspects = getItemAspects(ingredientId);
            }
            
            if (ingredientAspects != null && !ingredientAspects.isEmpty()) {
                for (Map.Entry<Identifier, Integer> aspectEntry : ingredientAspects.getMap().entrySet()) {
                    int totalAmount = aspectEntry.getValue() * count;
                    combinedAspects.merge(aspectEntry.getKey(), totalAmount, Integer::sum);
                }
            }
        }
        
        if (combinedAspects.isEmpty()) {
            return AspectData.DEFAULT;
        }
        
        double lossFactor;
        if (recipeEntry.type == RecipeType.CRAFTING) {
            lossFactor = config.getCraftingLoss();
        } else if (recipeEntry.type == RecipeType.SMELTING || 
                   recipeEntry.type == RecipeType.BLASTING || 
                   recipeEntry.type == RecipeType.SMOKING ||
                   recipeEntry.type == RecipeType.CAMPFIRE_COOKING) {
            lossFactor = config.getSmeltingLoss();
        } else if (recipeEntry.type == RecipeType.SMITHING) {
            lossFactor = config.getSmithingLoss();
        } else if (recipeEntry.type == RecipeType.STONECUTTING) {
            lossFactor = config.getStonecuttingLoss();
        } else {
            lossFactor = config.getCraftingLoss();
        }
        
        for (Identifier aspectId : combinedAspects.keySet()) {
            int originalValue = combinedAspects.getInt(aspectId);
            double calculation = originalValue * lossFactor / recipeEntry.outputCount;
            int adjustedValue = (int) Math.ceil(calculation);
            int finalValue = Math.max(1, adjustedValue);
            combinedAspects.put(aspectId, finalValue);
        }
        
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