package dev.overgrown.aspectslib.api.aether;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.aether.AetherDensity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating AetherDensity objects easily
 */
public class AetherDensityBuilder {
    private final Map<Identifier, Double> densities = new HashMap<>();

    public AetherDensityBuilder add(Identifier aspect, double density) {
        densities.put(aspect, density);
        return this;
    }

    public AetherDensityBuilder add(String aspectName, double density) {
        return add(AspectsLib.identifier(aspectName), density);
    }

    public AetherDensity build() {
        return new AetherDensity(densities);
    }

    public static AetherDensityBuilder create() {
        return new AetherDensityBuilder();
    }
}