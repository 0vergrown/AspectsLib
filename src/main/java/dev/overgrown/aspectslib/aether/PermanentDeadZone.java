package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;

/**
 * Permanent dead zone caused by catastrophic Aether collapse
 */
public class PermanentDeadZone extends DeadZone {
    public PermanentDeadZone() {
        super(0.0); // 0% effectiveness in permanent dead zones
    }

    @Override
    public DeadZoneType getType() {
        return DeadZoneType.PERMANENT;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", "Permanent");
        return nbt;
    }

    public static PermanentDeadZone fromNbt(NbtCompound nbt) {
        return new PermanentDeadZone();
    }
}