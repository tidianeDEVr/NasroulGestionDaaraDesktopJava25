package com.nasroul.sync;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opérations JDBC génériques partagées par le côté local (SQLite) et
 * {@link JdbcRemoteStore} (MySQL). Portables entre les deux moteurs :
 * identifiants entre backticks, horodatages liés en texte canonique.
 */
final class JdbcRows {

    private JdbcRows() {
    }

    static List<GenericSyncableEntity> fetchAll(Connection conn, String table, String where) throws SQLException {
        String sql = "SELECT * FROM `" + table + "`" + (where != null ? " WHERE " + where : "") + " ORDER BY id";
        List<GenericSyncableEntity> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(GenericSyncableEntity.fromResultSet(table, rs));
            }
        }
        if ("members".equals(table) && !rows.isEmpty()) {
            Map<Integer, List<Integer>> byMember = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT member_id, group_id FROM member_groups ORDER BY member_id, group_id")) {
                while (rs.next()) {
                    byMember.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getInt(2));
                }
            }
            for (GenericSyncableEntity row : rows) {
                row.setField(SyncManager.MEMBER_GROUPS_FIELD, byMember.getOrDefault(row.getId(), List.of()));
            }
        }
        return rows;
    }

    static GenericSyncableEntity get(Connection conn, String table, int id) throws SQLException {
        GenericSyncableEntity row = null;
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM `" + table + "` WHERE id = ?")) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    row = GenericSyncableEntity.fromResultSet(table, rs);
                }
            }
        }
        if (row != null && "members".equals(table)) {
            List<Integer> groupIds = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT group_id FROM member_groups WHERE member_id = ? ORDER BY group_id")) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        groupIds.add(rs.getInt(1));
                    }
                }
            }
            row.setField(SyncManager.MEMBER_GROUPS_FIELD, groupIds);
        }
        return row;
    }

    static Set<String> columns(Connection conn, String table) throws SQLException {
        Set<String> cols = new LinkedHashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + table + "` WHERE 1 = 0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                cols.add(md.getColumnLabel(i).toLowerCase());
            }
        }
        return cols;
    }

    static int insert(Connection conn, String table, Map<String, Object> fields) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            columns.add("`" + e.getKey() + "`");
            values.add(e.getValue());
        }
        String sql = "INSERT INTO `" + table + "` (" + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        try (PreparedStatement pstmt = prepareInsert(conn, sql)) {
            for (int i = 0; i < values.size(); i++) {
                SyncValues.bind(pstmt, i + 1, values.get(i));
            }
            pstmt.executeUpdate();
            return generatedId(conn, pstmt, table);
        }
    }

    static void update(Connection conn, String table, int id, Map<String, Object> fields) throws SQLException {
        if (fields.isEmpty()) {
            return;
        }
        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            sets.add("`" + e.getKey() + "` = ?");
            values.add(e.getValue());
        }
        String sql = "UPDATE `" + table + "` SET " + String.join(", ", sets) + " WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object value : values) {
                SyncValues.bind(pstmt, i++, value);
            }
            pstmt.setInt(i, id);
            pstmt.executeUpdate();
        }
    }

    /** Remplace les appartenances d'un membre (table de jointure). */
    static void writeMemberGroups(Connection conn, int memberId, List<Integer> groupIds) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM member_groups WHERE member_id = ?")) {
            del.setInt(1, memberId);
            del.executeUpdate();
        }
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO member_groups (member_id, group_id) VALUES (?, ?)")) {
            for (Integer id : groupIds) {
                ins.setInt(1, memberId);
                ins.setInt(2, id);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /**
     * Le driver SQLite ne supporte pas prepareStatement(sql, RETURN_GENERATED_KEYS)
     * (SQLFeatureNotSupportedException) alors que MySQL l'exige pour
     * getGeneratedKeys() : on tente, puis on se replie.
     */
    static PreparedStatement prepareInsert(Connection conn, String sql) throws SQLException {
        try {
            return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        } catch (SQLFeatureNotSupportedException e) {
            return conn.prepareStatement(sql);
        }
    }

    /** Id généré par la dernière insertion, quel que soit le moteur. */
    static int generatedId(Connection conn, PreparedStatement pstmt, String table) throws SQLException {
        try (ResultSet rs = pstmt.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {
            // repli ci-dessous
        }
        String product = conn.getMetaData().getDatabaseProductName();
        String fallback = product != null && product.toLowerCase().contains("sqlite")
                ? "SELECT last_insert_rowid()" : "SELECT LAST_INSERT_ID()";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(fallback)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Identifiant généré indisponible après insertion dans " + table);
    }

    /** Liste d'ids depuis un champ synthétique (null-safe). */
    static List<Integer> toIntList(Object value) {
        List<Integer> out = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Number n) {
                    out.add(n.intValue());
                }
            }
        }
        return out;
    }
}
