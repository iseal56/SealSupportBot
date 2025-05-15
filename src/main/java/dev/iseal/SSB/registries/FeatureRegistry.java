package dev.iseal.SSB.registries;

import de.leonhard.storage.Json;

import java.util.ArrayList;
import java.util.List;

public class FeatureRegistry {

    private static FeatureRegistry instance = new FeatureRegistry();
    public static FeatureRegistry getInstance() {
        return instance;
    }

    private final ArrayList<String> enabledFeatures = new ArrayList<>();
    private final ArrayList<String> registeredFeatures = new ArrayList<>();

    public void init() {

    }

    public void registerFeature(String featureName, boolean enabled) {
        if (registeredFeatures.contains(featureName) || enabledFeatures.contains(featureName)) {
            throw new IllegalArgumentException("Feature " + featureName + " is already registered");
        }
        registeredFeatures.add(featureName);
        if (enabled) {
            enabledFeatures.add(featureName);
        }
    }

    public void unregisterFeature(String featureName) {
        registeredFeatures.remove(featureName);
        enabledFeatures.remove(featureName);
    }

    public List<String> listFeatures(boolean onlyEnabled) {
        if (onlyEnabled) {
            // why the fuck do we need to cast it???
            return List.copyOf(enabledFeatures);
        } else {
            return List.copyOf(registeredFeatures);
        }

    }

    public boolean isFeatureEnabled(String featureName) {
        return enabledFeatures.contains(featureName);
    }

    public void disableFeature(String featureName) {
        if (!registeredFeatures.contains(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        enabledFeatures.remove(featureName);
    }

    public void enableFeature(String featureName) {
        if (!registeredFeatures.contains(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        enabledFeatures.add(featureName);
    }
}
