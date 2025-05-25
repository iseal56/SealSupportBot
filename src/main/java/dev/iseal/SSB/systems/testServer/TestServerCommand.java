package dev.iseal.SSB.systems.testServer;

import de.leonhard.storage.Yaml;
import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.CompletionException;
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
    private static final String DEFAULT_MINECRAFT_VERSION = "LATEST";

    // File handling constants
    private static final String TEMP_DOWNLOAD_DIR_NAME = "discord_ssb_downloads";
    private static final int DOWNLOAD_BUFFER_SIZE = 4096;
    private static final long DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS = 1500;

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
                                        .addOption(OptionType.STRING, OPTION_MINECRAFT_VERSION, "Minecraft version (e.g., 1.20.4), defaults to LATEST.", false),
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

        if (link == null && fileAttachment == null && messageId == null) {
            event.getHook().editOriginal("You must provide either a direct link, a file attachment or a message id to download from.").queue();
            return;
        }

        log.debug("Received create subcommand with link: {}, fileAttachment: {}, messageId: {}, minecraftVersion: {}",
                link, fileAttachment != null ? fileAttachment.getFileName() : "null", messageId, minecraftVersion);

        boolean noMoreThanOneIsNull =
                ((link == null ? 1 : 0) +
                        (fileAttachment == null ? 1 : 0) +
                        (messageId == null ? 1 : 0)) <= 1;

        if (noMoreThanOneIsNull) {
            event.getHook().editOriginal("Please provide either a link, a file or a message id, not more than one.").queue();
            return;
        }

        File tempDownloadDir = new File(System.getProperty("java.io.tmpdir"), TEMP_DOWNLOAD_DIR_NAME);
        if (!tempDownloadDir.exists() && !tempDownloadDir.mkdirs()) {
            log.error("Could not create temporary download directory: {}", tempDownloadDir.getAbsolutePath());
            event.getHook().editOriginal("Error: Could not create temporary download directory.").queue();
            return;
        }

        if (fileAttachment != null) {
            launchServerCreationForAttachment(event, fileAttachment, minecraftVersion, tempDownloadDir);
        } else if (link != null) { // link is not null
            try {
                URI.create(link).toURL(); // Validate URL early
                launchServerCreationForLink(event, link, minecraftVersion, tempDownloadDir);
            } catch (MalformedURLException e) {
                log.warn("Invalid URL provided: {}", link, e);
                event.getHook().editOriginal("Invalid URL format: " + link).queue();
            }
        } else if (messageId != null) {
            try {
                long msgId = Long.parseLong(messageId);
                Message originalMessage = event.getHook().retrieveOriginal().complete();
                Message message = originalMessage.getChannel().retrieveMessageById(msgId).complete();
                if (!event.getMember().hasPermission(message.getGuildChannel(), Permission.VIEW_CHANNEL)) {
                    log.warn("User {} does not have permission to view message ID {} in channel {}.",
                            event.getUser().getEffectiveName(), msgId, message.getChannel().getName());
                    event.getHook().editOriginal("You do not have permission to view this message.").queue();
                    return;
                }
                if (message == null || message.getAttachments().isEmpty()) {
                    event.getHook().editOriginal("No attachments found in the specified message.").queue();
                    return;
                }
                Message.Attachment attachment = message.getAttachments().get(0); // Use the first attachment
                launchServerCreationForAttachment(event, attachment, minecraftVersion, tempDownloadDir);
            } catch (NumberFormatException e) {
                log.warn("Invalid message ID format: {}", messageId, e);
                event.getHook().editOriginal("Invalid message ID format: " + messageId).queue();
            }
        }
    }

    /**
     * Launches the server creation process in a new thread for a file attachment.
     *
     * @param event            The command event.
     * @param attachment       The file attachment.
     * @param minecraftVersion The Minecraft version for the server.
     * @param tempDownloadDir  The temporary directory for downloads.
     */
    private void launchServerCreationForAttachment(SlashCommandInteractionEvent event, Message.Attachment attachment, String minecraftVersion, File tempDownloadDir) {
        UUID serverId = UUID.randomUUID(); // Generate serverId before starting the thread
        new Thread(() -> {
            String originalFileName = attachment.getFileName();
            File downloadedFile = new File(tempDownloadDir, serverId + "_" + sanitizeFileName(originalFileName));
            log.info("Starting server creation for attachment: {}, serverId: {}", originalFileName, serverId);

            try {
                downloadAttachment(event, attachment, downloadedFile, originalFileName);
                executeCoreServerCreationLogic(event, downloadedFile, originalFileName, serverId, minecraftVersion);
            } catch (IOException e) {
                log.error("IOException during attachment download/processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("Failed to download or process " + getUserFriendlyName(originalFileName) + ": " + e.getMessage()).queue();
                cleanupDownloadedFile(downloadedFile);
            } catch (Exception e) {
                log.error("Unexpected exception during attachment processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("An unexpected error occurred while processing " + getUserFriendlyName(originalFileName) + ".").queue();
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
     * @param tempDownloadDir  The temporary directory for downloads.
     */
    private void launchServerCreationForLink(SlashCommandInteractionEvent event, String link, String minecraftVersion, File tempDownloadDir) {
        UUID serverId = UUID.randomUUID(); // Generate serverId before starting the thread
        new Thread(() -> {
            String derivedFileName = deriveFileNameFromUrl(link);
            File downloadedFile = new File(tempDownloadDir, serverId + "_" + sanitizeFileName(derivedFileName));
            log.info("Starting server creation for link: {}, serverId: {}", link, serverId);

            try {
                downloadFromLink(event, link, downloadedFile, derivedFileName);
                executeCoreServerCreationLogic(event, downloadedFile, derivedFileName, serverId, minecraftVersion);
            } catch (IOException e) {
                log.error("IOException during link download/processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("Failed to download or process from link: " + e.getMessage()).queue();
                cleanupDownloadedFile(downloadedFile);
            } catch (Exception e) {
                log.error("Unexpected exception during link processing for serverId {}: {}", serverId, e.getMessage(), e);
                event.getHook().editOriginal("An unexpected error occurred while processing the link.").queue();
                cleanupDownloadedFile(downloadedFile);
            }
        }, "ServerCreate-Link-" + serverId).start();
    }

    /**
     * Downloads a file from a JDA Message.Attachment with progress updates.
     *
     * @param event            The command event for progress updates.
     * @param attachment       The attachment to download.
     * @param destinationFile  The file to save the download to.
     * @param originalFileName The original name of the file for display.
     * @throws IOException If an I/O error occurs during download.
     */
    private void downloadAttachment(SlashCommandInteractionEvent event, Message.Attachment attachment, File destinationFile, String originalFileName) throws IOException {
        long totalSize = attachment.getSize();
        String userFriendlyName = getUserFriendlyName(originalFileName);
        log.debug("Downloading attachment '{}' to '{}', size: {}", userFriendlyName, destinationFile.getAbsolutePath(), totalSize);

        try (InputStream inputStream = attachment.getProxy().download().join();
             FileOutputStream fos = new FileOutputStream(destinationFile)) {
            transferStreamWithProgress(event, inputStream, fos, totalSize, userFriendlyName);
        } catch (UncheckedIOException | CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to download attachment: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads a file from a URL with progress updates.
     *
     * @param event            The command event for progress updates.
     * @param urlString        The URL string to download from.
     * @param destinationFile  The file to save the download to.
     * @param originalFileName The original name of the file for display (can be derived).
     * @throws IOException If an I/O error occurs during download.
     */
    private void downloadFromLink(SlashCommandInteractionEvent event, String urlString, File destinationFile, String originalFileName) throws IOException {
        String userFriendlyName = getUserFriendlyName(originalFileName);
        log.debug("Downloading from link '{}' to '{}'", urlString, destinationFile.getAbsolutePath());
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        long totalSize = connection.getContentLengthLong();

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(destinationFile)) {
            transferStreamWithProgress(event, inputStream, fos, totalSize, userFriendlyName);
        }
    }

    /**
     * Transfers data from an InputStream to an OutputStream, providing progress updates.
     *
     * @param event              The command event for progress updates.
     * @param source             The InputStream to read from.
     * @param destination        The OutputStream to write to.
     * @param totalSize          The total size of the content, or -1 if unknown.
     * @param userFriendlyName   The name of the content being transferred for display.
     * @throws IOException If an I/O error occurs.
     */
    private void transferStreamWithProgress(SlashCommandInteractionEvent event, InputStream source, OutputStream destination, long totalSize, String userFriendlyName) throws IOException {
        String initialMessage = (totalSize <= 0) ?
                "Downloading " + userFriendlyName + "... (size unknown)" :
                "Downloading " + userFriendlyName + " (0%)...";
        event.getHook().editOriginal(initialMessage).queue();

        byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
        int bytesRead;
        long downloadedBytes = 0;
        long lastUpdateTime = System.currentTimeMillis();

        while ((bytesRead = source.read(buffer)) != -1) {
            destination.write(buffer, 0, bytesRead);
            downloadedBytes += bytesRead;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime > DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS) {
                String progressMessage;
                if (totalSize > 0) {
                    int percentage = (int) ((downloadedBytes * 100L) / totalSize);
                    progressMessage = "Downloading " + userFriendlyName + " (" + percentage + "%)...";
                } else {
                    progressMessage = "Downloading " + userFriendlyName + " (" + (downloadedBytes / 1024) + " KB)...";
                }
                event.getHook().editOriginal(progressMessage).queue();
                lastUpdateTime = currentTime;
            }
        }
        destination.flush();
        log.debug("Download of {} complete. Total bytes: {}.", userFriendlyName, downloadedBytes);
    }


    /**
     * Core logic for server creation after the archive file has been downloaded.
     * Passes the downloaded file to DockerHandler, which is assumed to handle extraction.
     *
     * @param event            The command event.
     * @param downloadedFile   The downloaded archive file.
     * @param originalFileName The original name of the archive.
     * @param serverId         The UUID for the new server.
     * @param minecraftVersion The Minecraft version.
     */
    private void executeCoreServerCreationLogic(SlashCommandInteractionEvent event, File downloadedFile, String originalFileName, UUID serverId, String minecraftVersion) {
        File serverSourceForDocker;
        DockerHandler handler = null;
        String userFriendlySourceName = getUserFriendlyName(originalFileName);

        try {
            event.getHook().editOriginal("Download of " + userFriendlySourceName + " complete. Processing server...").queue();

            String fileExtension = getFileExtension(originalFileName);
            log.info("Processing downloaded file: {} (type: {}) for serverId: {}", downloadedFile.getName(), fileExtension, serverId);

            if (".zip".equalsIgnoreCase(fileExtension) || isTarGz(fileExtension) || isTar(fileExtension) || is7z(fileExtension)) {
                serverSourceForDocker = downloadedFile; // DockerHandler will handle extraction if needed
                log.debug("Passing {} directly to DockerHandler for serverId: {}", downloadedFile.getAbsolutePath(), serverId);
            } else {
                String unsupportedMsg = "Unsupported file type: " + originalFileName + ". Please use .zip, .tar.gz, .tar, or .7z.";
                event.getHook().editOriginal(unsupportedMsg).queue();
                log.warn("Unsupported file type '{}' for serverId {}. File: {}", originalFileName, serverId, downloadedFile.getAbsolutePath());
                return; // Exit, finally block will clean up downloadedFile
            }

            log.debug("Initializing DockerHandler for serverId: {} with source: {}", serverId, serverSourceForDocker.getAbsolutePath());
            handler = new DockerHandler(serverSourceForDocker, serverId, minecraftVersion, logChannel);
            servers.put(serverId, handler);
            log.debug("DockerHandler created and stored for serverId: {}", serverId);

            handler.createServer(); // Assumes DockerHandler can handle the archive/directory
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
            removedHandler.stopServer();
        } else if (handler != null) {
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

    /**
     * Sanitizes a filename by replacing characters not suitable for typical file systems.
     *
     * @param fileName The original filename.
     * @return A sanitized filename.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unknown_file";
        return fileName.replaceAll("[^a-zA-Z0-9.\\-_]+", "_");
    }

    /**
     * Derives a filename from a URL string.
     *
     * @param urlString The URL string.
     * @return A derived filename or a default name.
     */
    private String deriveFileNameFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                String query = url.getQuery();
                if (query != null) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.toLowerCase().startsWith("filename=") || param.toLowerCase().startsWith("file=")) {
                            String potentialName = param.substring(param.indexOf("=") + 1);
                            if (!potentialName.trim().isEmpty()) return potentialName;
                        }
                    }
                }
                return "downloaded_content" + getExtensionFromContentType(url);
            }
            String name = new File(path).getName();
            return name.isEmpty() ? "downloaded_content" + getExtensionFromContentType(url) : name;
        } catch (MalformedURLException e) {
            log.warn("Could not parse URL for filename: {}", urlString, e);
            return "download_from_invalid_url.dat";
        }
    }

    /**
     * Tries to get a file extension from the Content-Type of a URL connection.
     * @param url The URL to inspect.
     * @return A file extension (e.g., ".zip") or ".dat" as a fallback.
     */
    private String getExtensionFromContentType(URL url) {
        try {
            URLConnection conn = url.openConnection();
            String contentType = conn.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
                if (contentType.contains("zip")) return ".zip";
                if (contentType.contains("gzip") || contentType.contains("x-gtar")) return ".tar.gz";
                if (contentType.contains("tar")) return ".tar";
                if (contentType.contains("x-7z-compressed")) return ".7z";
            }
        } catch (IOException e) {
            log.warn("Could not open connection to get content type for URL: {}", url, e);
        }
        return ".dat"; // Default extension
    }


    /**
     * Creates a user-friendly name for display, truncating if too long.
     *
     * @param fileName The original filename.
     * @return A user-friendly, potentially truncated name.
     */
    private String getUserFriendlyName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "file";
        return fileName.length() > 40 ? fileName.substring(0, 37) + "..." : fileName;
    }
}