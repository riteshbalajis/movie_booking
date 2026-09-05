package com.movie_booking.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Central configuration.
 *
 * <p>Values are resolved in this order, first match wins:
 * <ol>
 *   <li>a JVM system property  ({@code -Ddb.password=...})</li>
 *   <li>an environment variable, upper-snake-cased ({@code DB_PASSWORD})</li>
 *   <li>{@code config/app.properties} next to the working directory</li>
 *   <li>the hard-coded default passed by the caller</li>
 * </ol>
 *
 * <p>This ordering is what lets the same build run on a laptop and on a server
 * without editing source, and it keeps real credentials out of the repository.
 */
public final class AppConfig {

    private static final Properties FILE_PROPERTIES = loadPropertiesFile();

    private AppConfig() {
        // Utility class.
    }

    public static String get(String key, String defaultValue) {
        String fromSystem = System.getProperty(key);
        if (isPresent(fromSystem)) {
            return fromSystem.trim();
        }

        String fromEnv = System.getenv(toEnvName(key));
        if (isPresent(fromEnv)) {
            return fromEnv.trim();
        }

        String fromFile = FILE_PROPERTIES.getProperty(key);
        if (isPresent(fromFile)) {
            return fromFile.trim();
        }

        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Configuration key '" + key + "' must be an integer but was: " + value, ex);
        }
    }

    public static long getLong(String key, long defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Configuration key '" + key + "' must be a number but was: " + value, ex);
        }
    }

    /** {@code db.password} becomes {@code DB_PASSWORD}. */
    private static String toEnvName(String key) {
        return key.replace('.', '_').toUpperCase();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Properties loadPropertiesFile() {
        Properties properties = new Properties();
        Path path = Paths.get("config", "app.properties");

        if (Files.isReadable(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException ex) {
                System.err.println("[config] Could not read " + path.toAbsolutePath()
                        + ": " + ex.getMessage());
            }
        }
        return properties;
    }
}
