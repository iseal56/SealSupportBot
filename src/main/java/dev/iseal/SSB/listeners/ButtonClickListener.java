package dev.iseal.SSB.listeners;

import dev.iseal.SSB.utils.Utils;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ButtonClickListener extends ListenerAdapter {

    private static ButtonClickListener instance;
    public static ButtonClickListener getInstance() {
        if (instance == null) {
            instance = new ButtonClickListener();
        }
        return instance;
    }
    private ButtonClickListener() {}

    private final Map<String, Consumer<ButtonInteractionEvent>> consumerMap = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getButton().getId();
        if (id == null) {
            return;
        }

        Consumer<ButtonInteractionEvent> consumer = consumerMap.get(id);
        if (consumer != null) {
            consumer.accept(event);
        }
    }

    public void registerButtonConsumer(String[] ids, Consumer<ButtonInteractionEvent> consumer) {
        for (String id : ids) {
            consumerMap.put(id, consumer);
        }
    }

    public void registerButtonConsumer(String id, Consumer<ButtonInteractionEvent> consumer) {
        consumerMap.put(id, consumer);
    }

}
