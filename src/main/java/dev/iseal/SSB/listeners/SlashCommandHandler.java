package dev.iseal.SSB.listeners;

import dev.iseal.SSB.registries.CommandRegistry;
import dev.iseal.SSB.registries.FeatureRegistry;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class SlashCommandHandler extends ListenerAdapter {

    private final CommandRegistry registry = CommandRegistry.getInstance();
    private final Logger log = JDALogger.getLog("SBB-SCH");
    private final ThreadPoolExecutor commandThreadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    private final FeatureRegistry featureRegistry = FeatureRegistry.getInstance();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Handle the slash command interaction
        String commandName = event.getName();
        String commandString = event.getCommandString();
        String userDisplayName = event.getUser().getName();
        log.info("Received command {} by {}", commandString, userDisplayName);
        log.info("Checking if command {} is registered and enabled.", commandName);
        if (!featureRegistry.isFeatureEnabled("feature.command." + commandName)) {
            log.info("Command {} is disabled.", commandName);
            event.reply("This command is disabled. Ask an admin for more info.").setEphemeral(true).queue();
            return;
        }

        // Check if the command is registered and handle it
        AbstractCommand command = registry.getCommand(commandName);
        if (command != null) {
            commandThreadPool.execute(() -> {
                try {
                    command.handleCommand(event);
                } catch (Exception e) {
                    log.error("Failed to handle command {}: {}", commandString, e.getMessage());
                    event.reply("An error occurred while processing your command.").setEphemeral(true).queue();
                }
            });
        } else {
            event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
        }
    }
}
