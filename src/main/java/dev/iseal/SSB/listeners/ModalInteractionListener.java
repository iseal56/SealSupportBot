package dev.iseal.SSB.listeners;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ModalInteractionListener extends ListenerAdapter {

    private static ModalInteractionListener instance;
    public static ModalInteractionListener getInstance() {
        if (instance == null) {
            instance = new ModalInteractionListener();
        }
        return instance;
    }
    private ModalInteractionListener() {}

    private final Map<String, Consumer<ModalInteractionEvent>> consumerMap = new HashMap<>();
    private final Logger log = JDALogger.getLog("SBB-MIL");

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();

        Consumer<ModalInteractionEvent> consumer = consumerMap.get(id);
        if (consumer != null) {
            consumer.accept(event);
        } else {
            // we have to reply to the event or discord gets mad
            event.reply("Unknown modal interaction: " + id+". Report this to an admin.").setEphemeral(true).queue();
            log.info("Modal interaction with unknown id: {}", id);
        }
    }

    public void registerModalConsumer(String[] ids, Consumer<ModalInteractionEvent> consumer) {
        for (String id : ids) {
            consumerMap.put(id, consumer);
        }
    }

    public void registerModalConsumer(String id, Consumer<ModalInteractionEvent> consumer) {
        consumerMap.put(id, consumer);
    }
}
