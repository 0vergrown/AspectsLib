package dev.overgrown.aspectslib.entity.aura_node;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.data.AspectData;
import dev.overgrown.aspectslib.data.BiomeAspectModifier;
import dev.overgrown.aspectslib.data.BiomeAspectRegistry;
import dev.overgrown.aspectslib.resonance.ResonanceCalculator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.*;

public class AuraNodeEntity extends Entity {
    // Node types
    public enum NodeType {
        NORMAL, PURE, SINISTER, UNSTABLE, HUNGRY
    }

    // Regeneration accumulators
    private final Map<Identifier, Float> regenAccumulators = new HashMap<>();

    // Aspect state storage
    public static class AspectState {
        public int original;
        public int current;

        public AspectState(int original) {
            this.original = original;
            this.current = original;
        }

        public void regen(float amount) {
            if (current < original) {
                current = Math.min(original, current + (int)(original * amount));
            }
        }
    }

    // Tracked data
    private static final TrackedData<Integer> NODE_TYPE = DataTracker.registerData(AuraNodeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<NbtCompound> ASPECTS_NBT = DataTracker.registerData(AuraNodeEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    // Aspect identifiers
    public static final Identifier FAMES_ASPECT = AspectsLib.identifier("fames");
    public static final Identifier VITIUM_ASPECT = AspectsLib.identifier("vitium");

    private Map<Identifier, AspectState> aspects = new HashMap<>();
    private int instabilityCounter = 0;
    private int hungerCounter = 0;
    private int sinisterCounter = 0;

    public AuraNodeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true; // No collision
        this.setInvulnerable(true); // Can't take damage
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(NODE_TYPE, NodeType.NORMAL.ordinal());
        this.dataTracker.startTracking(ASPECTS_NBT, new NbtCompound());
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient()) {
            // Natural regeneration for aspects
            regenerateAspects();

            // Remove aspects that have been drained to 0
            removeDrainedAspects();

            // Check if node should die
            if (aspects.isEmpty()) {
//                this.discard();
                return;
            }

            // Type-specific behaviors
            switch (getNodeType()) {
                case SINISTER:
                    handleSinisterBehavior();
                    break;
                case HUNGRY:
                    handleHungryBehavior();
                    break;
                case UNSTABLE:
                    handleUnstableBehavior();
                    break;
            }

            // Update tracked data periodically
            if (this.age % 20 == 0) {
                updateTrackedAspects();
            }
        }
    }

    public int getRenderColour() {
        return 0xFFAA6655; // This a packed ARGB integer
    }

    private void regenerateAspects() {
        // Base regeneration rate (0.1% per second)
        final float BASE_REGEN_RATE = 0.00005f; // Much slower rate

        switch (getNodeType()) {
            case NORMAL, PURE -> regenerateStandard(BASE_REGEN_RATE);
            case SINISTER -> regenerateSinister(BASE_REGEN_RATE);
            case UNSTABLE -> regenerateUnstable(BASE_REGEN_RATE);
            case HUNGRY -> regenerateHungry(BASE_REGEN_RATE);
        }
    }

