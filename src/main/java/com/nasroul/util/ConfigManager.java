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
