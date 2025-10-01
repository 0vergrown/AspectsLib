package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;

/**
 * Base class for dead zones where magic is diminished or absent
 */
public abstract class DeadZone {
    protected final double aetherMultiplier;

    protected DeadZone(double aetherMultiplier) {
        this.aetherMultiplier = aetherMultiplier;
    }

    public double getAetherMultiplier() {
        return aetherMultiplier;
    }

    public abstract DeadZoneType getType();

    public abstract NbtCompound toNbt();

    public enum DeadZoneType {
        TEMPORARY,
        PERMANENT
    }
}