package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.listeners.ModalInteractionListener;
import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class representing a modal interaction.
 * It provides a structure for creating and handling modal submissions.
 * Subclasses must implement {@link #createModal()} to define the modal structure
 * and {@link #handleEvent(ModalInteractionEvent)} to define the logic for handling modal submissions.
 * This class does not extend {@link Feature} directly, and thus modals are not registered as features by default.
 */
public abstract class AbstractModal {

    /**
     * A map to store modal instances, keyed by their class.
     * This allows for retrieving a pre-built modal object associated with a specific {@link AbstractModal} subclass.
     */
    private static final Map<Class<? extends AbstractModal>, Modal> MODALS = new HashMap<>();

    /**
     * Abstract method to be implemented by subclasses to define the structure of the modal.
     * This method is called during the construction of an {@link AbstractModal} instance.
     *
     * @return The {@link Modal} object representing the structure of this modal.
     */
    protected abstract Modal createModal();

    /**
     * Constructor that initializes the modal by calling {@link #createModal()}
     * and registers this instance as a consumer for modal interaction events
     * with the {@link ModalInteractionListener}.
     * The modal created by {@link #createModal()} is stored in the static {@link #MODALS} map.
     */
    public AbstractModal() {
        Modal modal = createModal();
        MODALS.put(this.getClass(), modal);
        ModalInteractionListener.getInstance().registerModalConsumer(modal.getId(), this::handleEvent);
    }

    /**
     * Abstract method to be implemented by subclasses to define the logic for handling a modal submission.
     * <p>
     * WARN: You need to reply to the event (e.g., by using {@link net.dv8tion.jda.api.interactions.callbacks.IReplyCallback#reply(String)})
     * or Discord send an error (thus triggering an exception).
     *
     * @param event The {@link ModalInteractionEvent} that triggered this method.
     */
    public abstract void handleEvent(ModalInteractionEvent event);

    /**
     * Retrieves the {@link Modal} object associated with the given {@link AbstractModal} subclass.
     * This allows for accessing the pre-built modal structure.
     *
     * @param modalClass The class of the {@link AbstractModal} for which to retrieve the modal.
     * @return The {@link Modal} instance associated with the specified class, or {@code null} if no modal is registered for that class.
     */
    public static Modal getModal(Class<? extends AbstractModal> modalClass) {
        return MODALS.get(modalClass);
    }
}