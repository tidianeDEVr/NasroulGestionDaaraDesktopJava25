package com.nasroul.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private final Properties properties;

    private ConfigManager() {
        properties = new Properties();
        loadConfig();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
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
        return getProperty("db.sqlite.path", "association.db");
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
}
