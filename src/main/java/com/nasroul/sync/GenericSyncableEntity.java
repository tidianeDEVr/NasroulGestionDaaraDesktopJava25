package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic implementation of SyncableEntity for sync operations
 * Represents any table row as a Map of column name -> value
 */
public class GenericSyncableEntity extends SyncableEntity {

    private final String tableName;
    private final Map<String, Object> fields;

    public GenericSyncableEntity(String tableName) {
        this.tableName = tableName;
        this.fields = new HashMap<>();
    }

    /**
     * Create from ResultSet
     */
    public static GenericSyncableEntity fromResultSet(String tableName, ResultSet rs) throws SQLException {
        GenericSyncableEntity entity = new GenericSyncableEntity(tableName);

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object value = rs.getObject(i);
            entity.fields.put(columnName, value);
        }

        // Set sync metadata from parent class
        entity.setCreatedAt(parseLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(parseLocalDateTime(rs, "updated_at"));
        entity.setDeletedAt(parseLocalDateTime(rs, "deleted_at"));
        entity.setLastModifiedBy(getString(rs, "last_modified_by"));
        entity.setSyncStatus(getString(rs, "sync_status"));
        entity.setSyncVersion(getInteger(rs, "sync_version"));
        entity.setLastSyncAt(parseLocalDateTime(rs, "last_sync_at"));

        return entity;
    }

    public String getTableName() {
        return tableName;
    }

    public Object getField(String name) {
        return fields.get(name);
    }

    public void setField(String name, Object value) {
        fields.put(name, value);
    }

    public Map<String, Object> getAllFields() {
        return new HashMap<>(fields);
    }

    public Integer getId() {
        Object id = fields.get("id");
        return id != null ? (Integer) id : null;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        // Return all non-sync fields for hashing
        Map<String, Object> hashFields = new HashMap<>();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            // Exclude sync metadata fields from hash
            if (!key.equals("created_at") && !key.equals("updated_at") &&
                !key.equals("deleted_at") && !key.equals("last_modified_by") &&
                !key.equals("sync_status") && !key.equals("sync_version") &&
                !key.equals("last_sync_at")) {
                hashFields.put(key, entry.getValue());
            }
        }

        return hashFields;
    }

    // Helper methods to safely extract values from ResultSet
    private static String getString(ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    private static Integer getInteger(ResultSet rs, String columnName) {
        try {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            return null;
        }
    }

    private static java.time.LocalDateTime parseLocalDateTime(ResultSet rs, String columnName) {
        try {
            String dateStr = rs.getString(columnName);
            if (dateStr != null && !dateStr.isEmpty()) {
                // Parse SQLite datetime format: 'YYYY-MM-DD HH:MM:SS'
                return java.time.LocalDateTime.parse(dateStr.replace(" ", "T"));
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
}
