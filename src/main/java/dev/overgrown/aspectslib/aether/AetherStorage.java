package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents Aether storage for a player, entity, or location
 */
public class AetherStorage {
    private double currentAether;
    private double maxAether;
    private final Map<String, Object> properties;

    public AetherStorage() {
        this(100.0, 100.0);
    }

    public AetherStorage(double maxAether) {
        this(0.0, maxAether);
    }

    public AetherStorage(double currentAether, double maxAether) {
        this.currentAether = Math.min(currentAether, maxAether);
        this.maxAether = maxAether;
        this.properties = new HashMap<>();
    }

    // Basic getters and setters
    public double getCurrentAether() { return currentAether; }
    public double getMaxAether() { return maxAether; }

    public void setCurrentAether(double amount) {
        this.currentAether = Math.max(0, Math.min(amount, maxAether));
    }

    public void setMaxAether(double maxAether) {
        this.maxAether = Math.max(0, maxAether);
        this.currentAether = Math.min(this.currentAether, maxAether);
    }

    // Aether manipulation
    public boolean consume(double amount) {
        if (amount <= 0) return true;
        if (currentAether >= amount) {
            currentAether -= amount;
            return true;
        }
        return false;
    }

    public boolean canConsume(double amount) {
        return currentAether >= amount;
    }

    public void replenish(double amount) {
        if (amount > 0) {
            currentAether = Math.min(currentAether + amount, maxAether);
        }
    }

    public void replenishFull() {
        currentAether = maxAether;
    }

    public void drainFull() {
        currentAether = 0;
    }

    public double getFillPercentage() {
        return maxAether > 0 ? currentAether / maxAether : 0;
    }

    public boolean isEmpty() {
        return currentAether <= 0;
    }

    public boolean isFull() {
        return currentAether >= maxAether;
    }

    // Property management for mod extensions
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    // NBT serialization
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putDouble("CurrentAether", currentAether);
        nbt.putDouble("MaxAether", maxAether);

        if (!properties.isEmpty()) {
            NbtCompound propsNbt = new NbtCompound();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() instanceof String s) propsNbt.putString(entry.getKey(), s);
                else if (entry.getValue() instanceof Integer i) propsNbt.putInt(entry.getKey(), i);
                else if (entry.getValue() instanceof Double d) propsNbt.putDouble(entry.getKey(), d);
                else if (entry.getValue() instanceof Boolean b) propsNbt.putBoolean(entry.getKey(), b);
            }
            nbt.put("Properties", propsNbt);
        }

        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        this.currentAether = nbt.getDouble("CurrentAether");
        this.maxAether = nbt.getDouble("MaxAether");

        if (nbt.contains("Properties", 10)) {
            NbtCompound propsNbt = nbt.getCompound("Properties");
            for (String key : propsNbt.getKeys()) {
                switch (propsNbt.getType(key)) {
                    case NbtCompound.STRING_TYPE -> properties.put(key, propsNbt.getString(key));
                    case NbtCompound.INT_TYPE -> properties.put(key, propsNbt.getInt(key));
                    case NbtCompound.DOUBLE_TYPE -> properties.put(key, propsNbt.getDouble(key));
                    case NbtCompound.BYTE_TYPE -> properties.put(key, propsNbt.getBoolean(key));
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format("AetherStorage{%.1f/%.1f RU}", currentAether, maxAether);
    }
}