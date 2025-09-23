package dev.overgrown.aspectslib.entity.aura_node.client;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class AuraNodeVisibilityConfig {
    private static final List<BiPredicate<PlayerEntity, Boolean>> visibilityConditions = new ArrayList<>();
    private static boolean alwaysShow = false;

    public static void addVisibilityCondition(BiPredicate<PlayerEntity, Boolean> condition) {
        visibilityConditions.add(condition);
    }

    public static void setAlwaysShow(boolean alwaysShow) {
        AuraNodeVisibilityConfig.alwaysShow = alwaysShow;
    }

    public static boolean shouldShowNode(@Nullable PlayerEntity player, boolean hasAspects) {
        if (player == null) return false;

        // If alwaysShow is true, show regardless of conditions
        if (alwaysShow) return true;

        // If no conditions are set, use default behavior (low transparency)
        if (visibilityConditions.isEmpty()) return false;

        // Check if any condition matches
        return visibilityConditions.stream().anyMatch(condition -> condition.test(player, hasAspects));
    }
}