    private void regenerateStandard(float baseRate) {
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            regenerateAspect(entry.getKey(), entry.getValue(), baseRate);
        }
    }

    private void regenerateSinister(float baseRate) {
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            float rate = baseRate;
            if (entry.getKey().equals(VITIUM_ASPECT)) {
                rate *= 1.5f; // Vitium regenerates 50% faster
            }
            regenerateAspect(entry.getKey(), entry.getValue(), rate);
        }

        // Handle sinister corruption
        sinisterCounter++;
        if (sinisterCounter >= 100) { // Every 5 seconds
            sinisterCounter = 0;
            RegistryEntry<Biome> biomeEntry = getWorld().getBiome(getBlockPos());
            Identifier biomeId = biomeEntry.getKey().map(RegistryKey::getValue).orElse(null);

            if (biomeId != null) {
                AspectData currentBiomeAspects = BiomeAspectRegistry.get(biomeId);
                if (!currentBiomeAspects.isEmpty()) {
                    AspectData.Builder builder = new AspectData.Builder(currentBiomeAspects);
                    builder.add(VITIUM_ASPECT, 10); // Add Vitium to biome
                    BiomeAspectRegistry.update(biomeId, builder.build());
                }
            }
        }
    }

    private void regenerateUnstable(float baseRate) {
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            regenerateAspect(entry.getKey(), entry.getValue(), baseRate * 0.8f);
        }
    }

    private void regenerateHungry(float baseRate) {
        AspectState famesState = aspects.get(FAMES_ASPECT);
        if (famesState == null) return;

        // If Fames isn't full, consume other aspects
        if (famesState.current < famesState.original) {
            float totalConsumed = 0;
            List<Identifier> toRemove = new ArrayList<>();

            for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
                if (entry.getKey().equals(FAMES_ASPECT)) continue;

                AspectState state = entry.getValue();
                if (state.current <= 0) {
                    toRemove.add(entry.getKey());
                    continue;
                }

                // Calculate consumption (0.2% per tick)
                float consumeAmount = state.original * baseRate * 4;
                float available = Math.min(consumeAmount, state.current);

                state.current -= (int) available;
                totalConsumed += available;

                if (state.current <= 0) {
                    toRemove.add(entry.getKey());
                }
            }

            // Add consumed aspects to Fames
            if (totalConsumed > 0) {
                famesState.current = (int) Math.min(
                        famesState.original,
                        famesState.current + totalConsumed
                );
            }

            // Remove consumed aspects
            for (Identifier id : toRemove) {
                aspects.remove(id);
            }
        } else {
            // If Fames is full, consume other aspects aggressively
            float totalConsumed = 0;
            List<Identifier> toRemove = new ArrayList<>();

            for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
                if (entry.getKey().equals(FAMES_ASPECT)) continue;

                AspectState state = entry.getValue();
                if (state.current <= 0) {
                    toRemove.add(entry.getKey());
                    continue;
                }

                // More aggressive consumption (0.4% per tick)
                float consumeAmount = state.original * baseRate * 8;
                float available = Math.min(consumeAmount, state.current);

                state.current -= (int) available;
                totalConsumed += available;

                if (state.current <= 0) {
                    toRemove.add(entry.getKey());
                }
            }

            // Add consumed aspects to Fames (making it stronger)
            if (totalConsumed > 0) {
                famesState.original += (int) totalConsumed; // Make Fames stronger
                famesState.current = famesState.original;
            }

            // Remove consumed aspects
            for (Identifier id : toRemove) {
                aspects.remove(id);
            }
        }

        // If only Fames remains, and it's full, start consuming itself
        if (aspects.size() == 1 && aspects.containsKey(FAMES_ASPECT) &&
                famesState.current >= famesState.original) {

            // Self-consumption (0.1% per tick)
            float consumeAmount = famesState.original * baseRate * 2;
            famesState.current = Math.max(0, famesState.current - (int) consumeAmount);

            if (famesState.current <= 0) {
                aspects.remove(FAMES_ASPECT);
            }
        }
    }

    private void regenerateAspect(Identifier aspectId, AspectState state, float rate) {
        if (state.current >= state.original) return;

        // Get or create accumulator
        float accumulator = regenAccumulators.getOrDefault(aspectId, 0f);

        // Add this tick's regeneration
        float regenThisTick = state.original * rate;
        accumulator += regenThisTick;

        // Convert accumulated value to integer
        int toAdd = (int) accumulator;
        if (toAdd > 0) {
            int newCurrent = state.current + toAdd;
            if (newCurrent > state.original) {
                toAdd = state.original - state.current;
                newCurrent = state.original;
            }

            state.current = newCurrent;
            accumulator -= toAdd;

            // Debug logging
            AspectsLib.LOGGER.debug("Regenerated {}: {}/{} (+{})",
                    aspectId, state.current, state.original, toAdd);
        }

        // Store remaining fraction
        regenAccumulators.put(aspectId, accumulator);
    }

    private void removeDrainedAspects() {
        List<Identifier> toRemove = new ArrayList<>();
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            if (entry.getValue().current <= 0) {
                toRemove.add(entry.getKey());
            }
        }
        for (Identifier id : toRemove) {
            aspects.remove(id);
        }
    }

    private void handleSinisterBehavior() {
        sinisterCounter++;
        if (sinisterCounter >= 100) { // Every 5 seconds
            sinisterCounter = 0;
            RegistryEntry<Biome> biomeEntry = getWorld().getBiome(getBlockPos());
            Identifier biomeId = biomeEntry.getKey().map(RegistryKey::getValue).orElse(null);

            if (biomeId != null) {
                BiomeAspectModifier.addBiomeModification(biomeId, VITIUM_ASPECT, 10); // Add 10 Vitium per corruption cycle
                AspectsLib.LOGGER.debug("Sinister node corrupting biome {} at {}", biomeId, this.getBlockPos());
            }
        }
    }

    private void handleHungryBehavior() {
        hungerCounter++;

        // Consume every 10 seconds (200 ticks)
        if (hungerCounter >= 200) {
            hungerCounter = 0;
            RegistryEntry<Biome> biomeEntry = getWorld().getBiome(getBlockPos());
            Identifier biomeId = biomeEntry.getKey().map(RegistryKey::getValue).orElse(null);

            // Phase 1: Consume other aspects in the node
            if (aspects.size() > 1) {
                List<Identifier> nonFamesAspects = new ArrayList<>();
                for (Identifier id : aspects.keySet()) {
                    if (!id.equals(FAMES_ASPECT)) {
                        nonFamesAspects.add(id);
                    }
                }

                if (!nonFamesAspects.isEmpty()) {
                    Identifier target = nonFamesAspects.get(getWorld().getRandom().nextInt(nonFamesAspects.size()));
                    AspectState state = aspects.get(target);

                    // Consume 10% of the aspect
                    int consumeAmount = Math.max(1, state.current / 10);
                    state.current -= consumeAmount;

                    // If completely drained, remove with 10% chance
                    if (state.current <= 0 && getWorld().getRandom().nextFloat() < 0.1f) {
                        aspects.remove(target);
                    }
                }
            }
            // Phase 2: Consume from environment
            else if (aspects.containsKey(FAMES_ASPECT) && biomeId != null) {
                // Drain from all aspects in the biome
                AspectData currentBiomeAspects = BiomeAspectRegistry.get(biomeId);
                if (!currentBiomeAspects.isEmpty()) {
                    AspectData.Builder builder = new AspectData.Builder(AspectData.DEFAULT);
                    for (Identifier aspectId : currentBiomeAspects.getAspectIds()) {
                        int currentAmount = currentBiomeAspects.getLevel(aspectId);
                        if (currentAmount > 0) {
                            builder.add(aspectId, -Math.min(5, currentAmount)); // Drain 5 or whatever remains
                        }
                    }
                    // TODO: Apply the drainage (Need to handle this based on the new system)
                    AspectsLib.LOGGER.debug("Hungry node draining environment at {}", this.getBlockPos());
                }

                AspectState famesState = aspects.get(FAMES_ASPECT);
                if (famesState.current > 0) {
                    famesState.current = Math.max(0, famesState.current - 10);
                    if (famesState.current <= 0) {
                        this.discard();
                    }
                }
            }
        }
    }

    private void handleUnstableBehavior() {
        // Check for opposing resonance every 5 seconds (100 ticks)
        if (this.age % 100 == 0) {
            // Create AspectData for resonance calculation
            Object2IntOpenHashMap<Identifier> aspectMap = new Object2IntOpenHashMap<>();
            for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
                aspectMap.put(entry.getKey(), entry.getValue().current);
            }

            ResonanceCalculator.ResonanceResult result = ResonanceCalculator.calculate(new AspectData(aspectMap));

            // Increase instability if there's barrier cost (opposing resonance)
            if (result.barrierCost() > 0) {
                instabilityCounter += (int) Math.ceil(result.barrierCost());
                AspectsLib.LOGGER.debug("Unstable node instability: {}", instabilityCounter);

                // Explode when instability reaches threshold
                if (instabilityCounter >= 100) {
                    getWorld().createExplosion(this, getX(), getY(), getZ(), 3.0f, false, World.ExplosionSourceType.NONE);
                    this.discard();
                }
            }
        }
    }

    private void updateTrackedAspects() {
        NbtCompound aspectsNbt = new NbtCompound();
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            NbtCompound stateNbt = new NbtCompound();
            stateNbt.putInt("Original", entry.getValue().original);
            stateNbt.putInt("Current", entry.getValue().current);
            aspectsNbt.put(entry.getKey().toString(), stateNbt);
        }
        this.dataTracker.set(ASPECTS_NBT, aspectsNbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        // Read node type
        this.setNodeType(NodeType.values()[nbt.getByte("NodeType")]);

        // Read aspects - Create AspectState with correct current value
        aspects.clear();
        NbtList aspectsList = nbt.getList("Aspects", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : aspectsList) {
            NbtCompound aspectNbt = (NbtCompound) element;
            Identifier id = new Identifier(aspectNbt.getString("Id"));
            int original = aspectNbt.getInt("Original");
            int current = aspectNbt.getInt("Current");

            // Create AspectState with proper current value
            AspectState state = new AspectState(original);
            state.current = current;
            aspects.put(id, state);
        }

        instabilityCounter = nbt.getInt("Instability");
        hungerCounter = nbt.getInt("HungerCounter");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putByte("NodeType", (byte) getNodeType().ordinal());

        NbtList aspectsList = new NbtList();
        for (Map.Entry<Identifier, AspectState> entry : aspects.entrySet()) {
            NbtCompound aspectNbt = new NbtCompound();
            aspectNbt.putString("Id", entry.getKey().toString());
            aspectNbt.putInt("Original", entry.getValue().original);
            aspectNbt.putInt("Current", entry.getValue().current);
            aspectsList.add(aspectNbt);
        }
        nbt.put("Aspects", aspectsList);

        nbt.putInt("Instability", instabilityCounter);
        nbt.putInt("HungerCounter", hungerCounter);
    }

    protected void readCustomDataFromTrackedData() {
        this.setNodeType(NodeType.values()[dataTracker.get(NODE_TYPE)]);

        // Update aspects from tracked data
        NbtCompound aspectsNbt = dataTracker.get(ASPECTS_NBT);
        aspects.clear();
        for (String key : aspectsNbt.getKeys()) {
            Identifier id = new Identifier(key);
            NbtCompound stateNbt = aspectsNbt.getCompound(key);
            int original = stateNbt.getInt("Original");
            int current = stateNbt.getInt("Current");
            aspects.put(id, new AspectState(original) {{
                this.current = current;
            }});
        }
    }

    // Getters and setters
    public NodeType getNodeType() {
        return NodeType.values()[dataTracker.get(NODE_TYPE)];
    }

    public void setNodeType(NodeType type) {
        dataTracker.set(NODE_TYPE, type.ordinal());
    }

    public Map<Identifier, AspectState> getAspects() {
        return Collections.unmodifiableMap(aspects);
    }

    public void setAspects(Map<Identifier, AspectState> aspects) {
        this.aspects = new HashMap<>(aspects);
        updateTrackedAspects();
    }

    public void initializeAspects(Random random) {
        aspects.clear();

        switch (getNodeType()) {
            case PURE:
                // Pure node has only one aspect
                Identifier aspect = getRandomAspect(random, 0.95f); // 95% chance for primal
                aspects.put(aspect, new AspectState(random.nextInt(100) + 50));
                break;

            case HUNGRY:
                // Hungry node always has Fames
                aspects.put(FAMES_ASPECT, new AspectState(random.nextInt(100) + 50));

                // 50% chance to have one additional aspect
                if (random.nextBoolean()) {
                    aspects.put(getRandomAspect(random, 0.9f), new AspectState(random.nextInt(100) + 50));
                }
                break;

            default:
                // Normal, Sinister, Unstable: 1-4 aspects
                int count = random.nextInt(4) + 1;
                for (int i = 0; i < count; i++) {
                    Identifier newAspect = getRandomAspect(random, 0.8f); // 80% chance for primal
                    aspects.put(newAspect, new AspectState(random.nextInt(100) + 50));
                }
        }

        updateTrackedAspects();
    }

    private Identifier getRandomAspect(Random random, float primalChance) {
        // Organize aspects by tier
        List<Identifier> primal = Arrays.asList(
                AspectsLib.identifier("aer"), AspectsLib.identifier("aqua"), AspectsLib.identifier("ignis"),
                AspectsLib.identifier("ordo"), AspectsLib.identifier("perditio"), AspectsLib.identifier("terra")
        );

        List<Identifier> secondary = Arrays.asList(
                AspectsLib.identifier("gelum"), AspectsLib.identifier("lux"), AspectsLib.identifier("metallum"),
                AspectsLib.identifier("mortuus"), AspectsLib.identifier("motus"), AspectsLib.identifier("permutatio"),
                AspectsLib.identifier("potentia"), AspectsLib.identifier("vacuos"), AspectsLib.identifier("victus"),
                AspectsLib.identifier("vitreus")
        );

        List<Identifier> tertiary = Arrays.asList(
                AspectsLib.identifier("bestia"), AspectsLib.identifier("fames"), AspectsLib.identifier("exanimis"),
                AspectsLib.identifier("herba"), AspectsLib.identifier("instrumentum"), AspectsLib.identifier("praecantatio"),
                AspectsLib.identifier("spiritus"), AspectsLib.identifier("tenebrae"), AspectsLib.identifier("vinculum"),
                AspectsLib.identifier("volatus")
        );

        List<Identifier> quaternary = Arrays.asList(
                AspectsLib.identifier("alienis"), AspectsLib.identifier("alkimia"), AspectsLib.identifier("auram"),
                AspectsLib.identifier("aversion"), AspectsLib.identifier("cognitio"), AspectsLib.identifier("desiderium"),
                AspectsLib.identifier("fabrico"), AspectsLib.identifier("humanus"), AspectsLib.identifier("machina"),
                AspectsLib.identifier("praemunio"), AspectsLib.identifier("sensus"), AspectsLib.identifier("vitium")
        );

        // Safety checks for empty lists
        if (random.nextFloat() < primalChance && !primal.isEmpty()) {
            return primal.get(random.nextInt(primal.size()));
        }

        float roll = random.nextFloat();
        if (roll < 0.6f && !secondary.isEmpty()) {
            return secondary.get(random.nextInt(secondary.size()));
        } else if (roll < 0.9f && !tertiary.isEmpty()) {
            return tertiary.get(random.nextInt(tertiary.size()));
        } else if (!quaternary.isEmpty()) {
            return quaternary.get(random.nextInt(quaternary.size()));
        }

        // Fallback to primal if nothing else available
        return !primal.isEmpty() ? primal.get(random.nextInt(primal.size())) : AspectsLib.identifier("aer");
    }

    @Override
    public boolean doesRenderOnFire() {
        return false; // Don't render fire
    }
}