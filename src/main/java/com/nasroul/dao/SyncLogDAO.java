package com.nasroul.dao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DAO for sync_log table
 * Audit log for all sync operations
 */
public class SyncLogDAO {
    private final DatabaseManager dbManager;

    public SyncLogDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Log a sync operation
     */
    public void log(String syncSessionId, String tableName, int recordId,
                    String operation, String syncDirection, String status,
                    String errorMessage) throws SQLException {

        String sql = """
            INSERT INTO sync_log
            (sync_session_id, table_name, record_id, operation,
             sync_direction, status, error_message, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, syncSessionId);
            pstmt.setString(2, tableName);
            pstmt.setInt(3, recordId);
            pstmt.setString(4, operation);
            pstmt.setString(5, syncDirection);
            pstmt.setString(6, status);
            pstmt.setString(7, errorMessage);
            pstmt.setObject(8, LocalDateTime.now());

            pstmt.executeUpdate();
        }
    }

    /**
     * Start a new sync session
     */
    public String startSyncSession() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get all logs for a sync session
     */
    public List<SyncLog> getSessionLogs(String syncSessionId) throws SQLException {
        List<SyncLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM sync_log WHERE sync_session_id = ? ORDER BY synced_at";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, syncSessionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(extractSyncLog(rs));
                }
            }
        }

        return logs;
    }

    /**
     * Get recent sync logs (last N entries)
     */
    public List<SyncLog> getRecentLogs(int limit) throws SQLException {
        List<SyncLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM sync_log ORDER BY synced_at DESC LIMIT ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(extractSyncLog(rs));
                }
            }
        }

        return logs;
    }

    /**
     * Get failed sync operations
     */
    public List<SyncLog> getFailedSyncs() throws SQLException {
        List<SyncLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM sync_log WHERE status = 'FAILED' ORDER BY synced_at DESC";

        try (Connection conn = dbManager.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                logs.add(extractSyncLog(rs));
            }
        }

        return logs;
    }

    /**
     * Clean old sync logs (keep only last N days)
     */
    public void cleanOldLogs(int daysToKeep) throws SQLException {
        String sql = "DELETE FROM sync_log WHERE synced_at < ?";

        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, cutoff);
            pstmt.executeUpdate();
        }
    }

    private SyncLog extractSyncLog(ResultSet rs) throws SQLException {
        SyncLog log = new SyncLog();
        log.setId(rs.getInt("id"));
        log.setSyncSessionId(rs.getString("sync_session_id"));
        log.setTableName(rs.getString("table_name"));
        log.setRecordId(rs.getInt("record_id"));
        log.setOperation(rs.getString("operation"));
        log.setSyncDirection(rs.getString("sync_direction"));
        log.setStatus(rs.getString("status"));
        log.setErrorMessage(rs.getString("error_message"));

        Timestamp syncedAt = rs.getTimestamp("synced_at");
        if (syncedAt != null) log.setSyncedAt(syncedAt.toLocalDateTime());

        return log;
    }

    /**
     * Inner class representing a sync log entry
     */
    public static class SyncLog {
        private int id;
        private String syncSessionId;
        private String tableName;
        private int recordId;
        private String operation;
        private String syncDirection;
        private String status;
        private String errorMessage;
        private LocalDateTime syncedAt;

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getSyncSessionId() { return syncSessionId; }
        public void setSyncSessionId(String syncSessionId) { this.syncSessionId = syncSessionId; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public int getRecordId() { return recordId; }
        public void setRecordId(int recordId) { this.recordId = recordId; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public String getSyncDirection() { return syncDirection; }
        public void setSyncDirection(String syncDirection) { this.syncDirection = syncDirection; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public LocalDateTime getSyncedAt() { return syncedAt; }
        public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

        @Override
        public String toString() {
            return String.format("[%s] %s %s.%d: %s (%s)",
                    syncedAt, syncDirection, tableName, recordId, operation, status);
        }
    }
}
