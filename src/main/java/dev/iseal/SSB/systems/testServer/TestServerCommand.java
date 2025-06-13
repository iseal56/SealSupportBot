package dev.iseal.SSB.systems.testServer;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import dev.iseal.SSB.utils.utils.DownloadUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the /testserver slash command for creating and managing temporary Minecraft test servers.
 * Supports creating servers from .zip, .tar.gz, .tar, and .7z archives provided via direct link or file attachment.
 */
public class TestServerCommand extends AbstractCommand {

    private static final Logger log = LoggerFactory.getLogger(TestServerCommand.class);

    // Configuration keys
    private static final String CONFIG_ALLOWED_USERS = "allowedUsers";
    private static final String CONFIG_LOG_CHANNEL = "logChannel";
    private static final String CONFIG_CONNECT_ADDRESS_BASE = "connectAddressBase";
    private static final String DEFAULT_CONNECT_ADDRESS_BASE = "mc.iseal.dev";

    // Subcommand and option names
    private static final String SUBCOMMAND_CREATE = "create";
    private static final String SUBCOMMAND_DELETE = "delete";
    private static final String OPTION_LINK = "link";
    private static final String OPTION_FILE = "file";
    private static final String OPTION_MESSAGE_ID = "messageid";
    private static final String OPTION_MINECRAFT_VERSION = "minecraftversion";
    private static final String OPTION_ID = "id";
    private static final String OPTION_MEMORY = "memorylimit";
    private static final String DEFAULT_MINECRAFT_VERSION = "LATEST";

    private final Yaml config;
    private final List<Long> allowedUsers = new ArrayList<>();
    private final Map<UUID, DockerHandler> servers = new HashMap<>();
    private final TextChannel logChannel;
    private final String connectAddressBase;

    /**
     * Initializes the TestServerCommand, setting up configuration, allowed users, and command structure.
     */
    public TestServerCommand() {
        super(
                Commands.slash("testserver", "Create or delete a temporary test server.")
                        .addSubcommands(
                                new SubcommandData(SUBCOMMAND_CREATE, "Create a test server from an archive.")
                                        .addOption(OptionType.STRING, OPTION_LINK, "A direct link to a .zip, .tar.gz, .tar, or .7z file.", false)
                                        .addOption(OptionType.ATTACHMENT, OPTION_FILE, "A .zip, .tar.gz, .tar, or .7z file.", false)
                                        .addOption(OptionType.STRING, OPTION_MESSAGE_ID, "The message ID of the file attachment.", false)
                                        .addOption(OptionType.STRING, OPTION_MINECRAFT_VERSION, "Minecraft version (e.g., 1.20.4), defaults to LATEST.", false)
                                        .addOption(OptionType.INTEGER, OPTION_MEMORY, "Memory limit in MB (default: 6G).", false),
                                new SubcommandData(SUBCOMMAND_DELETE, "Delete an existing test server.")
                                        .addOption(OptionType.STRING, OPTION_ID, "The UUID of the server to delete.", true)
                        ),
                true, // useDeferReply = true
                true  // ephemeralDefer = true (initial reply is ephemeral)
        );
        log.debug("Initializing TestServerCommand...");

        config = new Yaml("config.yml", System.getProperty("user.dir") + "/config/testServer");
        config.setDefault(CONFIG_ALLOWED_USERS, Collections.singletonList("398908171357519872"));
        config.setDefault(CONFIG_LOG_CHANNEL, "1375873222817615912");
        config.setDefault(CONFIG_CONNECT_ADDRESS_BASE, DEFAULT_CONNECT_ADDRESS_BASE);

        config.getStringList(CONFIG_ALLOWED_USERS).forEach(userId -> {
            try {
                allowedUsers.add(Long.parseLong(userId));
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID format in config: {}", userId);
            }
        });

        logChannel = SSBMain.getJDA().getTextChannelById(config.getString(CONFIG_LOG_CHANNEL));
        if (logChannel == null) {
            log.error("Log channel not found (ID: {}). Please check your config.yml.", config.getString(CONFIG_LOG_CHANNEL));
            throw new IllegalStateException("Log channel not found. TestServer command cannot function.");
        }
        connectAddressBase = config.getString(CONFIG_CONNECT_ADDRESS_BASE);

        log.info("TestServerCommand initialized. Allowed users: {}. Log channel: {}. Connect address base: {}",
                allowedUsers.stream().map(String::valueOf).collect(Collectors.joining(", ")),
                logChannel.getName(),
                connectAddressBase);
    }

