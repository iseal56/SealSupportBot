package dev.iseal.SSB.systems.testServer;

import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class TestServerCommand extends AbstractCommand {
    public TestServerCommand() {
        super(
                Commands.slash("testserver", "Create a test server given a zip")
                        .addOption(
                                OptionType.STRING,
                                "link",
                                "The link to the zip file",
                                false
                        )
                        .addOption(
                                OptionType.ATTACHMENT,
                                "file",
                                "The zip file to upload",
                                false
                        )
                ,
                true
        );
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {

    }
}
