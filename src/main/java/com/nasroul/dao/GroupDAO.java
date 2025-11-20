package com.nasroul.dao;

import com.nasroul.model.Group;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    private final DatabaseManager dbManager;

    public GroupDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Group group) throws SQLException {
        String sql = """
            INSERT INTO `groups` (name, description, active)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getDescription());
            pstmt.setBoolean(3, group.isActive());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    group.setId(rs.getInt(1));
                }
            }
        }
    }

    public Group findById(int id) throws SQLException {
        String sql = "SELECT * FROM `groups` WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractGroup(rs);
            }
        }

        return null;
    }

    public List<Group> findAll() throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM `groups` ORDER BY name";

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                groups.add(extractGroup(rs));
            }
        }

        return groups;
    }

    public List<Group> findActive() throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM `groups` WHERE active = 1 ORDER BY name";

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                groups.add(extractGroup(rs));
            }
        }

        return groups;
    }

    public void update(Group group) throws SQLException {
        String sql = """
            UPDATE `groups`
            SET name = ?, description = ?, active = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getDescription());
            pstmt.setBoolean(3, group.isActive());
            pstmt.setInt(4, group.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM `groups` WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public int getMemberCount(int groupId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM members WHERE group_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    private Group extractGroup(ResultSet rs) throws SQLException {
        Group group = new Group();
        group.setId(rs.getInt("id"));
        group.setName(rs.getString("name"));
        group.setDescription(rs.getString("description"));
        group.setActive(rs.getBoolean("active"));

        return group;
    }
}
