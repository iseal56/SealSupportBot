package dev.iseal.SSB.commands;

import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class PingCommand extends AbstractCommand {
    public PingCommand() {
        super(Commands.slash("ping", "Ping the bot")
                .addOption(OptionType.BOOLEAN, "reply", "Reply to the ping", false),
                false);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {

    }
}
