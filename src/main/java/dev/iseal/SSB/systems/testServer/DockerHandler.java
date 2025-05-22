package dev.iseal.SSB.systems.testServer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.iseal.SSB.utils.Utils;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DockerHandler {

    private final UUID uuid;
    private final String serverName;
    private final String serverPath;
    private final int assignedPort;
    private final int debugPort;
    private final Logger log = JDALogger.getLog(getClass());

    private final boolean onlyPluginsProvided;
    private final String minecraftVersion; // To be used if onlyPluginsProvided is true

    public DockerHandler(File serverZipFile, UUID id, String minecraftVersion) throws Exception {
        this.uuid = id;
        this.serverName = "testserver-" + uuid;
        this.minecraftVersion = (minecraftVersion == null || minecraftVersion.trim().isEmpty()) ? "LATEST" : minecraftVersion;
        this.serverPath = System.getProperty("java.io.tmpdir") + File.separator + "servers" + File.separator + serverName + File.separator;
        File serverDir = new File(serverPath);

        if (!serverDir.mkdirs()) {
            if (!serverDir.exists() || !serverDir.isDirectory()) {
                log.error("Cannot create server directory: {}", serverPath);
                throw new RuntimeException("Cannot create server directory: " + serverPath);
            }
        }

        if (!serverZipFile.exists()) {
            log.error("File does not exist: {}", serverZipFile.getAbsolutePath());
            cleanupDirectory(serverDir);
            throw new IllegalArgumentException("File does not exist: " + serverZipFile.getAbsolutePath());
        }

        if (!"zip".equalsIgnoreCase(Utils.getFileExtension(serverZipFile))) {
            log.error("File extension must be zip, but was: {}", Utils.getFileExtension(serverZipFile));
            cleanupDirectory(serverDir);
            throw new IllegalArgumentException("File extension must be zip");
        }
        if (!serverZipFile.canRead()) {
            log.error("Cannot read file: {}", serverZipFile.getAbsolutePath());
            cleanupDirectory(serverDir);
            throw new IllegalArgumentException("Cannot read file: " + serverZipFile.getAbsolutePath());
        }

        try {
            Utils.unzip(serverZipFile, serverPath);
            log.info("Successfully unzipped {} to {}", serverZipFile.getName(), serverPath);

            File unzippedServerJar = new File(serverPath + "server.jar");
            File unzippedPluginsDir = new File(serverPath + "plugins");

            if (unzippedServerJar.exists() && unzippedServerJar.isFile()) {
                this.onlyPluginsProvided = false;
                log.info("Found server.jar in the unzipped archive at {}", unzippedServerJar.getAbsolutePath());
            } else if (unzippedPluginsDir.exists() && unzippedPluginsDir.isDirectory()) {
                this.onlyPluginsProvided = true;
                log.info("Found plugins directory at {} but no server.jar. Docker image will download PaperMC server version {}.", unzippedPluginsDir.getAbsolutePath(), this.minecraftVersion);
            } else {
                log.error("The unzipped folder {} does not contain a server.jar or a plugins/ directory.", serverPath);
                cleanupDirectory(serverDir);
                throw new IllegalArgumentException("Zip must contain a server.jar or a plugins/ directory.");
            }

        } catch (IOException e) {
            log.error("Failed to unzip file {} to {}: {}", serverZipFile.getName(), serverPath, e.getMessage(), e);
            cleanupDirectory(serverDir);
            throw e;
        }

        int portCandidate = 25565; // Start searching from default Minecraft port
        while (true) {
            if (portCandidate > 65535) {
                log.error("No available ports found after checking up to 65535.");
                cleanupDirectory(serverDir);
                throw new IllegalStateException("No available ports found");
            }
            if (isPortAvailable(portCandidate)) {
                assignedPort = portCandidate;
                log.info("Assigned host port {} for server {}", assignedPort, serverName);
                break;
            }
            portCandidate++;
        }

        int debugPortCandidate = 1025;
        while (true) {
            if (debugPortCandidate > 65535) {
                log.error("No available debug ports found after checking up to 65535.");
                cleanupDirectory(serverDir);
                throw new IllegalStateException("No available debug ports found");
            }
            if (isPortAvailable(debugPortCandidate) && debugPortCandidate != assignedPort) {
                debugPort = debugPortCandidate;
                log.info("Assigned debug port {} for server {}", assignedPort, serverName);
                break;
            }
            debugPortCandidate++;
        }
    }

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

    public void createServer() {
        DockerClient docker = buildDockerClient();
        log.info("Creating Docker container {} with data from {} on host port {}", serverName, serverPath, assignedPort);

        List<String> envVars = new ArrayList<>();
        envVars.add("EULA=TRUE");
        envVars.add("DEBUG=true");

        envVars.add("ENFORCE_WHITELIST=true");
        envVars.add("WHITELIST=ICube56");
        envVars.add("OPS=ICube56");

        if (onlyPluginsProvided) {
            envVars.add("TYPE=PAPER");
            envVars.add("VERSION=" + this.minecraftVersion);
            log.info("Configuring Docker image to download PaperMC server version: {}", this.minecraftVersion);
        }

        HostConfig hostConfig = new HostConfig()
                .withBinds(new Bind(serverPath, new Volume("/data")))
                .withNetworkMode("bridge")
                .withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(assignedPort), new ExposedPort(25565, InternetProtocol.TCP)), // Server port
                        new PortBinding(Ports.Binding.bindPort(debugPort), new ExposedPort(5005, InternetProtocol.TCP)) // Debug port
                )
                .withRestartPolicy(RestartPolicy.noRestart());

        CreateContainerResponse container = docker.createContainerCmd("itzg/minecraft-server")
                .withName(serverName)
                .withEnv(envVars)
                .withHostConfig(hostConfig)
                .withExposedPorts(new ExposedPort(25565, InternetProtocol.TCP)) // Server port
                .withExposedPorts(new ExposedPort(5005, InternetProtocol.TCP)) // Debug port
                .exec();

        docker.startContainerCmd(container.getId()).exec();
        log.info("Started Minecraft server container {} with ID {} on host port {}. Data bound from {}", serverName, container.getId(), assignedPort, serverPath);
        log.debug("Registering runtime hook to stop the server on JVM shutdown.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown detected. Stopping server {}...", serverName);
            stopServer();
        }));
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stopServer() {
        DockerClient docker = buildDockerClient(); // New line
        try {
            log.info("Stopping container {}...", serverName);
            docker.stopContainerCmd(serverName).exec();
            log.info("Container {} stopped.", serverName);
        } catch (Exception e) {
            log.warn("Failed to stop container {} (it might not be running or already removed): {}", serverName, e.getMessage());
        }

        try {
            log.info("Removing container {}...", serverName);
            docker.removeContainerCmd(serverName).exec();
            log.info("Container {} removed.", serverName);
        } catch (Exception e) {
            log.warn("Failed to remove container {} (it might have been already removed): {}", serverName, e.getMessage());
        }

        log.info("Deleting server directory {}...", serverPath);
        cleanupDirectory(new File(serverPath));
        log.info("Successfully processed stop/remove for container {} and deleted directory {}", serverName, serverPath);
    }

    private void cleanupDirectory(File directory) {
        if (directory != null && directory.exists()) {
            try {
                Utils.deleteDirectory(directory);
                log.info("Successfully deleted directory: {}", directory.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Failed to cleanup directory {}: {}", directory.getAbsolutePath(), e.getMessage(), e);
            }
        } else {
            log.debug("Cleanup: Directory {} is null or does not exist. No action taken.", directory == null ? "null" : directory.getAbsolutePath());
        }
    }

    public int getAssignedPort() {
        return assignedPort;
    }

    public int getDebugPort() {
        return debugPort;
    }
}