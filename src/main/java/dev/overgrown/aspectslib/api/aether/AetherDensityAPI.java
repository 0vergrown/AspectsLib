package dev.overgrown.aspectslib.api.aether;

/**
 * Provider for Aether Density API
 */
public class AetherDensityAPI {
    private static IAetherDensityAPI instance = null;

    public static void setInstance(IAetherDensityAPI api) {
        if (instance != null) {
            throw new IllegalStateException("Aether Density API instance already set!");
        }
        instance = api;
    }

    public static IAetherDensityAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Aether Density API not initialized! Make sure AspectsLib is loaded.");
        }
        return instance;
    }
}