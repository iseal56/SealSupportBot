package dev.iseal.SSB.systems;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.listeners.MessageListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.DetachedEntityException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class StopReplyPing {

    private static final Logger log = JDALogger.getLog(StopReplyPing.class);

    private static final StopReplyPing INSTANCE = new StopReplyPing();
    public static StopReplyPing getInstance() {
        return INSTANCE;
    }

    private final Yaml yaml = new Yaml("stopReplyPings.yml", System.getProperty("user.dir")+ File.separator + "config" + File.separator + "stopReplyPings");
    private final ArrayList<Long> bannedIDs = new ArrayList<>();

    public void init() {
        MessageListener.getInstance().registerMessageConsumer(this::execute);
        ArrayList<Long> defaultList = new ArrayList<>();
        defaultList.add(398908171357519872L);
        yaml.setDefault("bannedIDs", defaultList);
        yaml.setDefault("timeoutTime", 120);
        yaml.setDefault("logChannelID", 0L);
        yaml.getListParameterized("bannedIDs").forEach(id -> {
            if (id != null) {
                bannedIDs.add(Long.valueOf((String)id));
            }
        });
    }

    private void execute(MessageReceivedEvent event) {
        Message referenced = event.getMessage().getReferencedMessage();
        if (referenced == null) {
            return;
        }
        // get the original author's ID
        long originalAuthorId = referenced.getAuthor().getIdLong();

        boolean isProtectedUser = bannedIDs.stream()
                .anyMatch(id -> id.equals(originalAuthorId));

        if (!isProtectedUser) {
            return;
        }

        // check if the reply actually pings the original author
        boolean includesPing = event.getMessage().getMentions().getUsers().stream()
                .anyMatch(user -> user.getIdLong() == originalAuthorId);

        if (!includesPing) {
            return; // No ping, don't time out
        }

        Member member = event.getMember();

        if (member == null) {
            log.warn("Member is null. This should not happen. Please report this to the developer.");
            return;
        }

        if (member.getUser().isBot()) {
            return; // don't time out bots
        }

        log.info("Stopping reply ping from user {} to user {}", member.getEffectiveName(), referenced.getAuthor().getName());
        // timeout the user
        member.timeoutFor(yaml.getLong("timeoutTime"), TimeUnit.SECONDS).reason("Reply-Pinging to a protected user").queue();

        // send a modlog message
        TextChannel modLogChannel = SSBMain.getJDA().getTextChannelById(yaml.getLong("logChannelID"));
        if (modLogChannel == null) {
            log.warn("Mod log channel not found. Please set the mod log channel ID in the config.");
            return;
        }
        try {
            modLogChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("User " + member.getEffectiveName() + " has been timed out for reply-pinging to a message.")
                            .setThumbnail(member.getAvatarUrl())
                            .setDescription("The user reply-pinged in " + referenced.getChannel().getAsMention() + " with the message: \n"
                                    + event.getMessage().getContentDisplay() + "\n and therefore has been timed out")
                            .build()
            ).queue();
        } catch (InsufficientPermissionException e) {
            log.warn("Couldn't send messages to the mod log channel. Please check if the bot has permission to send messages in the channel.");
            log.warn("Error: {}", e.getMessage());
        }

        // send a dm to the user
        try {
            member.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("You have been timed out for reply-pinging to a protected user.")
                            .setDescription("You have been timed out for reply-pinging to " + referenced.getAuthor().getName() + ".")
                            .build()
            ).queue());
        } catch (InsufficientPermissionException e) {
            log.warn("Couldn't send messages to the user. Please check if the bot has permission to send messages in DMs.");
            log.warn("Error: {}", e.getMessage());
        } catch (UnsupportedOperationException e) {
            // ignore, user is a bot (how the fuck did this happen?)
        }
    }

}
