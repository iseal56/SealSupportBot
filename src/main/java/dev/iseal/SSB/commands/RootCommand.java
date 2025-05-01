package dev.iseal.SSB.commands;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class RootCommand extends AbstractCommand {

    private final Yaml yaml = new Yaml("rootCommandConfig.yml", System.getProperty("user.dir")+ File.separator + "config" + File.separator + "rootCommand");
    private final String[] rootIDs;

    public RootCommand() {
        super(
                Commands.slash("root", "Dev only command. Do not touch.")
                        .addSubcommands(
                                new SubcommandData("reboot", "Reboot the bot")
                        )
                ,
                false
        );
        ArrayList<String> defaultRootIDs = new ArrayList<>();
        defaultRootIDs.add("398908171357519872");
        yaml.setDefault("rootIDs", defaultRootIDs);
        rootIDs = yaml.getStringList("rootIDs").toArray(new String[0]);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (!Arrays.stream(rootIDs).anyMatch(id -> id.equals(event.getUser().getId()))) {
            // user is not authorized to use this command.
            event.reply("You are not authorized to use this command.").setEphemeral(true).queue();
            return;
        }
        if (subcommand == null || subcommand.isEmpty()) {
            event.reply("No subcommand provided.").setEphemeral(true).queue();
            return;
        }
        switch (subcommand.toLowerCase()) {
            case "reboot" -> {
                event.reply("Rebooting...").setEphemeral(true).complete();
                Utils.addTempFileData("root-reboot-requested-by", event.getMember().getId());
                Utils.addTempFileData("root-reboot-requested-at", System.currentTimeMillis());
                event.getJDA().shutdown();
                System.exit(0);
            }
            default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
        }
    }
}
