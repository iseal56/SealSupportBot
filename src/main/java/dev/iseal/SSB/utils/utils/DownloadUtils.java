package dev.iseal.SSB.utils.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

public class DownloadUtils {

    private static final Logger log = LoggerFactory.getLogger(DownloadUtils.class);

    private static final String TEMP_DOWNLOAD_DIR_NAME = "discord_ssb_downloads";
    private static final int DOWNLOAD_BUFFER_SIZE = 4096; // 4KB
    private static final long DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS = 1500; // 1.5 seconds
    private static final int MAX_NAME_LENGTH = 40;
    private static final String ELLIPSIS = "...";

    /**
     * Downloads a JDA Message.Attachment to a specified temporary directory structure
     * and returns the File object. Updates the interaction hook with progress.
     *
     * @param event            The command event for progress updates.
     * @param attachment       The attachment to download.
     * @param uniqueFilePrefix A prefix (e.g., serverId) to make the downloaded filename unique.
     * @return File object pointing to the downloaded file in the temporary directory.
     * @throws IOException If an I/O error occurs during download or directory creation.
     */
    public static File downloadAttachmentToTempDir(SlashCommandInteractionEvent event, Message.Attachment attachment, String uniqueFilePrefix) throws IOException {
        String userFriendlyName = getUserFriendlyName(attachment.getFileName());

        BiConsumer<Long, Long> eventProgressReporter = (downloadedBytes, totalSize) -> {
            if (event.getHook().isExpired()) {
                log.warn("Interaction hook expired during download progress for '{}'. Skipping update.", userFriendlyName);
                return;
            }

            String progressMessage;
            if (downloadedBytes == 0) { // Initial message
                progressMessage = (totalSize <= 0) ?
                        "Downloading " + userFriendlyName + "... (size unknown)" :
                        "Downloading " + userFriendlyName + " (0%)...";
            } else if (totalSize > 0) {
                int percentage = (int) ((downloadedBytes * 100L) / totalSize);
                if (downloadedBytes >= totalSize) percentage = 100; // Ensure 100% for final update
                progressMessage = "Downloading " + userFriendlyName + " (" + percentage + "%)...";
            } else { // totalSize <= 0 and downloadedBytes > 0
                progressMessage = "Downloading " + userFriendlyName + " (" + (downloadedBytes / 1024) + " KB)...";
            }

            event.getHook().editOriginal(progressMessage).queue(
                    null, // success
                    error -> log.warn("Failed to send download progress update for '{}': {}", userFriendlyName, error.getMessage())
            );
        };

        return downloadAttachmentToTempDir(attachment, uniqueFilePrefix, eventProgressReporter);
    }

    /**
     * Downloads a JDA Message.Attachment to a specified temporary directory structure
     * and returns the File object. Allows custom progress reporting via a BiConsumer.
     *
     * @param attachment       The attachment to download.
     * @param uniqueFilePrefix A prefix (e.g., serverId) to make the downloaded filename unique.
     * @param progressCallback A BiConsumer that accepts (bytesDownloaded, totalBytes) for progress updates. Can be null.
     * @return File object pointing to the downloaded file in the temporary directory.
     * @throws IOException If an I/O error occurs during download or directory creation.
     */
    public static File downloadAttachmentToTempDir(Message.Attachment attachment, String uniqueFilePrefix, BiConsumer<Long, Long> progressCallback) throws IOException {
        String originalFileName = attachment.getFileName();
        File tempBaseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempDownloadDir = new File(tempBaseDir, TEMP_DOWNLOAD_DIR_NAME);

        if (!tempDownloadDir.exists()) {
            if (!tempDownloadDir.mkdirs()) {
                log.error("Could not create temporary download directory: {}", tempDownloadDir.getAbsolutePath());
                throw new IOException("Could not create temporary download directory: " + tempDownloadDir.getAbsolutePath());
            }
            log.debug("Created temporary download directory: {}", tempDownloadDir.getAbsolutePath());
        }

        String sanitizedOriginalFileName = sanitizeFileName(originalFileName);
        File destinationFile = new File(tempDownloadDir, uniqueFilePrefix + "_" + sanitizedOriginalFileName);

        long totalSize = attachment.getSize();
        String userFriendlyName = getUserFriendlyName(originalFileName);
        log.debug("Attempting to download attachment '{}' to '{}'. Total size: {} bytes.", userFriendlyName, destinationFile.getAbsolutePath(), totalSize);

        try (InputStream inputStream = attachment.getProxy().download().join();
             FileOutputStream fos = new FileOutputStream(destinationFile)) {
            transferStreamWithProgress(inputStream, fos, totalSize, userFriendlyName, progressCallback);
        } catch (UncheckedIOException | CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            log.error("Failed to download attachment '{}': {}", userFriendlyName, e.getMessage(), e);
            throw new IOException("Failed to download attachment '" + userFriendlyName + "': " + e.getMessage(), e);
        }
        log.info("Attachment '{}' downloaded successfully to '{}'", userFriendlyName, destinationFile.getAbsolutePath());
        return destinationFile;
    }

