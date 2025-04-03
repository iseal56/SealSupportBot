package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.listeners.ModalInteractionListener;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractModal {

    private static final Map<Class<? extends AbstractModal>, Modal> MODALS = new HashMap<>();

    // Abstract method to be implemented by subclasses
    protected abstract Modal createModal();

    /**
     * Constructor that initializes the modal and registers the event listener.
     */
    public AbstractModal() {
        MODALS.put(this.getClass(), createModal());
        ModalInteractionListener.getInstance().registerModalConsumer(MODALS.get(this.getClass()).getId(), this::handleEvent);
    }

    /**
     * This method is called when the modal is submitted.
     * WARN: you need to reply to the event or discord gets mad.
     *
     * @param event The ModalInteractionEvent that triggered this method.
     */
    public abstract void handleEvent(ModalInteractionEvent event);

    public static Modal getModal(Class<? extends AbstractModal> modalClass) {
        return MODALS.get(modalClass);
    }
}