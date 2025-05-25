package dev.iseal.SSB.systems.testServer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.iseal.SSB.utils.Utils;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles Docker operations for creating, managing, and stopping Minecraft server containers.
 * Each instance of DockerHandler corresponds to a single Minecraft server instance.
 */
public class DockerHandler {

    private static final String TEMP_SERVER_ROOT_DIR_NAME = "servers";
    private static final String SERVER_JAR_FILENAME = "server.jar";
    private static final String PLUGINS_DIRECTORY_NAME = "plugins";
    private static final String LOGS_DIRECTORY_NAME = "logs";
    private static final String LATEST_LOG_FILENAME = "latest.log";
    private static final String FILTERED_LOG_FILENAME_PREFIX = "filtered_";
    private static final String MINECRAFT_DOCKER_IMAGE = "itzg/minecraft-server";
    private static final String DEFAULT_MINECRAFT_VERSION = "LATEST";
    private static final String IP_ADDRESS_LOG_FILTER_REGEX = "/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+";
    private static final String FILTERED_IP_REPLACEMENT_TEXT = "[FILTERED_IP]";
    private static final int DEFAULT_SERVER_PORT_START = 25565;
    private static final int DEFAULT_DEBUG_PORT_START = 1025;
    private static final int MAX_PORT_NUMBER = 65535;
    private static final String SERVER_NAME_PREFIX = "testserver-";
    // TODO: Imlement 7z
    private static final List<String> ALLOWED_COMPRESSED_EXTENSIONS = Arrays.asList("zip", "tar.gz");


    private final UUID uuid;
    private final String serverName;
    private final String serverPath;
    private int assignedPort;
    private int debugPort;
    private Closeable eventsListener;
    private volatile boolean stopListenerActive = false;
    private final TextChannel finalLogUploadChannel;
    private final Logger log = JDALogger.getLog(getClass());

    private boolean onlyPluginsProvided;
    private final String minecraftVersion;

    /**
     * Constructs a DockerHandler for a new Minecraft server instance.
     * This involves setting up directories, unzipping server files, and allocating ports.
     *
     * @param serverCompressedFile The compressed file containing server data (either a full server.jar or just a plugins folder).
     *                             Supported formats: .zip, .tar.gz.
     * @param id The unique identifier for this server instance.
     * @param minecraftVersion The Minecraft version to use if only plugins are provided (e.g., "1.19.4"). Defaults to "LATEST".
     * @param finalLogUploadChannelParam The JDA TextChannel where server logs should be uploaded upon shutdown.
     * @throws Exception if any error occurs during initialization, such as file I/O issues or port allocation failures.
     */
    public DockerHandler(File serverCompressedFile, UUID id, String minecraftVersion, TextChannel finalLogUploadChannelParam) throws Exception {
        this.uuid = id;
        this.serverName = SERVER_NAME_PREFIX + uuid;
        this.minecraftVersion = (minecraftVersion == null || minecraftVersion.trim().isEmpty()) ? DEFAULT_MINECRAFT_VERSION : minecraftVersion;
        this.serverPath = Paths.get(System.getProperty("java.io.tmpdir"), TEMP_SERVER_ROOT_DIR_NAME, serverName).toString();
        this.finalLogUploadChannel = finalLogUploadChannelParam;

        try {
            initializeServerFiles(serverCompressedFile);
            assignServerPorts();
        } catch (Exception e) {
            log.error("Failed to initialize DockerHandler for server {}: {}", serverName, e.getMessage(), e);
            // Attempt cleanup if serverPath was determined, even if initialization failed midway
            cleanupDirectory(new File(this.serverPath));
            throw e; // Re-throw the exception to signal failure
        }
    }

