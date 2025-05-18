package dev.iseal.SSB.utils.interfaces;

import dev.iseal.SSB.registries.FeatureRegistry;

public abstract class Feature {

    protected boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    protected void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
    }

    public abstract String getFeatureName();

    protected void registerFeature() {
        FeatureRegistry.getInstance().registerFeature(this);
    }
}
