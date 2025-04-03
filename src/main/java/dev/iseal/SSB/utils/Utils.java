package dev.iseal.SSB.utils;

import dev.iseal.SSB.SSBMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import org.reflections.Reflections;

import java.util.Set;

public class Utils {

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

}
