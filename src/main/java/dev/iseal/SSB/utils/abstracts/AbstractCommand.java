package dev.iseal.SSB.utils.abstracts;

import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * Abstract class representing a slash command.
 * It extends {@link Feature} and provides a structure for command handling.
 */
public abstract class AbstractCommand extends Feature {

    /**
     * The command data associated with this command.
     */
    private final CommandData command;
    /**
     * Flag indicating whether the command should be deferred.
     */
    private final boolean doesDefer;
    /**
     * Flag indicating whether the command is ephemeral by default.
     */
    private final boolean isEphemeralByDefault;

    /**
     * Constructs an AbstractCommand.
     *
     * @param command   The {@link CommandData} for this command.
     * @param doesDefer A boolean indicating whether the command reply should be deferred.
     * @param isEphemeralByDefault A boolean indicating whether the command reply should be ephemeral by default.
     *                             <p>
     *                             WARN: This only applies if {@code doesDefer} is true.
     *                             If {@code doesDefer} is false, each reply must be set to be ephemeral manually and
     *                             this option will have no effect.
     */
    public AbstractCommand(CommandData command, boolean doesDefer, boolean isEphemeralByDefault) {
        // only allow in guilds
        command.setContexts(InteractionContextType.GUILD);
        this.command = command;
        this.doesDefer = doesDefer;
        this.isEphemeralByDefault = isEphemeralByDefault;
        registerFeature();
    }

    /**
     * Gets the feature name for this command.
     * The name is prefixed with "feature.command.".
     *
     * @return The feature name.
     */
    @Override
    public String getFeatureName() {
        return "feature.command."+command.getName();
    }

    /**
     * Gets the {@link CommandData} for this command.
     *
     * @return The command data.
     */
    public CommandData getCommand() {
        return command;
    }

    /**
     * Handles the slash command interaction.
     * It defers the reply if {@code doesDefer} is true, then calls {@link #actuallyHandleCommand(SlashCommandInteractionEvent)}.
     *
     * @param event The {@link SlashCommandInteractionEvent} to handle.
     */
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (doesDefer) {
            // Defer the command and set the ephemeral flag
            event.deferReply().setEphemeral(isEphemeralByDefault).queue();
        }

        // handle it in both cases
        actuallyHandleCommand(event);
    }

    /**
     * Abstract method to be implemented by subclasses to define the actual command logic.
     *
     * @param event The {@link SlashCommandInteractionEvent} to handle.
     */
    protected abstract void actuallyHandleCommand(SlashCommandInteractionEvent event);
}