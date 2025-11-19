package com.nasroul.dao;

import com.nasroul.model.Expense;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDAO {
    private final DatabaseManager dbManager;

    public ExpenseDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Expense expense) throws SQLException {
        String sql = """
            INSERT INTO expenses (description, amount, date, category, entity_type, entity_id, member_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, expense.getDescription());
            pstmt.setDouble(2, expense.getAmount());
            pstmt.setString(3, expense.getDate().toString());
            pstmt.setString(4, expense.getCategory());
            pstmt.setString(5, expense.getEntityType());
            pstmt.setInt(6, expense.getEntityId());
            pstmt.setObject(7, expense.getMemberId());

            pstmt.executeUpdate();

            try (PreparedStatement idStmt = conn.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = idStmt.executeQuery()) {
                if (rs.next()) {
                    expense.setId(rs.getInt(1));
                }
            }
        }
    }

    public Expense findById(int id) throws SQLException {
        String sql = """
            SELECT e.*, m.first_name || ' ' || m.last_name AS member_name
            FROM expenses e
            LEFT JOIN members m ON e.member_id = m.id
            WHERE e.id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractExpense(rs);
            }
        }

        return null;
    }

    public List<Expense> findAll() throws SQLException {
        List<Expense> expenses = new ArrayList<>();
        String sql = """
            SELECT e.*, m.first_name || ' ' || m.last_name AS member_name
            FROM expenses e
            LEFT JOIN members m ON e.member_id = m.id
            ORDER BY e.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                expenses.add(extractExpense(rs));
            }
        }

        return expenses;
    }

    public List<Expense> findByEntity(String entityType, int entityId) throws SQLException {
        List<Expense> expenses = new ArrayList<>();
        String sql = """
            SELECT e.*, m.first_name || ' ' || m.last_name AS member_name
            FROM expenses e
            LEFT JOIN members m ON e.member_id = m.id
            WHERE e.entity_type = ? AND e.entity_id = ?
            ORDER BY e.date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                expenses.add(extractExpense(rs));
            }
        }

        return expenses;
    }

    public void update(Expense expense) throws SQLException {
        String sql = """
            UPDATE expenses
            SET description = ?, amount = ?, date = ?, category = ?,
                entity_type = ?, entity_id = ?, member_id = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, expense.getDescription());
            pstmt.setDouble(2, expense.getAmount());
            pstmt.setString(3, expense.getDate().toString());
            pstmt.setString(4, expense.getCategory());
            pstmt.setString(5, expense.getEntityType());
            pstmt.setInt(6, expense.getEntityId());
            pstmt.setObject(7, expense.getMemberId());
            pstmt.setInt(8, expense.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM expenses WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    private Expense extractExpense(ResultSet rs) throws SQLException {
        Expense expense = new Expense();
        expense.setId(rs.getInt("id"));
        expense.setDescription(rs.getString("description"));
        expense.setAmount(rs.getDouble("amount"));
        expense.setDate(LocalDate.parse(rs.getString("date")));
        expense.setCategory(rs.getString("category"));
        expense.setEntityType(rs.getString("entity_type"));
        expense.setEntityId(rs.getInt("entity_id"));

        int memberId = rs.getInt("member_id");
        expense.setMemberId(rs.wasNull() ? null : memberId);

        expense.setMemberName(rs.getString("member_name"));

        return expense;
    }
}
