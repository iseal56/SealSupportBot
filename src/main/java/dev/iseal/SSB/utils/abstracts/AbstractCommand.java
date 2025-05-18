package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public abstract class AbstractCommand extends Feature {

    private final CommandData command;
    private final boolean doesDefer;

    public AbstractCommand(CommandData command, boolean doesDefer) {
        // only allow in guilds
        command.setContexts(InteractionContextType.GUILD);
        this.command = command;
        this.doesDefer = doesDefer;
        registerFeature();
    }

    @Override
    public String getFeatureName() {
        return "feature.command."+command.getName();
    }

    public CommandData getCommand() {
        return command;
    }

    public void handleCommand(SlashCommandInteractionEvent event) {
        if (doesDefer) {
            // Defer the command
            event.deferReply().queue();
        }

        // handle it in both cases
        actuallyHandleCommand(event);
    }

    protected abstract void actuallyHandleCommand(SlashCommandInteractionEvent event);
}
