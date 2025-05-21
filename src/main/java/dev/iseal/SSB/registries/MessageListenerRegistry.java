package dev.iseal.SSB.registries;

import dev.iseal.SSB.listeners.MessageListener;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import dev.iseal.SSB.utils.abstracts.AbstractMessageListener;
import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.Arrays;

public class MessageListenerRegistry {

    private static final MessageListenerRegistry instance = new MessageListenerRegistry();
    public static MessageListenerRegistry getInstance() {
        return instance;
    }

    private final Logger log = JDALogger.getLog(getClass());

    private MessageListenerRegistry() {
        // Private constructor to prevent instantiation
    }

    public void init() {
        Utils.findAllClassesInPackage("dev.iseal.SSB.systems", AbstractMessageListener.class)
                .forEach(messageListener -> {
                    try {
                        // Instantiate the message listener class
                        @SuppressWarnings("unchecked")
                        AbstractMessageListener listener = Feature.getFeatureInstance((Class<? extends AbstractMessageListener>) messageListener);
                        // will register itself
                    } catch (Exception e) {
                        log.error("Failed to register message listener {}: {}", messageListener.getName(), e.getMessage());
                        Arrays.stream(e.getStackTrace()).forEach(element -> log.error(element.toString()));
                    }
                });
    }

}
