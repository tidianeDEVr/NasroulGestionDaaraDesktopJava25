package com.nasroul.dao;

import com.nasroul.model.Contribution;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ContributionDAO {
    private final DatabaseManager dbManager;

    public ContributionDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Contribution contribution) throws SQLException {
        String sql = """
            INSERT INTO contributions (member_id, entity_type, entity_id, amount, date, status, payment_method, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, contribution.getMemberId());
            pstmt.setString(2, contribution.getEntityType());
            pstmt.setInt(3, contribution.getEntityId());
            pstmt.setDouble(4, contribution.getAmount());
            pstmt.setString(5, contribution.getDate().toString());
            pstmt.setString(6, contribution.getStatus());
            pstmt.setString(7, contribution.getPaymentMethod());
            pstmt.setString(8, contribution.getNotes());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    contribution.setId(rs.getInt(1));
                }
            }
        }
    }

    public List<Contribution> findByMember(int memberId) throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*,
                   m.first_name || ' ' || m.last_name AS member_name,
                   CASE
                       WHEN c.entity_type = 'EVENT' THEN (SELECT name FROM events WHERE id = c.entity_id)
                       WHEN c.entity_type = 'PROJECT' THEN (SELECT name FROM projects WHERE id = c.entity_id)
                   END AS entity_name
            FROM contributions c
            LEFT JOIN members m ON c.member_id = m.id
            WHERE c.member_id = ?
            ORDER BY c.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, memberId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                contributions.add(extractContribution(rs));
            }
        }

        return contributions;
    }

    public List<Contribution> findByEntity(String entityType, int entityId) throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*,
                   m.first_name || ' ' || m.last_name AS member_name,
                   CASE
                       WHEN c.entity_type = 'EVENT' THEN (SELECT name FROM events WHERE id = c.entity_id)
                       WHEN c.entity_type = 'PROJECT' THEN (SELECT name FROM projects WHERE id = c.entity_id)
                   END AS entity_name
            FROM contributions c
            LEFT JOIN members m ON c.member_id = m.id
            WHERE c.entity_type = ? AND c.entity_id = ?
            ORDER BY c.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                contributions.add(extractContribution(rs));
            }
        }

        return contributions;
    }

    public List<Contribution> findPending() throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*,
                   m.first_name || ' ' || m.last_name AS member_name,
                   CASE
                       WHEN c.entity_type = 'EVENT' THEN (SELECT name FROM events WHERE id = c.entity_id)
                       WHEN c.entity_type = 'PROJECT' THEN (SELECT name FROM projects WHERE id = c.entity_id)
                   END AS entity_name
            FROM contributions c
            LEFT JOIN members m ON c.member_id = m.id
            WHERE c.status = 'PENDING'
            ORDER BY c.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                contributions.add(extractContribution(rs));
            }
        }

        return contributions;
    }

    public List<Contribution> findAll() throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*,
                   m.first_name || ' ' || m.last_name AS member_name,
                   CASE
                       WHEN c.entity_type = 'EVENT' THEN (SELECT name FROM events WHERE id = c.entity_id)
                       WHEN c.entity_type = 'PROJECT' THEN (SELECT name FROM projects WHERE id = c.entity_id)
                   END AS entity_name
            FROM contributions c
            LEFT JOIN members m ON c.member_id = m.id
            ORDER BY c.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                contributions.add(extractContribution(rs));
            }
        }

        return contributions;
    }

    public void update(Contribution contribution) throws SQLException {
        String sql = """
            UPDATE contributions
            SET member_id = ?, entity_type = ?, entity_id = ?, amount = ?,
                date = ?, status = ?, payment_method = ?, notes = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, contribution.getMemberId());
            pstmt.setString(2, contribution.getEntityType());
            pstmt.setInt(3, contribution.getEntityId());
            pstmt.setDouble(4, contribution.getAmount());
            pstmt.setString(5, contribution.getDate().toString());
            pstmt.setString(6, contribution.getStatus());
            pstmt.setString(7, contribution.getPaymentMethod());
            pstmt.setString(8, contribution.getNotes());
            pstmt.setInt(9, contribution.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM contributions WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public Double getTotalByEntity(String entityType, int entityId) throws SQLException {
        String sql = """
            SELECT SUM(amount)
            FROM contributions
            WHERE entity_type = ? AND entity_id = ? AND status = 'PAID'
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble(1);
            }
        }

        return 0.0;
    }

    private Contribution extractContribution(ResultSet rs) throws SQLException {
        Contribution contribution = new Contribution();
        contribution.setId(rs.getInt("id"));
        contribution.setMemberId(rs.getInt("member_id"));
        contribution.setMemberName(rs.getString("member_name"));
        contribution.setEntityType(rs.getString("entity_type"));
        contribution.setEntityId(rs.getInt("entity_id"));
        contribution.setEntityName(rs.getString("entity_name"));
        contribution.setAmount(rs.getDouble("amount"));
        contribution.setDate(LocalDate.parse(rs.getString("date")));
        contribution.setStatus(rs.getString("status"));
        contribution.setPaymentMethod(rs.getString("payment_method"));
        contribution.setNotes(rs.getString("notes"));

        return contribution;
    }
}
