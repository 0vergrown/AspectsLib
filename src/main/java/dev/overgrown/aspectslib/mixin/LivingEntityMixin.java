package dev.overgrown.aspectslib.mixin;

import dev.overgrown.aspectslib.api.IAspectAffinityEntity;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.EntityAspectRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements IAspectAffinityEntity {

    @Unique
    private AspectData aspectslib$originalAspectData = AspectData.DEFAULT;

    @Unique
    @Override
    public AspectData aspectslib$getOriginalAspectData() {
        return this.aspectslib$originalAspectData;
    }

    @Unique
    @Override
    public void aspectslib$setOriginalAspectData(AspectData data) {
        this.aspectslib$originalAspectData = data;
    }

    @Unique
    private static final TrackedData<NbtCompound> ASPECTS_DATA = DataTracker.registerData(LivingEntityMixin.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    @Unique
    private AspectData aspectslib$aspectData = AspectData.DEFAULT;

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onEntityInit(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
        // Initialize from registry only on server
        if (!world.isClient) {
            Identifier entityId = EntityType.getId(entityType);
            AspectData data = EntityAspectRegistry.get(entityId);
            if (data != null) {
                this.aspectslib$setAspectData(data);
                this.aspectslib$setOriginalAspectData(data); // Store original for reference
            }
        }
    }
    
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void aspects_writeOriginalDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        if (!this.aspectslib$originalAspectData.isEmpty()) {
            nbt.put("AspectsLibOriginalData", this.aspectslib$originalAspectData.toNbt());
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void aspects_readOriginalDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("AspectsLibOriginalData", NbtElement.COMPOUND_TYPE)) {
            this.aspectslib$originalAspectData = AspectData.fromNbt(nbt.getCompound("AspectsLibOriginalData"));
        }
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void aspects_initDataTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(ASPECTS_DATA, new NbtCompound());
    }

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void aspects_onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        if (ASPECTS_DATA.equals(data) && this.getWorld().isClient) {
            NbtCompound nbt = this.dataTracker.get(ASPECTS_DATA);
            this.aspectslib$aspectData = AspectData.fromNbt(nbt);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    public void aspects_writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        if (!this.aspectslib$getAspectData().isEmpty()) {
            nbt.put("AspectsLibData", this.aspectslib$getAspectData().toNbt());
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    public void aspects_readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("AspectsLibData", NbtElement.COMPOUND_TYPE)) {
            this.aspectslib$setAspectData(AspectData.fromNbt(nbt.getCompound("AspectsLibData")));
        }
    }

    @Override
    public AspectData aspectslib$getAspectData() {
        return this.aspectslib$aspectData;
    }

    @Override
    public void aspectslib$setAspectData(AspectData data) {
        this.aspectslib$aspectData = data;
        if (!this.getWorld().isClient) {
            this.dataTracker.set(ASPECTS_DATA, data.toNbt());
        }
    }
}