package com.nasroul.service;

import com.nasroul.dao.DatabaseManager;
import com.nasroul.util.DeviceIdGenerator;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing device registration and tracking
 * Registers devices in the sync_devices table
 */
public class DeviceRegistrationService {

    private static DeviceRegistrationService instance;
    private final DatabaseManager dbManager;
    private String currentDeviceId;
    private String deviceName;
    private String userName;

    private DeviceRegistrationService() {
        this.dbManager = DatabaseManager.getInstance();
        this.currentDeviceId = DeviceIdGenerator.getDeviceId();
        this.deviceName = getSystemHostname();
        this.userName = System.getProperty("user.name", "Unknown");
    }

    public static synchronized DeviceRegistrationService getInstance() {
        if (instance == null) {
            instance = new DeviceRegistrationService();
        }
        return instance;
    }

    /**
     * Register current device in the sync_devices table
     * Creates a new entry or updates existing one
     */
    public void registerDevice() throws SQLException {
        if (isDeviceRegistered()) {
            updateDeviceRegistration();
        } else {
            insertDeviceRegistration();
        }

        // Also register in MySQL for cross-device visibility
        registerDeviceMySQL();
    }

    /**
     * Register device in MySQL (for cross-device sync)
     */
    public void registerDeviceMySQL() throws SQLException {
        // Check if MySQL is available
        if (!dbManager.isMySQLAvailable()) {
            return; // Skip if MySQL not available
        }

        if (isDeviceRegisteredMySQL()) {
            updateDeviceRegistrationMySQL();
        } else {
            insertDeviceRegistrationMySQL();
        }
    }

    private boolean isDeviceRegisteredMySQL() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_devices WHERE device_id = ?";

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertDeviceRegistrationMySQL() throws SQLException {
        String sql = """
            INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active)
            VALUES (?, ?, ?, NOW(), 1)
            """;

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);
            pstmt.setString(2, deviceName);
            pstmt.setString(3, userName);

            pstmt.executeUpdate();
        }
    }

    private void updateDeviceRegistrationMySQL() throws SQLException {
        String sql = """
            UPDATE sync_devices
            SET device_name = ?, user_name = ?, last_sync_at = NOW(), is_active = 1
            WHERE device_id = ?
            """;

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, deviceName);
            pstmt.setString(2, userName);
            pstmt.setString(3, currentDeviceId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Check if current device is already registered
     */
    public boolean isDeviceRegistered() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_devices WHERE device_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertDeviceRegistration() throws SQLException {
        String sql = """
            INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active)
            VALUES (?, ?, ?, datetime('now'), 1)
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);
            pstmt.setString(2, deviceName);
            pstmt.setString(3, userName);

            pstmt.executeUpdate();
        }
    }

    private void updateDeviceRegistration() throws SQLException {
        String sql = """
            UPDATE sync_devices
            SET device_name = ?, user_name = ?, last_sync_at = datetime('now'), is_active = 1
            WHERE device_id = ?
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, deviceName);
            pstmt.setString(2, userName);
            pstmt.setString(3, currentDeviceId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Update last sync time for current device
     */
    public void updateLastSyncTime() throws SQLException {
        String sql = "UPDATE sync_devices SET last_sync_at = datetime('now') WHERE device_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);

            pstmt.executeUpdate();
        }

        // Also update in MySQL
        updateLastSyncTimeMySQL();
    }

    private void updateLastSyncTimeMySQL() throws SQLException {
        if (!dbManager.isMySQLAvailable()) {
            return;
        }

        String sql = "UPDATE sync_devices SET last_sync_at = NOW() WHERE device_id = ?";

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Deactivate current device
     */
    public void deactivateDevice() throws SQLException {
        String sql = "UPDATE sync_devices SET is_active = 0 WHERE device_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get all registered devices
     */
    public List<DeviceInfo> getAllDevices() throws SQLException {
        List<DeviceInfo> devices = new ArrayList<>();
        String sql = "SELECT * FROM sync_devices ORDER BY last_sync_at DESC";

        try (Connection conn = dbManager.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                devices.add(extractDeviceInfo(rs));
            }
        }

        return devices;
    }

    /**
     * Get active devices
     */
    public List<DeviceInfo> getActiveDevices() throws SQLException {
        List<DeviceInfo> devices = new ArrayList<>();
        String sql = "SELECT * FROM sync_devices WHERE is_active = 1 ORDER BY last_sync_at DESC";

        try (Connection conn = dbManager.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                devices.add(extractDeviceInfo(rs));
            }
        }

        return devices;
    }

    /**
     * Get current device info
     */
    public DeviceInfo getCurrentDeviceInfo() throws SQLException {
        String sql = "SELECT * FROM sync_devices WHERE device_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentDeviceId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractDeviceInfo(rs);
                }
            }
        }

        // Return default info if not registered yet
        DeviceInfo info = new DeviceInfo();
        info.setDeviceId(currentDeviceId);
        info.setDeviceName(deviceName);
        info.setUserName(userName);
        info.setActive(true);
        info.setLastSyncAt(null);
        return info;
    }

    private DeviceInfo extractDeviceInfo(ResultSet rs) throws SQLException {
        DeviceInfo info = new DeviceInfo();
        info.setDeviceId(rs.getString("device_id"));
        info.setDeviceName(rs.getString("device_name"));
        info.setUserName(rs.getString("user_name"));
        info.setActive(rs.getBoolean("is_active"));

        // SQLite stores datetime as TEXT - parse manually
        String lastSyncStr = rs.getString("last_sync_at");
        if (lastSyncStr != null && !lastSyncStr.isEmpty()) {
            try {
                info.setLastSyncAt(LocalDateTime.parse(lastSyncStr.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        return info;
    }

    private String getSystemHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // Getters
    public String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Device information class
     */
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private String userName;
        private LocalDateTime lastSyncAt;
        private boolean isActive;

        // Getters and Setters
        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public LocalDateTime getLastSyncAt() {
            return lastSyncAt;
        }

        public void setLastSyncAt(LocalDateTime lastSyncAt) {
            this.lastSyncAt = lastSyncAt;
        }

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean active) {
            isActive = active;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) - %s",
                    deviceName, userName,
                    isActive ? "Actif" : "Inactif");
        }
    }
}
