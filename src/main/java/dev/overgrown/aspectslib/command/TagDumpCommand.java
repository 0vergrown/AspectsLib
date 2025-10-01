package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.AspectsLib;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

public class TagDumpCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("tagdump")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(TagDumpCommand::dumpAllTags)
                .then(CommandManager.literal("item")
                        .executes(TagDumpCommand::dumpItemTags)));
    }
    
    private static int dumpAllTags(CommandContext<ServerCommandSource> context) {
        return dumpItemTags(context);
    }
    
    private static int dumpItemTags(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Collect all unique tags from all items
        Set<Identifier> allTags = new HashSet<>();
        Map<Identifier, List<Item>> tagToItems = new HashMap<>();
        
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            
            // Get all tags for this item
            item.getRegistryEntry().streamTags().forEach(tagKey -> {
                Identifier tagId = tagKey.id();
                allTags.add(tagId);
                tagToItems.computeIfAbsent(tagId, k -> new ArrayList<>()).add(item);
            });
        }
        
        // Sort tags by namespace and path
        List<Identifier> sortedTags = allTags.stream()
                .sorted(Comparator.comparing(Identifier::getNamespace)
                        .thenComparing(Identifier::getPath))
                .collect(Collectors.toList());
        
        source.sendFeedback(() -> Text.literal("§a=== Item Tags Dump ==="), false);
        source.sendFeedback(() -> Text.literal("§eFound " + sortedTags.size() + " unique item tags"), false);
        
        // Group by namespace
        Map<String, List<Identifier>> byNamespace = sortedTags.stream()
                .collect(Collectors.groupingBy(Identifier::getNamespace));
        
        for (Map.Entry<String, List<Identifier>> namespaceEntry : byNamespace.entrySet()) {
            String namespace = namespaceEntry.getKey();
            List<Identifier> tags = namespaceEntry.getValue();
            
            source.sendFeedback(() -> Text.literal("§6[" + namespace + "] (" + tags.size() + " tags):"), false);
            
            for (Identifier tagId : tags) {
                List<Item> items = tagToItems.get(tagId);
                String tagPath = tagId.getPath();
                int itemCount = items != null ? items.size() : 0;
                
                // Show first 3 example items
                String examples = "";
                if (items != null && !items.isEmpty()) {
                    examples = items.stream()
                            .limit(3)
                            .map(item -> Registries.ITEM.getId(item).getPath())
                            .collect(Collectors.joining(", "));
                    if (items.size() > 3) {
                        examples += ", ...";
                    }
                }
                
                String finalExamples = examples;
                source.sendFeedback(() -> Text.literal("  §7- " + tagPath + " §8(" + itemCount + " items) §3" + finalExamples), false);
            }
        }
        
        // Also log to console
        AspectsLib.LOGGER.info("=== Item Tags Dump ===");
        AspectsLib.LOGGER.info("Found {} unique item tags", sortedTags.size());
        
        for (Map.Entry<String, List<Identifier>> namespaceEntry : byNamespace.entrySet()) {
            String namespace = namespaceEntry.getKey();
            List<Identifier> tags = namespaceEntry.getValue();
            
            AspectsLib.LOGGER.info("[{}] ({} tags):", namespace, tags.size());
            
            for (Identifier tagId : tags) {
                List<Item> items = tagToItems.get(tagId);
                int itemCount = items != null ? items.size() : 0;
                AspectsLib.LOGGER.info("  - {} ({} items)", tagId, itemCount);
            }
        }
        
        return 1;
    }
}