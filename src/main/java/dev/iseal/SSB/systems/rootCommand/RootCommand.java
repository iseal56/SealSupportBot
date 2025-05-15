package dev.iseal.SSB.systems.rootCommand;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.registries.FeatureRegistry;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RootCommand extends AbstractCommand {

    private final Yaml yaml = new Yaml("rootCommandConfig.yml", System.getProperty("user.dir")+ File.separator + "config" + File.separator + "rootCommand");
    private final String[] rootIDs;
    private final FeatureRegistry featureRegistry = FeatureRegistry.getInstance();

    public RootCommand() {
        super(
                Commands.slash("root", "Dev only command. Do not touch.")
                        .addSubcommands(
                                new SubcommandData("reboot", "Reboot the bot"),
                                new SubcommandData("disablefeature", "Disable a feature")
                                        .addOption(
                                                OptionType.STRING,
                                                "feature",
                                                "The feature to disable"
                                        ),
                                new SubcommandData("enablefeature", "Enable a feature")
                                        .addOption(
                                                OptionType.STRING,
                                                "feature",
                                                "The feature to enable"
                                        ),
                                new SubcommandData("listfeatures", "List all features")
                        )
                ,
                true
        );
        ArrayList<String> defaultRootIDs = new ArrayList<>();
        defaultRootIDs.add("398908171357519872");
        yaml.setDefault("rootIDs", defaultRootIDs);
        rootIDs = yaml.getStringList("rootIDs").toArray(new String[0]);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        event.reply("Processing..").setEphemeral(true).queue();
        if (!Arrays.stream(rootIDs).anyMatch(id -> id.equals(event.getUser().getId()))) {
            // user is not authorized to use this command.
            event.getHook().editOriginal("You are not authorized to use this command.").queue();
            return;
        }
        if (subcommand == null || subcommand.isEmpty()) {
            event.getHook().editOriginal("No subcommand provided.").queue();
            return;
        }
        switch (subcommand) {
            case "reboot" -> handleReboot(event);
            case "disablefeature" -> handleDisableFeature(event);
            case "enablefeature" -> handleEnableFeature(event);
            case "listfeatures" -> handleListFeatures(event);
            default -> event.getHook().editOriginal("Unknown subcommand.").queue();
        }
    }

    private void handleReboot(SlashCommandInteractionEvent event) {
        event.getHook().editOriginal("Rebooting...").complete();
        Utils.addTempFileData("root-reboot-requested-by", event.getMember().getId());
        Utils.addTempFileData("root-reboot-requested-at", System.currentTimeMillis());

        // Start a new process before shutting down
        try {
            // Get the path to the running JAR
            String jarPath = new File(RootCommand.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();

            // Create a process builder to restart the application
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", jarPath, "--reboot");
            processBuilder.inheritIO(); // Inherit IO streams
            processBuilder.start();

            event.getJDA().shutdown();
            System.exit(0);
        } catch (Exception e) {
            event.getHook().editOriginal("Failed to restart: " + e.getMessage()).queue();
        }
    }

    private void handleDisableFeature(SlashCommandInteractionEvent event) {
        String featureName = event.getOption("feature").getAsString();
        if (featureRegistry.isFeatureEnabled(featureName)) {
            featureRegistry.disableFeature(featureName);
            event.getHook().editOriginal("Feature " + featureName + " disabled.").queue();
        } else {
            event.getHook().editOriginal("Feature " + featureName + " is already disabled.").queue();
        }
    }

    private void handleEnableFeature(SlashCommandInteractionEvent event) {
        String featureName = event.getOption("feature").getAsString();
        if (!featureRegistry.isFeatureEnabled(featureName)) {
            featureRegistry.enableFeature(featureName);
            event.getHook().editOriginal("Feature " + featureName + " enabled.").queue();
        } else {
            event.getHook().editOriginal("Feature " + featureName + " is already enabled.").queue();
        }
    }

    private void handleListFeatures(SlashCommandInteractionEvent event) {
        List<String> features = featureRegistry.listFeatures(false);
        List<String> enabledFeatures = featureRegistry.listFeatures(true);
        StringBuilder response = new StringBuilder("Features: ");

        for (String feature : features) {
            response.append(feature);
            if (!enabledFeatures.contains(feature)) {
                response.append(" (disabled)");
            }
            response.append(", ");
        }

        if (response.length() > 2) {
            response.setLength(response.length() - 2); // Remove the last comma and space
        }

        event.getHook().editOriginal(response.toString()).queue();
    }

}
