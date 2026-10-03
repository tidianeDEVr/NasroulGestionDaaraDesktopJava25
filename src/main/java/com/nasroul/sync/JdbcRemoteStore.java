package com.nasroul.sync;

import com.nasroul.dao.SyncLogDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base distante accédée en JDBC direct (MySQL en production, SQLite dans les
 * tests). Une connexion par session de synchronisation.
 */
public class JdbcRemoteStore implements RemoteStore {

    private final Connection conn;
    private final Runnable schemaUpgrader;

    public JdbcRemoteStore(Connection conn) {
        this(conn, null);
    }

    public JdbcRemoteStore(Connection conn, Runnable schemaUpgrader) {
        this.conn = conn;
        this.schemaUpgrader = schemaUpgrader;
    }

    public Connection getConnection() {
        return conn;
    }

    @Override
    public void ping() throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connexion distante fermée");
        }
    }

    @Override
    public Set<String> columns(String table) throws SQLException {
        return JdbcRows.columns(conn, table);
    }

    @Override
    public List<GenericSyncableEntity> fetchAll(String table) throws SQLException {
        return JdbcRows.fetchAll(conn, table, null);
    }

    @Override
    public GenericSyncableEntity get(String table, int id) throws SQLException {
        return JdbcRows.get(conn, table, id);
    }

    @Override
    public int insert(String table, Map<String, Object> fields) throws SQLException {
        return JdbcRows.insert(conn, table, fields);
    }

    @Override
    public void update(String table, int id, Map<String, Object> fields) throws SQLException {
        JdbcRows.update(conn, table, id, fields);
    }

    @Override
    public void writeMemberGroups(int memberId, List<Integer> groupIds) throws SQLException {
        JdbcRows.writeMemberGroups(conn, memberId, groupIds);
    }

    @Override
    public void logBatch(List<SyncLogDAO.Entry> entries) throws SQLException {
        new SyncLogDAO(() -> {
            throw new SQLException("connexion locale non disponible ici");
        }).logBatch(conn, entries);
    }

    @Override
    public void ensureSchema() {
        if (schemaUpgrader != null) {
            schemaUpgrader.run();
        }
    }

    @Override
    public void registerDevice(String deviceId, String deviceName, String userName) throws SQLException {
        String product = conn.getMetaData().getDatabaseProductName();
        boolean sqlite = product != null && product.toLowerCase().contains("sqlite");
        String now = SyncValues.nowUtc();
        String sql = sqlite
                ? "INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active) VALUES (?, ?, ?, ?, 1) "
                  + "ON CONFLICT(device_id) DO UPDATE SET device_name = excluded.device_name, user_name = excluded.user_name, "
                  + "last_sync_at = excluded.last_sync_at, is_active = 1"
                : "INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active) VALUES (?, ?, ?, ?, 1) "
                  + "ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), user_name = VALUES(user_name), "
                  + "last_sync_at = VALUES(last_sync_at), is_active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setString(2, deviceName);
            pstmt.setString(3, userName);
            pstmt.setString(4, now);
            pstmt.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    /** Petit utilitaire de test/diagnostic : exécute un SQL arbitraire. */
    void execute(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
