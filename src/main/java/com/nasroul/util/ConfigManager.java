package com.nasroul.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private final Properties properties;
    private final String appDataDir;

    private ConfigManager() {
        properties = new Properties();
        appDataDir = initializeAppDataDirectory();
        loadConfig();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Initialize application data directory based on OS
     * Windows: %APPDATA%/NasroulGestion
     * Linux/Mac: ~/.nasroulgestion
     */
    private String initializeAppDataDirectory() {
        String osName = System.getProperty("os.name").toLowerCase();
        String baseDir;

        if (osName.contains("win")) {
            // Windows: Use APPDATA directory
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                baseDir = appData + File.separator + "NasroulGestion";
            } else {
                // Fallback to user home if APPDATA not available
                baseDir = System.getProperty("user.home") + File.separator + "NasroulGestion";
            }
        } else if (osName.contains("mac")) {
            // macOS: Use Application Support
            baseDir = System.getProperty("user.home") + File.separator + "Library" + File.separator + "Application Support" + File.separator + "NasroulGestion";
        } else {
            // Linux and others: Use hidden directory in home
            baseDir = System.getProperty("user.home") + File.separator + ".nasroulgestion";
        }

        // Create directory if it doesn't exist
        try {
            Path dirPath = Paths.get(baseDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Created application data directory: " + baseDir);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not create data directory: " + e.getMessage());
            // Fallback to current directory if we can't create data directory
            baseDir = System.getProperty("user.dir");
        }

        System.out.println("Application data directory: " + baseDir);
        return baseDir;
    }

    private void loadConfig() {
        // Try to load from external config.properties first
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            System.out.println("Configuration loaded from config.properties");
            return;
        } catch (IOException e) {
            System.out.println("config.properties not found, trying classpath...");
        }

        // Fall back to classpath config.properties
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                properties.load(is);
                System.out.println("Configuration loaded from classpath");
            } else {
                System.err.println("Warning: No configuration file found. Using default values.");
            }
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    // Database Configuration getters
    public String getDbType() {
        return getProperty("db.type", "sqlite");
    }

    public String getMySQLHost() {
        return getProperty("db.mysql.host", "localhost");
    }

    public String getMySQLPort() {
        return getProperty("db.mysql.port", "3306");
    }

    public String getMySQLDatabase() {
        return getProperty("db.mysql.database", "association_manager");
    }

    public String getMySQLUsername() {
        return getProperty("db.mysql.username", "");
    }

    public String getMySQLPassword() {
        return getProperty("db.mysql.password", "");
    }

    public String getMySQLUseSSL() {
        return getProperty("db.mysql.useSSL", "false");
    }

    public String getMySQLServerTimezone() {
        return getProperty("db.mysql.serverTimezone", "UTC");
    }

    public String getSQLitePath() {
        String configuredPath = getProperty("db.sqlite.path", "association.db");

        // Check if path is absolute
        File file = new File(configuredPath);
        if (file.isAbsolute()) {
            // Use absolute path as-is, but ensure parent directory exists
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                try {
                    Files.createDirectories(parentDir.toPath());
                    System.out.println("Created database directory: " + parentDir.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Warning: Could not create database directory: " + e.getMessage());
                }
            }
            return configuredPath;
        } else {
            // Relative path: place in application data directory
            String fullPath = appDataDir + File.separator + configuredPath;
            System.out.println("SQLite database path: " + fullPath);
            return fullPath;
        }
    }

    public String getMySQLConnectionUrl() {
        return String.format("jdbc:mysql://%s:%s/%s?useSSL=%s&serverTimezone=%s&allowPublicKeyRetrieval=true",
            getMySQLHost(),
            getMySQLPort(),
            getMySQLDatabase(),
            getMySQLUseSSL(),
            getMySQLServerTimezone()
        );
    }

    // SMS Configuration getters
    public String getSmsAccountId() {
        return getProperty("sms.account.id", "");
    }

    public String getSmsPassword() {
        return getProperty("sms.password", "");
    }

    public String getSmsSender() {
        return getProperty("sms.sender", "LAM");
    }

    public String getSmsApiUrl() {
        return getProperty("sms.api.url", "https://lamsms.lafricamobile.com/api");
    }

    public String getSmsCreditsUrl() {
        return getProperty("sms.credits.url", "https://lamsms.lafricamobile.com/credits");
    }

    // Sync Configuration getters

    /**
     * Check if auto-sync is enabled
     */
    public boolean isSyncAutoEnabled() {
        return Boolean.parseBoolean(getProperty("sync.auto.enabled", "false"));
    }

    /**
     * Get auto-sync interval in minutes
     */
    public int getSyncAutoInterval() {
        return Integer.parseInt(getProperty("sync.auto.interval", "30"));
    }

    /**
     * Get sync conflict resolution strategy
     * Options: LAST_WRITE_WINS, LOCAL_WINS, REMOTE_WINS, MANUAL, HIGHER_VERSION_WINS
     */
    public String getSyncConflictStrategy() {
        return getProperty("sync.conflict.strategy", "LAST_WRITE_WINS");
    }

    /**
     * Check if sync should run on startup
     */
    public boolean isSyncOnStartup() {
        return Boolean.parseBoolean(getProperty("sync.on.startup", "false"));
    }

    /**
     * Get number of days to keep sync logs
     */
    public int getSyncLogRetentionDays() {
        return Integer.parseInt(getProperty("sync.log.retention.days", "30"));
    }

    /**
     * Check if offline mode is enabled (only use SQLite)
     */
    public boolean isOfflineModeEnabled() {
        return Boolean.parseBoolean(getProperty("sync.offline.mode", "false"));
    }

    /**
     * Get sync timeout in seconds
     */
    public int getSyncTimeout() {
        return Integer.parseInt(getProperty("sync.timeout.seconds", "300"));
    }

    /**
     * Check if sync notifications are enabled
     */
    public boolean isSyncNotificationsEnabled() {
        return Boolean.parseBoolean(getProperty("sync.notifications.enabled", "true"));
    }
}
