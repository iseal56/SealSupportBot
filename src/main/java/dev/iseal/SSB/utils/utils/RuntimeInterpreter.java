package dev.iseal.SSB.utils.utils;

import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

public class RuntimeInterpreter {

    Logger log = JDALogger.getLog(RuntimeInterpreter.class);

    public static Object evaluate(String code) throws Exception {
        // Create compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("JavaCompiler not found. Ensure you are running with a JDK, not just a JRE.");
        }

        // Create unique directory in temp folder
        String uniqueDirName = "dynamic-" + UUID.randomUUID();
        File tempDir = new File(System.getProperty("java.io.tmpdir"), uniqueDirName);
        if (!tempDir.mkdirs() && !tempDir.exists()) {
            throw new IOException("Could not create temporary directory: " + tempDir.getAbsolutePath());
        }

        File sourceFile = new File(tempDir, "DynamicClass.java");
        String finalCode;

        // Heuristic: if the code contains "class DynamicClass", assume user provided the full class.
        // This implies that if they provide a class, it MUST be named DynamicClass for the current loading mechanism.
        if (code != null && code.contains("class DynamicClass")) {
            finalCode = code;
        } else {
            // Otherwise, wrap the code in the constructor of a new DynamicClass.
            // This assumes 'code' consists of valid Java statements.
            StringBuilder sb = new StringBuilder();
            sb.append("public class DynamicClass {\n");
            sb.append("    public DynamicClass() {\n");
            if (code != null) {
                sb.append("        ").append(code).append("\n");
            }
            sb.append("    }\n");
            sb.append("}\n");
            finalCode = sb.toString();
        }

        try {
            // Write code to temp file
            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(finalCode);
            }

            // Compile it
            int result = compiler.run(null, null, null, sourceFile.getPath());
            if (result != 0) {
                // Consider capturing compiler output for better error reporting if possible
                throw new RuntimeException("Compilation failed. Source code:\n" + finalCode);
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
                    // This simple delete is fine as we only create .java and .class files directly in tempDir
                    file.delete();
                }
            }
            directory.delete();
        }
    }
}