package dev.iseal.SSB.utils.utils;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

public class RuntimeInterpreter {

    public static Object evaluate(String code) throws Exception {
        // Create compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Create unique directory in temp folder
        String uniqueDirName = "dynamic-" + UUID.randomUUID();
        File tempDir = new File(System.getProperty("java.io.tmpdir"), uniqueDirName);
        tempDir.mkdirs();

        File sourceFile = new File(tempDir, "DynamicClass.java");

        try {
            // Write code to temp file
            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(code);
            }

            // Compile it
            int result = compiler.run(null, null, null, sourceFile.getPath());
            if (result != 0) {
                throw new RuntimeException("Compilation failed");
            }

            // Load and use the class
            try (URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { tempDir.toURI().toURL() })) {
                Class<?> cls = Class.forName("DynamicClass", true, classLoader);
                return cls.getDeclaredConstructor().newInstance();
            }
        } finally {
            // Clean up all files
            deleteDirectory(tempDir);
        }
    }

    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            directory.delete();
        }
    }
}