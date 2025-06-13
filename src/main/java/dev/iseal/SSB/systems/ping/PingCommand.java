package dev.iseal.SSB.systems.ping;

import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class PingCommand extends AbstractCommand {

    public PingCommand() {
        super(Commands.slash("ping", "Ping the bot")
                .addOption(OptionType.BOOLEAN, "ephemeral", "Reply to the ping privately?", false),
                false, false);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        boolean ephemeral = event.getOption("ephemeral") != null && event.getOption("ephemeral").getAsBoolean();
        String replyMessage = "Pong! Latency: " + event.getJDA().getGatewayPing() + "ms";

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Ping Response");
        eb.setDescription(replyMessage);
        if (ephemeral) {
            event.replyEmbeds(eb.build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(eb.build()).queue();
        }
    }
}
