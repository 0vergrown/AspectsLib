package dev.overgrown.aspectslib.aether;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Aether storage for all players on the server
 */
public class PlayerAetherStorage extends PersistentState {
    private final Map<UUID, AetherStorage> playerAether = new HashMap<>();

    public PlayerAetherStorage() {
        // Empty constructor for PersistentState
    }

    public AetherStorage getOrCreateStorage(PlayerEntity player) {
        this.markDirty();
        return playerAether.computeIfAbsent(player.getUuid(), uuid -> new AetherStorage(100.0));
    }

    public AetherStorage getStorage(PlayerEntity player) {
        return playerAether.get(player.getUuid());
    }

    public boolean hasStorage(PlayerEntity player) {
        return playerAether.containsKey(player.getUuid());
    }

    public void removeStorage(PlayerEntity player) {
        playerAether.remove(player.getUuid());
        this.markDirty();
    }

    public void tick() {
        // Natural Aether regeneration
        boolean changed = false;
        for (AetherStorage storage : playerAether.values()) {
            if (!storage.isFull()) {
                storage.replenish(0.1); // 0.1 RU per tick natural regeneration
                changed = true;
            }
        }
        if (changed) {
            this.markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound playersNbt = new NbtCompound();

        for (Map.Entry<UUID, AetherStorage> entry : playerAether.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }

        nbt.put("PlayerAether", playersNbt);
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        playerAether.clear();

        if (nbt.contains("PlayerAether", 10)) {
            NbtCompound playersNbt = nbt.getCompound("PlayerAether");
            for (String uuidStr : playersNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    AetherStorage storage = new AetherStorage();
                    storage.fromNbt(playersNbt.getCompound(uuidStr));
                    playerAether.put(uuid, storage);
                } catch (IllegalArgumentException e) {
                    dev.overgrown.aspectslib.AspectsLib.LOGGER.warn("Invalid UUID in player Aether storage: {}", uuidStr);
                }
            }
        }
    }

    public static PlayerAetherStorage get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                nbt -> {
                    PlayerAetherStorage storage = new PlayerAetherStorage();
                    storage.fromNbt(nbt);
                    return storage;
                },
                PlayerAetherStorage::new,
                "player_aether"
        );
    }
}