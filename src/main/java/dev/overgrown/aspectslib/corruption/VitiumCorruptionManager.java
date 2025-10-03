package dev.overgrown.aspectslib.corruption;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import dev.overgrown.aspectslib.aether.DeadZoneManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.entity.SculkSpreadManager;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VitiumCorruptionManager extends PersistentState {
    private static final int CORRUPTION_TARGET_COUNT = 3;
    private static final Identifier VITIUM_ASPECT = AspectsLib.identifier("vitium");
    private static final Set<Identifier> SCULK_BLOCKS = Set.of(
            new Identifier("minecraft", "sculk"),
            new Identifier("minecraft", "sculk_catalyst"),
            new Identifier("minecraft", "sculk_vein"),
            new Identifier("minecraft", "sculk_sensor"),
            new Identifier("minecraft", "sculk_shrieker")
    );

    private final ServerWorld world;
    private final Map<RegistryKey<Biome>, CorruptionData> corruptionData = new HashMap<>();
    private final Set<ChunkPos> activeCorruptionChunks = new HashSet<>();
    private int tickCounter = 0;

    public VitiumCorruptionManager(ServerWorld world) {
        this.world = world;
    }

    public static VitiumCorruptionManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                nbt -> fromNbt(world, nbt),
                () -> new VitiumCorruptionManager(world),
                "aspectslib_vitium_corruption"
        );
    }

    public void tick() {
        tickCounter++;

        // Process corruption every 5 seconds (100 ticks)
        if (tickCounter % 100 == 0) {
            processCorruption();
            tickCounter = 0;
        }
    }

    private void processCorruption() {
        // Get all biomes that need processing
        Set<RegistryKey<Biome>> biomesToProcess = new HashSet<>();

        for (Map.Entry<RegistryKey<Biome>, CorruptionData> entry : corruptionData.entrySet()) {
            RegistryKey<Biome> biomeKey = entry.getKey();
            CorruptionData data = entry.getValue();

            if (shouldProcessBiome(biomeKey, data)) {
                biomesToProcess.add(biomeKey);
            }
        }

        // Process each corrupted biome
        for (RegistryKey<Biome> biomeKey : biomesToProcess) {
            processBiomeCorruption(biomeKey, corruptionData.get(biomeKey));
        }

        // Spread corruption to adjacent chunks
        spreadCorruption();
    }

    private boolean shouldProcessBiome(RegistryKey<Biome> biomeKey, CorruptionData data) {
        AspectData biomeAspects = BiomeAspectRegistry.get(biomeKey);
        if (biomeAspects.isEmpty()) return false;

        int vitiumAmount = biomeAspects.getLevel(VITIUM_ASPECT);
        int totalOtherAspects = calculateTotalOtherAspects(biomeAspects);

        return vitiumAmount > totalOtherAspects;
    }

    private int calculateTotalOtherAspects(AspectData aspects) {
        int total = 0;
        for (Identifier aspectId : aspects.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ASPECT)) {
                total += aspects.getLevel(aspectId);
            }
        }
        return total;
    }

    private void processBiomeCorruption(RegistryKey<Biome> biomeKey, CorruptionData data) {
        AspectData currentAspects = BiomeAspectRegistry.get(biomeKey);
        int vitiumAmount = currentAspects.getLevel(VITIUM_ASPECT);

        AspectsLib.LOGGER.debug("Processing corruption for biome {} with Vitium: {}", biomeKey.getValue(), vitiumAmount);

        // Get random positions in the biome to corrupt
        List<BlockPos> corruptionTargets = findCorruptionTargets(biomeKey, data, CORRUPTION_TARGET_COUNT); // Reduced from 5 to 3 for performance

        int successfulCorruptions = 0;
        for (BlockPos pos : corruptionTargets) {
            if (world.getBiome(pos).getKey().orElse(null) == biomeKey) {
                if (placeSculkBlock(pos)) {
                    successfulCorruptions++;
                    // Update aspects: +1 Vitium
                    updateBiomeAspects(biomeKey, vitiumAmount + successfulCorruptions);

                    AspectsLib.LOGGER.debug("Successfully corrupted block at {} in biome {}", pos, biomeKey.getValue());
                }
            }
        }

        if (successfulCorruptions > 0) {
            AspectsLib.LOGGER.info("Corrupted {} blocks in biome {}", successfulCorruptions, biomeKey.getValue());
        }

        // Check if only Vitium remains
        if (isOnlyVitiumRemaining(biomeKey)) {
            depleteAether(biomeKey, data);
        }
    }

    private List<BlockPos> findCorruptionTargets(RegistryKey<Biome> biomeKey, CorruptionData data, int count) {
        List<BlockPos> targets = new ArrayList<>();
        net.minecraft.util.math.random.Random random = world.getRandom();

        // Get existing corrupted chunks or find new ones
        Set<ChunkPos> chunksToSearch = data.getCorruptedChunks();
        if (chunksToSearch.isEmpty()) {
            // Find some initial chunks in this biome
            chunksToSearch = findChunksInBiome(biomeKey, data, 3);
            data.getCorruptedChunks().addAll(chunksToSearch);
            this.markDirty();
        }

        for (ChunkPos chunkPos : chunksToSearch) {
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                continue;
            }

            // Try multiple positions in the chunk
            for (int i = 0; i < 5; i++) {
                int x = chunkPos.getStartX() + random.nextInt(16);
                int z = chunkPos.getStartZ() + random.nextInt(16);

                // Try to find a valid Y position
                for (int yAttempt = 0; yAttempt < 3; yAttempt++) {
                    int y;
                    if (yAttempt == 0) {
                        // First try: surface level
                        y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                    } else if (yAttempt == 1) {
                        // Second try: a bit below surface
                        y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) - 1 - random.nextInt(3);
                    } else {
                        // Third try: random position in the chunk
                        y = world.getBottomY() + random.nextInt(world.getHeight());
                    }

                    BlockPos pos = new BlockPos(x, y, z);

                    // Verify the position is in the correct biome
                    if (world.getBiome(pos).getKey().orElse(null) == biomeKey &&
                            isValidCorruptionTarget(pos)) {
                        targets.add(pos);
                        if (targets.size() >= count) return targets;
                        break; // Found a valid position in this attempt
                    }
                }
            }
            if (targets.size() >= count) break;
        }

        return targets;
    }

    private Set<ChunkPos> findChunksInBiome(RegistryKey<Biome> biomeKey, CorruptionData data, int count) {
        Set<ChunkPos> chunks = new HashSet<>();
        net.minecraft.util.math.random.Random random = world.getRandom();

        // Search around existing corrupted chunks first
        for (ChunkPos existing : data.getCorruptedChunks()) {
            for (int i = 0; i < 2; i++) {
                int offsetX = random.nextInt(3) - 1;
                int offsetZ = random.nextInt(3) - 1;
                ChunkPos newChunk = new ChunkPos(existing.x + offsetX, existing.z + offsetZ);

                if (isChunkInBiome(newChunk, biomeKey)) {
                    chunks.add(newChunk);
                    if (chunks.size() >= count) return chunks;
                }
            }
        }

        return chunks;
    }

    private boolean isChunkInBiome(ChunkPos chunkPos, RegistryKey<Biome> biomeKey) {
        BlockPos center = chunkPos.getCenterAtY(64);
        return world.getBiome(center).getKey().orElse(null) == biomeKey;
    }

    private boolean isValidCorruptionTarget(BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }

        BlockState currentState = world.getBlockState(pos);

        // Don't replace air, liquids, or already placed sculk
        if (currentState.isAir()) return false;
        if (!currentState.getFluidState().isEmpty()) return false;

        // Check if it's already a sculk block
        Identifier currentBlockId = net.minecraft.registry.Registries.BLOCK.getId(currentState.getBlock());
        if (SCULK_BLOCKS.contains(currentBlockId)) return false;

        // Don't replace bedrock or other unbreakable blocks
        if (currentState.getBlock().getHardness() < 0) return false;

        // Don't replace important blocks like chests, spawners, etc.
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.SHULKER_BOXES)) return false;
        if (currentState.isOf(Blocks.SPAWNER)) return false;
        if (currentState.isOf(Blocks.CHEST)) return false;

        // Prefer replacing natural blocks
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.DIRT)) return true;
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.STONE_ORE_REPLACEABLES)) return true;
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return true;
        if (currentState.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return true;

        return true;
    }

    private boolean placeSculkBlock(BlockPos pos) {
        // Check if the chunk is loaded and position is valid
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }

        BlockState currentState = world.getBlockState(pos);

        // Don't replace bedrock, unbreakable blocks, or air
        if (currentState.isAir() || currentState.getBlock().getHardness() < 0) {
            return false;
        }

        // Don't replace fluids
        if (!currentState.getFluidState().isEmpty()) {
            return false;
        }

        // Check if it's already a sculk block
        Identifier currentBlockId = net.minecraft.registry.Registries.BLOCK.getId(currentState.getBlock());
        if (SCULK_BLOCKS.contains(currentBlockId)) {
            return false;
        }

        // Choose a random sculk block type based on probabilities
        Identifier sculkType = chooseSculkBlockType();

        // Get the block from registry
        Block block = net.minecraft.registry.Registries.BLOCK.get(sculkType);
        if (block == Blocks.AIR) {
            AspectsLib.LOGGER.warn("Unknown sculk block: {}", sculkType);
            return false;
        }

        BlockState sculkState = block.getDefaultState();

        try {
            if (sculkType.getPath().equals("sculk_vein")) {
                return placeSculkVein(pos, currentState);
            }

            // Try to place the block with proper flags
            boolean success = world.setBlockState(pos, sculkState, Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);

            if (success) {
                AspectsLib.LOGGER.debug("Placed sculk block {} at {}", sculkType, pos);

                // Play placement effects
                world.playSound(null, pos, sculkState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.emitGameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Emitter.of(sculkState));

                // For sculk catalyst, trigger growth
                if (sculkType.getPath().equals("sculk_catalyst")) {
                    triggerSculkSpread(pos);
                }

                // For sculk sensor, set it to inactive state
                if (sculkType.getPath().equals("sculk_sensor")) {
                    world.setBlockState(pos, sculkState.with(net.minecraft.state.property.Properties.SCULK_SENSOR_PHASE,
                            net.minecraft.block.enums.SculkSensorPhase.INACTIVE), Block.NOTIFY_ALL);
                }
            }

            return success;
        } catch (Exception e) {
            AspectsLib.LOGGER.error("Failed to place sculk block at {}: {}", pos, e.getMessage());
            return false;
        }
    }

    // Helper method to place sculk veins properly
    private boolean placeSculkVein(BlockPos pos, BlockState currentState) {
        BlockState veinState = Blocks.SCULK_VEIN.getDefaultState();

        // Attach Sculk veins to surrounding blocks
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            if (world.getBlockState(neighborPos).isSideSolidFullSquare(world, neighborPos, direction.getOpposite())) {
                veinState = veinState.with(MultifaceGrowthBlock.getProperty(direction), true);
            }
        }

        // Only place if it can attach to at least one face
        boolean hasAnyAttachment = false;
        for (Direction direction : Direction.values()) {
            if (veinState.get(MultifaceGrowthBlock.getProperty(direction))) {
                hasAnyAttachment = true;
                break;
            }
        }
        if (!hasAnyAttachment) {
            return false;
        }

        boolean success = world.setBlockState(pos, veinState, Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        if (success) {
            world.playSound(null, pos, veinState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
        return success;
    }

    private Identifier chooseSculkBlockType() {
        net.minecraft.util.math.random.Random random = world.getRandom();
        float chance = random.nextFloat();

        if (chance < 0.5f) { // 50% chance for regular sculk
            return new Identifier("minecraft", "sculk");
        } else if (chance < 0.7f) { // 20% chance for sculk vein
            return new Identifier("minecraft", "sculk_vein");
        } else if (chance < 0.85f) { // 15% chance for sculk sensor
            return new Identifier("minecraft", "sculk_sensor");
        } else if (chance < 0.95f) { // 10% chance for sculk catalyst
            return new Identifier("minecraft", "sculk_catalyst");
        } else { // 5% chance for sculk shrieker
            return new Identifier("minecraft", "sculk_shrieker");
        }
    }

    private void triggerSculkSpread(BlockPos pos) {
        if (world.getBlockState(pos).isOf(Blocks.SCULK_CATALYST)) {
            // Create and configure a sculk spread manager
            SculkSpreadManager spreadManager = SculkSpreadManager.create();

            // Spread some sculk around the catalyst
            int spreadAmount = 10 + world.getRandom().nextInt(20);
            spreadManager.spread(pos, spreadAmount);

            // Tick the spread manager to actually spread the sculk
            // Doing this over multiple ticks for better performance
            for (int i = 0; i < 3; i++) {
                spreadManager.tick(world, pos, world.getRandom(), true);
            }

            AspectsLib.LOGGER.debug("Triggered sculk spread from catalyst at {}", pos);
        }
    }

    private void updateBiomeAspects(RegistryKey<Biome> biomeKey, int newVitiumAmount) {
        AspectData current = BiomeAspectRegistry.get(biomeKey);
        AspectData.Builder builder = new AspectData.Builder(current);

        // Set new Vitium amount
        builder.set(VITIUM_ASPECT, newVitiumAmount);

        // Reduce one random non-Vitium aspect
        List<Identifier> nonVitiumAspects = new ArrayList<>();
        for (Identifier aspectId : current.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ASPECT) && current.getLevel(aspectId) > 0) {
                nonVitiumAspects.add(aspectId);
            }
        }

        if (!nonVitiumAspects.isEmpty()) {
            Identifier aspectToReduce = nonVitiumAspects.get(world.getRandom().nextInt(nonVitiumAspects.size()));
            int currentLevel = current.getLevel(aspectToReduce);
            builder.set(aspectToReduce, Math.max(0, currentLevel - 1));
        }

        // Update the registry
        BiomeAspectRegistry.update(biomeKey, builder.build());
        this.markDirty();
    }

    private boolean isOnlyVitiumRemaining(RegistryKey<Biome> biomeKey) {
        AspectData aspects = BiomeAspectRegistry.get(biomeKey);
        for (Identifier aspectId : aspects.getAspectIds()) {
            if (!aspectId.equals(VITIUM_ASPECT) && aspects.getLevel(aspectId) > 0) {
                return false;
            }
        }
        return aspects.getLevel(VITIUM_ASPECT) > 0;
    }

    private void depleteAether(RegistryKey<Biome> biomeKey, CorruptionData data) {
        // Reduce Aether in the biome
        data.setAether(data.getAether() - 1);

        if (data.getAether() <= 0) {
            createDeadZone(biomeKey);
        }
        this.markDirty();
    }

    private void createDeadZone(RegistryKey<Biome> biomeKey) {
        // Remove all aspects from the biome
        BiomeAspectRegistry.update(biomeKey, AspectData.DEFAULT);

        // Create a dead zone in all corrupted chunks
        DeadZoneManager deadZoneManager = DeadZoneManager.get(world);
        for (ChunkPos chunkPos : corruptionData.get(biomeKey).getCorruptedChunks()) {
            deadZoneManager.addPermanentDeadZone(chunkPos);
        }

        // Remove from corruption tracking
        corruptionData.remove(biomeKey);

        AspectsLib.LOGGER.info("Created dead zone for biome: {}", biomeKey.getValue());
        this.markDirty();
    }

    private void spreadCorruption() {
        // Spread corruption to adjacent chunks
        Map<RegistryKey<Biome>, Set<ChunkPos>> newCorruptions = new HashMap<>();

        for (Map.Entry<RegistryKey<Biome>, CorruptionData> entry : corruptionData.entrySet()) {
            RegistryKey<Biome> biomeKey = entry.getKey();
            CorruptionData data = entry.getValue();

            for (ChunkPos corruptedChunk : data.getCorruptedChunks()) {
                // Try to spread to adjacent chunks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;

                        ChunkPos newChunk = new ChunkPos(corruptedChunk.x + dx, corruptedChunk.z + dz);
                        if (!data.getCorruptedChunks().contains(newChunk) &&
                                isChunkInBiome(newChunk, biomeKey) &&
                                world.getRandom().nextFloat() < 0.3f) { // 30% chance to spread

                            newCorruptions.computeIfAbsent(biomeKey, k -> new HashSet<>()).add(newChunk);
                        }
                    }
                }
            }
        }

        // Add new corruptions
        for (Map.Entry<RegistryKey<Biome>, Set<ChunkPos>> entry : newCorruptions.entrySet()) {
            corruptionData.get(entry.getKey()).getCorruptedChunks().addAll(entry.getValue());
            this.markDirty();
        }
    }

    // API Methods
    public int addVitiumToBiome(RegistryKey<Biome> biomeKey, int amount) {
        AspectData current = BiomeAspectRegistry.get(biomeKey);
        AspectData.Builder builder = new AspectData.Builder(current);
        builder.add(VITIUM_ASPECT, amount);
        AspectData newData = builder.build();
        BiomeAspectRegistry.update(biomeKey, newData);

        // Initialize corruption data if needed
        if (!corruptionData.containsKey(biomeKey)) {
            corruptionData.put(biomeKey, new CorruptionData());
        }
        this.markDirty();

        return newData.getLevel(VITIUM_ASPECT);
    }

    public int setVitiumInBiome(RegistryKey<Biome> biomeKey, int amount) {
        AspectData current = BiomeAspectRegistry.get(biomeKey);
        AspectData.Builder builder = new AspectData.Builder(current);
        builder.set(VITIUM_ASPECT, amount);
        AspectData newData = builder.build();
        BiomeAspectRegistry.update(biomeKey, newData);

        // Initialize corruption data if needed
        if (!corruptionData.containsKey(biomeKey) && amount > 0) {
            corruptionData.put(biomeKey, new CorruptionData());
        }
        this.markDirty();

        return newData.getLevel(VITIUM_ASPECT);
    }

    public int removeVitiumFromBiome(RegistryKey<Biome> biomeKey, int amount) {
        AspectData current = BiomeAspectRegistry.get(biomeKey);
        int currentVitium = current.getLevel(VITIUM_ASPECT);
        int newAmount = Math.max(0, currentVitium - amount);

        AspectData.Builder builder = new AspectData.Builder(current);
        builder.set(VITIUM_ASPECT, newAmount);
        AspectData newData = builder.build();
        BiomeAspectRegistry.update(biomeKey, newData);

        // Remove corruption data if Vitium is zero
        if (newAmount == 0 && corruptionData.containsKey(biomeKey)) {
            corruptionData.remove(biomeKey);
        }
        this.markDirty();

        return newAmount;
    }

    public boolean startBiomeCorruption(RegistryKey<Biome> biomeKey, int initialVitium) {
        if (initialVitium < 16) {
            return false; // Minimum required Vitium to start corruption
        }

        setVitiumInBiome(biomeKey, initialVitium);

        // Make sure corruption data exists and add some initial corrupted chunks
        CorruptionData data = corruptionData.computeIfAbsent(biomeKey, k -> new CorruptionData());

        // Find and add some initial corrupted chunks
        Set<ChunkPos> initialChunks = findChunksInBiome(biomeKey, data, CORRUPTION_TARGET_COUNT);
        data.getCorruptedChunks().addAll(initialChunks);

        this.markDirty();
        return true;
    }


    public boolean isBiomeCorrupted(RegistryKey<Biome> biomeKey) {
        return corruptionData.containsKey(biomeKey);
    }

    public int getBiomeVitiumLevel(RegistryKey<Biome> biomeKey) {
        AspectData aspects = BiomeAspectRegistry.get(biomeKey);
        return aspects.getLevel(VITIUM_ASPECT);
    }

    public boolean isCorruptionActive(RegistryKey<Biome> biomeKey) {
        CorruptionData data = corruptionData.get(biomeKey);
        if (data == null || data.getCorruptedChunks().isEmpty()) {
            return false;
        }

        // Corruption is active if we have Vitium and corrupted chunks
        AspectData aspects = BiomeAspectRegistry.get(biomeKey);
        return aspects.getLevel(VITIUM_ASPECT) > 0 && !data.getCorruptedChunks().isEmpty();
    }

    /**
     * Get the corruption level of a biome (ratio of Vitium to other aspects)
     */
    public float getCorruptionLevel(RegistryKey<Biome> biomeKey) {
        if (!isBiomeCorrupted(biomeKey)) {
            return 0.0f;
        }

        AspectData aspects = BiomeAspectRegistry.get(biomeKey);
        int vitiumAmount = aspects.getLevel(VITIUM_ASPECT);
        int totalOtherAspects = calculateTotalOtherAspects(aspects);

        if (totalOtherAspects == 0) {
            return 1.0f; // Fully corrupted
        }

        return Math.min(1.0f, (float) vitiumAmount / (vitiumAmount + totalOtherAspects));
    }

    /**
     * Get all currently corrupted biomes
     */
    public Set<RegistryKey<Biome>> getCorruptedBiomes() {
        return new HashSet<>(corruptionData.keySet());
    }

    /**
     * Get corruption data for a specific biome
     */
    @Nullable
    public CorruptionData getCorruptionData(RegistryKey<Biome> biomeKey) {
        return corruptionData.get(biomeKey);
    }

    /**
     * Force remove corruption from a biome (for admin/API use)
     */
    public boolean removeCorruption(RegistryKey<Biome> biomeKey) {
        CorruptionData removed = corruptionData.remove(biomeKey);
        if (removed != null) {
            // Reset Vitium level to 0
            AspectData current = BiomeAspectRegistry.get(biomeKey);
            AspectData.Builder builder = new AspectData.Builder(current);
            builder.set(VITIUM_ASPECT, 0);
            BiomeAspectRegistry.update(biomeKey, builder.build());

            this.markDirty();
            return true;
        }
        return false;
    }

    // NBT serialization
    public static VitiumCorruptionManager fromNbt(ServerWorld world, NbtCompound nbt) {
        VitiumCorruptionManager manager = new VitiumCorruptionManager(world);

        // Load corruption data
        NbtCompound corruptionDataNbt = nbt.getCompound("corruptionData");
        for (String biomeKeyStr : corruptionDataNbt.getKeys()) {
            RegistryKey<Biome> biomeKey = RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME, new Identifier(biomeKeyStr));
            NbtCompound dataNbt = corruptionDataNbt.getCompound(biomeKeyStr);

            CorruptionData data = CorruptionData.fromNbt(dataNbt);
            manager.corruptionData.put(biomeKey, data);
        }

        // Load active corruption chunks
        NbtList activeChunksList = nbt.getList("activeCorruptionChunks", NbtElement.LONG_TYPE);
        for (NbtElement element : activeChunksList) {
            long chunkPosLong = ((NbtLong) element).longValue();
            manager.activeCorruptionChunks.add(new ChunkPos(chunkPosLong));
        }

        manager.tickCounter = nbt.getInt("tickCounter");

        return manager;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Save corruption data
        NbtCompound corruptionDataNbt = new NbtCompound();
        for (Map.Entry<RegistryKey<Biome>, CorruptionData> entry : corruptionData.entrySet()) {
            String biomeKeyStr = entry.getKey().getValue().toString();
            CorruptionData data = entry.getValue();
            corruptionDataNbt.put(biomeKeyStr, data.toNbt());
        }
        nbt.put("corruptionData", corruptionDataNbt);

        // Save active corruption chunks
        NbtList activeChunksList = new NbtList();
        for (ChunkPos chunkPos : activeCorruptionChunks) {
            activeChunksList.add(NbtLong.of(chunkPos.toLong()));
        }
        nbt.put("activeCorruptionChunks", activeChunksList);

        nbt.putInt("tickCounter", tickCounter);

        return nbt;
    }

    public static class CorruptionData {
        private final Set<ChunkPos> corruptedChunks = new HashSet<>();
        private int aether = 100; // Starting Aether amount

        public Set<ChunkPos> getCorruptedChunks() {
            return corruptedChunks;
        }

        public int getAether() {
            return aether;
        }

        public void setAether(int aether) {
            this.aether = aether;
        }

        // NBT serialization for CorruptionData
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();

            // Save corrupted chunks
            NbtList chunksList = new NbtList();
            for (ChunkPos chunkPos : corruptedChunks) {
                chunksList.add(NbtLong.of(chunkPos.toLong()));
            }
            nbt.put("corruptedChunks", chunksList);

            // Save Aether
            nbt.putInt("aether", aether);

            return nbt;
        }

        public static CorruptionData fromNbt(NbtCompound nbt) {
            CorruptionData data = new CorruptionData();

            // Load corrupted chunks
            NbtList chunksList = nbt.getList("corruptedChunks", NbtElement.LONG_TYPE);
            for (NbtElement element : chunksList) {
                long chunkPosLong = ((NbtLong) element).longValue();
                data.corruptedChunks.add(new ChunkPos(chunkPosLong));
            }

            // Load Aether
            data.aether = nbt.getInt("aether");

            return data;
        }
    }
}