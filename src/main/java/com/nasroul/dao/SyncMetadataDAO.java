package com.nasroul.dao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for sync_metadata table
 * Tracks synchronization state for each record
 */
public class SyncMetadataDAO {
    private final DatabaseManager dbManager;

    public SyncMetadataDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Save or update sync metadata for a record
     */
    public void save(String tableName, int recordId, int syncVersion,
                     String localHash, String remoteHash, String syncStatus) throws SQLException {

        // Check if exists
        if (exists(tableName, recordId)) {
            update(tableName, recordId, syncVersion, localHash, remoteHash, syncStatus);
        } else {
            insert(tableName, recordId, syncVersion, localHash, remoteHash, syncStatus);
        }
    }

    private void insert(String tableName, int recordId, int syncVersion,
                       String localHash, String remoteHash, String syncStatus) throws SQLException {
        String sql = """
            INSERT INTO sync_metadata
            (table_name, record_id, sync_version, local_hash, remote_hash,
             last_sync_at, sync_status, conflict_resolution)
            VALUES (?, ?, ?, ?, ?, datetime('now'), ?, ?)
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            pstmt.setInt(3, syncVersion);
            pstmt.setString(4, localHash);
            pstmt.setString(5, remoteHash);
            pstmt.setString(6, syncStatus);
            pstmt.setString(7, null); // conflict_resolution

            pstmt.executeUpdate();
        }
    }

    private void update(String tableName, int recordId, int syncVersion,
                       String localHash, String remoteHash, String syncStatus) throws SQLException {
        String sql = """
            UPDATE sync_metadata
            SET sync_version = ?, local_hash = ?, remote_hash = ?,
                last_sync_at = datetime('now'), sync_status = ?
            WHERE table_name = ? AND record_id = ?
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, syncVersion);
            pstmt.setString(2, localHash);
            pstmt.setString(3, remoteHash);
            pstmt.setString(4, syncStatus);
            pstmt.setString(5, tableName);
            pstmt.setInt(6, recordId);

