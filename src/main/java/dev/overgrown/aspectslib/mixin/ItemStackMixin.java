package dev.overgrown.aspectslib.mixin;

import dev.overgrown.aspectslib.api.IAspectDataProvider;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.ItemAspectRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;

/**
 * Mixin to add aspect data support to ItemStack.
 * <p>
 * Responsibilities:
 * <ol type="1">
 * <li>Attaches AspectData to ItemStack via NBT</li>
 * <li>Handles initialization from tags/registry</li>
 * <li>Manages data copying and cache invalidation</li>
 * </ol>
 * <p>
 * Important Connections:
 * <li>{@link IAspectDataProvider}: Interface implemented by this mixin</li>
 * <li>{@link ItemAspectRegistry}: Source of default item aspects</li>
 * <li>{@link AspectData}: Actual aspect storage</li>
 */

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements IAspectDataProvider {

    @Shadow public abstract Item getItem();
    @Shadow public abstract NbtCompound getOrCreateNbt();
    @Shadow public abstract NbtCompound getNbt();

    @Unique
    private AspectData aspectslib$cachedAspectData = null;

    @Unique
    private boolean aspectslib$aspectDataInitialized = false;

    @Override
    public AspectData aspectslib$getAspectData() {
        if (!aspectslib$aspectDataInitialized) {
            aspectslib$initializeAspectData();
            aspectslib$aspectDataInitialized = true;
        }

        return Objects.requireNonNullElse(aspectslib$cachedAspectData, AspectData.DEFAULT);
    }

    @Override
    public void aspectslib$setAspectData(AspectData data) {
        aspectslib$cachedAspectData = data;
        aspectslib$aspectDataInitialized = true;

        // Handle NBT persistence (Null-safe data handling)
        NbtCompound nbt = getNbt();
        if (data == null) {
            if (nbt != null) {
                nbt.remove("AspectsLibData");
                if (nbt.isEmpty()) {
                    ((ItemStack) (Object) this).setNbt(null);
                }
            }
        } else if (!data.isEmpty()) {
            getOrCreateNbt().put("AspectsLibData", data.toNbt());
        }
    }

    /** Initialize aspect data from NBT or registry defaults */
    @Unique
    private void aspectslib$initializeAspectData() {
        NbtCompound nbt = getNbt();
        if (nbt != null && nbt.contains("AspectsLibData")) {
            aspectslib$cachedAspectData = AspectData.fromNbt(nbt.getCompound("AspectsLibData"));
            return;
        }

        // Build from registry defaults
        AspectData aspectData = new AspectData(new Object2IntOpenHashMap<>());
        Identifier itemId = Registries.ITEM.getId(getItem());
        ItemStack self = (ItemStack) (Object) this;
        boolean hasDirectMatch = false;

        for (Map.Entry<Identifier, AspectData> entry : ItemAspectRegistry.entries()) {
            Identifier id = entry.getKey();
            AspectData itemAspectData = entry.getValue();

            if (itemId.equals(id)) {
                aspectData = aspectData.addAspect(itemAspectData);
                hasDirectMatch = true;
            }
        }

        if (!hasDirectMatch) {
            for (Map.Entry<Identifier, AspectData> entry : ItemAspectRegistry.entries()) {
                Identifier id = entry.getKey();
                AspectData itemAspectData = entry.getValue();

                if (!itemId.equals(id)) {
                    TagKey<Item> tagKey = TagKey.of(Registries.ITEM.getKey(), id);
                    if (self.getRegistryEntry().isIn(tagKey)) {
                        aspectData = aspectData.addAspect(itemAspectData);
                    }
                }
            }
        }

        aspectslib$cachedAspectData = aspectData.isEmpty() ? null : aspectData;
    }

    /** Reset cache when NBT changes */
    @Inject(method = "setNbt", at = @At("RETURN"))
    private void onSetNbt(NbtCompound nbt, CallbackInfo ci) {
        aspectslib$aspectDataInitialized = false;
        aspectslib$cachedAspectData = null;
    }

    /** Copy aspect data when item is copied */
    @Inject(method = "copy", at = @At("RETURN"))
    private void onCopy(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack copy = cir.getReturnValue();
        if (aspectslib$cachedAspectData != null && !aspectslib$cachedAspectData.isEmpty()) {
            IAspectDataProvider copyProvider = (IAspectDataProvider) (Object) copy;
            if (copyProvider != null) {
                copyProvider.aspectslib$setAspectData(aspectslib$cachedAspectData);
            }
        }
    }
}