    /**
     * Downloads a JDA Message.Attachment to a specified temporary directory structure
     * and returns the File object without reporting progress.
     *
     * @param attachment       The attachment to download.
     * @param uniqueFilePrefix A prefix (e.g., serverId) to make the downloaded filename unique.
     * @return File object pointing to the downloaded file in the temporary directory.
     * @throws IOException If an I/O error occurs during download or directory creation.
     */
    public static File downloadAttachmentToTempDir(Message.Attachment attachment, String uniqueFilePrefix) throws IOException {
        return downloadAttachmentToTempDir(attachment, uniqueFilePrefix, null);
    }

    /**
     * Downloads a file from a URL to a specified temporary directory structure
     * and returns the File object. Updates the interaction hook with progress.
     *
     * @param event            The command event for progress updates.
     * @param urlString        The URL string to download from.
     * @param uniqueFilePrefix A prefix (e.g., serverId) to make the downloaded filename unique.
     * @return File object pointing to the downloaded file in the temporary directory.
     * @throws IOException If an I/O error occurs during download or directory creation.
     */
    public static File downloadUrlToTempDir(SlashCommandInteractionEvent event, String urlString, String uniqueFilePrefix) throws IOException {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            log.warn("Invalid URL provided for download: {}", urlString, e);
            throw new IOException("Invalid URL format: " + urlString, e);
        }

        String derivedFileName = deriveFileNameFromUrl(urlString);
        String userFriendlyName = getUserFriendlyName(derivedFileName);

