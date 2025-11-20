package com.nasroul.sync;

import com.nasroul.dao.*;
import com.nasroul.model.SyncableEntity;
import com.nasroul.sync.ConflictDetector.ConflictType;
import com.nasroul.sync.ConflictResolver.Resolution;
import com.nasroul.sync.ConflictResolver.ResolutionAction;
import com.nasroul.util.DeviceIdGenerator;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main synchronization orchestrator
 * Coordinates PULL and PUSH operations between SQLite (local) and MySQL (remote)
 */
public class SyncManager {

    private final DatabaseManager dbManager;
    private final SyncMetadataDAO syncMetadataDAO;
    private final SyncLogDAO syncLogDAO;
    private final ConflictDetector conflictDetector;
    private final ConflictResolver conflictResolver;

    private String currentSyncSession;

    public SyncManager() {
        this.dbManager = DatabaseManager.getInstance();
        this.syncMetadataDAO = new SyncMetadataDAO();
        this.syncLogDAO = new SyncLogDAO();
        this.conflictDetector = new ConflictDetector();
        this.conflictResolver = new ConflictResolver();
    }

    /**
     * Perform full synchronization: PULL then PUSH
     *
     * @return SyncResult with statistics
     */
    public SyncResult synchronize() throws SQLException {
        currentSyncSession = syncLogDAO.startSyncSession();
        SyncResult result = new SyncResult();

        try {
            // Check if MySQL is available
            if (!dbManager.isMySQLAvailable()) {
                result.setSuccess(false);
                result.setErrorMessage("MySQL server not available - operating in offline mode");
                return result;
            }

            // PHASE 1: PULL - Get changes from remote (MySQL) to local (SQLite)
            SyncResult pullResult = pullFromRemote();
            result.merge(pullResult);

            // PHASE 2: PUSH - Send local changes to remote (MySQL)
            SyncResult pushResult = pushToRemote();
            result.merge(pushResult);

            result.setSuccess(true);
            result.setSyncSessionId(currentSyncSession);

        } catch (SQLException e) {
            result.setSuccess(false);
            result.setErrorMessage("Sync failed: " + e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * PULL: Download changes from remote MySQL to local SQLite
     */
    private SyncResult pullFromRemote() throws SQLException {
        SyncResult result = new SyncResult();

        // List of tables to sync
        String[] tables = {"groups", "members", "events", "projects", "expenses", "contributions", "payment_groups"};

        for (String tableName : tables) {
            try {
                int pulled = pullTableFromRemote(tableName);
                result.addPulled(pulled);
            } catch (SQLException e) {
                result.addError(tableName + " pull failed: " + e.getMessage());
                syncLogDAO.log(currentSyncSession, tableName, 0, "PULL", "PULL",
                        "FAILED", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Pull a specific table from remote
     */
    private int pullTableFromRemote(String tableName) throws SQLException {
        int pulledCount = 0;

        // Get last sync time for this table
        LocalDateTime lastSync = getLastSyncTime(tableName);

        // Get all records from remote (MySQL) that were modified after last sync
        // Use MySQL-compatible min date if lastSync is null
        String sql = lastSync != null
            ? "SELECT * FROM `" + tableName + "` WHERE updated_at > ? OR updated_at IS NULL"
            : "SELECT * FROM `" + tableName + "`";

        try (Connection remoteConn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = remoteConn.prepareStatement(sql)) {

            if (lastSync != null) {
                pstmt.setObject(1, lastSync);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int recordId = rs.getInt("id");

                    try {
                        // Get local version
                        SyncableEntity localEntity = getLocalEntity(tableName, recordId);
                        SyncableEntity remoteEntity = extractEntity(tableName, rs);

                        // Get last synced hash
                        SyncMetadataDAO.SyncMetadata metadata = syncMetadataDAO.get(tableName, recordId);
                        String lastSyncedHash = metadata != null ? metadata.getRemoteHash() : null;

                        // Detect conflicts
                        ConflictType conflict = conflictDetector.detectConflict(
                                localEntity, remoteEntity, lastSyncedHash);

                        if (conflict != ConflictType.NO_CONFLICT) {
                            // Resolve conflict
                            Resolution resolution = conflictResolver.resolve(localEntity, remoteEntity, conflict);

                            if (resolution.getAction() == ResolutionAction.TAKE_REMOTE) {
                                updateLocalEntity(tableName, remoteEntity);
                                pulledCount++;
                            } else if (resolution.getAction() == ResolutionAction.MANUAL_RESOLUTION) {
                                // Mark as conflict for manual resolution
                                markConflict(tableName, recordId, "Manual resolution required");
                            }
                            // If TAKE_LOCAL, do nothing (keep local version)
                        } else {
                            // No conflict, update local
                            if (localEntity == null || !localEntity.calculateHash().equals(remoteEntity.calculateHash())) {
                                updateLocalEntity(tableName, remoteEntity);
                                pulledCount++;
                            }
                        }

                        // Update sync metadata in BOTH SQLite (local) AND MySQL (remote)
                        String hash = remoteEntity.calculateHash();
                        syncMetadataDAO.save(tableName, recordId,
                                remoteEntity.getSyncVersion(),
                                hash, hash, "SYNCED");

                        // CRITICAL: Also save to MySQL so other devices can see sync state
                        syncMetadataDAO.saveMySQLMetadata(tableName, recordId,
                                remoteEntity.getSyncVersion(),
                                hash, hash, "SYNCED");

                        syncLogDAO.log(currentSyncSession, tableName, recordId,
                                "UPDATE", "PULL", "SUCCESS", null);

                        // Also log to MySQL
                        syncLogDAO.logMySQL(currentSyncSession, tableName, recordId,
                                "UPDATE", "PULL", "SUCCESS", null);

                    } catch (Exception e) {
                        syncLogDAO.log(currentSyncSession, tableName, recordId,
                                "UPDATE", "PULL", "FAILED", e.getMessage());
                    }
                }
            }
        }

        return pulledCount;
    }

    /**
     * PUSH: Upload local changes to remote MySQL
     */
    private SyncResult pushToRemote() throws SQLException {
        SyncResult result = new SyncResult();

        // Get all entities with PENDING sync status
        String[] tables = {"groups", "members", "events", "projects", "expenses", "contributions", "payment_groups"};

        for (String tableName : tables) {
            try {
                int pushed = pushTableToRemote(tableName);
                result.addPushed(pushed);
            } catch (SQLException e) {
                result.addError(tableName + " push failed: " + e.getMessage());
                syncLogDAO.log(currentSyncSession, tableName, 0, "PUSH", "PUSH",
                        "FAILED", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Push a specific table to remote
     */
    private int pushTableToRemote(String tableName) throws SQLException {
        int pushedCount = 0;

        // Get all pending records from local SQLite
        String sql = "SELECT * FROM `" + tableName + "` WHERE sync_status = 'PENDING'";

        try (Connection localConn = dbManager.getSQLiteConnection();
             Statement stmt = localConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int recordId = rs.getInt("id");

                try {
                    SyncableEntity localEntity = extractEntity(tableName, rs);
                    SyncableEntity remoteEntity = getRemoteEntity(tableName, recordId);

                    // Check if remote was modified
                    if (remoteEntity != null) {
                        // Detect conflicts
                        SyncMetadataDAO.SyncMetadata metadata = syncMetadataDAO.get(tableName, recordId);
                        String lastSyncedHash = metadata != null ? metadata.getLocalHash() : null;

                        ConflictType conflict = conflictDetector.detectConflict(
                                localEntity, remoteEntity, lastSyncedHash);

                        if (conflict != ConflictType.NO_CONFLICT) {
                            Resolution resolution = conflictResolver.resolve(localEntity, remoteEntity, conflict);

                            if (resolution.getAction() == ResolutionAction.TAKE_LOCAL) {
                                updateRemoteEntity(tableName, localEntity);
                                pushedCount++;
                            } else if (resolution.getAction() == ResolutionAction.MANUAL_RESOLUTION) {
                                markConflict(tableName, recordId, "Manual resolution required");
                                continue;
                            }
                            // If TAKE_REMOTE, update local instead
                            else if (resolution.getAction() == ResolutionAction.TAKE_REMOTE) {
                                updateLocalEntity(tableName, remoteEntity);
                            }
                        } else {
                            // No conflict, push to remote
                            updateRemoteEntity(tableName, localEntity);
                            pushedCount++;
                        }
                    } else {
                        // New record, insert to remote
                        insertRemoteEntity(tableName, localEntity);
                        pushedCount++;
                    }

                    // Update local sync status
                    markAsSynced(tableName, recordId);

                    // Update sync metadata in BOTH SQLite AND MySQL
                    String hash = localEntity.calculateHash();
                    syncMetadataDAO.save(tableName, recordId,
                            localEntity.getSyncVersion(),
                            hash, hash, "SYNCED");

                    // CRITICAL: Also save to MySQL so other devices can see sync state
                    syncMetadataDAO.saveMySQLMetadata(tableName, recordId,
                            localEntity.getSyncVersion(),
                            hash, hash, "SYNCED");

                    syncLogDAO.log(currentSyncSession, tableName, recordId,
                            "UPDATE", "PUSH", "SUCCESS", null);

                    // Also log to MySQL
                    syncLogDAO.logMySQL(currentSyncSession, tableName, recordId,
                            "UPDATE", "PUSH", "SUCCESS", null);

                } catch (Exception e) {
                    syncLogDAO.log(currentSyncSession, tableName, recordId,
                            "UPDATE", "PUSH", "FAILED", e.getMessage());
                }
            }
        }

        return pushedCount;
    }

    // Helper methods for entity operations

    private SyncableEntity getLocalEntity(String tableName, int recordId) throws SQLException {
        String sql = "SELECT * FROM `" + tableName + "` WHERE id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, recordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return GenericSyncableEntity.fromResultSet(tableName, rs);
                }
            }
        }

        return null;
    }

    private SyncableEntity getRemoteEntity(String tableName, int recordId) throws SQLException {
        String sql = "SELECT * FROM `" + tableName + "` WHERE id = ?";

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, recordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return GenericSyncableEntity.fromResultSet(tableName, rs);
                }
            }
        }

        return null;
    }

    private SyncableEntity extractEntity(String tableName, ResultSet rs) throws SQLException {
        return GenericSyncableEntity.fromResultSet(tableName, rs);
    }

    private void updateLocalEntity(String tableName, SyncableEntity entity) throws SQLException {
        if (!(entity instanceof GenericSyncableEntity)) {
            throw new SQLException("Entity must be GenericSyncableEntity");
        }

        GenericSyncableEntity genericEntity = (GenericSyncableEntity) entity;
        Map<String, Object> fields = genericEntity.getAllFields();

        // Build dynamic UPDATE SQL
        StringBuilder sql = new StringBuilder("UPDATE `").append(tableName).append("` SET ");
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!entry.getKey().equals("id")) {  // Don't update ID
                setClauses.add(entry.getKey() + " = ?");
                values.add(entry.getValue());
            }
        }

        sql.append(String.join(", ", setClauses));
        sql.append(", sync_status = 'SYNCED', last_sync_at = datetime('now') WHERE id = ?");

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (Object value : values) {
                pstmt.setObject(paramIndex++, value);
            }
            pstmt.setInt(paramIndex, genericEntity.getId());

            pstmt.executeUpdate();
        }
    }