    /**
     * Initializes the server directory, validates the input compressed file, unzips it,
     * and determines if a full server.jar or only plugins were provided.
     *
     * @param serverCompressedFile The server compressed file.
     * @throws IOException if directory creation or file operations fail.
     * @throws IllegalArgumentException if the server compressed file is invalid.
     */
    private void initializeServerFiles(File serverCompressedFile) throws IOException, IllegalArgumentException {
        File serverDir = new File(this.serverPath);
        if (!serverDir.mkdirs() && (!serverDir.exists() || !serverDir.isDirectory())) {
            log.error("Cannot create server directory: {}", this.serverPath);
            throw new IOException("Cannot create server directory: " + this.serverPath);
        }

        validateServerCompressedFile(serverCompressedFile);

        try {
            Utils.unzip(serverCompressedFile, this.serverPath);
            log.info("Successfully uncompressed {} to {}", serverCompressedFile.getName(), this.serverPath);
            determineServerType();
        } catch (IOException e) {
            log.error("Failed to uncompress file {} to {}: {}", serverCompressedFile.getName(), this.serverPath, e.getMessage(), e);
            // serverDir is already created, cleanup will be handled by the calling constructor's catch block
            throw e;
        }
    }

    /**
     * Validates the provided server compressed file for existence, extension, and readability.
     *
     * @param serverCompressedFile The file to validate.
     * @throws IllegalArgumentException if validation fails.
     */
    private void validateServerCompressedFile(File serverCompressedFile) throws IllegalArgumentException {
        if (!serverCompressedFile.exists()) {
            log.error("Server compressed file does not exist: {}", serverCompressedFile.getAbsolutePath());
            throw new IllegalArgumentException("Server compressed file does not exist: " + serverCompressedFile.getAbsolutePath());
        }

        String fileExtension = Utils.getFileExtension(serverCompressedFile);
        // Special handling for tar.gz
        if (serverCompressedFile.getName().toLowerCase().endsWith(".tar.gz")) {
            fileExtension = "tar.gz";
        }

        if (!ALLOWED_COMPRESSED_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            log.error("Server file extension must be one of {}, but was: {}", ALLOWED_COMPRESSED_EXTENSIONS, fileExtension);
            throw new IllegalArgumentException("Server file extension must be one of " + ALLOWED_COMPRESSED_EXTENSIONS);
        }
        if (!serverCompressedFile.canRead()) {
            log.error("Cannot read server compressed file: {}", serverCompressedFile.getAbsolutePath());
            throw new IllegalArgumentException("Cannot read server compressed file: " + serverCompressedFile.getAbsolutePath());
        }
    }

    /**
     * Checks the uncompressed server files to determine if a server.jar is present
     * or if only a plugins directory was provided. Sets the {@code onlyPluginsProvided} flag.
     *
     * @throws IllegalArgumentException if neither server.jar nor a plugins directory is found.
     */
    private void determineServerType() throws IllegalArgumentException {
        if (log.isDebugEnabled()) {
            try {
                String fileTree = Files.walk(Paths.get(this.serverPath))
                        .map(path -> Paths.get(this.serverPath).relativize(path).toString())
                        .collect(Collectors.joining("\n  ", "  ", "")); // Indent for readability
                log.debug("Uncompressed file tree for server {} in '{}':\n{}", this.serverName, this.serverPath, fileTree);
            } catch (IOException e) {
                log.warn("Could not generate file tree for debug logging for server {} at path '{}': {}", this.serverName, this.serverPath, e.getMessage());
            }
        }

        File unzippedServerJar = Paths.get(this.serverPath, SERVER_JAR_FILENAME).toFile();
        File unzippedPluginsDir = Paths.get(this.serverPath, PLUGINS_DIRECTORY_NAME).toFile();

        if (unzippedServerJar.exists() && unzippedServerJar.isFile()) {
            this.onlyPluginsProvided = false;
            log.info("Found {} in the uncompressed archive at {}", SERVER_JAR_FILENAME, unzippedServerJar.getAbsolutePath());
        } else if (unzippedPluginsDir.exists() && unzippedPluginsDir.isDirectory()) {
            this.onlyPluginsProvided = true;
            log.info("Found {} directory at {} but no {}. Docker image will download PaperMC server version {}.",
                    PLUGINS_DIRECTORY_NAME, unzippedPluginsDir.getAbsolutePath(), SERVER_JAR_FILENAME, this.minecraftVersion);
        } else {
            log.error("The uncompressed folder {} does not contain a {} or a {}/ directory.", this.serverPath, SERVER_JAR_FILENAME, PLUGINS_DIRECTORY_NAME);

            // Cleanup will be handled by the calling constructor's catch block
            throw new IllegalArgumentException("Compressed file must contain a " + SERVER_JAR_FILENAME + " or a " + PLUGINS_DIRECTORY_NAME + "/ directory.");
        }
    }

