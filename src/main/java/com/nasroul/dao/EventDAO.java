package com.nasroul.dao;

import com.nasroul.model.Event;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventDAO {
    private final DatabaseManager dbManager;

    public EventDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Event event) throws SQLException {
        String sql = """
            INSERT INTO events (name, description, start_date, end_date, location, status, organizer_id, max_capacity, contribution_target, active, created_at, updated_at, last_modified_by, sync_status, sync_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), 'system', 'PENDING', 1)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, event.getName());
            pstmt.setString(2, event.getDescription());
            pstmt.setString(3, event.getStartDate().toString());
            pstmt.setString(4, event.getEndDate() != null ? event.getEndDate().toString() : null);
            pstmt.setString(5, event.getLocation());
            pstmt.setString(6, event.getStatus());

            // Handle nullable organizer_id - SQLite JDBC doesn't support setObject for nulls
            if (event.getOrganizerId() != null) {
                pstmt.setInt(7, event.getOrganizerId());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            // Handle nullable max_capacity - SQLite JDBC doesn't support setObject for nulls
            if (event.getMaxCapacity() != null) {
                pstmt.setInt(8, event.getMaxCapacity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            pstmt.setDouble(9, event.getContributionTarget() != null ? event.getContributionTarget() : 0.0);
            pstmt.setBoolean(10, event.isActive());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    event.setId(rs.getInt(1));
                }
            }
        }
    }

    public Event findById(int id) throws SQLException {
        String sql = """
            SELECT e.*, m.first_name || ' ' || m.last_name AS organizer_name
            FROM events e
            LEFT JOIN members m ON e.organizer_id = m.id
            WHERE e.id = ? AND e.deleted_at IS NULL
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractEvent(rs);
            }
        }

        return null;
    }

    public List<Event> findAll() throws SQLException {
        List<Event> events = new ArrayList<>();
        String sql = """
            SELECT e.*, m.first_name || ' ' || m.last_name AS organizer_name
            FROM events e
            LEFT JOIN members m ON e.organizer_id = m.id
            WHERE e.deleted_at IS NULL
            ORDER BY e.start_date DESC
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                events.add(extractEvent(rs));
            }
        }

        return events;
    }

    public void update(Event event) throws SQLException {
        String sql = """
            UPDATE events
            SET name = ?, description = ?, start_date = ?, end_date = ?,
                location = ?, status = ?, organizer_id = ?, max_capacity = ?, contribution_target = ?, active = ?,
                updated_at = datetime('now'), last_modified_by = 'system', sync_status = 'PENDING', sync_version = sync_version + 1
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, event.getName());
            pstmt.setString(2, event.getDescription());
            pstmt.setString(3, event.getStartDate().toString());
            pstmt.setString(4, event.getEndDate() != null ? event.getEndDate().toString() : null);
            pstmt.setString(5, event.getLocation());
            pstmt.setString(6, event.getStatus());

            // Handle nullable organizer_id - SQLite JDBC doesn't support setObject for nulls
            if (event.getOrganizerId() != null) {
                pstmt.setInt(7, event.getOrganizerId());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            // Handle nullable max_capacity - SQLite JDBC doesn't support setObject for nulls
            if (event.getMaxCapacity() != null) {
                pstmt.setInt(8, event.getMaxCapacity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            pstmt.setDouble(9, event.getContributionTarget() != null ? event.getContributionTarget() : 0.0);
            pstmt.setBoolean(10, event.isActive());
            pstmt.setInt(11, event.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = """
            UPDATE events
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

    private Event extractEvent(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setId(rs.getInt("id"));
        event.setName(rs.getString("name"));
        event.setDescription(rs.getString("description"));

        // Parse start_date with try-catch for SQLite compatibility
        String startDateStr = rs.getString("start_date");
        try {
            event.setStartDate(LocalDateTime.parse(startDateStr.replace(" ", "T")));
        } catch (Exception e) {
            System.err.println("Error parsing start_date: " + startDateStr + " - " + e.getMessage());
            event.setStartDate(LocalDateTime.now());
        }

        // Parse end_date with try-catch for SQLite compatibility
        String endDate = rs.getString("end_date");
        if (endDate != null) {
            try {
                event.setEndDate(LocalDateTime.parse(endDate.replace(" ", "T")));
            } catch (Exception e) {
                System.err.println("Error parsing end_date: " + endDate + " - " + e.getMessage());
                event.setEndDate(null);
            }
        } else {
            event.setEndDate(null);
        }

        event.setLocation(rs.getString("location"));
        event.setStatus(rs.getString("status"));

        int organizerId = rs.getInt("organizer_id");
        event.setOrganizerId(rs.wasNull() ? null : organizerId);

        event.setOrganizerName(rs.getString("organizer_name"));

        int maxCapacity = rs.getInt("max_capacity");
        event.setMaxCapacity(rs.wasNull() ? null : maxCapacity);

        event.setContributionTarget(rs.getDouble("contribution_target"));
        event.setActive(rs.getBoolean("active"));

        return event;
    }
}
