package dev.iseal.SSB.listeners;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MessageListener extends ListenerAdapter {

    private static MessageListener instance = new MessageListener();

    public static MessageListener getInstance() {
        return instance;
    }

    private final List<Consumer<MessageReceivedEvent>> consumerList = new ArrayList<>();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        consumerList.forEach(consumer -> consumer.accept(event));
    }

    public void registerMessageConsumer(Consumer<MessageReceivedEvent> consumer) {
        consumerList.add(consumer);
    }

}
