package dev.overgrown.aspectslib.aether;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages PlayerAetherStorage instances for different worlds
 */
public class PlayerAetherManager extends PersistentState {
    private static final Map<World, PlayerAetherStorage> STORAGES = new HashMap<>();

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

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // This will be handled by the individual PlayerAetherStorage instances
        return nbt;
    }
}