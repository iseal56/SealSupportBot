package dev.iseal.SSB;

import dev.iseal.SSB.listeners.ButtonClickListener;
import dev.iseal.SSB.listeners.MessageListener;
import dev.iseal.SSB.listeners.ModalInteractionListener;
import dev.iseal.SSB.listeners.SlashCommandHandler;
import dev.iseal.SSB.managers.AdDataManager;
import dev.iseal.SSB.registries.CommandRegistry;
import dev.iseal.SSB.registries.FeatureRegistry;
import dev.iseal.SSB.registries.MessageListenerRegistry;
import dev.iseal.SSB.registries.ModalRegistry;
import dev.iseal.SSB.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;

public class SSBMain {

    private static final Logger log = JDALogger.getLog(SSBMain.class);

    private static JDA jda;

    public static void main(String[] args) {
        String token = readToken();
        JDA api = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)

                //listeners
                .addEventListeners(new SlashCommandHandler())
                .addEventListeners(ButtonClickListener.getInstance())
                .addEventListeners(ModalInteractionListener.getInstance())
                .addEventListeners(MessageListener.getInstance())

                // set the activity
                .setActivity(Activity.watching("the world burn"))
                //finally, build the JDA instance
                .build();
        try {
            api.awaitReady();
            log.info("SSB is ready!");
        } catch (InterruptedException e) {
            log.warn("Failed to start SSB. Shutting down.");
            System.exit(0);
        }
        jda = api;

        log.info("SSB is starting up...");

        log.info("Initializing critical registries...");
        FeatureRegistry.getInstance().init();
        log.info("Critical registries initialized!");

        log.info("Initializing SSB registries...");
        CommandRegistry.getInstance().init();
        ModalRegistry.getInstance().init();
        MessageListenerRegistry.getInstance().init();
        log.info("SSB registries initialized!");

        log.info("Initializing SSB data managers...");
        AdDataManager.getInstance();
        log.info("SSB data managers initialized!");

        log.info("Checking if bot has been rebooted manually...");
        if (Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("--reboot"))) {
            log.info("Bot has been rebooted manually. Checking for reboot request...");
            // check if the bot was rebooted manually
            String id = Utils.getTempFileData("root-reboot-requested-by", "");
            Long time = Utils.getTempFileData("root-reboot-requested-at", 0L);
            if (id != null && !id.isEmpty() && time != null && time != 0L) {
                log.info("Bot was rebooted manually. Finishing reboot request...");
                checkForReboot();
            } else {
                log.error("No reboot request found, yet the reboot argument was passed.");
            }
        }
        log.info("Check done!");
        log.info("Loading done!");
        log.info("SSB is now running as {}!", api.getSelfUser().getAsTag());
    }

    private static String readToken() {
        String token = null;
        try {
            // read from a token.txt file in the same directory as the jar
            Path path = Paths.get("token.txt");
            token = new String(Files.readAllBytes(path));
        } catch (java.io.IOException e) {
            log.warn("Token file not found. Please create a token.txt file with your bot token.");
            log.warn("Shutting down.");
            System.exit(0);
        }
        return token;
    }

    public static JDA getJDA() {
        return jda;
    }

    private static void checkForReboot() {
        String id = Utils.getTempFileData("root-reboot-requested-by", "");
        Long time = Utils.getTempFileData("root-reboot-requested-at", 0L);
        long now = Instant.now().getEpochSecond();
        if (id == null || id.isEmpty()) {
            return;
        }
        if (time == null || time == 0L) {
            return;
        }
        // get user and channel
        User user = jda.retrieveUserById(id).complete();
        PrivateChannel channel = user.openPrivateChannel().complete();
        // check if the reboot was requested more than 5 minutes ago
        if (now - time > 5 * 60 * 1000) {
            log.error("Reboot requested by {} was more than 5 minutes ago.", id);
            log.error("There might be an issue with the bot.");

            // dm the user
            channel.sendMessage("The reboot requested was more than 5 minutes ago!").queue();
            return;
        }

        int secondsTaken = (int) (now - time) / 1000;
        float minutesTaken = (float) secondsTaken / 60;
        Utils.removeTempFileData("root-reboot-requested-by");
        Utils.removeTempFileData("root-reboot-requested-at");
        log.debug("Reboot requested by {} complete! It took {} seconds ({} minutes). Requested at {}", id, secondsTaken, minutesTaken, time);
        // dm the user
        channel.sendMessage("The reboot requested <t:"+time+":F> has been completed. It took "+secondsTaken+" seconds ("+minutesTaken+" minutes).").queue();
        log.info("Reboot requested by {} complete!", id);
    }

}
