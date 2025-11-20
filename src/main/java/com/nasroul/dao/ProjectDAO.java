package com.nasroul.dao;

import com.nasroul.model.Project;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProjectDAO {
    private final DatabaseManager dbManager;

    public ProjectDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Project project) throws SQLException {
        String sql = """
            INSERT INTO projects (name, description, start_date, end_date, status, budget, target_budget, manager_id, created_at, updated_at, last_modified_by, sync_status, sync_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), 'system', 'PENDING', 1)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, project.getName());
            pstmt.setString(2, project.getDescription());
            pstmt.setString(3, project.getStartDate() != null ? project.getStartDate().toString() : null);
            pstmt.setString(4, project.getEndDate() != null ? project.getEndDate().toString() : null);
            pstmt.setString(5, project.getStatus());
            pstmt.setDouble(6, project.getBudget());
            pstmt.setDouble(7, project.getTargetBudget());
            pstmt.setObject(8, project.getManagerId());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    project.setId(rs.getInt(1));
                }
            }
        }
    }

    public Project findById(int id) throws SQLException {
        String sql = """
            SELECT p.*, m.first_name || ' ' || m.last_name AS manager_name
            FROM projects p
            LEFT JOIN members m ON p.manager_id = m.id
            WHERE p.id = ? AND p.deleted_at IS NULL
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractProject(rs);
            }
        }

        return null;
    }

    public List<Project> findAll() throws SQLException {
        List<Project> projects = new ArrayList<>();
        String sql = """
            SELECT p.*, m.first_name || ' ' || m.last_name AS manager_name
            FROM projects p
            LEFT JOIN members m ON p.manager_id = m.id
            WHERE p.deleted_at IS NULL
            ORDER BY p.name
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                projects.add(extractProject(rs));
            }
        }

        return projects;
    }

    public void update(Project project) throws SQLException {
        String sql = """
            UPDATE projects
            SET name = ?, description = ?, start_date = ?, end_date = ?,
                status = ?, budget = ?, target_budget = ?, manager_id = ?,
                updated_at = datetime('now'), last_modified_by = 'system', sync_status = 'PENDING', sync_version = sync_version + 1
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, project.getName());
            pstmt.setString(2, project.getDescription());
            pstmt.setString(3, project.getStartDate() != null ? project.getStartDate().toString() : null);
            pstmt.setString(4, project.getEndDate() != null ? project.getEndDate().toString() : null);
            pstmt.setString(5, project.getStatus());
            pstmt.setDouble(6, project.getBudget());
            pstmt.setDouble(7, project.getTargetBudget());
            pstmt.setObject(8, project.getManagerId());
            pstmt.setInt(9, project.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = """
            UPDATE projects
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

    private Project extractProject(ResultSet rs) throws SQLException {
        Project project = new Project();
        project.setId(rs.getInt("id"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));

        String startDate = rs.getString("start_date");
        project.setStartDate(startDate != null ? LocalDate.parse(startDate) : null);

        String endDate = rs.getString("end_date");
        project.setEndDate(endDate != null ? LocalDate.parse(endDate) : null);

        project.setStatus(rs.getString("status"));
        project.setBudget(rs.getDouble("budget"));
        project.setTargetBudget(rs.getDouble("target_budget"));

        int managerId = rs.getInt("manager_id");
        project.setManagerId(rs.wasNull() ? null : managerId);

        project.setManagerName(rs.getString("manager_name"));

        return project;
    }
}
