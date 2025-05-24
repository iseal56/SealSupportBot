package dev.iseal.SSB.systems.rootCommand;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.registries.FeatureRegistry;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import dev.iseal.SSB.utils.interfaces.Feature;
import dev.iseal.SSB.utils.utils.RuntimeInterpreter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RootCommand extends AbstractCommand {

    private final Yaml yaml = new Yaml("rootCommandConfig.yml", System.getProperty("user.dir")+ File.separator + "config" + File.separator + "rootCommand");
    private final String[] rootIDs;
    private final FeatureRegistry featureRegistry = FeatureRegistry.getInstance();
    private final Logger log = JDALogger.getLog(getClass());

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
                                        .addOption(
                                                OptionType.BOOLEAN,
                                                "onlyenabled",
                                                "Only list enabled features",
                                                false
                                        ),
                                new SubcommandData("eval", "Evaluate java code")
                                        .addOption(
                                                OptionType.STRING,
                                                "code",
                                                "The code to evaluate",
                                                false
                                        )
                                        .addOption(
                                                OptionType.ATTACHMENT,
                                                "code_file",
                                                "The code to evaluate from a file",
                                                false
                                        )
                        )
                ,
                true,
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
        event.getHook().setEphemeral(true);
        event.getHook().editOriginal("Processing command...").queue();
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
            case "eval" -> handleEval(event);
            default -> event.getHook().editOriginal("Unknown subcommand.").queue();
        }
    }

    private void handleReboot(SlashCommandInteractionEvent event) {
        event.getHook().editOriginal("Rebooting...").complete();
        log.debug("Rebooting... Requested at: " + System.currentTimeMillis());
        Utils.addTempFileData("root-reboot-requested-by", event.getMember().getId());
        Utils.addTempFileData("root-reboot-requested-at", Instant.now().getEpochSecond());

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
        if (featureName.equals(this.getFeatureName())) {
            event.getHook().editOriginal("You cannot disable this command.").queue();
            return; // don't disable this command
        }
        if (!featureRegistry.isFeatureRegistered(featureName)) {
            event.getHook().editOriginal("Feature " + featureName + " is not registered.").queue();
            return;
        }
        if (featureRegistry.isFeatureEnabled(featureName)) {
            featureRegistry.disableFeature(featureName);
            event.getHook().editOriginal("Feature " + featureName + " disabled.").queue();
        } else {
            event.getHook().editOriginal("Feature " + featureName + " is already disabled.").queue();
        }
    }

    private void handleEnableFeature(SlashCommandInteractionEvent event) {
        String featureName = event.getOption("feature").getAsString();
        if (!featureRegistry.isFeatureRegistered(featureName)) {
            event.getHook().editOriginal("Feature " + featureName + " is not registered.").queue();
            return;
        }
        if (!featureRegistry.isFeatureEnabled(featureName)) {
            featureRegistry.enableFeature(featureName);
            event.getHook().editOriginal("Feature " + featureName + " enabled.").queue();
        } else {
            event.getHook().editOriginal("Feature " + featureName + " is already enabled.").queue();
        }
    }

    private void handleListFeatures(SlashCommandInteractionEvent event) {
        boolean onlyEnabled = event.getOption("onlyenabled") != null && event.getOption("onlyenabled").getAsBoolean();
        List<String> features = new ArrayList<>(featureRegistry.listFeatures(false).stream().map(Feature::getFeatureName).toList());
        List<String> enabledFeatures = new ArrayList<>(featureRegistry.listFeatures(true).stream().map(Feature::getFeatureName).toList());
        features.sort(String::compareTo);
        enabledFeatures.sort(String::compareTo);
        StringBuilder response = new StringBuilder();

        for (String feature : features) {
            if (enabledFeatures.contains(feature)) {
                response.append(feature)
                        .append(", \n");
            } else if (!onlyEnabled) {
                response.append(feature)
                        .append(" (disabled)")
                        .append(", \n");
            }
        }

        if (response.length() > 2) {
            response.setLength(response.length() - 2); // Remove the last comma and space
        }

        event.getHook().editOriginal("Done").queue();
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Features");
        embed.setDescription(response.toString());
        embed.setColor(Color.GREEN);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleEval(SlashCommandInteractionEvent event) {
        String codeStr = event.getOption("code") != null ? event.getOption("code").getAsString() : null;
        Message.Attachment codeFile = event.getOption("code_file") != null ? event.getOption("code_file").getAsAttachment() : null;

        if (codeStr == null && codeFile == null) {
            event.getHook().editOriginal("No code provided.").queue();
            return;
        }
        if (codeStr != null && codeFile != null) {
            event.getHook().editOriginal("You can only provide one of code or code_file.").queue();
            return;
        }

        if (codeFile != null) {
            // Download the file and read the contents
            File tempFile = new File(System.getProperty("java.io.tmpdir") + File.separator + codeFile.getFileName());
            codeFile.getProxy().downloadToFile(tempFile)
                    .thenAccept(file -> {
                        try {
                            String code = Utils.readFile(file);
                            Object result = RuntimeInterpreter.evaluate(code);
                            event.getHook().editOriginal("Code evaluated successfully: " + result).queue();
                            file.delete(); // Clean up the temp file
                        } catch (Exception e) {
                            event.getHook().editOriginal("Failed to evaluate code: " + e.getMessage()).queue();
                            e.printStackTrace();
                        }
                    })
                    .exceptionally(e -> {
                        event.getHook().editOriginal("Failed to download file: " + e.getMessage()).queue();
                        return null;
                    });
        } else {
            try {
                Object result = RuntimeInterpreter.evaluate(codeStr);
                event.getHook().editOriginal("Code evaluated successfully: " + result).queue();
            } catch (Exception e) {
                StringBuilder sb = new StringBuilder();
                sb.append("Error: ").append(e.getMessage()).append("\n");
                sb.append("Stacktrace: \n");
                for (StackTraceElement element : e.getStackTrace()) {
                    sb.append(element.toString()).append("\n");
                }
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Error in eval");
                embed.setDescription(sb.toString());
                embed.setColor(Color.RED);
                event.getHook().editOriginal("Done").queue();
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            }
        }
    }

}
