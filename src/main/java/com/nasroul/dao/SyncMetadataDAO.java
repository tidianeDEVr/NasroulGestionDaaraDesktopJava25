package com.nasroul.dao;

import com.nasroul.sync.SyncValues;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO de la table locale sync_metadata : pour chaque ligne synchronisée,
 * l'id distant (mapping local ↔ MySQL), la version et le hash du contenu
 * lors de la dernière synchronisation (base de la fusion à trois voies).
 *
 * Toutes les opérations existent en deux variantes : avec une Connection
 * fournie (utilisée par SyncManager dans sa transaction de session) ou sans
 * (ouverture/fermeture d'une connexion locale à chaque appel).
 */
public class SyncMetadataDAO {

    /** Fournisseur de connexion locale (SQLite). */
    @FunctionalInterface
    public interface ConnectionSource {
        Connection open() throws SQLException;
    }

    private final ConnectionSource local;

    public SyncMetadataDAO() {
        this(() -> DatabaseManager.getInstance().getSQLiteConnection());
    }

    public SyncMetadataDAO(ConnectionSource local) {
        this.local = local;
    }

    // ------------------------------------------------------------------ save

    /**
     * Enregistre (upsert) l'état de sync d'une ligne. Un remoteId null ne
     * supprime jamais un mapping existant.
     */
    public void save(Connection conn, String tableName, int recordId, Integer remoteId,
                     int syncVersion, String contentHash, String syncStatus) throws SQLException {
        String sql = """
            INSERT INTO sync_metadata
                (table_name, record_id, remote_id, sync_version, local_hash, remote_hash,
                 last_sync_at, sync_status, conflict_resolution)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
            ON CONFLICT(table_name, record_id) DO UPDATE SET
                remote_id = COALESCE(excluded.remote_id, sync_metadata.remote_id),
                sync_version = excluded.sync_version,
                local_hash = excluded.local_hash,
                remote_hash = excluded.remote_hash,
                last_sync_at = excluded.last_sync_at,
                sync_status = excluded.sync_status
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            if (remoteId != null) pstmt.setInt(3, remoteId); else pstmt.setNull(3, Types.INTEGER);
            pstmt.setInt(4, syncVersion);
            pstmt.setString(5, contentHash);
            pstmt.setString(6, contentHash);
            pstmt.setString(7, SyncValues.nowUtc());
            pstmt.setString(8, syncStatus);
            pstmt.executeUpdate();
        }
    }

    /** Variante historique (sans remoteId, connexion autonome). */
    public void save(String tableName, int recordId, int syncVersion,
                     String localHash, String remoteHash, String syncStatus) throws SQLException {
        try (Connection conn = local.open()) {
            save(conn, tableName, recordId, null, syncVersion, localHash, syncStatus);
        }
    }

    public void delete(Connection conn, String tableName, int recordId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM sync_metadata WHERE table_name = ? AND record_id = ?")) {
            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            pstmt.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ read

    public boolean exists(String tableName, int recordId) throws SQLException {
        try (Connection conn = local.open()) {
            return get(conn, tableName, recordId) != null;
        }
    }

    public SyncMetadata get(String tableName, int recordId) throws SQLException {
        try (Connection conn = local.open()) {
            return get(conn, tableName, recordId);
        }
    }

    public SyncMetadata get(Connection conn, String tableName, int recordId) throws SQLException {
        String sql = "SELECT * FROM sync_metadata WHERE table_name = ? AND record_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setInt(2, recordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? extractSyncMetadata(rs) : null;
            }
        }
    }

    /**
     * Get all records that need to be synced
     */
    public List<SyncMetadata> getPendingSync() throws SQLException {
        List<SyncMetadata> pending = new ArrayList<>();
        String sql = "SELECT * FROM sync_metadata WHERE sync_status IN ('PENDING', 'CONFLICT')";
        try (Connection conn = local.open();
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
        try (Connection conn = local.open()) {
            markConflictResolved(conn, tableName, recordId, resolution);
        }
    }

    public void markConflictResolved(Connection conn, String tableName, int recordId, String resolution)
            throws SQLException {
        String sql = """
            UPDATE sync_metadata
            SET conflict_resolution = ?, sync_status = 'SYNCED', last_sync_at = ?
            WHERE table_name = ? AND record_id = ?
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resolution);
            pstmt.setString(2, SyncValues.nowUtc());
            pstmt.setString(3, tableName);
            pstmt.setInt(4, recordId);
            pstmt.executeUpdate();
        }
    }

    // --------------------------------------------------------------- mapping

    /**
     * Associe une ligne locale à son id distant (upsert : la ligne de
     * métadonnées est créée si elle n'existe pas encore).
     */
    public void setRemoteId(String tableName, int localId, int remoteId) throws SQLException {
        try (Connection conn = local.open()) {
            setRemoteId(conn, tableName, localId, remoteId);
        }
    }

    public void setRemoteId(Connection conn, String tableName, int localId, int remoteId) throws SQLException {
        String sql = """
            INSERT INTO sync_metadata (table_name, record_id, remote_id, sync_version, sync_status)
            VALUES (?, ?, ?, 1, 'PENDING')
            ON CONFLICT(table_name, record_id) DO UPDATE SET remote_id = excluded.remote_id
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setInt(2, localId);
            pstmt.setInt(3, remoteId);
            pstmt.executeUpdate();
        }
    }

    public Integer getRemoteId(String tableName, int localId) throws SQLException {
        try (Connection conn = local.open()) {
            return getRemoteId(conn, tableName, localId);
        }
    }

    public Integer getRemoteId(Connection conn, String tableName, int localId) throws SQLException {
        String sql = "SELECT remote_id FROM sync_metadata WHERE table_name = ? AND record_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    public Integer getLocalIdByRemoteId(String tableName, int remoteId) throws SQLException {
        try (Connection conn = local.open()) {
            return getLocalIdByRemoteId(conn, tableName, remoteId);
        }
    }

    public Integer getLocalIdByRemoteId(Connection conn, String tableName, int remoteId) throws SQLException {
        String sql = "SELECT record_id FROM sync_metadata WHERE table_name = ? AND remote_id = ? ORDER BY record_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setInt(2, remoteId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("record_id") : null;
            }
        }
    }

    /** Tous les mappings d'une table : id local → id distant. */
    public Map<Integer, Integer> getLocalToRemoteMap(Connection conn, String tableName) throws SQLException {
        Map<Integer, Integer> map = new HashMap<>();
        String sql = "SELECT record_id, remote_id FROM sync_metadata WHERE table_name = ? AND remote_id IS NOT NULL";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("record_id"), rs.getInt("remote_id"));
                }
            }
        }
        return map;
    }

    // --------------------------------------------------------------- extract

    private SyncMetadata extractSyncMetadata(ResultSet rs) throws SQLException {
        SyncMetadata meta = new SyncMetadata();
        meta.setTableName(rs.getString("table_name"));
        meta.setRecordId(rs.getInt("record_id"));

        int remoteId = rs.getInt("remote_id");
        meta.setRemoteId(rs.wasNull() ? null : remoteId);

        meta.setSyncVersion(rs.getInt("sync_version"));
        meta.setLocalHash(rs.getString("local_hash"));
        meta.setRemoteHash(rs.getString("remote_hash"));
        meta.setLastSyncAt(SyncValues.parseDateTime(rs.getString("last_sync_at")));
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
        private Integer remoteId;  // MySQL ID (can be null for new records)
        private int syncVersion;
        private String localHash;
        private String remoteHash;
        private LocalDateTime lastSyncAt;
        private String syncStatus;
        private String conflictResolution;

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
