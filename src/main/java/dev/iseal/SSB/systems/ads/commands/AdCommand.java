package dev.iseal.SSB.systems.ads.commands;

import dev.iseal.SSB.systems.ads.modals.AdCreationModal;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class AdCommand extends AbstractCommand {

    public AdCommand() {
        super(
                Commands.slash("ad", "Advertise something of yours"),
                false,
                false
        );
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        event.replyModal(AdCreationModal.getModal()).queue();
    }
}
