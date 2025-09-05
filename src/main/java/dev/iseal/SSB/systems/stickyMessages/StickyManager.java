package dev.iseal.SSB.systems.stickyMessages;

import de.leonhard.storage.Json;
import de.leonhard.storage.Yaml;
import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.utils.abstracts.AbstractMessageListener;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StickyManager extends AbstractMessageListener {

    private final Json json = new Json("stickyMessages.json", System.getProperty("user.dir") + "/data/stickyMessages");
    private final Yaml yaml = new Yaml("config.yml", System.getProperty("user.dir") + "/config/stickyMessages");
    private final HashMap<StandardGuildMessageChannel, String> stickyMessages = new HashMap<>();
    private final Logger log = JDALogger.getLog(StickyManager.class);
    private final int MAX_COOLDOWN; // millis
    private final int MAX_TIME_BEFORE_LAST_MSG;
    private final static String FOOTER_CONTENT = "\n\n-# This is a sticky message. It's not replying to anyone, just here to stay.";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final HashMap<StandardGuildMessageChannel, Long> lastRunTime = new HashMap<>();
    private final HashMap<StandardGuildMessageChannel, ScheduledFuture<?>> scheduledTasks = new HashMap<>();
    private final HashMap<StandardGuildMessageChannel, Long> lastStickyMessageId = new HashMap<>();

    private static final StickyManager instance = new StickyManager();
    public static StickyManager getInstance() {
        return instance;
    }

    private StickyManager() {
        super("system.stickyMessages");
        json.setDefault("stickyMessages", new HashMap<String, String>());
        Map<String, String> storedMessages = json.getMapParameterized("stickyMessages");
        storedMessages.forEach((channelIdStr, message) -> {
            if (message != null) {
                try {
                    long channelId = Long.parseLong(channelIdStr);
                    var guildChannel = SSBMain.getJDA().getGuildChannelById(channelId);
                    if (guildChannel instanceof StandardGuildMessageChannel) {
                        stickyMessages.put((StandardGuildMessageChannel) guildChannel, message);
                    } else {
                        log.warn("Channel ID {} from storage is not a StandardGuildMessageChannel or not found.", channelIdStr);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid channel ID format in stickyMessages.json: {}", channelIdStr, e);
                    Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).forEachOrdered(log::warn);
                }
            }
        });

        yaml.setDefault("cooldown", 3000);
        yaml.setDefault("maxTimeBeforeLastMsg", 1000);
        MAX_COOLDOWN = yaml.getInt("cooldown");
        MAX_TIME_BEFORE_LAST_MSG = yaml.getInt("maxTimeBeforeLastMsg");
        log.info("StickyManager initialized. Cooldown: {}ms, MaxTimeBeforeLastMsg: {}ms. Loaded {} sticky messages.", MAX_COOLDOWN, MAX_TIME_BEFORE_LAST_MSG, stickyMessages.size());
    }

    @Override
    public void handleMessage(MessageReceivedEvent event) {
        StandardGuildMessageChannel channel;
        try {
            channel = (StandardGuildMessageChannel) event.getChannel().asGuildMessageChannel();
        } catch (ClassCastException | IllegalStateException e) {
            return; // Not a guild message channel
        }

        if (event.getAuthor().equals(event.getJDA().getSelfUser())) {
            return; // Skip self messages
        }

        String stickyMessage = stickyMessages.get(channel);
        if (stickyMessage != null) {
            long lastRun = lastRunTime.getOrDefault(channel, 0L);
            cancelExistingTask(channel);

            long timeSinceLastStickySent = System.currentTimeMillis() - lastRun;
            long delay = Math.max(0, MAX_COOLDOWN - timeSinceLastStickySent);

            log.debug("Scheduling sticky for channel {} with delay {}ms.", channel.getId(), delay);
            scheduleSticky(channel, stickyMessage, delay);
        }
    }

    private OptionalLong getLastMessageTimestamp(StandardGuildMessageChannel channel) {
        try {
            List<Message> messages = channel.getHistory().retrievePast(1).complete();
            if (!messages.isEmpty()) {
                return OptionalLong.of(messages.get(0).getTimeCreated().toInstant().toEpochMilli());
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve message history for channel {}: {}", channel.getId(), e.getMessage());
            Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).forEachOrdered(log::warn);
        }
        return OptionalLong.empty();
    }

    private void cancelExistingTask(StandardGuildMessageChannel channel) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(channel);
        if (existingTask != null) {
            existingTask.cancel(false);
            log.debug("Cancelled existing sticky task for channel {}", channel.getId());
        }
    }

    private void scheduleSticky(StandardGuildMessageChannel channel, String message, long initialDelay) {
        log.debug("Scheduling sticky message for channel: {} ({}), initial delay: {}ms", channel.getName(), channel.getId(), initialDelay);

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            long now = Instant.now().toEpochMilli();
            OptionalLong lastMsgTimestampOpt = getLastMessageTimestamp(channel);

            if (lastMsgTimestampOpt.isEmpty()) {
                log.warn("No messages in channel {} during scheduled sticky task. Aborting.", channel.getId());
                scheduledTasks.remove(channel);
                return;
            }

            long lastMsgTime = lastMsgTimestampOpt.getAsLong();
            long timeSinceLastMsg = now - lastMsgTime;

            if (timeSinceLastMsg >= MAX_TIME_BEFORE_LAST_MSG) {
                log.debug("Threshold exceeded, sending sticky message to channel {}", channel.getId());
                sendStickyMessage(channel, message);
                lastRunTime.put(channel, now);
                scheduledTasks.remove(channel);
            } else {
                long newDelay = MAX_TIME_BEFORE_LAST_MSG - timeSinceLastMsg;
                log.debug("Recent message detected, rescheduling with delay: {}ms", newDelay);
                scheduleSticky(channel, message, newDelay);
            }
        }, initialDelay, TimeUnit.MILLISECONDS);

        cancelExistingTask(channel); // Ensure no duplicate tasks
        scheduledTasks.put(channel, task);
        log.debug("Sticky message task scheduled successfully for channel: {}", channel.getId());
    }



    private void sendStickyMessage(StandardGuildMessageChannel channel, String content) {
        Long previousMessageId = lastStickyMessageId.get(channel);
        if (previousMessageId != null) {
            channel.retrieveMessageById(previousMessageId).queue(
                    prevMsg -> prevMsg.delete().queue(
                            success -> log.info("Deleted previous sticky message {} in channel {}", previousMessageId, channel.getName()),
                            error -> log.warn("Failed to delete previous sticky message {}: {}", previousMessageId, error.getMessage())
                    ),
                    error -> { // Commonly "Unknown Message" if already deleted
                        log.debug("Previous sticky message {} not found for deletion in channel {}: {}", previousMessageId, channel.getId(), error.getMessage());
                        lastStickyMessageId.remove(channel); // Assume it's gone
                    }
            );
        }

        channel.sendMessage("@silent\n"+content+FOOTER_CONTENT).queue(
                sentMessage -> {
                    lastStickyMessageId.put(channel, sentMessage.getIdLong());
                    log.info("Sent sticky message to channel: {} (ID: {})", channel.getName(), sentMessage.getId());
                },
                error -> log.error("Failed to send sticky message to channel {}: {}", channel.getId(), error.getMessage())
        );
    }

    public String addStickyMessage(StandardGuildMessageChannel channel, String message) {
        if (stickyMessages.containsKey(channel)) {
            return "Sticky message already exists for this channel!";
        }
        if (message.length() > 2000) {
            return "Message is too long! Maximum length is 2000 characters.";
        }
        if (message.isEmpty()) {
            return "Message is too short! Minimum length is 1 character.";
        }
        if (!channel.canTalk()) {
            return "I don't have permission to send messages in this channel!";
        }

        stickyMessages.put(channel, message);
        updateFile();
        log.info("Added sticky message to channel: {} | Message: {}", channel.getName(), message.substring(0, Math.min(50, message.length())) + (message.length() > 50 ? "..." : ""));
        return "Sticky message added successfully!";
    }

    public String removeStickyMessage(StandardGuildMessageChannel channel) {
        if (!stickyMessages.containsKey(channel)) {
            return "No sticky message found for this channel!";
        }
        stickyMessages.remove(channel);
        updateFile();
        log.info("Removed sticky message from channel: {}", channel.getName());
        return "Sticky message removed successfully!";
    }

    public String listStickyMessages(Guild guild) {
        StringBuilder sb = new StringBuilder();
        final boolean[] found = {false}; // Effectively final array for use in lambda

        stickyMessages.forEach((channel, message) -> {
            if (channel.getGuild().getIdLong() == guild.getIdLong()) {
                if (!found[0]) {
                    sb.append("Sticky messages in this server:\n");
                    found[0] = true;
                }
                String preview = message.substring(0, Math.min(70, message.length())) + (message.length() > 70 ? "..." : "");
                sb.append(String.format("Channel: %s (`#%s`) | Message: \"%s\"\n", channel.getName(), channel.getId(), preview));
            }
        });

        if (!found[0]) {
            return "No sticky messages found in this server!";
        }
        return sb.toString();
    }

    private void updateFile() {
        HashMap<String, String> convertedStickyMessages = new HashMap<>();
        stickyMessages.forEach((channel, message) -> convertedStickyMessages.put(String.valueOf(channel.getIdLong()), message));
        json.set("stickyMessages", convertedStickyMessages);
        log.info("Sticky messages storage updated. Total: {}", convertedStickyMessages.size());
    }
}