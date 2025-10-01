package dev.overgrown.aspectslib.aether;

import dev.overgrown.aspectslib.AspectsLib;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages dead zones (both temporary and permanent) in the world
 */
public class DeadZoneManager extends PersistentState {
    private static final String PERSISTENT_ID = "aspectslib_dead_zones";

    private final Map<ChunkPos, DeadZone> deadZones = new HashMap<>();
    private final Map<ChunkPos, Long> temporaryRecoveryTimers = new HashMap<>();
    private final World world;

    public DeadZoneManager(World world) {
        this.world = world;
    }

    public static DeadZoneManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                nbt -> fromNbt(world, nbt),
                () -> new DeadZoneManager(world),
                PERSISTENT_ID
        );
    }

    public static DeadZoneManager fromNbt(World world, NbtCompound nbt) {
        DeadZoneManager manager = new DeadZoneManager(world);

        // Load temporary dead zones
        if (nbt.contains("TemporaryDeadZones", 9)) {
            NbtList tempList = nbt.getList("TemporaryDeadZones", 10);
            for (int i = 0; i < tempList.size(); i++) {
                NbtCompound zoneNbt = tempList.getCompound(i);
                ChunkPos pos = new ChunkPos(zoneNbt.getLong("Pos"));
                TemporaryDeadZone zone = TemporaryDeadZone.fromNbt(zoneNbt);
                manager.deadZones.put(pos, zone);
                manager.temporaryRecoveryTimers.put(pos, world.getTime() + zone.getRecoveryTime());
            }
        }

        // Load permanent dead zones
        if (nbt.contains("PermanentDeadZones", 9)) {
            NbtList permList = nbt.getList("PermanentDeadZones", 9);
            for (int i = 0; i < permList.size(); i++) {
                NbtCompound zoneNbt = permList.getCompound(i);
                ChunkPos pos = new ChunkPos(zoneNbt.getLong("Pos"));
                PermanentDeadZone zone = PermanentDeadZone.fromNbt(zoneNbt);
                manager.deadZones.put(pos, zone);
            }
        }

        return manager;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Save temporary dead zones
        NbtList tempList = new NbtList();
        for (Map.Entry<ChunkPos, DeadZone> entry : deadZones.entrySet()) {
            if (entry.getValue() instanceof TemporaryDeadZone tempZone) {
                NbtCompound zoneNbt = tempZone.toNbt();
                zoneNbt.putLong("Pos", entry.getKey().toLong());
                tempList.add(zoneNbt);
            }
        }
        nbt.put("TemporaryDeadZones", tempList);

        // Save permanent dead zones
        NbtList permList = new NbtList();
        for (Map.Entry<ChunkPos, DeadZone> entry : deadZones.entrySet()) {
            if (entry.getValue() instanceof PermanentDeadZone permZone) {
                NbtCompound zoneNbt = permZone.toNbt();
                zoneNbt.putLong("Pos", entry.getKey().toLong());
                permList.add(zoneNbt);
            }
        }
        nbt.put("PermanentDeadZones", permList);

        return nbt;
    }

    public void tick() {
        long currentTime = world.getTime();

        // Check for temporary zone recovery
        temporaryRecoveryTimers.entrySet().removeIf(entry -> {
            if (currentTime >= entry.getValue()) {
                ChunkPos pos = entry.getKey();
                DeadZone zone = deadZones.get(pos);
                if (zone instanceof TemporaryDeadZone tempZone) {
                    tempZone.recover(1.0); // Recover 1 RU per day
                    if (tempZone.isRecovered()) {
                        deadZones.remove(pos);
                        AspectsLib.LOGGER.info("Temporary dead zone recovered at {}", pos);
                        markDirty();
                        return true;
                    }
                    markDirty();
                }
            }
            return false;
        });
    }

    public void addTemporaryDeadZone(ChunkPos pos, double initialDrain, long recoveryTime) {
        TemporaryDeadZone zone = new TemporaryDeadZone(initialDrain, recoveryTime);
        deadZones.put(pos, zone);
        temporaryRecoveryTimers.put(pos, world.getTime() + recoveryTime);
        markDirty();

        AspectsLib.LOGGER.info("Created temporary dead zone at {} with {} RU drain", pos, initialDrain);
    }

    public void addPermanentDeadZone(ChunkPos pos) {
        PermanentDeadZone zone = new PermanentDeadZone();
        deadZones.put(pos, zone);
        markDirty();

        AspectsLib.LOGGER.info("Created permanent dead zone at {}", pos);
    }

    public boolean isDeadZone(ChunkPos pos) {
        return deadZones.containsKey(pos);
    }

    public DeadZone getDeadZone(ChunkPos pos) {
        return deadZones.get(pos);
    }

    public double getAetherMultiplier(ChunkPos pos) {
        DeadZone zone = deadZones.get(pos);
        return zone != null ? zone.getAetherMultiplier() : 1.0;
    }

    public Map<ChunkPos, DeadZone> getDeadZones() {
        return new HashMap<>(deadZones);
    }
}