            pstmt.executeUpdate();
        }
    }

    public boolean exists(String tableName, int recordId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_metadata WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Get sync metadata for a specific record
     */
    public SyncMetadata get(String tableName, int recordId) throws SQLException {
        String sql = "SELECT * FROM sync_metadata WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractSyncMetadata(rs);
                }
            }
        }

        return null;
    }

    /**
     * Get all records that need to be synced
     */
    public List<SyncMetadata> getPendingSync() throws SQLException {
        List<SyncMetadata> pending = new ArrayList<>();
        String sql = "SELECT * FROM sync_metadata WHERE sync_status IN ('PENDING', 'CONFLICT')";

        try (Connection conn = dbManager.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                pending.add(extractSyncMetadata(rs));
            }
        }

        return pending;
    }

    /**
     * Mark conflict resolution
     */
    public void markConflictResolved(String tableName, int recordId, String resolution) throws SQLException {
        String sql = """
            UPDATE sync_metadata
            SET conflict_resolution = ?, sync_status = 'SYNCED', last_sync_at = datetime('now')
            WHERE table_name = ? AND record_id = ?
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resolution);
            pstmt.setString(2, tableName);
            pstmt.setInt(3, recordId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Save metadata to MySQL server (for cross-device sync)
     */
    public void saveMySQLMetadata(String tableName, int recordId, int syncVersion,
                                  String localHash, String remoteHash, String syncStatus) throws SQLException {
        // Check if MySQL is available
        if (!dbManager.isMySQLAvailable()) {
            return; // Skip if MySQL not available
        }

        // Check if exists in MySQL
        if (existsMySQL(tableName, recordId)) {
            updateMySQL(tableName, recordId, syncVersion, localHash, remoteHash, syncStatus);
        } else {
            insertMySQL(tableName, recordId, syncVersion, localHash, remoteHash, syncStatus);
        }
    }

    private boolean existsMySQL(String tableName, int recordId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_metadata WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertMySQL(String tableName, int recordId, int syncVersion,
                            String localHash, String remoteHash, String syncStatus) throws SQLException {
        String sql = """
            INSERT INTO sync_metadata
            (table_name, record_id, sync_version, local_hash, remote_hash,
             last_sync_at, sync_status, conflict_resolution)
            VALUES (?, ?, ?, ?, ?, NOW(), ?, ?)
            """;

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            pstmt.setInt(3, syncVersion);
            pstmt.setString(4, localHash);
            pstmt.setString(5, remoteHash);
            pstmt.setString(6, syncStatus);
            pstmt.setString(7, null); // conflict_resolution

            pstmt.executeUpdate();
        }
    }

    private void updateMySQL(String tableName, int recordId, int syncVersion,
                            String localHash, String remoteHash, String syncStatus) throws SQLException {
        String sql = """
            UPDATE sync_metadata
            SET sync_version = ?, local_hash = ?, remote_hash = ?,
                last_sync_at = NOW(), sync_status = ?
            WHERE table_name = ? AND record_id = ?
            """;

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, syncVersion);
            pstmt.setString(2, localHash);
            pstmt.setString(3, remoteHash);
            pstmt.setString(4, syncStatus);
            pstmt.setString(5, tableName);
            pstmt.setInt(6, recordId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Get sync metadata from MySQL server
     */
    public SyncMetadata getMySQLMetadata(String tableName, int recordId) throws SQLException {
        if (!dbManager.isMySQLAvailable()) {
            return null;
        }

        String sql = "SELECT * FROM sync_metadata WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractSyncMetadataMySQL(rs);
                }
            }
        }

        return null;
    }

    private SyncMetadata extractSyncMetadataMySQL(ResultSet rs) throws SQLException {
        SyncMetadata meta = new SyncMetadata();
        meta.setTableName(rs.getString("table_name"));
        meta.setRecordId(rs.getInt("record_id"));

        // Extract remote_id (can be null)
        int remoteId = rs.getInt("remote_id");
        meta.setRemoteId(rs.wasNull() ? null : remoteId);

        meta.setSyncVersion(rs.getInt("sync_version"));
        meta.setLocalHash(rs.getString("local_hash"));
        meta.setRemoteHash(rs.getString("remote_hash"));

        // MySQL stores DATETIME properly - use getTimestamp
        Timestamp lastSync = rs.getTimestamp("last_sync_at");
        if (lastSync != null) {
            meta.setLastSyncAt(lastSync.toLocalDateTime());
        }

        meta.setSyncStatus(rs.getString("sync_status"));
        meta.setConflictResolution(rs.getString("conflict_resolution"));

        return meta;
    }

    private SyncMetadata extractSyncMetadata(ResultSet rs) throws SQLException {
        SyncMetadata meta = new SyncMetadata();
        meta.setTableName(rs.getString("table_name"));
        meta.setRecordId(rs.getInt("record_id"));

        // Extract remote_id (can be null)
        int remoteId = rs.getInt("remote_id");
        meta.setRemoteId(rs.wasNull() ? null : remoteId);

        meta.setSyncVersion(rs.getInt("sync_version"));
        meta.setLocalHash(rs.getString("local_hash"));
        meta.setRemoteHash(rs.getString("remote_hash"));

        // SQLite stores datetime as TEXT - parse manually
        String lastSyncStr = rs.getString("last_sync_at");
        if (lastSyncStr != null && !lastSyncStr.isEmpty()) {
            try {
                meta.setLastSyncAt(LocalDateTime.parse(lastSyncStr.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        meta.setSyncStatus(rs.getString("sync_status"));
        meta.setConflictResolution(rs.getString("conflict_resolution"));

        return meta;
    }

    /**
     * Set the remote ID for a local record
     */
    public void setRemoteId(String tableName, int localId, int remoteId) throws SQLException {
        String sql = "UPDATE sync_metadata SET remote_id = ? WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, remoteId);
            pstmt.setString(2, tableName);
            pstmt.setInt(3, localId);

            pstmt.executeUpdate();
        }
    }

    /**
     * Get remote ID for a local record
     */
    public Integer getRemoteId(String tableName, int localId) throws SQLException {
        String sql = "SELECT remote_id FROM sync_metadata WHERE table_name = ? AND record_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, localId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int remoteId = rs.getInt("remote_id");
                    return rs.wasNull() ? null : remoteId;
                }
            }
        }

        return null;
    }

    /**
     * Get local ID for a remote record
     */
    public Integer getLocalIdByRemoteId(String tableName, int remoteId) throws SQLException {
        String sql = "SELECT record_id FROM sync_metadata WHERE table_name = ? AND remote_id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, remoteId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("record_id");
                }
            }
        }

        return null;
    }

    /**
     * Inner class representing sync metadata
     */
    public static class SyncMetadata {
        private String tableName;
        private int recordId;
        private Integer remoteId;  // MySQL ID (can be null for new records)
        private int syncVersion;
        private String localHash;
        private String remoteHash;
        private LocalDateTime lastSyncAt;
        private String syncStatus;
        private String conflictResolution;

        // Getters and setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public int getRecordId() { return recordId; }
        public void setRecordId(int recordId) { this.recordId = recordId; }

        public Integer getRemoteId() { return remoteId; }
        public void setRemoteId(Integer remoteId) { this.remoteId = remoteId; }

        public int getSyncVersion() { return syncVersion; }
        public void setSyncVersion(int syncVersion) { this.syncVersion = syncVersion; }

        public String getLocalHash() { return localHash; }
        public void setLocalHash(String localHash) { this.localHash = localHash; }

        public String getRemoteHash() { return remoteHash; }
        public void setRemoteHash(String remoteHash) { this.remoteHash = remoteHash; }

        public LocalDateTime getLastSyncAt() { return lastSyncAt; }
        public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

        public String getSyncStatus() { return syncStatus; }
        public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

        public String getConflictResolution() { return conflictResolution; }
        public void setConflictResolution(String conflictResolution) { this.conflictResolution = conflictResolution; }
    }
}
