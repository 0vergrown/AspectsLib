package dev.overgrown.aspectslib.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class AspectsTooltipConfig {
    private static final List<BiPredicate<ItemStack, PlayerEntity>> visibilityConditions = new ArrayList<>();
    private static boolean alwaysShow = true;

    static {
        visibilityConditions.add((stack, player) -> alwaysShow);
    }

    public static void addVisibilityCondition(BiPredicate<ItemStack, PlayerEntity> condition) {
        visibilityConditions.add(condition);
    }

    public static void setAlwaysShow(boolean alwaysShow) {
        AspectsTooltipConfig.alwaysShow = alwaysShow;
    }

    public static boolean shouldShowTooltip(ItemStack stack, @Nullable PlayerEntity player) {
        return visibilityConditions.stream().anyMatch(condition -> condition.test(stack, player));
    }
}