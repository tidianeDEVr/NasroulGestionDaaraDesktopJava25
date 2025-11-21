package com.nasroul.dao;

import com.nasroul.model.PaymentGroup;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentGroupDAO {
    private final DatabaseManager dbManager;

    public PaymentGroupDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(PaymentGroup paymentGroup) throws SQLException {
        String sql = """
            INSERT INTO payment_groups (group_id, entity_type, entity_id, amount, created_at, updated_at, last_modified_by, sync_status, sync_version)
            VALUES (?, ?, ?, ?, datetime('now'), datetime('now'), 'system', 'PENDING', 1)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, paymentGroup.getGroupId());
            pstmt.setString(2, paymentGroup.getEntityType());
            pstmt.setInt(3, paymentGroup.getEntityId());
            pstmt.setDouble(4, paymentGroup.getAmount());

            pstmt.executeUpdate();

            // Get generated ID using last_insert_rowid() for SQLite compatibility
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(getIdSql)) {
                if (rs.next()) {
                    paymentGroup.setId(rs.getInt(1));
                }
            }
        }
    }

    public PaymentGroup findById(int id) throws SQLException {
        String sql = """
            SELECT pg.*,
                   g.name AS group_name,
                   COALESCE(e.name, p.name) AS entity_name
            FROM payment_groups pg
            LEFT JOIN `groups` g ON pg.group_id = g.id AND g.deleted_at IS NULL
            LEFT JOIN events e ON pg.entity_type = 'EVENT' AND pg.entity_id = e.id AND e.deleted_at IS NULL
            LEFT JOIN projects p ON pg.entity_type = 'PROJECT' AND pg.entity_id = p.id AND p.deleted_at IS NULL
            WHERE pg.id = ? AND pg.deleted_at IS NULL
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractPaymentGroup(rs);
                }
            }
        }
        return null;
    }

    public List<PaymentGroup> findAll() throws SQLException {
        String sql = """
            SELECT pg.*,
                   g.name AS group_name,
                   COALESCE(e.name, p.name) AS entity_name
            FROM payment_groups pg
            LEFT JOIN `groups` g ON pg.group_id = g.id AND g.deleted_at IS NULL
            LEFT JOIN events e ON pg.entity_type = 'EVENT' AND pg.entity_id = e.id AND e.deleted_at IS NULL
            LEFT JOIN projects p ON pg.entity_type = 'PROJECT' AND pg.entity_id = p.id AND p.deleted_at IS NULL
            WHERE pg.deleted_at IS NULL
            ORDER BY pg.id DESC
            """;

        List<PaymentGroup> paymentGroups = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                paymentGroups.add(extractPaymentGroup(rs));
            }
        }

        return paymentGroups;
    }

    public List<PaymentGroup> findByGroup(int groupId) throws SQLException {
        String sql = """
            SELECT pg.*,
                   g.name AS group_name,
                   COALESCE(e.name, p.name) AS entity_name
            FROM payment_groups pg
            LEFT JOIN `groups` g ON pg.group_id = g.id AND g.deleted_at IS NULL
            LEFT JOIN events e ON pg.entity_type = 'EVENT' AND pg.entity_id = e.id AND e.deleted_at IS NULL
            LEFT JOIN projects p ON pg.entity_type = 'PROJECT' AND pg.entity_id = p.id AND p.deleted_at IS NULL
            WHERE pg.group_id = ? AND pg.deleted_at IS NULL
            ORDER BY pg.id DESC
            """;

        List<PaymentGroup> paymentGroups = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    paymentGroups.add(extractPaymentGroup(rs));
                }
            }
        }

        return paymentGroups;
    }

    public List<PaymentGroup> findByEntity(String entityType, int entityId) throws SQLException {
        String sql = """
            SELECT pg.*,
                   g.name AS group_name,
                   COALESCE(e.name, p.name) AS entity_name
            FROM payment_groups pg
            LEFT JOIN `groups` g ON pg.group_id = g.id AND g.deleted_at IS NULL
            LEFT JOIN events e ON pg.entity_type = 'EVENT' AND pg.entity_id = e.id AND e.deleted_at IS NULL
            LEFT JOIN projects p ON pg.entity_type = 'PROJECT' AND pg.entity_id = p.id AND p.deleted_at IS NULL
            WHERE pg.entity_type = ? AND pg.entity_id = ? AND pg.deleted_at IS NULL
            ORDER BY pg.id DESC
            """;

        List<PaymentGroup> paymentGroups = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    paymentGroups.add(extractPaymentGroup(rs));
                }
            }
        }

        return paymentGroups;
    }

    public void update(PaymentGroup paymentGroup) throws SQLException {
        String sql = """
            UPDATE payment_groups
            SET group_id = ?, entity_type = ?, entity_id = ?, amount = ?,
                updated_at = datetime('now'), last_modified_by = 'system', sync_status = 'PENDING', sync_version = sync_version + 1
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, paymentGroup.getGroupId());
            pstmt.setString(2, paymentGroup.getEntityType());
            pstmt.setInt(3, paymentGroup.getEntityId());
            pstmt.setDouble(4, paymentGroup.getAmount());
            pstmt.setInt(5, paymentGroup.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = """
            UPDATE payment_groups
            SET deleted_at = datetime('now'),
                updated_at = datetime('now'),
                last_modified_by = 'system',
                sync_status = 'PENDING',
                sync_version = sync_version + 1
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public Double getTotalByEntity(String entityType, int entityId) throws SQLException {
        String sql = """
            SELECT SUM(amount) AS total
            FROM payment_groups
            WHERE entity_type = ? AND entity_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total");
                }
            }
        }
        return 0.0;
    }

    private PaymentGroup extractPaymentGroup(ResultSet rs) throws SQLException {
        PaymentGroup pg = new PaymentGroup();
        pg.setId(rs.getInt("id"));
        pg.setGroupId(rs.getInt("group_id"));
        pg.setGroupName(rs.getString("group_name"));
        pg.setEntityType(rs.getString("entity_type"));
        pg.setEntityId(rs.getInt("entity_id"));
        pg.setEntityName(rs.getString("entity_name"));
        pg.setAmount(rs.getDouble("amount"));

        return pg;
    }
}
