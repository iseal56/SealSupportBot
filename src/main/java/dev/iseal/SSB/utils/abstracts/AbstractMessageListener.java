package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.listeners.MessageListener;
import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Abstract class representing a message listener.
 * It extends {@link Feature} and provides a structure for handling received messages.
 * Subclasses must implement the {@link #handleMessage(MessageReceivedEvent)} method
 * to define specific message processing logic.
 */
public abstract class AbstractMessageListener extends Feature {
    /**
     * The name of the feature associated with this message listener.
     */
    protected final String featureName;

    /**
     * Constructor that initializes the message listener and registers the event listener.
     * The feature name will be prefixed with "feature.messageListener.".
     *
     * @param featureName The specific name of this message listener feature.
     *                    For example, if "powergemsResourcePackReply.ResourcePackSticky" is provided,
     *                    the full feature name will be "feature.messageListener.powergemsResourcePackReply.ResourcePackSticky".
     */
    public AbstractMessageListener(String featureName) {
        this.featureName = featureName;
        MessageListener.getInstance().registerMessageConsumer(this);
        registerFeature();
    }

    /**
     * Abstract method to be implemented by subclasses to define the logic for handling a received message.
     *
     * @param event The {@link MessageReceivedEvent} to handle.
     */
    public abstract void handleMessage(MessageReceivedEvent event);

    /**
     * Gets the feature name for this message listener.
     * The name is prefixed with "feature.messageListener.".
     *
     * @return The full feature name.
     */
    @Override
    public String getFeatureName() {
        return "feature.messageListener."+featureName;
    }
}