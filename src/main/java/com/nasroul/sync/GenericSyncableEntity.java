package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Représentation générique d'une ligne de table synchronisable :
 * colonne → valeur, plus les métadonnées de sync héritées.
 *
 * Les colonnes temporelles sont normalisées au format canonique dès la
 * lecture (voir {@link SyncValues}) afin que la comparaison et l'écriture
 * soient indépendantes du moteur d'origine.
 */
public class GenericSyncableEntity extends SyncableEntity {

    private final String tableName;
    private final Map<String, Object> fields;

    public GenericSyncableEntity(String tableName) {
        this.tableName = tableName;
        this.fields = new LinkedHashMap<>();
    }

    /**
     * Create from ResultSet
     */
    public static GenericSyncableEntity fromResultSet(String tableName, ResultSet rs) throws SQLException {
        GenericSyncableEntity entity = new GenericSyncableEntity(tableName);

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            if (columnName == null || columnName.isEmpty()) {
                columnName = metaData.getColumnName(i);
            }
            columnName = columnName.toLowerCase();
            int columnType = metaData.getColumnType(i);

            Object value;
            if (columnType == java.sql.Types.BLOB || columnType == java.sql.Types.BINARY ||
                columnType == java.sql.Types.VARBINARY || columnType == java.sql.Types.LONGVARBINARY) {
                byte[] bytes = rs.getBytes(i);
                value = rs.wasNull() ? null : bytes;
            } else if (SyncValues.TEMPORAL_COLUMNS.contains(columnName)) {
                // getString évite la conversion de fuseau du driver MySQL ; la
                // normalisation gère aussi les anciennes valeurs en epoch millis.
                value = SyncValues.normalizeTemporal(rs.getString(i));
            } else {
                value = rs.getObject(i);
                if (value instanceof byte[] bytes) {
                    value = bytes; // BLOB déclaré sans type (SQLite)
                }
            }

            entity.fields.put(columnName, value);
        }

        entity.refreshSyncMetadata();
        return entity;
    }

    /** Construit une entité depuis une map colonne → valeur (ex. ligne JSON de la passerelle). */
    public static GenericSyncableEntity fromFields(String tableName, Map<String, Object> values) {
        GenericSyncableEntity entity = new GenericSyncableEntity(tableName);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String column = e.getKey().toLowerCase();
            Object value = e.getValue();
            if (SyncValues.TEMPORAL_COLUMNS.contains(column)) {
                value = SyncValues.normalizeTemporal(value);
            }
            entity.fields.put(column, value);
        }
        entity.refreshSyncMetadata();
        return entity;
    }

    /** Copie de cette entité avec un autre jeu de champs (ex. FK converties). */
    public GenericSyncableEntity withFields(Map<String, Object> newFields) {
        GenericSyncableEntity copy = new GenericSyncableEntity(tableName);
        copy.fields.putAll(newFields);
        copy.refreshSyncMetadata();
        return copy;
    }

    /** Recalcule les métadonnées de sync (parent) à partir des champs. */
    public void refreshSyncMetadata() {
        setCreatedAt(SyncValues.parseDateTime(fields.get("created_at")));
        setUpdatedAt(SyncValues.parseDateTime(fields.get("updated_at")));
        setDeletedAt(SyncValues.parseDateTime(fields.get("deleted_at")));
        Object modifiedBy = fields.get("last_modified_by");
        setLastModifiedBy(modifiedBy != null ? modifiedBy.toString() : null);
        Object status = fields.get("sync_status");
        setSyncStatus(status != null ? status.toString() : null);
        Object version = fields.get("sync_version");
        setSyncVersion(version instanceof Number n ? n.intValue()
                : version != null ? parseIntOrNull(version.toString()) : null);
        setLastSyncAt(SyncValues.parseDateTime(fields.get("last_sync_at")));
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getTableName() {
        return tableName;
    }

    public Object getField(String name) {
        return fields.get(name);
    }

    public void setField(String name, Object value) {
        fields.put(name, value);
        if (SyncValues.SYNC_META_COLUMNS.contains(name)) {
            refreshSyncMetadata();
        }
    }

    public Map<String, Object> getAllFields() {
        return new LinkedHashMap<>(fields);
    }

    public Integer getId() {
        Object id = fields.get("id");
        return id instanceof Number n ? n.intValue() : null;
    }

    /** Valeur textuelle d'un champ (null-safe). */
    public String getString(String name) {
        Object value = fields.get(name);
        return value != null ? value.toString() : null;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        // Contenu métier uniquement : ni l'id (différent sur chaque poste),
        // ni les métadonnées de sync.
        Map<String, Object> hashFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("id") && !SyncValues.SYNC_META_COLUMNS.contains(key)) {
                hashFields.put(key, entry.getValue());
            }
        }
        return hashFields;
    }

    @Override
    public String toString() {
        return tableName + fields;
    }
}
