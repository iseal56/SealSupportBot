package dev.iseal.SSB.utils;

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
}
