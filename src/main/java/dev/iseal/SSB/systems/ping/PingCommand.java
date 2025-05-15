package dev.iseal.SSB.systems.ping;

import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class PingCommand extends AbstractCommand {

    public PingCommand() {
        super(Commands.slash("ping", "Ping the bot")
                .addOption(OptionType.BOOLEAN, "ephemeral", "Reply to the ping privately?", false),
                false);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        if (event.getOption("ephemeral") != null) {
            boolean ephemeral = event.getOption("ephemeral").getAsBoolean();
            boolean hasPermission = event.getMember().hasPermission(Permission.BAN_MEMBERS);
            if (hasPermission && !ephemeral) {
                event.reply("Pong! Hey admin!").setEphemeral(false).queue();
            } else if (!hasPermission && !ephemeral) {
                event.reply("You don't have permission to do that!").setEphemeral(true).queue();
            } else {
                event.reply("Pong! " + (hasPermission ? "Hey admin!" : "")).setEphemeral(true).queue();
            }
        } else {
            event.reply("Pong!").setEphemeral(true).queue();
        }
    }
}
