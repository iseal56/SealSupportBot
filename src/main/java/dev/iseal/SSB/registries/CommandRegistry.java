package dev.iseal.SSB.registries;

import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.util.HashMap;

public class CommandRegistry {

    private static CommandRegistry INSTANCE;
    public static CommandRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CommandRegistry();
        }
        return INSTANCE;
    }

    private final Logger log = JDALogger.getLog("SBB-CommandRegistry");
    private final HashMap<String, CommandData> registeredCommands = new HashMap<>();

    private CommandRegistry() {}

    public void init() {
        // detect all commands in the package
        // and run registerCommand
        // then, register them with the JDA instance

        // Register all commands in the package
        Utils.findAllClassesInPackage("dev.iseal.SSB.commands", AbstractCommand.class)
                .forEach(commandClass -> {
                    try {
                        // Instantiate the command class
                        AbstractCommand command = (AbstractCommand) commandClass.getDeclaredConstructor().newInstance();
                        // Register the command
                        registerCommand(command.getCommand().getName(), command.getCommand());
                    } catch (Exception e) {
                        log.error("Failed to register command {}: {}", commandClass.getName(), e.getMessage());
                    }
                });

        SSBMain.getJDA().getGuildCache().forEach(
                guild -> guild.updateCommands().addCommands(
                        registeredCommands.values()
                ).queue(
                        success -> log.info("Command registered in guild {}", guild.getName()),
                        failure -> log.error("Failed to register command in guild {}: {}", guild.getName(), failure.getMessage())
                )
        );
    }

    public void registerCommand(String commandName, CommandData commandObject) {
        // Register the command with the command object
        log.info("Registering command: {}", commandName);
        registeredCommands.put(commandName, commandObject);
    }

}
