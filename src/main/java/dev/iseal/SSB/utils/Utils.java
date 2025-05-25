package dev.iseal.SSB.utils;

import de.leonhard.storage.Json;
import dev.iseal.SSB.SSBMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

    private static final Json tempData = new Json("tempData.json", System.getProperty("user.dir") + "/data/tempData");
    private static final Logger log = JDALogger.getLog(Utils.class);

    /**
     * Find all classes in a package that extend a given class
     *
     *
     * @param packageName the package name to search in
     * @param clazz the class to search for
     * @return a set of classes that extend the given class
     */
    public static Set<Class<?>> findAllClassesInPackage(String packageName, Class<?> clazz) {
        Reflections reflections = new Reflections(packageName);
        return (Set<Class<?>>) reflections.getSubTypesOf(clazz);
    }

    public static User getUserFromCacheOrFetch(long userID) {
        JDA jda = SSBMain.getJDA();
        User user = jda.getUserById(userID);
        if (user == null) {
            user = jda.retrieveUserById(userID).complete();
        }
        return user;
    }

    public static Member getGuildMemberFromCacheOrFetch(User user, Guild guild) {
        Member member = guild.getMember(user);
        if (member == null) {
            member = guild.retrieveMember(user).complete();
        }
        return member;
    }

    /**
     * A functional interface that represents a consumer that takes three arguments.
     *
     * @param <T> the type of the first argument
     * @param <U> the type of the second argument
     * @param <V> the type of the third argument
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    public static void sendMessage(long userId, String content) {
        SSBMain.getJDA().openPrivateChannelById(userId)
                .flatMap(channel -> channel.sendMessage(content))
                .queue();
    }

    public static void sendEmbed(long userId, EmbedBuilder embed) {
        SSBMain.getJDA().openPrivateChannelById(userId)
                .flatMap(channel -> channel.sendMessageEmbeds(embed.build()))
                .queue();
    }

    public static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (java.io.IOException e) {
            log.error("Failed to read file {}: {}", file.getName(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void addTempFileData(String key, Object value) {
        tempData.set(key, value);
    }

    public static <T> T getTempFileData(String key, T type) {
        return tempData.get(key, type);
    }

    public static void removeTempFileData(String key) {
        tempData.remove(key);
    }


    public static String getFileExtension(File file) {
        String name = file.getName();
        if (name == null || name.isEmpty()) {
            return ""; // empty extension
        }
        if (!name.contains(".")) {
            return ""; // no extension
        }
        // check for complex extensions
        if (hasCharacterCountExact(name, '.', 2)) {
            // if there are multiple dots, it could be a file with a complex extension like "archive.tar.gz"
            // return the part after the second to last dot
            int lastIndexOf = name.lastIndexOf(".");
            int secondLastIndexOf = name.lastIndexOf(".", lastIndexOf - 1);
            if (secondLastIndexOf == -1) {
                return ""; // no valid extension
            }
            return name.substring(secondLastIndexOf + 1);
        }
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf + 1);
    }

    public static void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    public static void unzip(File zipFilePath, String destDirectory) throws IOException {
        if (zipFilePath == null || !zipFilePath.exists() || !zipFilePath.isFile()) {
            throw new IOException("Invalid zip file path: " + zipFilePath);
        }
        if (destDirectory == null || destDirectory.isEmpty()) {
            throw new IOException("Destination directory cannot be null or empty.");
        }
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        String fileExtension = getFileExtension(zipFilePath);
        log.debug("Attempting to extract file {} with extension {}", zipFilePath.getName(), fileExtension);

        switch (fileExtension) {
            case "zip":
                unzipZipFile(zipFilePath, destDir);
                break;
            case "tar":
                unzipTarFile(zipFilePath, destDir);
                break;
            case "tar.gz":
                unzipTarGzFile(zipFilePath, destDir);
                break;
            case "7z":
                // Implement 7z extraction logic here
                throw new UnsupportedOperationException("7z file extraction is not implemented yet.");
                //break;
            default:
                throw new IOException("Unsupported file type: " + getFileExtension(zipFilePath));
        }
    }

    private static void unzipZipFile(File zipFilePath, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static void unzipTarFile(File tarFilePath, File destDir) throws IOException {
        try (InputStream fi = new FileInputStream(tarFilePath);
             InputStream bi = new BufferedInputStream(fi);
             TarArchiveInputStream tis = new TarArchiveInputStream(bi)) {
            extractFromTarArchiveInputStream(tis, destDir);
        }
    }

    private static void unzipTarGzFile(File tarGzFilePath, File destDir) throws IOException {
        try (InputStream fi = new FileInputStream(tarGzFilePath);
             InputStream bi = new BufferedInputStream(fi);
             InputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {
            extractFromTarArchiveInputStream(tis, destDir);
        }
    }

    private static void extractFromTarArchiveInputStream(TarArchiveInputStream tis, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        TarArchiveEntry entry;
        while ((entry = tis.getNextTarEntry()) != null) {
            File newFile = newFile(destDir, entry.getName());
            if (entry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = tis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
    }



    // Helper method to prevent Zip Slip vulnerability
    private static File newFile(File destinationDir, String zipEntryName) throws IOException {
        File destFile = new File(destinationDir, zipEntryName);
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntryName);
        }
        return destFile;
    }

    /**
     * Checks if a specific character appears more or the exact number of times in a string.
     *
     * @param str The string to check. Can be null.
     * @param character The character to count.
     * @param expectedOccurrences The expected number of times the character should appear. Must be non-negative.
     * @return {@code true} if the character appears more or the exact number of {@code expectedOccurrences} times,
     *         {@code false} otherwise. Returns {@code false} if the input string is null or
     *         if {@code expectedOccurrences} is negative.
     */
    public static boolean hasAtLeastCharacterCount(String str, char character, int expectedOccurrences) {
        if (str == null || expectedOccurrences < 0) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == character) {
                count++;
            }
        }
        return count >= expectedOccurrences;
    }

    /**
     * Checks if a specific character appears an exact number of times in a string.
     *
     * @param str The string to check. Can be null.
     * @param character The character to count.
     * @param expectedOccurrences The expected number of times the character should appear. Must be non-negative.
     * @return {@code true} if the character appears exactly {@code expectedOccurrences} times,
     *         {@code false} otherwise. Returns {@code false} if the input string is null or
     *         if {@code expectedOccurrences} is negative.
     */
    public static boolean hasCharacterCountExact(String str, char character, int expectedOccurrences) {
        if (str == null || expectedOccurrences < 0) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == character) {
                count++;
            }
        }
        return count == expectedOccurrences;
    }

}
