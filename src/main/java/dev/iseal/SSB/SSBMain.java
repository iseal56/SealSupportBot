package dev.iseal.SSB;

import dev.iseal.SSB.registries.CommandRegistry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SSBMain {

    public static final String VERSION = "1.0.0.0-DEV1";
    private static final Logger log = JDALogger.getLog("SBB Main");
    private static JDA jda;

    public static void main(String[] args) {
        String token = readToken();
        JDA api = JDABuilder.createDefault(token).build();
        try {
            api.awaitReady();
            log.info("SSB is ready!");
        } catch (InterruptedException e) {
            log.warn("Failed to start SSB. Shutting down.");
            System.exit(0);
        }
        jda = api;

        log.info("Initializing SSB commands...");
        CommandRegistry.getInstance().init();
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

}