    private void updateRemoteEntity(String tableName, SyncableEntity entity) throws SQLException {
        if (!(entity instanceof GenericSyncableEntity)) {
            throw new SQLException("Entity must be GenericSyncableEntity");
        }

        GenericSyncableEntity genericEntity = (GenericSyncableEntity) entity;
        Map<String, Object> fields = genericEntity.getAllFields();

        // Build dynamic UPDATE SQL for MySQL
        StringBuilder sql = new StringBuilder("UPDATE `").append(tableName).append("` SET ");
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!entry.getKey().equals("id")) {  // Don't update ID
                setClauses.add("`" + entry.getKey() + "` = ?");
                values.add(entry.getValue());
            }
        }

        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = ?");

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (Object value : values) {
                pstmt.setObject(paramIndex++, value);
            }
            pstmt.setInt(paramIndex, genericEntity.getId());

            pstmt.executeUpdate();
        }
    }

    private void insertRemoteEntity(String tableName, SyncableEntity entity) throws SQLException {
        if (!(entity instanceof GenericSyncableEntity)) {
            throw new SQLException("Entity must be GenericSyncableEntity");
        }

        GenericSyncableEntity genericEntity = (GenericSyncableEntity) entity;
        Map<String, Object> fields = genericEntity.getAllFields();

        // Build dynamic INSERT SQL for MySQL
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(tableName).append("` (");
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!entry.getKey().equals("id")) {  // Skip auto-increment ID
                columns.add("`" + entry.getKey() + "`");
                placeholders.add("?");
                values.add(entry.getValue());
            }
        }

        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(String.join(", ", placeholders));
        sql.append(")");

        try (Connection conn = dbManager.getMySQLConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (Object value : values) {
                pstmt.setObject(paramIndex++, value);
            }

            pstmt.executeUpdate();
        }
    }

    private void markAsSynced(String tableName, int recordId) throws SQLException {
        // Use SQLite's datetime('now') function instead of setObject with LocalDateTime
        String sql = "UPDATE `" + tableName + "` SET sync_status = 'SYNCED', last_sync_at = datetime('now') WHERE id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, recordId);
            pstmt.executeUpdate();
        }
    }

    private void markConflict(String tableName, int recordId, String reason) throws SQLException {
        String sql = "UPDATE `" + tableName + "` SET sync_status = 'CONFLICT' WHERE id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, recordId);
            pstmt.executeUpdate();
        }
    }

    private LocalDateTime getLastSyncTime(String tableName) throws SQLException {
        String sql = "SELECT MAX(last_sync_at) FROM `" + tableName + "`";

        try (Connection conn = dbManager.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                // SQLite stores datetime as TEXT - get as String and parse
                String dateStr = rs.getString(1);
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        // Parse SQLite datetime format: 'YYYY-MM-DD HH:MM:SS'
                        return LocalDateTime.parse(dateStr.replace(" ", "T"));
                    } catch (Exception e) {
                        System.err.println("Failed to parse last sync time: " + dateStr);
                        return null;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Sync result statistics
     */
    public static class SyncResult {
        private boolean success;
        private int recordsPulled;
        private int recordsPushed;
        private int conflicts;
        private List<String> errors;
        private String errorMessage;
        private String syncSessionId;

        public SyncResult() {
            this.errors = new ArrayList<>();
        }

        public void merge(SyncResult other) {
            this.recordsPulled += other.recordsPulled;
            this.recordsPushed += other.recordsPushed;
            this.conflicts += other.conflicts;
            this.errors.addAll(other.errors);
        }

        public void addPulled(int count) {
            this.recordsPulled += count;
        }

        public void addPushed(int count) {
            this.recordsPushed += count;
        }

        public void addConflict() {
            this.conflicts++;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getRecordsPulled() { return recordsPulled; }
        public int getRecordsPushed() { return recordsPushed; }
        public int getConflicts() { return conflicts; }
        public List<String> getErrors() { return errors; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getSyncSessionId() { return syncSessionId; }
        public void setSyncSessionId(String syncSessionId) { this.syncSessionId = syncSessionId; }

        @Override
        public String toString() {
            return String.format("Sync Result: %s | Pulled: %d, Pushed: %d, Conflicts: %d, Errors: %d",
                    success ? "SUCCESS" : "FAILED",
                    recordsPulled, recordsPushed, conflicts, errors.size());
        }
    }
}
