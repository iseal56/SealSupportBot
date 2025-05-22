package dev.iseal.SSB.systems.testServer;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class TestServerCommand extends AbstractCommand {

    private final Yaml config = new Yaml("config.yml", System.getProperty("user.dir") + "/config/testServer");
    private final ArrayList<Long> allowedUsers = new ArrayList<>();
    private final Logger log = JDALogger.getLog(getClass());
    private final HashMap<UUID, DockerHandler> servers = new HashMap<>();

    public TestServerCommand() {
        super(
                Commands.slash("testserver", "Create a test server given a zip")
                        .addSubcommands(
                                new SubcommandData("create", "Create a test server given a zip")
                                        .addOption(OptionType.STRING, "link", "A direct link to a zip file", false)
                                        .addOption(OptionType.ATTACHMENT, "file", "A zip file", false)
                                        .addOption(OptionType.STRING, "minecraftversion", "Minecraft version (e.g., 1.20.4), defaults to LATEST", false),
                                new SubcommandData("delete", "Delete a test server")
                                        .addOption(OptionType.STRING, "id", "The ID of the server to delete", true)
                        ),
                true
        );
        log.debug("Initializing TestServerCommand");
        ArrayList<String> allowedUsersDefault = new ArrayList<>();
        allowedUsersDefault.add("398908171357519872");
        config.setDefault("allowedUsers", allowedUsersDefault);
        config.getListParameterized("allowedUsers").forEach(
                user -> allowedUsers.add(Long.parseLong(String.valueOf(user)))
        );
        log.debug("Final allowedUsers: {}", allowedUsers);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        log.debug("actuallyHandleCommand called by user: {}, command: /testserver {}", event.getUser().getEffectiveName(), event.getSubcommandName());

        if (!allowedUsers.contains(event.getUser().getIdLong())) {
            log.info("User {} ({}) attempted to use testServer command but is not allowed.", event.getUser().getEffectiveName(), event.getUser().getIdLong());
            event.reply("You are not allowed to use this command.").setEphemeral(true).queue();
            log.debug("Permission denied for user {}", event.getUser().getIdLong());
            return;
        }
        log.debug("User {} is allowed to use the command.", event.getUser().getIdLong());

        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            log.warn("Subcommand name is null for event: {}", event.getCommandString());
            event.reply("Unknown subcommand.").setEphemeral(true).queue();
            return;
        }
        log.debug("Subcommand received: {}", subcommandName);

        if (subcommandName.equalsIgnoreCase("create")) {
            log.debug("Executing 'create' subcommand.");

            String link = event.getOption("link", OptionMapping::getAsString);
            Message.Attachment fileAttachment = event.getOption("file", OptionMapping::getAsAttachment);
            String minecraftVersionArg = event.getOption("minecraftversion", "LATEST", OptionMapping::getAsString);

            log.debug("Create options: link='{}', fileAttachment='{}', minecraftVersion='{}'",
                    link, (fileAttachment != null ? fileAttachment.getFileName() : "null"), minecraftVersionArg);

            if (link == null && fileAttachment == null) {
                log.debug("Both link and fileAttachment are null.");
                event.getHook().editOriginal("You need to provide either a link or a file.").queue();
                return;
            }
            if (link != null && fileAttachment != null) {
                log.debug("Both link and fileAttachment are provided.");
                event.getHook().editOriginal("You can only provide one of the two: a link or a file.").queue();
                return;
            }

            File tempDownloadDir = new File(System.getProperty("java.io.tmpdir"), "discord_ssb_downloads");
            log.debug("Temporary download directory path: {}", tempDownloadDir.getAbsolutePath());
            if (!tempDownloadDir.exists()) {
                log.debug("Temporary download directory does not exist, attempting to create.");
                if (!tempDownloadDir.mkdirs()) {
                    log.error("Could not create temporary download directory: {}", tempDownloadDir.getAbsolutePath());
                    event.getHook().editOriginal("Error: Could not create temporary download directory.").queue();
                    return;
                }
                log.debug("Temporary download directory created successfully.");
            } else {
                log.debug("Temporary download directory already exists.");
            }


            if (fileAttachment != null) {
                final String fileName = fileAttachment.getFileName();
                final long totalSize = fileAttachment.getSize();
                final File localFile = new File(tempDownloadDir, UUID.randomUUID() + "_" + fileName);
                final UUID serverId = UUID.randomUUID();
                final String finalMinecraftVersion = minecraftVersionArg;

                log.debug("Processing file attachment: fileName='{}', totalSize={}, localFile='{}', serverId={}",
                        fileName, totalSize, localFile.getAbsolutePath(), serverId);

                new Thread(() -> {
                    log.debug("File attachment download thread started for serverId: {}", serverId);
                    DockerHandler handler = null;
                    try {
                        log.debug("Attempting to download attachment proxy for serverId: {}", serverId);
                        try (InputStream inputStream = fileAttachment.getProxy().download().join();
                             FileOutputStream fos = new FileOutputStream(localFile)) {
                            log.debug("Successfully opened streams for file download. serverId: {}", serverId);

                            event.getHook().editOriginal("Downloading " + fileName + " (0%)...").queue();
                            log.debug("Initial download message sent. serverId: {}", serverId);

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long downloadedBytes = 0;
                            long lastUpdateTime = System.currentTimeMillis();
                            final long UPDATE_INTERVAL_MS = 1500;
                            log.debug("Starting download loop. serverId: {}", serverId);

                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                downloadedBytes += bytesRead;
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastUpdateTime > UPDATE_INTERVAL_MS) {
                                    int percentage = (totalSize == 0) ? 100 : (int) ((downloadedBytes * 100L) / totalSize);
                                    log.debug("Download progress for serverId {}: {} bytes, {}%", serverId, downloadedBytes, percentage);
                                    event.getHook().editOriginal("Downloading " + fileName + " (" + percentage + "%)...").queue();
                                    lastUpdateTime = currentTime;
                                }
                            }
                            fos.flush();
                            log.debug("File download loop completed. Total bytes downloaded: {}. serverId: {}", downloadedBytes, serverId);
                            event.getHook().editOriginal("Download of " + fileName + " complete (100%). Processing server...").queue();

                            log.debug("Initializing DockerHandler for serverId: {}", serverId);
                            handler = new DockerHandler(localFile, serverId, finalMinecraftVersion);
                            servers.put(serverId, handler);
                            log.debug("DockerHandler created and stored for serverId: {}", serverId);
                            handler.createServer();
                            log.debug("DockerHandler.createServer() called for serverId: {}", serverId);

                            int assignedPort = handler.getAssignedPort();
                            int debugPort = handler.getDebugPort();
                            String connectAddress = "mc.iseal.dev:" + assignedPort;
                            log.info("Server {} created successfully. Connect: {}, Debug Port: {}. serverId: {}", serverId, connectAddress, debugPort, serverId);
                            event.getHook().sendMessage("Server " + serverId + " created! Connect: `" + connectAddress + "` Debug on host port: `" + debugPort + "`").queue();

                        }
                    } catch (IOException e) {
                        log.error("IOException during file download/processing for serverId {}: {}", serverId, e.getMessage(), e);
                        event.getHook().editOriginal("Failed to download " + fileName + ": " + e.getMessage()).queue();
                    } catch (Exception e) {
                        log.error("Exception during server creation/start for serverId {}: {}", serverId, e.getMessage(), e);
                        event.getHook().editOriginal("Error creating server " + serverId + ": " + e.getMessage()).queue();
                        if (servers.containsKey(serverId)) {
                            log.debug("Removing server {} from map due to error.", serverId);
                            DockerHandler toStop = servers.remove(serverId);
                            if (toStop != null) {
                                log.debug("Stopping server {} due to error.", serverId);
                                toStop.stopServer();
                            }
                        } else if (handler != null) {
                            log.debug("Stopping handler (not in map) for server {} due to error.", serverId);
                            handler.stopServer();
                        }
                    } finally {
                        log.debug("File attachment thread finally block for serverId: {}. Attempting to delete local file: {}", serverId, localFile.getAbsolutePath());
                        if (localFile.exists() && !localFile.delete()) {
                            log.warn("Could not delete temporary downloaded file: {}", localFile.getAbsolutePath());
                        } else if (localFile.exists()) {
                            log.debug("Successfully deleted temporary file: {}", localFile.getAbsolutePath());
                        } else {
                            log.debug("Temporary file {} did not exist, no deletion needed.", localFile.getAbsolutePath());
                        }
                        log.debug("File attachment download thread finished for serverId: {}", serverId);
                    }
                }).start();

            } else if (link != null) {
                final File localFile = new File(tempDownloadDir, UUID.randomUUID() + ".zip"); // Generic name for linked file
                final UUID serverIdForLink = UUID.randomUUID();
                final String finalLink = link;
                final String finalMinecraftVersion = minecraftVersionArg;

                log.debug("Processing link: localFile='{}', serverIdForLink={}, link='{}'",
                        localFile.getAbsolutePath(), serverIdForLink, finalLink);

                new Thread(() -> {
                    log.debug("Link download thread started for serverId: {}", serverIdForLink);
                    DockerHandler linkHandler = null;
                    long actualTotalSize = 0;
                    String displayLink = finalLink.length() > 50 ? finalLink.substring(0, 47) + "..." : finalLink;

                    try {
                        log.debug("Attempting to open URL connection for link: {}. serverId: {}", finalLink, serverIdForLink);
                        URL url = new URL(finalLink);
                        URLConnection conn = url.openConnection();
                        actualTotalSize = conn.getContentLengthLong();
                        log.debug("URL connection opened. ContentLength: {}. serverId: {}", actualTotalSize, serverIdForLink);

                        try (InputStream inputStream = conn.getInputStream();
                             FileOutputStream fos = new FileOutputStream(localFile)) {
                            log.debug("Successfully opened streams for link download. serverId: {}", serverIdForLink);

                            if (actualTotalSize <= 0) {
                                event.getHook().editOriginal("Downloading from " + displayLink + "... (size unknown)").queue();
                                log.debug("Initial download message (size unknown) sent. serverId: {}", serverIdForLink);
                            } else {
                                event.getHook().editOriginal("Downloading from " + displayLink + " (0%)...").queue();
                                log.debug("Initial download message (with size) sent. serverId: {}", serverIdForLink);
                            }

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            long downloadedBytes = 0;
                            long lastUpdateTime = System.currentTimeMillis();
                            final long UPDATE_INTERVAL_MS = 1500;
                            log.debug("Starting download loop for link. serverId: {}", serverIdForLink);

                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                downloadedBytes += bytesRead;
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastUpdateTime > UPDATE_INTERVAL_MS) {
                                    if (actualTotalSize > 0) {
                                        int percentage = (int) ((downloadedBytes * 100L) / actualTotalSize);
                                        log.debug("Link download progress for serverId {}: {} bytes, {}%", serverIdForLink, downloadedBytes, percentage);
                                        event.getHook().editOriginal("Downloading from " + displayLink + " (" + percentage + "%)...").queue();
                                    } else {
                                        log.debug("Link download progress for serverId {} (size unknown): {} KB", serverIdForLink, (downloadedBytes / 1024));
                                        event.getHook().editOriginal("Downloading from " + displayLink + " (" + (downloadedBytes / 1024) + " KB, total size unknown)...").queue();
                                    }
                                    lastUpdateTime = currentTime;
                                }
                            }
                            fos.flush();
                            log.debug("Link download loop completed. Total bytes downloaded: {}. serverId: {}", downloadedBytes, serverIdForLink);
                        }
                        event.getHook().editOriginal("Download from " + displayLink + " complete. Processing server...").queue();

                        log.debug("Initializing DockerHandler for serverId: {}", serverIdForLink);
                        linkHandler = new DockerHandler(localFile, serverIdForLink, finalMinecraftVersion);
                        servers.put(serverIdForLink, linkHandler);
                        log.debug("DockerHandler created and stored for serverId: {}", serverIdForLink);
                        linkHandler.createServer();
                        log.debug("DockerHandler.createServer() called for serverId: {}", serverIdForLink);

                        int assignedPort = linkHandler.getAssignedPort();
                        int debugPort = linkHandler.getDebugPort();
                        String connectAddress = "mc.iseal.dev:" + assignedPort;
                        log.info("Server {} (from link) created successfully. Connect: {}, Debug Port: {}. serverId: {}", serverIdForLink, connectAddress, debugPort, serverIdForLink);
                        event.getHook().sendMessage("Server " + serverIdForLink + " created! Connect: `" + connectAddress + "` Debug on host port: `" + debugPort + "`").queue();

                    } catch (IOException e) {
                        log.error("IOException during link download/processing for serverId {}: {}", serverIdForLink, e.getMessage(), e);
                        event.getHook().editOriginal("Failed to download from " + displayLink + ": " + e.getMessage()).queue();
                    } catch (Exception e) {
                        log.error("Exception during server creation/start (from link) for serverId {}: {}", serverIdForLink, e.getMessage(), e);
                        event.getHook().editOriginal("Error creating server " + serverIdForLink + ": " + e.getMessage()).queue();
                        if (servers.containsKey(serverIdForLink)) {
                            log.debug("Removing server {} (from link) from map due to error.", serverIdForLink);
                            DockerHandler toStop = servers.remove(serverIdForLink);
                            if (toStop != null) {
                                log.debug("Stopping server {} (from link) due to error.", serverIdForLink);
                                toStop.stopServer();
                            }
                        } else if (linkHandler != null) {
                            log.debug("Stopping handler (not in map) for server {} (from link) due to error.", serverIdForLink);
                            linkHandler.stopServer();
                        }
                    } finally {
                        log.debug("Link download thread finally block for serverId: {}. Attempting to delete local file: {}", serverIdForLink, localFile.getAbsolutePath());
                        if (localFile.exists() && !localFile.delete()) {
                            log.warn("Could not delete temporary downloaded file: {}", localFile.getAbsolutePath());
                        } else if (localFile.exists()) {
                            log.debug("Successfully deleted temporary file: {}", localFile.getAbsolutePath());
                        } else {
                            log.debug("Temporary file {} did not exist, no deletion needed.", localFile.getAbsolutePath());
                        }
                        log.debug("Link download thread finished for serverId: {}", serverIdForLink);
                    }
                }).start();
            }
        } else if (subcommandName.equalsIgnoreCase("delete")) {
            log.debug("Executing 'delete' subcommand.");

            String idStr = event.getOption("id", OptionMapping::getAsString);
            log.debug("Delete option: id='{}'", idStr);
            UUID uuid;
            try {
                uuid = UUID.fromString(idStr);
                log.debug("Successfully parsed UUID: {}", uuid);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid server ID format for delete: {}", idStr, e);
                event.getHook().editOriginal("Invalid server ID format: " + idStr).queue();
                return;
            }

            log.debug("Attempting to remove server with ID: {}", uuid);
            DockerHandler handler = servers.remove(uuid);
            if (handler == null) {
                log.warn("No server found with ID: {} for deletion.", uuid);
                event.getHook().editOriginal("No server found with ID: " + uuid).queue();
                return;
            }
            log.debug("Server {} found and removed from map. Proceeding to stop.", uuid);

            try {
                handler.stopServer();
                log.info("Server {} deleted successfully.", uuid);
                event.getHook().editOriginal("Server " + uuid + " deleted successfully.").queue();
            } catch (Exception e) {
                log.error("Failed to stop/delete server {}: {}", uuid, e.getMessage(), e);
                event.getHook().editOriginal("Failed to delete server " + uuid + ". Error: " + e.getMessage()).queue();
            }
        } else {
            log.warn("Unknown subcommand received: {}", subcommandName);
            event.reply("Unknown subcommand: " + subcommandName).setEphemeral(true).queue();
        }
        log.debug("actuallyHandleCommand finished for user: {}, command: /testserver {}", event.getUser().getEffectiveName(), event.getSubcommandName());
    }
}