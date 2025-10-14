package dev.overgrown.aspectslib.aether;

import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;

public class AetherChunkData {
    private final World world;
    private final ChunkPos chunkPos;
    private final Map<Identifier, Integer> currentAether;
    private final Map<Identifier, Integer> maxAether;
    private long lastRecoveryTime;
    private double totalExpendedThisHour;
    private long hourStartTime;
    private boolean initialized = false;

    public AetherChunkData(World world, ChunkPos chunkPos) {
        this.world = world;
        this.chunkPos = chunkPos;
        this.currentAether = new HashMap<>();
        this.maxAether = new HashMap<>();
        this.lastRecoveryTime = world.getTime();
        this.hourStartTime = world.getTime();
        this.totalExpendedThisHour = 0;

        initializeFromBiome();
    }

    private AetherChunkData(World world, ChunkPos chunkPos, Map<Identifier, Integer> currentAether,
                            Map<Identifier, Integer> maxAether, long lastRecoveryTime,
                            double totalExpendedThisHour, long hourStartTime) {
        this.world = world;
        this.chunkPos = chunkPos;
        this.currentAether = currentAether;
        this.maxAether = maxAether;
        this.lastRecoveryTime = lastRecoveryTime;
        this.totalExpendedThisHour = totalExpendedThisHour;
        this.hourStartTime = hourStartTime;
        this.initialized = true;
    }

    private void initializeFromBiome() {
        if (initialized) return;

        // Sample multiple points in the chunk for better biome representation
        int[] sampleX = {chunkPos.getStartX() + 4, chunkPos.getStartX() + 8, chunkPos.getStartX() + 12};
        int[] sampleZ = {chunkPos.getStartZ() + 4, chunkPos.getStartZ() + 8, chunkPos.getStartZ() + 12};

        Map<Identifier, Integer> biomeAspectTotals = new HashMap<>();
        int sampleCount = 0;

        for (int x : sampleX) {
            for (int z : sampleZ) {
                BlockPos samplePos = new BlockPos(x, 64, z);
                RegistryEntry<Biome> biomeEntry = world.getBiome(samplePos);

                biomeEntry.getKey().ifPresent(biomeKey -> {
                    AspectData biomeAspects = BiomeAspectRegistry.get(biomeKey);
                    if (!biomeAspects.isEmpty()) {
                        for (Map.Entry<Identifier, Integer> entry : biomeAspects.getMap().entrySet()) {
                            biomeAspectTotals.merge(entry.getKey(), entry.getValue(), Integer::sum);
                        }
                    }
                });
                sampleCount++;
            }
        }

        // Calculate average and set capacities
        for (Map.Entry<Identifier, Integer> entry : biomeAspectTotals.entrySet()) {
            int averageDensity = entry.getValue() / sampleCount;
            int capacity = averageDensity * 16 * 16 * 256; // RU/m³ * chunk volume
            maxAether.put(entry.getKey(), capacity);
            currentAether.put(entry.getKey(), capacity);
        }

        initialized = true;
    }

    public boolean canHarvest(Identifier aspectId, int amount) {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            return false;
        }

