package dev.overgrown.aspectslib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.aspectslib.api.AspectsAPI;
import dev.overgrown.aspectslib.data.Aspect;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectModifier;
import dev.overgrown.aspectslib.data.ModRegistries;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.Optional;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public class AspectDebugCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("aspectdebug")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(AspectDebugCommand::showHeldItemAspects)
                .then(CommandManager.literal("item")
                        .executes(AspectDebugCommand::showHeldItemAspects))
                .then(CommandManager.literal("entity")
                        .executes(AspectDebugCommand::showLookingAtEntity))
                .then(CommandManager.literal("block")
                        .executes(AspectDebugCommand::showLookingAtBlock))
                .then(CommandManager.literal("biome")
                        .executes(AspectDebugCommand::showCurrentBiome)));
    }

    private static int showHeldItemAspects(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            source.sendFeedback(() -> Text.literal("You're not holding any item"), false);
            return 0;
        }

        AspectData aspectData = AspectsAPI.getAspectData(heldItem);

        if (aspectData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Item '" + heldItem.getItem().getName().getString() +
                    "' has no aspects"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Aspects for '" + heldItem.getItem().getName().getString() + "':"), false);

        for (Object2IntMap.Entry<Identifier> entry : aspectData.getMap().object2IntEntrySet()) {
            Identifier aspectId = entry.getKey();
            int amount = entry.getIntValue();

            Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
            String aspectName = aspect != null ? aspect.name() : aspectId.toString();

            source.sendFeedback(() -> Text.literal("  " + aspectName + ": " + amount), false);
        }

        return 1;
    }

    private static int showLookingAtEntity(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        Entity targetEntity = getTargetedEntity(player, 20.0);

        if (targetEntity == null) {
            source.sendFeedback(() -> Text.literal("You're not looking at any entity"), false);
            return 0;
        }

        Entity entity = targetEntity;
        Identifier entityId = entity.getType().getRegistryEntry().registryKey().getValue();

        AspectData aspectData = AspectsAPI.getEntityAspectData(entityId);

        if (aspectData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Entity '" + entity.getType().getName().getString() +
                    "' has no aspects"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Aspects for entity '" + entity.getType().getName().getString() + "':"), false);

        for (Object2IntMap.Entry<Identifier> entry : aspectData.getMap().object2IntEntrySet()) {
            Identifier aspectId = entry.getKey();
            int amount = entry.getIntValue();

            Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
            String aspectName = aspect != null ? aspect.name() : aspectId.toString();

            source.sendFeedback(() -> Text.literal("  " + aspectName + ": " + amount), false);
        }

        return 1;
    }

    private static int showLookingAtBlock(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        HitResult hitResult = player.raycast(20.0, 0.0f, false);

        if (!(hitResult instanceof BlockHitResult blockHit)) {
            source.sendFeedback(() -> Text.literal("You're not looking at any block"), false);
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState blockState = player.getWorld().getBlockState(pos);
        Identifier blockId = blockState.getBlock().getRegistryEntry().registryKey().getValue();

        AspectData aspectData = AspectsAPI.getBlockAspectData(blockId);

        if (aspectData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Block '" + blockState.getBlock().getName().getString() +
                    "' has no aspects"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Aspects for block '" + blockState.getBlock().getName().getString() + "':"), false);

        for (Object2IntMap.Entry<Identifier> entry : aspectData.getMap().object2IntEntrySet()) {
            Identifier aspectId = entry.getKey();
            int amount = entry.getIntValue();

            Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
            String aspectName = aspect != null ? aspect.name() : aspectId.toString();

            source.sendFeedback(() -> Text.literal("  " + aspectName + ": " + amount), false);
        }

        return 1;
    }

    private static int showCurrentBiome(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }

        BlockPos pos = player.getBlockPos();
        RegistryKey<Biome> biomeKey = player.getWorld().getBiome(pos).getKey().orElse(null);

        if (biomeKey == null) {
            source.sendFeedback(() -> Text.literal("Could not determine current biome"), false);
            return 0;
        }

        Identifier biomeId = biomeKey.getValue();
        AspectData aspectData = BiomeAspectModifier.getCombinedBiomeAspects(biomeId);

        if (aspectData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Biome '" + biomeKey.getValue().toString() +
                    "' has no aspects"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Aspects for biome '" + biomeKey.getValue().toString() + "':"), false);

        for (Object2IntMap.Entry<Identifier> entry : aspectData.getMap().object2IntEntrySet()) {
            Identifier aspectId = entry.getKey();
            int amount = entry.getIntValue();

            Aspect aspect = ModRegistries.ASPECTS.get(aspectId);
            String aspectName = aspect != null ? aspect.name() : aspectId.toString();

            source.sendFeedback(() -> Text.literal("  " + aspectName + ": " + amount), false);
        }

        return 1;
    }

    private static Entity getTargetedEntity(ServerPlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(maxDistance));

        Box searchBox = player.getBoundingBox().stretch(lookVec.multiply(maxDistance)).expand(1.0);

        double closestDistance = maxDistance;
        Entity closestEntity = null;

        for (Entity entity : player.getWorld().getOtherEntities(player, searchBox)) {
            Box entityBox = entity.getBoundingBox().expand(entity.getTargetingMargin());
            Optional<Vec3d> hitPos = entityBox.raycast(eyePos, endPos);

            if (hitPos.isPresent()) {
                double distance = eyePos.distanceTo(hitPos.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }
}