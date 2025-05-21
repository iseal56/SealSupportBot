package dev.iseal.SSB.utils.interfaces;

import dev.iseal.SSB.SSBMain;
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

    public static <T extends Feature> T getFeatureInstance(Class<T> clazz) {
        try {
            // Try to get the getInstance method first
            try {
                java.lang.reflect.Method getInstanceMethod = clazz.getDeclaredMethod("getInstance");
                if (java.lang.reflect.Modifier.isStatic(getInstanceMethod.getModifiers()) &&
                        clazz.isAssignableFrom(getInstanceMethod.getReturnType())) {
                    @SuppressWarnings("unchecked")
                    T instance = (T) getInstanceMethod.invoke(null);
                    return instance;
                }
            } catch (NoSuchMethodException e) {
                // getInstance method not found, will use constructor instead
            }

            // Fall back to constructor if getInstance isn't available
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }
}
