package dev.iseal.SSB.registries;

import dev.iseal.SSB.utils.interfaces.Feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureRegistry {

    private static final FeatureRegistry instance = new FeatureRegistry();
    public static FeatureRegistry getInstance() {
        return instance;
    }

    private final ArrayList<Feature> enabledFeatures = new ArrayList<>();
    private final Map<String, Feature> registeredFeatures = new HashMap<>();

    public void init() {

    }

    public void registerFeature(Feature feature) {
        String featureName = feature.getFeatureName();
        if (registeredFeatures.containsKey(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is already registered");
        registeredFeatures.put(featureName, feature);
        if (feature.isEnabled()) {
            enabledFeatures.add(feature);
        }
    }

    public void unregisterFeature(String featureName) {
        if (!registeredFeatures.containsKey(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        Feature feature = registeredFeatures.remove(featureName);
        enabledFeatures.remove(feature);
    }

    public void unregisterFeature(Feature feature) {
        String featureName = feature.getFeatureName();
        if (!registeredFeatures.containsKey(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        registeredFeatures.remove(featureName);
        enabledFeatures.remove(feature);
    }

    public List<Feature> listFeatures(boolean onlyEnabled) {
        if (onlyEnabled) {
            return List.copyOf(enabledFeatures);
        } else {
            return List.copyOf(registeredFeatures.values());
        }
    }

    public boolean isFeatureEnabled(Feature feature) {
        return enabledFeatures.contains(feature);
    }
    
    public boolean isFeatureEnabled(String featureName) {
        Feature feature = registeredFeatures.get(featureName);
        return feature != null && enabledFeatures.contains(feature);
    }

    public void disableFeature(Feature feature) {
        if (!registeredFeatures.containsKey(feature.getFeatureName()))
            throw new IllegalArgumentException("Feature " + feature.getFeatureName() + " is not registered");
        enabledFeatures.remove(feature);
    }
    
    public void disableFeature(String featureName) {
        if (!registeredFeatures.containsKey(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        Feature feature = registeredFeatures.get(featureName);
        enabledFeatures.remove(feature);
    }

    public void enableFeature(Feature feature) {
        if (!registeredFeatures.containsKey(feature.getFeatureName()))
            throw new IllegalArgumentException("Feature " + feature.getFeatureName() + " is not registered");
        enabledFeatures.add(feature);
    }
    
    public void enableFeature(String featureName) {
        if (!registeredFeatures.containsKey(featureName))
            throw new IllegalArgumentException("Feature " + featureName + " is not registered");
        Feature feature = registeredFeatures.get(featureName);
        enabledFeatures.add(feature);
    }
}
