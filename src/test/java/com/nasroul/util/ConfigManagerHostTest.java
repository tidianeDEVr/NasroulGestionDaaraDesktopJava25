package com.nasroul.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerHostTest {

    @Test
    void stripsSchemePathPortAndCredentials() {
        assertEquals("nabou.wanekoohost.com", ConfigManager.normalizeMySQLHost("https://nabou.wanekoohost.com"));
        assertEquals("nabou.wanekoohost.com", ConfigManager.normalizeMySQLHost("https://nabou.wanekoohost.com/"));
        assertEquals("nabou.wanekoohost.com", ConfigManager.normalizeMySQLHost(" nabou.wanekoohost.com:3306 "));
        assertEquals("nabou.wanekoohost.com", ConfigManager.normalizeMySQLHost("jdbc:mysql://user:pwd@nabou.wanekoohost.com:3306/db"));
        assertEquals("65.21.8.38", ConfigManager.normalizeMySQLHost("65.21.8.38"));
        assertEquals("[::1]", ConfigManager.normalizeMySQLHost("[::1]"));
        assertEquals("localhost", ConfigManager.normalizeMySQLHost(""));
        assertEquals("localhost", ConfigManager.normalizeMySQLHost(null));
    }
}
