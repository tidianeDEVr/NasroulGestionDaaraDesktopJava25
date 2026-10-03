package com.nasroul.sync;

import com.nasroul.dao.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bases SQLite jetables jouant le rôle de poste(s) et de serveur, avec le
 * schéma complet de l'application. Les requêtes utilisent la même grammaire
 * que la production (backticks acceptés par SQLite).
 */
final class SyncTestSupport {

    /** Une base SQLite sur fichier temporaire, schéma complet. */
    static final class Db implements AutoCloseable {
        final Path file;

        Db(String label) throws Exception {
            file = Files.createTempFile("sync-" + label + "-", ".db");
            try (Connection c = open()) {
                DatabaseManager.initializeSQLiteSchema(c);
            }
        }

        Connection open() throws SQLException {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA busy_timeout=5000");
            }
            return c;
        }

        /** Insère une ligne et renvoie son id. Les colonnes de sync absentes reçoivent des défauts. */
        int insert(String table, Map<String, Object> cols) throws SQLException {
            Map<String, Object> all = new LinkedHashMap<>(cols);
            all.putIfAbsent("created_at", "2026-01-01 08:00:00");
            all.putIfAbsent("updated_at", "2026-01-01 08:00:00");
            all.putIfAbsent("last_modified_by", "test");
            all.putIfAbsent("sync_status", "PENDING");
            all.putIfAbsent("sync_version", 1);
            List<String> names = new ArrayList<>(all.keySet());
            String sql = "INSERT INTO `" + table + "` (" + String.join(", ", names) + ") VALUES ("
                    + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")";
            try (Connection c = open();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                for (String n : names) {
                    SyncValues.bind(ps, i++, all.get(n));
                }
                ps.executeUpdate();
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }

        void exec(String sql, Object... params) throws SQLException {
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    SyncValues.bind(ps, i + 1, params[i]);
                }
                ps.executeUpdate();
            }
        }

        int count(String table, String where, Object... params) throws SQLException {
            Object v = scalar("SELECT COUNT(*) FROM `" + table + "`" + (where != null ? " WHERE " + where : ""), params);
            return ((Number) v).intValue();
        }

        Object scalar(String sql, Object... params) throws SQLException {
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    SyncValues.bind(ps, i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getObject(1) : null;
                }
            }
        }

        String string(String sql, Object... params) throws SQLException {
            Object v = scalar(sql, params);
            return v != null ? v.toString() : null;
        }

        Integer integer(String sql, Object... params) throws SQLException {
            Object v = scalar(sql, params);
            return v != null ? ((Number) v).intValue() : null;
        }

        /** id distant d'une ligne locale, via sync_metadata. */
        Integer remoteIdOf(String table, int localId) throws SQLException {
            return integer("SELECT remote_id FROM sync_metadata WHERE table_name = ? AND record_id = ?", table, localId);
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(file);
        }
    }

    /** Un « poste » : sa base locale + le serveur partagé. */
    static SyncConnections connections(Db local, Db remote) {
        return new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return local.open();
            }

            @Override
            public RemoteStore openRemote() throws SQLException {
                return new JdbcRemoteStore(remote.open());
            }
        };
    }

    static SyncManager manager(Db local, Db remote) {
        return new SyncManager(connections(local, remote), ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
    }

    static SyncManager manager(Db local, Db remote, ConflictResolver.ResolutionStrategy strategy) {
        return new SyncManager(connections(local, remote), strategy);
    }

    // ----------------------------------------------------------- fabriques

    static Map<String, Object> group(String name, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", "Groupe " + name);
        m.put("active", 1);
        m.put("contribution_target", 0.0);
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> member(String first, String last, String phone, Integer groupId, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("first_name", first);
        m.put("last_name", last);
        m.put("email", null);
        m.put("phone", phone);
        m.put("join_date", "2026-01-01");
        m.put("role", "MEMBRE");
        m.put("active", 1);
        m.put("group_id", groupId);
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> event(String name, Integer organizerId, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("start_date", "2026-03-01");
        m.put("status", "PLANNED");
        m.put("organizer_id", organizerId);
        m.put("active", 1);
        m.put("contribution_target", 0.0);
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> project(String name, Integer managerId, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("status", "PLANNING");
        m.put("budget", 100000.0);
        m.put("manager_id", managerId);
        m.put("contribution_target", 0.0);
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> contribution(int memberId, String entityType, int entityId, Integer groupId,
                                            double amount, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("member_id", memberId);
        m.put("entity_type", entityType);
        m.put("entity_id", entityId);
        m.put("group_id", groupId);
        m.put("amount", amount);
        m.put("date", "2026-02-01");
        m.put("status", "PAID");
        m.put("payment_method", "CASH");
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> expense(String description, String entityType, int entityId, Integer memberId,
                                       double amount, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", description);
        m.put("amount", amount);
        m.put("date", "2026-02-05");
        m.put("category", "LOGISTIQUE");
        m.put("entity_type", entityType);
        m.put("entity_id", entityId);
        m.put("member_id", memberId);
        m.put("updated_at", updatedAt);
        return m;
    }

    static Map<String, Object> paymentGroup(int groupId, String entityType, int entityId, double amount, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group_id", groupId);
        m.put("entity_type", entityType);
        m.put("entity_id", entityId);
        m.put("amount", amount);
        m.put("updated_at", updatedAt);
        return m;
    }

    private SyncTestSupport() {
    }
}