    @Override
    protected void actuallyHandleCommand(SlashCommandInteractionEvent event) {
        if (!allowedUsers.contains(event.getUser().getIdLong())) {
            log.info("User {} ({}) attempted to use /testserver but is not allowed.",
                    event.getUser().getEffectiveName(), event.getUser().getIdLong());
            event.getHook().editOriginal("You are not allowed to use this command.").queue();
            return;
        }

        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            log.warn("Subcommand name is null for event: {}", event.getCommandString());
            event.getHook().editOriginal("Unknown subcommand.").queue();
            return;
        }

        switch (subcommandName.toLowerCase()) {
            case SUBCOMMAND_CREATE:
                handleCreateSubcommand(event);
                break;
            case SUBCOMMAND_DELETE:
                handleDeleteSubcommand(event);
                break;
            default:
                log.warn("Unknown subcommand received: {}", subcommandName);
                event.getHook().editOriginal("Unknown subcommand: " + subcommandName).queue();
                break;
        }
        log.debug("/testserver command processed for user: {}", event.getUser().getEffectiveName());
    }

    /**
     * Handles the "create" subcommand logic.
     *
     * @param event The slash command event.
     */
    private void handleCreateSubcommand(SlashCommandInteractionEvent event) {
        log.debug("Executing '{}' subcommand.", SUBCOMMAND_CREATE);

        String link = event.getOption(OPTION_LINK, OptionMapping::getAsString);
        Message.Attachment fileAttachment = event.getOption(OPTION_FILE, OptionMapping::getAsAttachment);
        String messageId = event.getOption(OPTION_MESSAGE_ID, OptionMapping::getAsString);
        String minecraftVersion = event.getOption(OPTION_MINECRAFT_VERSION, DEFAULT_MINECRAFT_VERSION, OptionMapping::getAsString);
        String memoryLimitStr = event.getOption(OPTION_MEMORY, OptionMapping::getAsString);

        if (link == null && fileAttachment == null && messageId == null) {
            event.getHook().editOriginal("You must provide either a direct link, a file attachment or a message id to download from.").queue();
            return;
        }

        log.debug("Received create subcommand with link: {}, fileAttachment: {}, messageId: {}, minecraftVersion: {}",
                link, fileAttachment != null ? fileAttachment.getFileName() : "null", messageId, minecraftVersion);

        int providedOptions = 0;
        if (link != null) providedOptions++;
        if (fileAttachment != null) providedOptions++;
        if (messageId != null) providedOptions++;

        if (providedOptions > 1) {
            event.getHook().editOriginal("Please provide either a link, a file, or a message id, but not more than one.").queue();
            return;
        }

        if (fileAttachment != null) {
            launchServerCreationForAttachment(event, fileAttachment, minecraftVersion, memoryLimitStr);
        } else if (link != null) {
            launchServerCreationForLink(event, link, minecraftVersion, memoryLimitStr);
        } else if (messageId != null) { // messageId is not null
            try {
                long msgId = Long.parseLong(messageId);
                // Retrieve message from the current channel. Consider if message could be in another channel.
                // For simplicity, assuming current channel or a channel accessible to the bot.
                // event.getChannel().retrieveMessageById(msgId).queue( message -> { ... }, failure -> { ... });
                // This means the channel where the command was run.
                Message originalInteractionMessage = event.getHook().retrieveOriginal().complete(); // To get the channel
                Message messageWithAttachment = originalInteractionMessage.getChannel().retrieveMessageById(msgId).complete();

                if (!event.getMember().hasPermission(messageWithAttachment.getGuildChannel(), Permission.VIEW_CHANNEL)) {
                    log.warn("User {} does not have permission to view message ID {} in channel {}.",
                            event.getUser().getEffectiveName(), msgId, messageWithAttachment.getChannel().getName());
                    event.getHook().editOriginal("You do not have permission to view this message.").queue();
                    return;
                }

                if (messageWithAttachment.getAttachments().isEmpty()) {
                    event.getHook().editOriginal("No attachments found in the specified message.").queue();
                    return;
                }
                Message.Attachment attachmentFromMessage = messageWithAttachment.getAttachments().get(0); // Use the first attachment
                launchServerCreationForAttachment(event, attachmentFromMessage, minecraftVersion, memoryLimitStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid message ID format: {}", messageId, e);
                event.getHook().editOriginal("Invalid message ID format: " + messageId).queue();
            } catch (Exception e) { // Catch potential errors from message retrieval
                log.error("Error retrieving message or attachment for messageId {}: {}", messageId, e.getMessage(), e);
                event.getHook().editOriginal("Could not retrieve the attachment from message ID: " + messageId).queue();
            }
        }
    }

    /**
     * Launches the server creation process in a new thread for a file attachment.
     *
     * @param event            The command event.
     * @param attachment       The file attachment.
     * @param minecraftVersion The Minecraft version for the server.
     * @param memoryLimitStr   The memory limit string.
     */
    private void launchServerCreationForAttachment(SlashCommandInteractionEvent event, Message.Attachment attachment, String minecraftVersion, String memoryLimitStr) {
        UUID serverId = UUID.randomUUID();
        new Thread(() -> {
            File downloadedFile = null;
            String originalFileName = attachment.getFileName();
            log.info("Starting server creation for attachment: {}, serverId: {}", originalFileName, serverId);

            try {
                downloadedFile = DownloadUtils.downloadAttachmentToTempDir(event, attachment, serverId.toString());
                executeCoreServerCreationLogic(event, downloadedFile, originalFileName, serverId, minecraftVersion, memoryLimitStr);
            } catch (IOException e) {
                log.error("IOException during attachment download/processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("Failed to download or process " + DownloadUtils.getUserFriendlyName(originalFileName) + ": " + e.getMessage()).queue();
                cleanupDownloadedFile(downloadedFile); // Clean up if download failed and file exists
            } catch (Exception e) {
                log.error("Unexpected exception during attachment processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("An unexpected error occurred while processing " + DownloadUtils.getUserFriendlyName(originalFileName) + ".").queue();
                cleanupDownloadedFile(downloadedFile);
            }
        }, "ServerCreate-Attach-" + serverId).start();
    }

    /**
     * Launches the server creation process in a new thread for a URL link.
     *
     * @param event            The command event.
     * @param link             The URL link to the archive.
     * @param minecraftVersion The Minecraft version for the server.
     * @param memoryLimitStr   The memory limit string.
     */
    private void launchServerCreationForLink(SlashCommandInteractionEvent event, String link, String minecraftVersion, String memoryLimitStr) {
        UUID serverId = UUID.randomUUID();
        new Thread(() -> {
            File downloadedFile = null;
            String derivedFileName = null;
            try {
                derivedFileName = DownloadUtils.deriveFileNameFromUrl(link);
                log.info("Starting server creation for link: {}, derived name: {}, serverId: {}", link, derivedFileName, serverId);

                downloadedFile = DownloadUtils.downloadUrlToTempDir(event, link, serverId.toString());
                executeCoreServerCreationLogic(event, downloadedFile, derivedFileName, serverId, minecraftVersion, memoryLimitStr);
            } catch (IOException e) {
                log.error("IOException during link download/processing for serverId {}: {}", serverId, e.getMessage(), e);
                String userFriendlyLinkName = (derivedFileName != null) ? DownloadUtils.getUserFriendlyName(derivedFileName) : "the linked file";
                event.getHook().editOriginal("Failed to download or process " + userFriendlyLinkName + ": " + e.getMessage()).queue();
                cleanupDownloadedFile(downloadedFile);
            } catch (Exception e) {
                log.error("Unexpected exception during link processing for serverId {}: {}", serverId, e.getMessage(), e);
                String userFriendlyLinkName = (derivedFileName != null) ? DownloadUtils.getUserFriendlyName(derivedFileName) : "the linked file";
                event.getHook().editOriginal("An unexpected error occurred while processing " + userFriendlyLinkName + ".").queue();
                cleanupDownloadedFile(downloadedFile);
            }
        }, "ServerCreate-Link-" + serverId).start();
    }

    // downloadAttachment, downloadFromLink, and transferStreamWithProgress methods removed.

    /**
     * Core logic for server creation after the archive file has been downloaded.
     * Passes the downloaded file to DockerHandler, which is assumed to handle extraction.
     *
     * @param event            The command event.
     * @param downloadedFile   The downloaded archive file (name includes serverId prefix).
     * @param originalFileName The original name of the archive (user-facing, pre-sanitization/prefixing).
     * @param serverId         The UUID for the new server.
     * @param minecraftVersion The Minecraft version.
     * @param memoryLimitStr   The memory limit string.
     */
    private void executeCoreServerCreationLogic(SlashCommandInteractionEvent event, File downloadedFile, String originalFileName, UUID serverId, String minecraftVersion, String memoryLimitStr) {
        DockerHandler handler = null;
        String userFriendlySourceName = DownloadUtils.getUserFriendlyName(originalFileName);

        try {
            event.getHook().editOriginal("Download of " + userFriendlySourceName + " complete. Processing server...").queue();

            // originalFileName is the name before serverId prefix and sanitization by DownloadUtils.
            // downloadedFile.getName() is the actual name on disk (e.g., serverId_sanitizedOriginalName.zip)
            String fileExtension = getFileExtension(originalFileName); // Use original name for extension detection
            log.info("Processing downloaded file: {} (original name: {}, type: {}) for serverId: {}",
                    downloadedFile.getName(), originalFileName, fileExtension, serverId);


            if (".zip".equalsIgnoreCase(fileExtension) || isTarGz(fileExtension) || isTar(fileExtension) || is7z(fileExtension)) {
                log.debug("Passing {} directly to DockerHandler for serverId: {}", downloadedFile.getAbsolutePath(), serverId);
            } else {
                String unsupportedMsg = "Unsupported file type: " + userFriendlySourceName + ". Please use .zip, .tar.gz, .tar, or .7z.";
                event.getHook().editOriginal(unsupportedMsg).queue();
                log.warn("Unsupported file type '{}' (original: {}) for serverId {}. File: {}",
                        fileExtension, originalFileName, serverId, downloadedFile.getAbsolutePath());
                // No need to return here, finally block will clean up downloadedFile
                // However, we should not proceed with DockerHandler creation.
                return;
            }

            log.debug("Initializing DockerHandler for serverId: {} with source: {}", serverId, downloadedFile.getAbsolutePath());
            handler = new DockerHandler(downloadedFile, serverId, minecraftVersion, logChannel, memoryLimitStr);
            servers.put(serverId, handler);
            log.debug("DockerHandler created and stored for serverId: {}", serverId);

            handler.createServer();
            log.debug("DockerHandler.createServer() called for serverId: {}", serverId);

            int assignedPort = handler.getAssignedPort();
            int debugPort = handler.getDebugPort();
            String connectAddr = connectAddressBase + ":" + assignedPort;
            log.info("Server {} created successfully. Connect: {}, Debug Port: {}. serverId: {}", serverId, connectAddr, debugPort, serverId);
            event.getHook().sendMessage("Server **" + serverId + "** created!\nConnect: `" + connectAddr + "`\nDebug (host port): `" + debugPort + "`").setEphemeral(false).queue();

        } catch (IOException e) {
            log.error("IOException during server processing for serverId {}: {}", serverId, e.getMessage(), e);
            event.getHook().editOriginal("Failed to process " + userFriendlySourceName + ": " + e.getMessage()).queue();
            cleanupFailedServerAttempt(serverId, handler);
        } catch (Exception e) {
            log.error("General exception during server creation/start for serverId {}: {}", serverId, e.getMessage(), e);
            event.getHook().editOriginal("An error occurred while creating server " + serverId + ": " + e.getMessage()).queue();
            cleanupFailedServerAttempt(serverId, handler);
        } finally {
            log.debug("Core server creation logic 'finally' block for serverId: {}.", serverId);
            cleanupDownloadedFile(downloadedFile); // Always try to clean up the downloaded archive
            log.debug("Core server creation logic thread finished for serverId: {}", serverId);
        }
    }

    /**
     * Handles the "delete" subcommand logic.
     *
     * @param event The slash command event.
     */
    private void handleDeleteSubcommand(SlashCommandInteractionEvent event) {
        log.debug("Executing '{}' subcommand.", SUBCOMMAND_DELETE);
        String idStr = event.getOption(OPTION_ID, OptionMapping::getAsString);
        if (idStr == null) {
            event.getHook().editOriginal("Server ID is required for deletion.").queue();
            return;
        }

        UUID serverUuid;
        try {
            serverUuid = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid server UUID format for delete: {}", idStr, e);
            event.getHook().editOriginal("Invalid server ID format: " + idStr).queue();
            return;
        }

        DockerHandler handler = servers.remove(serverUuid);
        if (handler == null) {
            log.warn("No server found with ID: {} for deletion.", serverUuid);
            event.getHook().editOriginal("No server found with ID: " + serverUuid).queue();
            return;
        }

        log.info("Attempting to delete server with ID: {}", serverUuid);
        event.getHook().editOriginal("Deleting server " + serverUuid + "...").queue();
        try {
            handler.stopServer();
            log.info("Server {} deleted successfully.", serverUuid);
            event.getHook().sendMessage("Server " + serverUuid + " deleted successfully.").setEphemeral(false).queue();
        } catch (Exception e) {
            log.error("Failed to stop/delete server {}: {}", serverUuid, e.getMessage(), e);
            event.getHook().editOriginal("Failed to delete server " + serverUuid + ". Error: " + e.getMessage()).queue();
            // Re-add handler if deletion failed, or handle state more robustly?
            // For now, it's removed from the map. If stopServer fails, container might still be running.
        }
    }

    // --- File Utility Methods ---

    /**
     * Cleans up resources if server creation fails.
     *
     * @param serverId The ID of the server that failed.
     * @param handler  The DockerHandler instance, if created.
     */
    private void cleanupFailedServerAttempt(UUID serverId, DockerHandler handler) {
        DockerHandler removedHandler = servers.remove(serverId);
        if (removedHandler != null) {
            log.debug("Stopping and cleaning up server {} due to creation error.", serverId);
            removedHandler.stopServer(); // This might throw, ensure it's handled or logged
        } else if (handler != null) { // If handler was created but not added to map yet
            log.debug("Stopping and cleaning up handler for server {} (not in map) due to creation error.", serverId);
            handler.stopServer();
        }
    }

    /**
     * Deletes the temporary downloaded file.
     *
     * @param file The file to delete.
     */
    private void cleanupDownloadedFile(File file) {
        if (file != null && file.exists()) {
            log.debug("Attempting to delete temporary downloaded file: {}", file.getAbsolutePath());
            if (file.delete()) {
                log.debug("Successfully deleted temporary downloaded file: {}", file.getAbsolutePath());
            } else {
                log.warn("Could not delete temporary downloaded file: {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * Gets the file extension from a filename. Handles ".tar.gz" specifically.
     *
     * @param fileName The name of the file.
     * @return The file extension (e.g., ".zip", ".tar.gz") or an empty string if none.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".tar.gz")) return ".tar.gz";
        if (lowerName.endsWith(".tar.xz")) return ".tar.xz";
        int lastDot = lowerName.lastIndexOf('.');
        return (lastDot == -1) ? "" : lowerName.substring(lastDot);
    }

    private boolean isTarGz(String extension) {
        return ".tar.gz".equalsIgnoreCase(extension) || ".tgz".equalsIgnoreCase(extension);
    }

    private boolean isTar(String extension) {
        return ".tar".equalsIgnoreCase(extension);
    }

    private boolean is7z(String extension) {
        return ".7z".equalsIgnoreCase(extension);
    }
}