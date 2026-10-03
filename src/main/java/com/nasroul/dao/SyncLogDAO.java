package com.nasroul.dao;

import com.nasroul.sync.SyncValues;

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

    private final SyncMetadataDAO.ConnectionSource local;

    public SyncLogDAO() {
        this(() -> DatabaseManager.getInstance().getSQLiteConnection());
    }

    public SyncLogDAO(SyncMetadataDAO.ConnectionSource local) {
        this.local = local;
    }

    /** Une entrée de journal (utilisée pour l'envoi groupé vers le serveur). */
    public record Entry(String syncSessionId, String tableName, int recordId, String operation,
                        String syncDirection, String status, String errorMessage, String syncedAt) {
    }

    private static final String INSERT_SQL = """
        INSERT INTO sync_log
        (sync_session_id, table_name, record_id, operation,
         sync_direction, status, error_message, synced_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    /**
     * Log a sync operation (connexion locale autonome)
     */
    public void log(String syncSessionId, String tableName, int recordId,
                    String operation, String syncDirection, String status,
                    String errorMessage) throws SQLException {
        try (Connection conn = local.open()) {
            log(conn, syncSessionId, tableName, recordId, operation, syncDirection, status, errorMessage);
        }
    }

    /**
     * Log a sync operation sur la connexion fournie (locale ou distante : le
     * SQL est portable, l'horodatage est lié en paramètre).
     */
    public void log(Connection conn, String syncSessionId, String tableName, int recordId,
                    String operation, String syncDirection, String status,
                    String errorMessage) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL)) {
            bind(pstmt, new Entry(syncSessionId, tableName, recordId, operation, syncDirection,
                    status, truncate(errorMessage), SyncValues.nowUtc()));
            pstmt.executeUpdate();
        }
    }

    /** Insertion groupée (un aller-retour) : journal partagé côté serveur. */
    public void logBatch(Connection conn, List<Entry> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL)) {
            for (Entry entry : entries) {
                bind(pstmt, entry);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private static void bind(PreparedStatement pstmt, Entry e) throws SQLException {
        pstmt.setString(1, e.syncSessionId());
        pstmt.setString(2, e.tableName());
        pstmt.setInt(3, e.recordId());
        pstmt.setString(4, e.operation());
        pstmt.setString(5, e.syncDirection());
        pstmt.setString(6, e.status());
        pstmt.setString(7, truncate(e.errorMessage()));
        pstmt.setString(8, e.syncedAt() != null ? e.syncedAt() : SyncValues.nowUtc());
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    /**
     * Log a sync operation to MySQL (for cross-device visibility)
     */
    public void logMySQL(String syncSessionId, String tableName, int recordId,
                         String operation, String syncDirection, String status,
                         String errorMessage) throws SQLException {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (!dbManager.isMySQLAvailable()) {
            return;
        }
        try (Connection conn = dbManager.getMySQLConnection()) {
            log(conn, syncSessionId, tableName, recordId, operation, syncDirection, status, errorMessage);
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
        String sql = "SELECT * FROM sync_log WHERE sync_session_id = ? ORDER BY synced_at, id";

        try (Connection conn = local.open();
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
        String sql = "SELECT * FROM sync_log ORDER BY synced_at DESC, id DESC LIMIT ?";

        try (Connection conn = local.open();
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
        String sql = "SELECT * FROM sync_log WHERE status = 'FAILED' ORDER BY synced_at DESC, id DESC";

        try (Connection conn = local.open();
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
     *
     * @return nombre de lignes supprimées
     */
    public int cleanOldLogs(int daysToKeep) throws SQLException {
        String sql = "DELETE FROM sync_log WHERE synced_at < datetime('now', '-' || ? || ' days')";
        try (Connection conn = local.open();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Math.max(0, daysToKeep));
            return pstmt.executeUpdate();
        }
    }

    /** Nombre de journaux plus anciens que N jours. */
    public int countOlderThan(int days) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_log WHERE synced_at < datetime('now', '-' || ? || ' days')";
        try (Connection conn = local.open();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Math.max(0, days));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Nombre total de journaux locaux. */
    public int countAll() throws SQLException {
        try (Connection conn = local.open();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sync_log")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Supprime tout l'historique local.
     *
     * @return nombre de lignes supprimées
     */
    public int deleteAll() throws SQLException {
        try (Connection conn = local.open();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate("DELETE FROM sync_log");
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
        log.setSyncedAt(SyncValues.parseDateTime(rs.getString("synced_at")));
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
