package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;

/**
 * Temporary dead zone caused by aggressive Aether overharvesting
 */
public class TemporaryDeadZone extends DeadZone {
    private double currentDrain;
    private final double initialDrain;
    private final long recoveryTime; // in ticks (1 day = 24000 ticks)

    public TemporaryDeadZone(double initialDrain, long recoveryTime) {
        super(0.1); // 10% effectiveness in temporary dead zones
        this.currentDrain = initialDrain;
        this.initialDrain = initialDrain;
        this.recoveryTime = recoveryTime;
    }

    public void recover(double amount) {
        currentDrain = Math.max(0, currentDrain - amount);
    }

    public boolean isRecovered() {
        return currentDrain <= 0;
    }

    public double getCurrentDrain() {
        return currentDrain;
    }

    public double getInitialDrain() {
        return initialDrain;
    }

    public long getRecoveryTime() {
        return recoveryTime;
    }

    @Override
    public DeadZoneType getType() {
        return DeadZoneType.TEMPORARY;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", "Temporary");
        nbt.putDouble("CurrentDrain", currentDrain);
        nbt.putDouble("InitialDrain", initialDrain);
        nbt.putLong("RecoveryTime", recoveryTime);
        return nbt;
    }

    public static TemporaryDeadZone fromNbt(NbtCompound nbt) {
        TemporaryDeadZone zone = new TemporaryDeadZone(
                nbt.getDouble("InitialDrain"),
                nbt.getLong("RecoveryTime")
        );
        zone.currentDrain = nbt.getDouble("CurrentDrain");
        return zone;
    }
}