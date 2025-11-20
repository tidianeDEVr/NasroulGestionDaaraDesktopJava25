package com.nasroul.dao;

import com.nasroul.model.Group;
import com.nasroul.util.DeviceIdGenerator;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    private final DatabaseManager dbManager;

    public GroupDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Group group) throws SQLException {
        String sql = """
            INSERT INTO `groups` (name, description, active,
                created_at, updated_at, last_modified_by, sync_status, sync_version)
            VALUES (?, ?, ?, datetime('now'), datetime('now'), ?, 'PENDING', 1)
            """;

        String deviceId = DeviceIdGenerator.getDeviceId();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getDescription());
            pstmt.setBoolean(3, group.isActive());
            pstmt.setString(4, deviceId); // last_modified_by

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    group.setId(rs.getInt(1));
                }
            }

            // Set sync metadata on the model
            LocalDateTime now = LocalDateTime.now();
            group.setCreatedAt(now);
            group.setUpdatedAt(now);
            group.setLastModifiedBy(deviceId);
            group.setSyncStatus("PENDING");
            group.setSyncVersion(1);
        }
    }

    public Group findById(int id) throws SQLException {
        String sql = "SELECT * FROM `groups` WHERE id = ? AND deleted_at IS NULL";

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
        String sql = "SELECT * FROM `groups` WHERE deleted_at IS NULL ORDER BY name";

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
        String sql = "SELECT * FROM `groups` WHERE active = 1 AND deleted_at IS NULL ORDER BY name";

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
            SET name = ?, description = ?, active = ?,
                updated_at = datetime('now'), last_modified_by = ?, sync_status = 'PENDING',
                sync_version = sync_version + 1
            WHERE id = ?
            """;

        String deviceId = DeviceIdGenerator.getDeviceId();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getDescription());
            pstmt.setBoolean(3, group.isActive());
            pstmt.setString(4, deviceId); // last_modified_by
            pstmt.setInt(5, group.getId());

            pstmt.executeUpdate();

            // Update sync metadata on the model
            group.setUpdatedAt(LocalDateTime.now());
            group.setLastModifiedBy(deviceId);
            group.setSyncStatus("PENDING");
            group.setSyncVersion((group.getSyncVersion() != null ? group.getSyncVersion() : 0) + 1);
        }
    }

    public void delete(int id) throws SQLException {
        // Soft delete: mark as deleted instead of physical deletion
        String sql = """
            UPDATE `groups`
            SET deleted_at = datetime('now'), updated_at = datetime('now'),
                last_modified_by = ?, sync_status = 'PENDING', sync_version = sync_version + 1
            WHERE id = ?
            """;

        String deviceId = DeviceIdGenerator.getDeviceId();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, deviceId); // last_modified_by
            pstmt.setInt(2, id);

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

        // Extract sync metadata - SQLite stores datetime as TEXT
        String createdAt = rs.getString("created_at");
        if (createdAt != null && !createdAt.isEmpty()) {
            try {
                group.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null && !updatedAt.isEmpty()) {
            try {
                group.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        String deletedAt = rs.getString("deleted_at");
        if (deletedAt != null && !deletedAt.isEmpty()) {
            try {
                group.setDeletedAt(LocalDateTime.parse(deletedAt.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        group.setLastModifiedBy(rs.getString("last_modified_by"));
        group.setSyncStatus(rs.getString("sync_status"));
        group.setSyncVersion(rs.getInt("sync_version"));

        String lastSyncAt = rs.getString("last_sync_at");
        if (lastSyncAt != null && !lastSyncAt.isEmpty()) {
            try {
                group.setLastSyncAt(LocalDateTime.parse(lastSyncAt.replace(" ", "T")));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        return group;
    }
}