        Integer current = currentAether.get(aspectId);
        return current != null && current >= amount;
    }

    public boolean harvestAether(Identifier aspectId, int amount) {
        if (!canHarvest(aspectId, amount)) {
            return false;
        }

        Integer current = currentAether.get(aspectId);
        if (current != null) {
            currentAether.put(aspectId, current - amount);
            totalExpendedThisHour += amount;
            checkForDeadZone();
            return true;
        }
        return false;
    }

    private void checkForDeadZone() {
        long currentTime = world.getTime();

        // Reset hourly counter if needed
        if (currentTime - hourStartTime > 72000) { // 1 hour in ticks
            totalExpendedThisHour = 0;
            hourStartTime = currentTime;
        }

        // Check for permanent dead zone condition
        if (totalExpendedThisHour > AetherManager.getPermanentDeadZoneThreshold(world)) {
            AetherManager.markAsDeadZone(world, chunkPos, new DeadZoneData(true, world.getTime()));
            return;
        }

        // Check for temporary dead zone condition (all aspects severely depleted)
        boolean severelyDepleted = true;
        for (Map.Entry<Identifier, Integer> entry : currentAether.entrySet()) {
            int max = maxAether.getOrDefault(entry.getKey(), 0);
            if (max > 0 && entry.getValue() > max * 0.1) { // Less than 90% depleted
                severelyDepleted = false;
                break;
            }
        }

        if (severelyDepleted) {
            AetherManager.markAsDeadZone(world, chunkPos, new DeadZoneData(false, world.getTime()));
        }
    }

    public void recoverAether() {
        if (AetherManager.isDeadZone(world, chunkPos)) {
            recoverDeadZone();
            return;
        }

        long currentTime = world.getTime();
        long timeSinceLastRecovery = currentTime - lastRecoveryTime;

        // Recover based on configured rate
        double recoveryRate = AetherManager.getRecoveryRate(world);
        if (timeSinceLastRecovery >= 24000) { // One day in ticks
            int recoveryCycles = (int) (timeSinceLastRecovery / 24000);

            for (Map.Entry<Identifier, Integer> entry : maxAether.entrySet()) {
                Identifier aspectId = entry.getKey();
                int max = entry.getValue();
                int current = currentAether.getOrDefault(aspectId, 0);

                if (current < max) {
                    int recoveryAmount = (int) (recoveryRate * recoveryCycles);
                    currentAether.put(aspectId, Math.min(max, current + recoveryAmount));
                }
            }
            lastRecoveryTime = currentTime - (timeSinceLastRecovery % 24000);
        }
    }

    private void recoverDeadZone() {
        DeadZoneData deadZoneData = AetherManager.getDeadZoneData(world, chunkPos);
        if (deadZoneData == null || deadZoneData.isPermanent()) {
            return;
        }

        long currentTime = world.getTime();
        long timeSinceLastRecovery = currentTime - lastRecoveryTime;

        if (timeSinceLastRecovery >= 24000) {
            int recoveryCycles = (int) (timeSinceLastRecovery / 24000);
            double recoveryAmount = AetherManager.getRecoveryRate(world) * recoveryCycles;

            boolean sufficientlyRecovered = true;

            for (Map.Entry<Identifier, Integer> entry : maxAether.entrySet()) {
                Identifier aspectId = entry.getKey();
                int max = entry.getValue();
                int current = currentAether.getOrDefault(aspectId, 0);

                if (current < max) {
                    int newAmount = Math.min(max, current + (int) recoveryAmount);
                    currentAether.put(aspectId, newAmount);

                    // Check if the recovery threshold was reached
                    if (newAmount < max * AetherManager.getTemporaryDeadZoneRecoveryThreshold(world)) {
                        sufficientlyRecovered = false;
                    }
                }
            }

            lastRecoveryTime = currentTime - (timeSinceLastRecovery % 24000);

            if (sufficientlyRecovered) {
                AetherManager.removeDeadZone(world, chunkPos);
            }
        }
    }

    public int getCurrentAether(Identifier aspectId) {
        return currentAether.getOrDefault(aspectId, 0);
    }

    public int getMaxAether(Identifier aspectId) {
        return maxAether.getOrDefault(aspectId, 0);
    }

    public double getAetherPercentage(Identifier aspectId) {
        int max = getMaxAether(aspectId);
        if (max == 0) return 0;
        return (double) getCurrentAether(aspectId) / max;
    }

    public boolean isEmpty() {
        return currentAether.isEmpty() && maxAether.isEmpty();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        // Save current Aether
        NbtList currentList = new NbtList();
        for (Map.Entry<Identifier, Integer> entry : currentAether.entrySet()) {
            NbtCompound aspectNbt = new NbtCompound();
            aspectNbt.putString("Aspect", entry.getKey().toString());
            aspectNbt.putInt("Amount", entry.getValue());
            currentList.add(aspectNbt);
        }
        nbt.put("CurrentAether", currentList);

        // Save max Aether
        NbtList maxList = new NbtList();
        for (Map.Entry<Identifier, Integer> entry : maxAether.entrySet()) {
            NbtCompound aspectNbt = new NbtCompound();
            aspectNbt.putString("Aspect", entry.getKey().toString());
            aspectNbt.putInt("Amount", entry.getValue());
            maxList.add(aspectNbt);
        }
        nbt.put("MaxAether", maxList);

        nbt.putLong("LastRecoveryTime", lastRecoveryTime);
        nbt.putDouble("TotalExpendedThisHour", totalExpendedThisHour);
        nbt.putLong("HourStartTime", hourStartTime);
        nbt.putBoolean("Initialized", initialized);

        return nbt;
    }

    public static AetherChunkData fromNbt(NbtCompound nbt) {
        Map<Identifier, Integer> currentAether = new HashMap<>();
        Map<Identifier, Integer> maxAether = new HashMap<>();

        // Load current Aether
        if (nbt.contains("CurrentAether", NbtElement.LIST_TYPE)) {
            NbtList currentList = nbt.getList("CurrentAether", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < currentList.size(); i++) {
                NbtCompound aspectNbt = currentList.getCompound(i);
                Identifier aspectId = new Identifier(aspectNbt.getString("Aspect"));
                int amount = aspectNbt.getInt("Amount");
                currentAether.put(aspectId, amount);
            }
        }

        // Load max Aether
        if (nbt.contains("MaxAether", NbtElement.LIST_TYPE)) {
            NbtList maxList = nbt.getList("MaxAether", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < maxList.size(); i++) {
                NbtCompound aspectNbt = maxList.getCompound(i);
                Identifier aspectId = new Identifier(aspectNbt.getString("Aspect"));
                int amount = aspectNbt.getInt("Amount");
                maxAether.put(aspectId, amount);
            }
        }

        return new AetherChunkData(
                null, // World will be set when needed
                new ChunkPos(0, 0), // Position will be set by caller
                currentAether,
                maxAether,
                nbt.getLong("LastRecoveryTime"),
                nbt.getDouble("TotalExpendedThisHour"),
                nbt.getLong("HourStartTime")
        );
    }
}