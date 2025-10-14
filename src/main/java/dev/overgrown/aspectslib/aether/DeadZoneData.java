package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;

public class DeadZoneData {
    private final boolean permanent;
    private final long creationTime;

    public DeadZoneData(boolean permanent, long creationTime) {
        this.permanent = permanent;
        this.creationTime = creationTime;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Permanent", permanent);
        nbt.putLong("CreationTime", creationTime);
        return nbt;
    }

    public static DeadZoneData fromNbt(NbtCompound nbt) {
        return new DeadZoneData(
                nbt.getBoolean("Permanent"),
                nbt.getLong("CreationTime")
        );
    }
}