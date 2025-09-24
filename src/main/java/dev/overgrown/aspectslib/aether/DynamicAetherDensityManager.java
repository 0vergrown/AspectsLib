package dev.overgrown.aspectslib.aether;

import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicAetherDensityManager {
    private static final Map<Identifier, Map<Identifier, Double>> modifications = new ConcurrentHashMap<>();

    public static void addModification(Identifier biomeId, Identifier aspect, double amount) {
        modifications.computeIfAbsent(biomeId, k -> new ConcurrentHashMap<>())
                .merge(aspect, amount, Double::sum);
    }

    public static void drainAllAspects(Identifier biomeId, double amount) {
        Map<Identifier, Double> biomeMods = modifications.get(biomeId);
        if (biomeMods != null) {
            biomeMods.replaceAll((aspect, current) -> current - amount);
        }
    }

    public static double getModification(Identifier biomeId, Identifier aspect) {
        Map<Identifier, Double> biomeMods = modifications.get(biomeId);
        return biomeMods != null ? biomeMods.getOrDefault(aspect, 0.0) : 0.0;
    }

    public static Map<Identifier, Double> getModifications(Identifier biomeId) {
        return modifications.get(biomeId);
    }

    public static Map<Identifier, Map<Identifier, Double>> getModificationsMap() {
        return modifications;
    }

    public static void reset() {
        modifications.clear();
    }
}