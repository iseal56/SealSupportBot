package dev.iseal.SSB.systems.ads.modals;

import dev.iseal.SSB.managers.AdDataManager;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractModal;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;

public class AdDenialModal extends AbstractModal {

    @Override
    protected Modal createModal() {
        TextInput body = TextInput.create("body", "Body", TextInputStyle.PARAGRAPH)
                .setPlaceholder("The ad was denied for the reason...")
                .setMinLength(0)
                .setMaxLength(2000)
                .build();

        return Modal.create("denyReasonModal", "Deny reason")
                .addComponents(ActionRow.of(body))
                .build();
    }

    @Override
    public void handleEvent(ModalInteractionEvent event) {
        // this code is quite shit.
        MessageEmbed embed = event.getMessage().getEmbeds().get(0);
        String adID = embed.getFooter().getText().replace("Ad ID: ", "");

        // send message to dms
        long userID = AdDataManager.getInstance().getUserIDbyAdID(adID);
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Ad denied");
        builder.setColor(Color.RED);
        builder.setDescription("Your ad in the "+event.getGuild().getName()+" server has been denied with the following reason: " +
                "\n" + event.getValue("body").getAsString());

        Utils.sendEmbed(userID, builder);

        AdDataManager.getInstance().removeAdID(adID);
        event.getMessage().delete().queue();
        event.reply("The ad was denied successfully").setEphemeral(true).queue();
    }

    public static Modal getModal() {
        return getModal(AdDenialModal.class);
    }

}
