package dev.iseal.SSB.registries;

import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import dev.iseal.SSB.utils.interfaces.Feature;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.stream.Collectors;

public class CommandRegistry {

    private static CommandRegistry INSTANCE;
    public static CommandRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CommandRegistry();
        }
        return INSTANCE;
    }

    private final Logger log = JDALogger.getLog("SBB-CommandRegistry");
    private final HashMap<String, AbstractCommand> registeredCommands = new HashMap<>();

    private CommandRegistry() {}

    public void init() {
        // Register all commands in the package
        Utils.findAllClassesInPackage("dev.iseal.SSB.systems", AbstractCommand.class)
                .forEach(commandClass -> {
                    try {
                        // Instantiate the command class
                        AbstractCommand command = Feature.getFeatureInstance((Class<? extends AbstractCommand>) commandClass);
                        // Register the command
                        registerCommand(command.getCommand().getName(), command);
                    } catch (Exception e) {
                        log.error("Failed to register command {}: {} {}", commandClass.getName(), e.getMessage(), e.getStackTrace());
                    }
                });

        SSBMain.getJDA().getGuildCache().forEach(
                guild -> guild.updateCommands().addCommands(
                        registeredCommands.values().stream()
                                .map(AbstractCommand::getCommand)
                                .collect(Collectors.toList())
                ).queue(
                        success -> log.info("Command registered in guild {}", guild.getName()),
                        failure -> log.error("Failed to register command in guild {}: {}", guild.getName(), failure.getMessage())
                )
        );
    }

    public void registerCommand(String commandName, AbstractCommand commandObject) {
        // Register the command with the command object
        log.info("Registering command: {}", commandName);
        registeredCommands.put(commandName, commandObject);
    }

    public boolean isCommandRegistered(String commandName) {
        return registeredCommands.containsKey(commandName);
    }

    public AbstractCommand getCommand(String commandName) {
        if (!isCommandRegistered(commandName)) {
            log.warn("Command {} is not registered", commandName);
            return null;
        }
        return registeredCommands.get(commandName);
    }

}
