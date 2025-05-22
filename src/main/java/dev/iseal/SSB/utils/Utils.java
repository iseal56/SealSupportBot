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
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
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

    // Helper method to prevent Zip Slip vulnerability
    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }

}
