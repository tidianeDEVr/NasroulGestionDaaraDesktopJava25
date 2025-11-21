package com.nasroul.sync;

import com.nasroul.dao.*;
import com.nasroul.model.SyncableEntity;
import com.nasroul.sync.ConflictDetector.ConflictType;
import com.nasroul.sync.ConflictResolver.Resolution;
import com.nasroul.sync.ConflictResolver.ResolutionAction;
import java.util.Map;

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
                result.setErrorMessage("Impossible de se connecter au serveur.\n\n" +
                    "L'application continue de fonctionner en mode hors ligne.\n" +
                    "Vos données locales sont sauvegardées.\n\n" +
                    "Veuillez vérifier:\n" +
                    "• Votre connexion Internet\n" +
                    "• Les paramètres de connexion au serveur\n" +
                    "• Que le serveur est bien démarré");
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
            // Provide user-friendly error messages
            String userMessage = getUserFriendlyErrorMessage(e);
            result.setErrorMessage(userMessage);
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
                result.addPulled(tableName, pulled);
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

        // IMPORTANT: Get ALL records, including soft-deleted ones (deleted_at IS NOT NULL)
        // This ensures soft deletes propagate between devices
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
                    int remoteId = rs.getInt("id");
                    String remoteDeletedAt = rs.getString("deleted_at");

                    try {
                        // Find local ID for this remote ID
                        Integer localId = syncMetadataDAO.getLocalIdByRemoteId(tableName, remoteId);

                        SyncableEntity remoteEntity = extractEntity(tableName, rs);

                        if (localId != null) {
                            // Mapping exists - update local record
                            SyncableEntity localEntity = getLocalEntity(tableName, localId);

                            // CRITICAL: Propagate soft delete
                            if (remoteDeletedAt != null && !remoteDeletedAt.isEmpty()) {
                                // Remote was deleted - mark local as deleted too
                                softDeleteLocal(tableName, localId);
                                pulledCount++;
                                System.out.println("Propagated soft delete: " + tableName + " local ID " + localId);
                            } else if (localEntity == null || !localEntity.calculateHash().equals(remoteEntity.calculateHash())) {
                                // Update local with remote changes
                                updateLocalEntity(tableName, remoteEntity);
                                pulledCount++;
                            }

                            // Update sync metadata
                            String hash = remoteEntity.calculateHash();
                            syncMetadataDAO.save(tableName, localId,
                                    remoteEntity.getSyncVersion(),
                                    hash, hash, "SYNCED");

                            syncLogDAO.log(currentSyncSession, tableName, localId,
                                    "UPDATE", "PULL", "SUCCESS", null);

                        } else {
                            // No mapping - new record from another device
                            if (remoteDeletedAt == null || remoteDeletedAt.isEmpty()) {
                                // Only create if not deleted
                                int newLocalId = insertLocalEntity(tableName, remoteEntity);
                                syncMetadataDAO.setRemoteId(tableName, newLocalId, remoteId);
                                pulledCount++;
                                System.out.println("Created local record from remote: " + tableName + " local ID " + newLocalId + " ← remote ID " + remoteId);

                                // Update sync metadata
                                String hash = remoteEntity.calculateHash();
                                syncMetadataDAO.save(tableName, newLocalId,
                                        remoteEntity.getSyncVersion(),
                                        hash, hash, "SYNCED");

                                syncLogDAO.log(currentSyncSession, tableName, newLocalId,
                                        "INSERT", "PULL", "SUCCESS", null);
                            }
                        }

                        // CRITICAL: Also save to MySQL so other devices can see sync state
                        String hash = remoteEntity.calculateHash();
                        syncMetadataDAO.saveMySQLMetadata(tableName, remoteId,
                                remoteEntity.getSyncVersion(),
                                hash, hash, "SYNCED");

                        // Also log to MySQL
                        syncLogDAO.logMySQL(currentSyncSession, tableName, remoteId,
                                "UPDATE", "PULL", "SUCCESS", null);

                    } catch (Exception e) {
                        syncLogDAO.log(currentSyncSession, tableName, remoteId,
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
                result.addPushed(tableName, pushed);
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

        // CRITICAL: Convert remote FK IDs to local FK IDs before updating
        fields = convertForeignKeysForPull(tableName, fields);

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
        int localId = genericEntity.getId();

        // Get remote ID for this local record
        Integer remoteId = syncMetadataDAO.getRemoteId(tableName, localId);
        if (remoteId == null) {
            // No mapping exists - this should be an insert, not update
            System.out.println("No remote ID mapping found for " + tableName + " local ID " + localId + " - inserting instead");
            insertRemoteEntity(tableName, entity);
            return;
        }

        Map<String, Object> fields = genericEntity.getAllFields();

        // CRITICAL: Convert local FK IDs to remote FK IDs before pushing
        fields = convertForeignKeysForPush(tableName, fields);

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
            // Use REMOTE ID in WHERE clause, not local ID!
            pstmt.setInt(paramIndex, remoteId);

            pstmt.executeUpdate();
        }
    }

    private void insertRemoteEntity(String tableName, SyncableEntity entity) throws SQLException {
        if (!(entity instanceof GenericSyncableEntity)) {
            throw new SQLException("Entity must be GenericSyncableEntity");
        }

        GenericSyncableEntity genericEntity = (GenericSyncableEntity) entity;
        int localId = genericEntity.getId();
        Map<String, Object> fields = genericEntity.getAllFields();

        // CRITICAL: Convert local FK IDs to remote FK IDs before pushing
        fields = convertForeignKeysForPush(tableName, fields);

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
             PreparedStatement pstmt = conn.prepareStatement(sql.toString(),
                                                             Statement.RETURN_GENERATED_KEYS)) {

            int paramIndex = 1;
            for (Object value : values) {
                pstmt.setObject(paramIndex++, value);
            }

            pstmt.executeUpdate();

            // CRITICAL: Capture MySQL generated ID and save mapping
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int remoteId = rs.getInt(1);
                    syncMetadataDAO.setRemoteId(tableName, localId, remoteId);
                    System.out.println("Mapped " + tableName + " local ID " + localId + " → remote ID " + remoteId);
                }
            }
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

    /**
     * Soft delete a local record by setting deleted_at timestamp
     */
    private void softDeleteLocal(String tableName, int localId) throws SQLException {
        String sql = "UPDATE `" + tableName + "` SET deleted_at = datetime('now'), " +
                     "sync_status = 'SYNCED' WHERE id = ?";

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, localId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Insert entity into local database and return generated ID
     */
    private int insertLocalEntity(String tableName, SyncableEntity entity) throws SQLException {
        if (!(entity instanceof GenericSyncableEntity)) {
            throw new SQLException("Entity must be GenericSyncableEntity");
        }

        GenericSyncableEntity genericEntity = (GenericSyncableEntity) entity;
        Map<String, Object> fields = genericEntity.getAllFields();

        // CRITICAL: Convert remote FK IDs to local FK IDs before inserting
        fields = convertForeignKeysForPull(tableName, fields);

        // Build dynamic INSERT SQL for SQLite
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(tableName).append("` (");
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!entry.getKey().equals("id")) {  // Skip auto-increment ID
                columns.add(entry.getKey());
                placeholders.add("?");
                values.add(entry.getValue());
            }
        }

        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(String.join(", ", placeholders));
        sql.append(")");

        try (Connection conn = dbManager.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString(),
                                                             Statement.RETURN_GENERATED_KEYS)) {

            int paramIndex = 1;
            for (Object value : values) {
                pstmt.setObject(paramIndex++, value);
            }

            pstmt.executeUpdate();

            // Return the generated local ID
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

            throw new SQLException("Failed to get generated ID for inserted local entity");
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
     * Get foreign key mappings for a table
     * Returns Map of: FK column name → referenced table name
     */
    private Map<String, String> getForeignKeyMappings(String tableName) {
        Map<String, String> mappings = new java.util.HashMap<>();

        switch (tableName) {
            case "members":
                mappings.put("group_id", "groups");
                break;

            case "events":
                mappings.put("organizer_id", "members");
                break;

            case "projects":
                // No FK for now
                break;

            case "contributions":
                mappings.put("member_id", "members");
                // entity_id is polymorphic (events or projects) - handled specially
                break;

            case "expenses":
                mappings.put("project_id", "projects");
                mappings.put("paid_by", "members");
                break;

            case "payment_groups":
                mappings.put("group_id", "groups");
                break;

            default:
                // No mappings for unknown tables
                break;
        }

        return mappings;
    }

    /**
     * Convert local FK IDs to remote FK IDs before PUSH
     * CRITICAL: This ensures relationships are preserved when syncing to MySQL
     */
    private Map<String, Object> convertForeignKeysForPush(String tableName, Map<String, Object> fields) throws SQLException {
        Map<String, Object> converted = new java.util.HashMap<>(fields);
        Map<String, String> fkMappings = getForeignKeyMappings(tableName);

        for (Map.Entry<String, String> fk : fkMappings.entrySet()) {
            String fkColumn = fk.getKey();    // e.g., "group_id"
            String fkTable = fk.getValue();   // e.g., "groups"

            Object localFkValue = fields.get(fkColumn);
            if (localFkValue != null && localFkValue instanceof Integer) {
                int localFkId = (Integer) localFkValue;

                // Get remote ID for this local FK
                Integer remoteFkId = syncMetadataDAO.getRemoteId(fkTable, localFkId);
                if (remoteFkId != null) {
                    converted.put(fkColumn, remoteFkId);
                    System.out.println("Converted FK for PUSH: " + tableName + "." + fkColumn + " local " + localFkId + " → remote " + remoteFkId);
                } else {
                    System.err.println("WARNING: No remote ID mapping found for " + fkTable + " local ID " + localFkId + " - keeping local ID (may cause FK constraint error)");
                }
            }
        }

        // Special handling for polymorphic entity_id in contributions
        if (tableName.equals("contributions")) {
            String entityType = (String) fields.get("entity_type");
            Object entityIdValue = fields.get("entity_id");

            if (entityType != null && entityIdValue != null && entityIdValue instanceof Integer) {
                int localEntityId = (Integer) entityIdValue;
                String entityTable = entityType.equals("EVENT") ? "events" : "projects";

                Integer remoteEntityId = syncMetadataDAO.getRemoteId(entityTable, localEntityId);
                if (remoteEntityId != null) {
                    converted.put("entity_id", remoteEntityId);
                    System.out.println("Converted polymorphic FK for PUSH: contributions.entity_id (" + entityType + ") local " + localEntityId + " → remote " + remoteEntityId);
                } else {
                    System.err.println("WARNING: No remote ID mapping found for " + entityTable + " local ID " + localEntityId);
                }
            }
        }

        return converted;
    }

    /**
     * Convert remote FK IDs to local FK IDs after PULL
     * CRITICAL: This ensures relationships work correctly in local SQLite
     */
    private Map<String, Object> convertForeignKeysForPull(String tableName, Map<String, Object> fields) throws SQLException {
        Map<String, Object> converted = new java.util.HashMap<>(fields);
        Map<String, String> fkMappings = getForeignKeyMappings(tableName);

        for (Map.Entry<String, String> fk : fkMappings.entrySet()) {
            String fkColumn = fk.getKey();    // e.g., "group_id"
            String fkTable = fk.getValue();   // e.g., "groups"

            Object remoteFkValue = fields.get(fkColumn);
            if (remoteFkValue != null && remoteFkValue instanceof Integer) {
                int remoteFkId = (Integer) remoteFkValue;

                // Get local ID for this remote FK
                Integer localFkId = syncMetadataDAO.getLocalIdByRemoteId(fkTable, remoteFkId);
                if (localFkId != null) {
                    converted.put(fkColumn, localFkId);
                    System.out.println("Converted FK for PULL: " + tableName + "." + fkColumn + " remote " + remoteFkId + " → local " + localFkId);
                } else {
                    // FK reference doesn't exist locally yet - this could happen if:
                    // 1. Referenced record hasn't been synced yet
                    // 2. Referenced record was deleted
                    System.err.println("WARNING: No local ID mapping found for " + fkTable + " remote ID " + remoteFkId + " - setting FK to NULL");
                    converted.put(fkColumn, null);
                }
            }
        }

        // Special handling for polymorphic entity_id in contributions
        if (tableName.equals("contributions")) {
            String entityType = (String) fields.get("entity_type");
            Object entityIdValue = fields.get("entity_id");

            if (entityType != null && entityIdValue != null && entityIdValue instanceof Integer) {
                int remoteEntityId = (Integer) entityIdValue;
                String entityTable = entityType.equals("EVENT") ? "events" : "projects";

                Integer localEntityId = syncMetadataDAO.getLocalIdByRemoteId(entityTable, remoteEntityId);
                if (localEntityId != null) {
                    converted.put("entity_id", localEntityId);
                    System.out.println("Converted polymorphic FK for PULL: contributions.entity_id (" + entityType + ") remote " + remoteEntityId + " → local " + localEntityId);
                } else {
                    System.err.println("WARNING: No local ID mapping found for " + entityTable + " remote ID " + remoteEntityId + " - setting entity_id to NULL");
                    converted.put("entity_id", null);
                }
            }
        }

        return converted;
    }

    /**
     * Convert technical SQL errors to user-friendly messages
     */
    private String getUserFriendlyErrorMessage(SQLException e) {
        String errorMsg = e.getMessage().toLowerCase();

        // Connection errors
        if (errorMsg.contains("connection") || errorMsg.contains("timeout") ||
            errorMsg.contains("refused") || errorMsg.contains("unreachable")) {
            return "Erreur de connexion au serveur.\n\n" +
                   "Le serveur de synchronisation n'est pas accessible.\n\n" +
                   "Veuillez vérifier:\n" +
                   "• Votre connexion Internet\n" +
                   "• Que le serveur est bien en ligne\n" +
                   "• Les paramètres de connexion dans le fichier de configuration";
        }

        // Authentication errors
        if (errorMsg.contains("access denied") || errorMsg.contains("authentication") ||
            errorMsg.contains("password")) {
            return "Erreur d'authentification.\n\n" +
                   "Les identifiants de connexion au serveur sont incorrects.\n\n" +
                   "Veuillez vérifier:\n" +
                   "• Le nom d'utilisateur\n" +
                   "• Le mot de passe\n" +
                   "• Les droits d'accès à la base de données";
        }

        // Database not found
        if (errorMsg.contains("unknown database") || errorMsg.contains("database") && errorMsg.contains("not found")) {
            return "Base de données introuvable.\n\n" +
                   "La base de données spécifiée n'existe pas sur le serveur.\n\n" +
                   "Veuillez contacter l'administrateur système.";
        }

        // Network errors
        if (errorMsg.contains("network") || errorMsg.contains("host")) {
            return "Erreur réseau.\n\n" +
                   "Impossible de joindre le serveur de synchronisation.\n\n" +
                   "Veuillez vérifier votre connexion Internet.";
        }

        // Default message for other SQL errors
        return "Erreur de synchronisation.\n\n" +
               "Une erreur technique s'est produite lors de la synchronisation.\n\n" +
               "Détails techniques: " + e.getMessage() + "\n\n" +
               "Si le problème persiste, veuillez contacter le support.";
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

        // Detailed tracking by table
        private Map<String, Integer> pullByTable;
        private Map<String, Integer> pushByTable;
        private Map<String, Integer> conflictsByTable;

        public SyncResult() {
            this.errors = new ArrayList<>();
            this.pullByTable = new java.util.HashMap<>();
            this.pushByTable = new java.util.HashMap<>();
            this.conflictsByTable = new java.util.HashMap<>();
        }

        public void merge(SyncResult other) {
            this.recordsPulled += other.recordsPulled;
            this.recordsPushed += other.recordsPushed;
            this.conflicts += other.conflicts;
            this.errors.addAll(other.errors);

            // Merge table-specific stats
            other.pullByTable.forEach((table, count) ->
                pullByTable.merge(table, count, Integer::sum));
            other.pushByTable.forEach((table, count) ->
                pushByTable.merge(table, count, Integer::sum));
            other.conflictsByTable.forEach((table, count) ->
                conflictsByTable.merge(table, count, Integer::sum));
        }

        public void addPulled(int count) {
            this.recordsPulled += count;
        }

        public void addPulled(String tableName, int count) {
            this.recordsPulled += count;
            pullByTable.merge(tableName, count, Integer::sum);
        }

        public void addPushed(int count) {
            this.recordsPushed += count;
        }

        public void addPushed(String tableName, int count) {
            this.recordsPushed += count;
            pushByTable.merge(tableName, count, Integer::sum);
        }

        public void addConflict() {
            this.conflicts++;
        }

        public void addConflict(String tableName) {
            this.conflicts++;
            conflictsByTable.merge(tableName, 1, Integer::sum);
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

        // Detailed tracking getters
        public Map<String, Integer> getPullByTable() { return pullByTable; }
        public Map<String, Integer> getPushByTable() { return pushByTable; }
        public Map<String, Integer> getConflictsByTable() { return conflictsByTable; }

        @Override
        public String toString() {
            return String.format("Sync Result: %s | Pulled: %d, Pushed: %d, Conflicts: %d, Errors: %d",
                    success ? "SUCCESS" : "FAILED",
                    recordsPulled, recordsPushed, conflicts, errors.size());
        }
    }
}
