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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            pstmt.setInt(3, syncVersion);
            pstmt.setString(4, localHash);
            pstmt.setString(5, remoteHash);
            pstmt.setObject(6, LocalDateTime.now());
            pstmt.setString(7, syncStatus);
            pstmt.setString(8, null); // conflict_resolution

            pstmt.executeUpdate();
        }
    }

    private void update(String tableName, int recordId, int syncVersion,
                       String localHash, String remoteHash, String syncStatus) throws SQLException {
        String sql = """
            UPDATE sync_metadata
            SET sync_version = ?, local_hash = ?, remote_hash = ?,
                last_sync_at = ?, sync_status = ?
            WHERE table_name = ? AND record_id = ?
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, syncVersion);
            pstmt.setString(2, localHash);
            pstmt.setString(3, remoteHash);
            pstmt.setObject(4, LocalDateTime.now());
            pstmt.setString(5, syncStatus);
            pstmt.setString(6, tableName);
            pstmt.setInt(7, recordId);

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
            SET conflict_resolution = ?, sync_status = 'SYNCED', last_sync_at = ?
            WHERE table_name = ? AND record_id = ?
            """;

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resolution);
            pstmt.setObject(2, LocalDateTime.now());
            pstmt.setString(3, tableName);
            pstmt.setInt(4, recordId);

            pstmt.executeUpdate();
        }
    }

    private SyncMetadata extractSyncMetadata(ResultSet rs) throws SQLException {
        SyncMetadata meta = new SyncMetadata();
        meta.setTableName(rs.getString("table_name"));
        meta.setRecordId(rs.getInt("record_id"));
        meta.setSyncVersion(rs.getInt("sync_version"));
        meta.setLocalHash(rs.getString("local_hash"));
        meta.setRemoteHash(rs.getString("remote_hash"));

        Timestamp lastSync = rs.getTimestamp("last_sync_at");
        if (lastSync != null) meta.setLastSyncAt(lastSync.toLocalDateTime());

        meta.setSyncStatus(rs.getString("sync_status"));
        meta.setConflictResolution(rs.getString("conflict_resolution"));

        return meta;
    }

    /**
     * Inner class representing sync metadata
     */
    public static class SyncMetadata {
        private String tableName;
        private int recordId;
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