    /**
     * Assigns available host ports for the Minecraft server and its debug interface.
     *
     * @throws IllegalStateException if suitable ports cannot be found.
     */
    private void assignServerPorts() throws IllegalStateException {
        this.assignedPort = findAvailablePort("server", DEFAULT_SERVER_PORT_START);
        log.info("Assigned host port {} for server {}", this.assignedPort, this.serverName);

        this.debugPort = findAvailablePort("debug", DEFAULT_DEBUG_PORT_START, this.assignedPort);
        log.info("Assigned debug port {} for server {}", this.debugPort, this.serverName);
    }

    /**
     * Finds an available network port starting from a given port, excluding specified ports.
     *
     * @param portType A descriptive name for the port type (e.g., "server", "debug") for logging.
     * @param startPort The port number to start searching from.
     * @param excludePorts An array of port numbers to exclude from consideration.
     * @return An available port number.
     * @throws IllegalStateException if no available port is found within the valid range.
     */
    private int findAvailablePort(String portType, int startPort, int... excludePorts) throws IllegalStateException {
        int portCandidate = startPort;
        List<Integer> excluded = Arrays.stream(excludePorts).boxed().collect(Collectors.toList());

        while (portCandidate <= MAX_PORT_NUMBER) {
            if (!excluded.contains(portCandidate) && isPortAvailable(portCandidate)) {
                return portCandidate;
            }
            portCandidate++;
        }
        log.error("No available {} ports found starting from {} up to {} (excluding: {}).", portType, startPort, MAX_PORT_NUMBER, excluded);
        throw new IllegalStateException("No available " + portType + " ports found");
    }

