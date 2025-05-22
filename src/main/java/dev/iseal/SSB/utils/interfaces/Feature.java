package dev.iseal.SSB.utils.interfaces;

import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.registries.FeatureRegistry;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * Abstract class representing a feature within the bot.
 * Features can be enabled or disabled and must provide a name.
 * This class also provides a utility method to obtain instances of feature subclasses.
 */
public abstract class Feature {

    /**
     * Indicates whether the feature is currently enabled.
     * Defaults to true.
     */
    protected boolean enabled = true;
    /**
     * Logger instance for the Feature class.
     */
    private static Logger log = JDALogger.getLog(Feature.class);


    /**
     * Checks if the feature is enabled.
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state of the feature.
     *
     * @param newEnabled The new enabled state.
     */
    protected void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
    }

    /**
     * Gets the name of the feature.
     * This method must be implemented by subclasses.
     *
     * @return The name of the feature.
     */
    public abstract String getFeatureName();

    /**
     * Registers this feature instance with the {@link FeatureRegistry}.
     */
    protected void registerFeature() {
        FeatureRegistry.getInstance().registerFeature(this);
    }

    /**
     * Retrieves an instance of the specified feature class.
     * <p>
     * This method first attempts to invoke a static {@code getInstance()} method
     * on the provided class. If such a method exists and returns an instance
     * assignable to the class, that instance is returned.
     * <p>
     * If a suitable {@code getInstance()} method is not found, this method
     * attempts to create a new instance using the class's default (no-argument)
     * constructor.
     *
     * @param clazz The class of the feature to instantiate.
     * @param <T>   The type of the feature.
     * @return An instance of the specified feature class.
     * @throws RuntimeException if instantiation fails due to reflection errors or
     *                          if an exception occurs during the invocation of
     *                          {@code getInstance()} or the constructor.
     */
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
            if (e instanceof InvocationTargetException) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    log.error("Failed to instantiate feature: " + clazz.getName() + " with error of "+ cause.getClass().getSimpleName(), cause.getMessage());
                    Arrays.stream(cause.getStackTrace()).map(StackTraceElement::toString).forEach(log::error);
                }
            }
            log.error("Failed to instantiate feature: " + clazz.getName() + " with error of "+ e.getClass().getSimpleName(), e.getMessage());
            Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).forEach(log::error);
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }
}