        BiConsumer<Long, Long> eventProgressReporter = (downloadedBytes, totalSize) -> {
            if (event.getHook().isExpired()) {
                log.warn("Interaction hook expired during download progress for '{}'. Skipping update.", userFriendlyName);
                return;
            }
            String progressMessage;
            if (downloadedBytes == 0) {
                progressMessage = (totalSize <= 0) ?
                        "Downloading " + userFriendlyName + "... (size unknown)" :
                        "Downloading " + userFriendlyName + " (0%)...";
            } else if (totalSize > 0) {
                int percentage = (int) ((downloadedBytes * 100L) / totalSize);
                if (downloadedBytes >= totalSize) percentage = 100;
                progressMessage = "Downloading " + userFriendlyName + " (" + percentage + "%)...";
            } else {
                progressMessage = "Downloading " + userFriendlyName + " (" + (downloadedBytes / 1024) + " KB)...";
            }
            event.getHook().editOriginal(progressMessage).queue(
                    null,
                    error -> log.warn("Failed to send download progress update for '{}': {}", userFriendlyName, error.getMessage())
            );
        };
        return downloadUrlToTempDirInternal(url, urlString, uniqueFilePrefix, eventProgressReporter);
    }

    /**
     * Downloads a file from a URL to a specified temporary directory structure
     * and returns the File object. Allows custom progress reporting via a BiConsumer.
     *
     * @param urlString        The URL string to download from.
     * @param uniqueFilePrefix A prefix (e.g., serverId) to make the downloaded filename unique.
     * @param progressCallback A BiConsumer that accepts (bytesDownloaded, totalBytes) for progress updates. Can be null.
     * @return File object pointing to the downloaded file in the temporary directory.
     * @throws IOException If an I/O error occurs during download or directory creation.
     */
    public static File downloadUrlToTempDir(String urlString, String uniqueFilePrefix, BiConsumer<Long, Long> progressCallback) throws IOException {
        URL url;
        try {
            url = new URI(urlString).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            log.warn("Invalid URL provided for download: {}", urlString, e);
            throw new IOException("Invalid URL format: " + urlString, e);
        }
        return downloadUrlToTempDirInternal(url, urlString, uniqueFilePrefix, progressCallback);
    }

    private static File downloadUrlToTempDirInternal(URL url, String originalUrlString, String uniqueFilePrefix, BiConsumer<Long, Long> progressCallback) throws IOException {
        String derivedFileName = deriveFileNameFromUrl(originalUrlString);
        File tempBaseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempDownloadDir = new File(tempBaseDir, TEMP_DOWNLOAD_DIR_NAME);

        if (!tempDownloadDir.exists()) {
            if (!tempDownloadDir.mkdirs()) {
                log.error("Could not create temporary download directory: {}", tempDownloadDir.getAbsolutePath());
                throw new IOException("Could not create temporary download directory: " + tempDownloadDir.getAbsolutePath());
            }
            log.debug("Created temporary download directory: {}", tempDownloadDir.getAbsolutePath());
        }

        String sanitizedDerivedFileName = sanitizeFileName(derivedFileName);
        File destinationFile = new File(tempDownloadDir, uniqueFilePrefix + "_" + sanitizedDerivedFileName);

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "SealSupportBot/1.0");

        long totalSize = connection.getContentLengthLong();
        String userFriendlyName = getUserFriendlyName(derivedFileName);
        log.debug("Attempting to download from URL '{}' to '{}'. Total size: {} bytes.", originalUrlString, destinationFile.getAbsolutePath(), totalSize);

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(destinationFile)) {
            transferStreamWithProgress(inputStream, fos, totalSize, userFriendlyName, progressCallback);
        } catch (IOException e) {
            log.error("Failed to download from URL '{}' (derived name: {}): {}", originalUrlString, userFriendlyName, e.getMessage(), e);
            throw new IOException("Failed to download '" + userFriendlyName + "' from URL: " + e.getMessage(), e);
        }
        log.info("URL content '{}' (from {}) downloaded successfully to '{}'", userFriendlyName, originalUrlString, destinationFile.getAbsolutePath());
        return destinationFile;
    }


    /**
     * Transfers data from an InputStream to an OutputStream, providing progress updates
     * via a BiConsumer callback.
     *
     * @param source           The InputStream to read from.
     * @param destination      The OutputStream to write to.
     * @param totalSize        The total size of the content, or -1 if unknown.
     * @param userFriendlyName The name of the content being transferred for display in logs.
     * @param progressCallback A BiConsumer that accepts (bytesDownloaded, totalBytes). Called initially,
     *                         periodically during transfer, and finally upon completion. Can be null.
     * @throws IOException If an I/O error occurs.
     */
    private static void transferStreamWithProgress(InputStream source, OutputStream destination, long totalSize, String userFriendlyName, BiConsumer<Long, Long> progressCallback) throws IOException {
        if (progressCallback != null) {
            progressCallback.accept(0L, totalSize);
        }

        byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
        int bytesRead;
        long downloadedBytes = 0;
        long lastProgressCallbackTime = System.currentTimeMillis();

        while ((bytesRead = source.read(buffer)) != -1) {
            destination.write(buffer, 0, bytesRead);
            downloadedBytes += bytesRead;

            if (progressCallback != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProgressCallbackTime > DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS || downloadedBytes == totalSize) {
                    progressCallback.accept(downloadedBytes, totalSize);
                    lastProgressCallbackTime = currentTime;
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(downloadedBytes, totalSize); // Final call
        }

        destination.flush();
        log.debug("Download of '{}' complete. Total bytes transferred: {}.", userFriendlyName, downloadedBytes);
    }

    /**
     * Sanitizes a filename by replacing characters not suitable for typical file systems.
     *
     * @param fileName The original filename.
     * @return A sanitized filename.
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return "unknown_file";
        return fileName.replaceAll("[^a-zA-Z0-9.\\-_]+", "_");
    }

    /**
     * Creates a user-friendly name for display, truncating if too long.
     *
     * @param fileName The original filename.
     * @return A user-friendly, potentially truncated name.
     */
    public static String getUserFriendlyName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "file";
        return fileName.length() > MAX_NAME_LENGTH ? fileName.substring(0, MAX_NAME_LENGTH - ELLIPSIS.length()) + ELLIPSIS : fileName;
    }

    /**
     * Derives a filename from a URL string.
     *
     * @param urlString The URL string.
     * @return A derived filename or a default name.
     */
    public static String deriveFileNameFromUrl(String urlString) {
        try {
            URL url = new URI(urlString).toURL();
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
        } catch (MalformedURLException | URISyntaxException e) {
            log.warn("Could not parse URL for filename derivation: {}. Returning default.", urlString, e);
            return "download_from_invalid_url.dat";
        }
    }

    /**
     * Tries to get a file extension from the Content-Type of a URL connection.
     * @param url The URL to inspect.
     * @return A file extension (e.g., ".zip") or ".dat" as a fallback.
     */
    private static String getExtensionFromContentType(URL url) {
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
}