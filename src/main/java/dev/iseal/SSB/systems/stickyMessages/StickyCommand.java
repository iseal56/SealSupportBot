package dev.iseal.SSB.systems.stickyMessages;

import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

public class StickyCommand extends AbstractCommand {
    public StickyCommand() {
        super(
                Commands.slash("sticky", "Configures sticky messages.")
                        .addSubcommands(
                                new SubcommandData("add", "Adds a sticky message")
                                        .addOption(OptionType.CHANNEL, "channel", "The channel to add the sticky message to", true)
                                        .addOption(OptionType.STRING, "message", "The message to add as sticky", true),
                                new SubcommandData("remove", "Removes a sticky message")
                                        .addOption(OptionType.CHANNEL, "channel", "The channel to remove the sticky message from", true),
                                new SubcommandData("list", "Lists all sticky messages")
                        ),
                false,
                false
        );
    }

    private final Logger log = JDALogger.getLog(getClass());
    private final StickyManager stickyManager = StickyManager.getInstance();

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        String subCommand = event.getSubcommandName();
        //TODO: check if the user has permission to use this command
        Member user = event.getMember();

        if (!user.hasPermission(Permission.MANAGE_CHANNEL)) {
            log.debug("User " + user.getUser().getAsTag() + " tried to use the sticky command without permission.");
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        switch (subCommand) {
            case "add" -> {
                StandardGuildMessageChannel channel = event.getOption("channel")
                        .getAsChannel()
                        .asStandardGuildMessageChannel();
                String message = event.getOption("message").getAsString();
                event.reply(stickyManager.addStickyMessage(channel, message)).setEphemeral(true).queue();
            }
            case "remove" -> {
                StandardGuildMessageChannel channel = event.getOption("channel")
                        .getAsChannel()
                        .asStandardGuildMessageChannel();
                event.reply(stickyManager.removeStickyMessage(channel)).setEphemeral(true).queue();
            }
            case "list" -> event.reply(stickyManager.listStickyMessages(event.getGuild())).setEphemeral(true).queue();
            case null -> event.reply("No subcommand provided.").setEphemeral(true).queue();
            default -> event.reply("Unknown subcommand: " + subCommand).setEphemeral(true).queue();
        }
    }
}
