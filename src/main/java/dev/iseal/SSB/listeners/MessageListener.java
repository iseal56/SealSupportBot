package dev.iseal.SSB.listeners;

import dev.iseal.SSB.registries.FeatureRegistry;
import dev.iseal.SSB.utils.abstracts.AbstractMessageListener;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

public class MessageListener extends ListenerAdapter {

    private static MessageListener instance = new MessageListener();
    public static MessageListener getInstance() {
        return instance;
    }

    private final Logger log = JDALogger.getLog(getClass());
    private final List<AbstractMessageListener> consumerList = new ArrayList<>();
    private final FeatureRegistry featureRegistry = FeatureRegistry.getInstance();
    private MessageListener() {
        // private constructor to prevent instantiation
    }
    private final ThreadPoolExecutor messageThreadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        messageThreadPool.execute(() -> {
            try {
                consumerList.forEach(consumer -> {
                    if (featureRegistry.isFeatureEnabled(consumer.getFeatureName())) {
                        consumer.handleMessage(event);
                    } else {
                        log.info("Feature {} is disabled. Skipping message handling.", consumer.getFeatureName());
                    }
                });
            } catch (Exception e) {
                // fail silently
                log.error("Failed to handle message received event: {}", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void registerMessageConsumer(AbstractMessageListener consumer) {
        consumerList.add(consumer);
    }

}