    /**
     * Builds and configures a DockerClient instance.
     * @return A configured DockerClient.
     */
    private DockerClient buildDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        return DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    /**
     * Creates and starts the Docker container for the Minecraft server.
     * It configures environment variables, port bindings, and volume mounts.
     * Also registers a shutdown hook to attempt to stop the server on JVM exit.
     */
    public void createServer() {
        final DockerClient docker = buildDockerClient();
        log.info("Creating Docker container {} with data from {} on host port {}", serverName, serverPath, assignedPort);

        List<String> envVars = new ArrayList<>();
        envVars.add("EULA=TRUE");
        envVars.add("DEBUG=true"); // For itzg/minecraft-server debug mode (enables Java debug agent on port 5005)
        envVars.add("ENFORCE_WHITELIST=true");
        envVars.add("WHITELIST=ICube56");
        envVars.add("OPS=ICube56");

        // limit ram usage
        envVars.add("MEMORY=5M"); // Set memory limit to 2GB, adjust as needed

        if (onlyPluginsProvided) {
            envVars.add("TYPE=PAPER");
            envVars.add("VERSION=" + this.minecraftVersion);
            log.info("Configuring Docker image to download PaperMC server version: {}", this.minecraftVersion);
        }

        HostConfig hostConfig = new HostConfig()
                .withBinds(new Bind(serverPath, new Volume("/data")))
                .withNetworkMode("bridge")
                .withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(assignedPort), new ExposedPort(25565, InternetProtocol.TCP)),
                        new PortBinding(Ports.Binding.bindPort(debugPort), new ExposedPort(5005, InternetProtocol.TCP)) // Minecraft debug port
                )
                .withRestartPolicy(RestartPolicy.noRestart());

        CreateContainerResponse container = docker.createContainerCmd(MINECRAFT_DOCKER_IMAGE)
                .withName(serverName)
                .withEnv(envVars)
                .withHostConfig(hostConfig)
                .withExposedPorts( // These are ports exposed *by the container image*, mapped by PortBindings
                        new ExposedPort(25565, InternetProtocol.TCP),
                        new ExposedPort(5005, InternetProtocol.TCP)
                )
                .exec();

        final String containerId = container.getId();
        docker.startContainerCmd(containerId).exec();
        log.info("Started Minecraft server container {} with ID {} on host port {}. Data bound from {}", serverName, containerId, assignedPort, serverPath);

        startEventListeners(containerId); // Start listening for container events

        log.debug("Registering runtime hook to stop the server {} on JVM shutdown.", serverName);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown detected. Attempting to stop server {}...", serverName);
            stopServer();
        }, "ShutdownHook-" + serverName));
    }

    /**
     * Checks if a given network port is available for use.
     * @param port The port number to check.
     * @return True if the port is available, false otherwise.
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true); // Allow fast reuse
            return true;
        } catch (IOException e) {
            return false; // Port is likely in use or other issue
        }
    }

    /**
     * Stops the Minecraft server container, uploads logs, removes the container, and cleans up local files.
     * This method is designed to be safe to call multiple times.
     */
    public void stopServer() {
        this.stopListenerActive = false; // Signal event listener to stop processing

        if (this.eventsListener != null) {
            try {
                this.eventsListener.close();
                log.info("Closed Docker event listener for {}.", serverName);
            } catch (IOException e) {
                log.warn("Failed to close Docker event listener for {}: {}", serverName, e.getMessage());
            } finally {
                this.eventsListener = null;
            }
        }

        DockerClient docker = buildDockerClient();
        try {
            // Check if container exists before trying to stop/remove
            InspectContainerResponse response = docker.inspectContainerCmd(serverName).exec(); // Throws NotFoundException if not found
            System.out.println(response.getState().getHealth().getStatus());
            String healthStatus = response.getState().getHealth() != null ? response.getState().getHealth().getStatus() : "unknown";

            log.info("Container {} (ID: {}) shutting down. Health status: {}", serverName, response.getId(), healthStatus);

            if ("starting".equals(healthStatus) || "unhealthy".equals(healthStatus) || "unknown".equals(healthStatus)) {
                log.warn("Container {} is not in a stable state. Not collecting logs as they most likely won't be available yet.", serverName);
            } else {
                log.debug("Container {} (ID: {}) is healthy. Proceeding with log upload.", serverName, response.getId());
                uploadLog(); // Attempt to upload logs before removing data
            }

            log.info("Stopping container {}...", serverName);
            docker.stopContainerCmd(serverName).withTimeout(30).exec(); // Give 30 seconds to stop gracefully
            log.info("Container {} stopped.", serverName);

            log.info("Removing container {}...", serverName);
            docker.removeContainerCmd(serverName).exec();
            log.info("Container {} removed.", serverName);

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.warn("Container {} not found. It might have been already stopped/removed.", serverName);
        } catch (Exception e) {
            log.error("Error during stop/remove of container {}: {}", serverName, e.getMessage(), e);
        } finally {
            cleanupDirectory(new File(serverPath));
            log.info("Finished stop process for server {} and cleaned up directory {}.", serverName, serverPath);
        }
    }

    /**
     * Recursively deletes the specified directory and its contents.
     * @param directory The directory to delete.
     */
    private void cleanupDirectory(File directory) {
        if (directory != null && directory.exists()) {
            try {
                Utils.deleteDirectory(directory);
                log.info("Successfully deleted directory: {}", directory.getAbsolutePath());
            } catch (Exception e) { // Catching general Exception as Utils.deleteDirectory might throw various
                log.warn("Failed to cleanup directory {}: {}", directory.getAbsolutePath(), e.getMessage(), e);
            }
        } else {
            log.debug("Cleanup: Directory {} is null or does not exist. No action taken.", directory == null ? "null" : directory.getAbsolutePath());
        }
    }

    /**
     * @return The host port assigned to the Minecraft server (maps to container port 25565).
     */
    public int getAssignedPort() {
        return assignedPort;
    }

    /**
     * @return The host port assigned for Java debugging (maps to container port 5005).
     */
    public int getDebugPort() {
        return debugPort;
    }

    /**
     * Starts listening for Docker events (like stop, die) for the specified container.
     * If the container stops unexpectedly, this listener will trigger cleanup.
     *
     * @param containerId The ID of the container to monitor.
     */
    private void startEventListeners(String containerId) {
        if (this.eventsListener != null) { // Close any existing listener
            try {
                this.eventsListener.close();
                log.info("Closed existing event listener before starting a new one for {}.", serverName);
            } catch (IOException e) {
                log.warn("Could not close previous event listener for {}: {}", serverName, e.getMessage());
            }
        }

        DockerClient dockerClientForListeners = buildDockerClient();
        this.stopListenerActive = true; // Activate listener processing

        EventsCmd eventsCmd = dockerClientForListeners.eventsCmd()
                .withContainerFilter(containerId)
                .withEventFilter("stop", "die", "kill", "destroy"); // Listen for relevant events

        this.eventsListener = eventsCmd.exec(new ResultCallback.Adapter<Event>() {
            @Override
            public void onStart(Closeable closeable) {
                log.info("Event listener (for stop/die events) started for container {} (ID: {}).", serverName, containerId);
            }

            @Override
            public void onNext(Event event) {
                if (!stopListenerActive) { // Check if we should still process events
                    try {
                        close(); // Close the callback if listener is deactivated
                    } catch (IOException e) {
                        log.warn("Error closing event listener callback for {} after deactivation: {}", serverName, e.getMessage());
                    }
                    return;
                }

                String action = event.getAction();
                log.info("Container {} (ID: {}) event: Action={}, Status={}. Processing stop.",
                        serverName, event.getId(), action, event.getStatus());

                // Ensure stopServer is called and listener is deactivated
                if (stopListenerActive) { // Double check, as stopServer might be called from elsewhere
                    stopListenerActive = false; // Deactivate before calling stopServer to prevent re-entry
                    try {
                        close(); // Attempt to close the event stream itself
                        log.info("Event listener for container {} (ID: {}) self-closed after {} event.", serverName, event.getId(), action);
                    } catch (IOException e) {
                        log.warn("Error self-closing event listener for {} (ID: {}): {}", serverName, event.getId(), e.getMessage());
                    }
                    // Call stopServer to ensure full cleanup, even if triggered by event
                    // This might be redundant if stopServer was called externally, but stopServer is idempotent.
                    stopServer();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (stopListenerActive) { // Log error only if listener was supposed to be active
                    log.error("Error in Docker event listener for container {}: {}", serverName, throwable.getMessage(), throwable);
                }
                // If the event listener itself errors, attempt to stop the server to ensure cleanup.
                // This is a critical failure of the monitoring mechanism.
                if (stopListenerActive) {
                    stopListenerActive = false;
                    stopServer();
                }
            }

            @Override
            public void onComplete() {
                log.info("Docker event stream (for stop/die events) completed for container {}.", serverName);
                // If stream completes, it means container is likely gone or Docker daemon is shutting down listener.
                // Ensure cleanup if it wasn't already triggered.
                if (stopListenerActive) {
                    stopListenerActive = false;
                    // Do not call stopServer() here directly as it might have been a clean shutdown
                    // and stopServer() would have already been called.
                    // However, log upload might be relevant if not done.
                    uploadLog();
                }
            }
        });
        log.info("Event listener (for stop/die events) registered for container {} (ID: {})", serverName, containerId);
    }

    /**
     * Uploads the server's latest log file (filtered for IP addresses) to the configured Discord channel.
     */
    private void uploadLog() {
        if (finalLogUploadChannel == null) {
            log.warn("Final log upload channel is null for server {}. Skipping log upload.", serverName);
            return;
        }

        Path logFilePath = Paths.get(serverPath, LOGS_DIRECTORY_NAME, LATEST_LOG_FILENAME);
        File logFile = logFilePath.toFile();

        if (logFile.exists() && logFile.canRead()) {
            Path filteredLogFilePath = Paths.get(serverPath, LOGS_DIRECTORY_NAME, FILTERED_LOG_FILENAME_PREFIX + LATEST_LOG_FILENAME);
            File filteredLogFile = filteredLogFilePath.toFile();

            try {
                filterLogFile(logFile, filteredLogFile);

                finalLogUploadChannel.sendMessage(
                        new MessageCreateBuilder()
                                .addContent("Server logs for **" + serverName + "** (UUID: " + uuid + ")")
                                .addFiles(FileUpload.fromData(filteredLogFile))
                                .build()
                ).queue(
                        success -> {
                            log.info("Successfully uploaded filtered server logs for {} to channel {}.", serverName, finalLogUploadChannel.getName());
                            deleteTemporaryFile(filteredLogFile, "filtered log");
                        },
                        failure -> {
                            log.error("Failed to upload server logs for {} to channel {}: {}", serverName, finalLogUploadChannel.getName(), failure.getMessage());
                            deleteTemporaryFile(filteredLogFile, "filtered log (on failure)");
                        }
                );
            } catch (IOException e) {
                log.error("Error processing or uploading logs for server {} to channel {}: {}", serverName, finalLogUploadChannel.getName(), e.getMessage(), e);
                deleteTemporaryFile(filteredLogFile, "filtered log (on exception)");
            }
        } else {
            log.warn("Log file does not exist or cannot be read for server {}: {}", serverName, logFile.getAbsolutePath());
        }
    }

    /**
     * Helper method to delete a temporary file and log the outcome.
     * @param file The file to delete.
     * @param description A description of the file for logging purposes.
     */
    private void deleteTemporaryFile(File file, String description) {
        if (file.exists()) {
            try {
                if (Files.deleteIfExists(file.toPath())) {
                    log.debug("Successfully deleted temporary {} file: {}", description, file.getAbsolutePath());
                } else {
                    log.warn("Could not delete temporary {} file (deleteIfExists returned false): {}", description, file.getAbsolutePath());
                }
            } catch (IOException e) {
                log.warn("Failed to delete temporary {} file {}: {}", description, file.getAbsolutePath(), e.getMessage());
            }
        }
    }


    /**
     * Filters a log file to remove IP address-like patterns, writing the output to a new file.
     *
     * @param sourceFile The original log file.
     * @param destFile The file where the filtered log content will be written.
     * @throws IOException if an I/O error occurs during reading or writing.
     */
    private void filterLogFile(File sourceFile, File destFile) throws IOException {
        Pattern ipPattern = Pattern.compile(IP_ADDRESS_LOG_FILTER_REGEX);
        log.debug("Filtering log file {} to {}", sourceFile.getAbsolutePath(), destFile.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(destFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String filteredLine = ipPattern.matcher(line).replaceAll(FILTERED_IP_REPLACEMENT_TEXT);
                writer.write(filteredLine);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Error filtering log file from {} to {}: {}", sourceFile.getAbsolutePath(), destFile.getAbsolutePath(), e.getMessage(), e);
            throw e;
        }
        log.debug("Successfully filtered log file {} to {}", sourceFile.getAbsolutePath(), destFile.getAbsolutePath());
    }
}