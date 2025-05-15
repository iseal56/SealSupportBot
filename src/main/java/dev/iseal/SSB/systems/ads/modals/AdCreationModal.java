package dev.iseal.SSB.systems.ads.modals;

import dev.iseal.SSB.managers.AdDataManager;
import dev.iseal.SSB.utils.abstracts.AbstractModal;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

public class AdCreationModal extends AbstractModal {

    private final Logger log = JDALogger.getLog("SBB-ACM");

    @Override
    protected Modal createModal() {
        TextInput adInput = TextInput.create("ad", "Ad", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Insert your ad here")
                .setMinLength(0)
                .setMaxLength(2000)
                .build();

        return Modal.create("adModal", "AD Creation")
                .addComponents(ActionRow.of(adInput))
                .build();
    }

    @Override
    public void handleEvent(ModalInteractionEvent event) {
        String ad = event.getValue("ad").getAsString();

        String returnString = AdDataManager.getInstance().addPendingAd(ad, event.getUser());
        log.info("{} added an ad: {} with return string: {}", event.getUser().getName(), ad, returnString);
        event.reply(returnString).setEphemeral(true).queue();
    }

    public static Modal getModal() {
        return getModal(AdCreationModal.class);
    }
}
