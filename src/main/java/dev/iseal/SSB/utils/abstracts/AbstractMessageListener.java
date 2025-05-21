package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.listeners.MessageListener;
import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public abstract class AbstractMessageListener extends Feature {
    protected final String featureName;

    /**
     * Constructor that initializes the message listener and registers the event listener.
     *
     * @param featureName The name of the feature constructed as "system.&lt;featureName&gt;"
     *                    example: system.powergemsResourcePackReply.ResourcePackSticky
     */
    public AbstractMessageListener(String featureName) {
        this.featureName = featureName;
        MessageListener.getInstance().registerMessageConsumer(this);
        registerFeature();
    }

    public abstract void handleMessage(MessageReceivedEvent event);

    @Override
    public String getFeatureName() {
        return "feature.messageListener."+featureName;
    }
}
