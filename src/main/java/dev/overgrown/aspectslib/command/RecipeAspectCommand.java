package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.recipe.RecipeAspectManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class RecipeAspectCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("aspectslib")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("recipe")
                .then(CommandManager.literal("recalculate")
                    .executes(RecipeAspectCommand::recalculate))
                .then(CommandManager.literal("enable")
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(RecipeAspectCommand::setEnabled)))
            )
        );
    }
    
    private static int recalculate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("Starting recipe aspect recalculation..."), true);
        
        RecipeAspectManager manager = RecipeAspectManager.getInstance();
        manager.setServer(source.getServer());
        manager.recalculateAspects();
        
        return 1;
    }
    
    private static int setEnabled(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        
        RecipeAspectManager manager = RecipeAspectManager.getInstance();
        manager.setEnabled(enabled);
        
        source.sendFeedback(() -> Text.literal("Recipe aspect calculation " + 
            (enabled ? "enabled" : "disabled")), true);
        
        return 1;
    }
}