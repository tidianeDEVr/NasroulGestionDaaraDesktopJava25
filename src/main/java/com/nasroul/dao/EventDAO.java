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
            INSERT INTO events (name, description, start_date, end_date, location, status, organizer_id, max_capacity, contribution_target, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, event.getName());
            pstmt.setString(2, event.getDescription());
            pstmt.setString(3, event.getStartDate().toString());
            pstmt.setString(4, event.getEndDate() != null ? event.getEndDate().toString() : null);
            pstmt.setString(5, event.getLocation());
            pstmt.setString(6, event.getStatus());
            pstmt.setObject(7, event.getOrganizerId());
            pstmt.setObject(8, event.getMaxCapacity());
            pstmt.setDouble(9, event.getContributionTarget() != null ? event.getContributionTarget() : 0.0);
            pstmt.setBoolean(10, event.isActive());

            pstmt.executeUpdate();

            try (PreparedStatement idStmt = conn.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = idStmt.executeQuery()) {
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
            WHERE e.id = ?
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
                location = ?, status = ?, organizer_id = ?, max_capacity = ?, contribution_target = ?, active = ?
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
            pstmt.setObject(7, event.getOrganizerId());
            pstmt.setObject(8, event.getMaxCapacity());
            pstmt.setDouble(9, event.getContributionTarget() != null ? event.getContributionTarget() : 0.0);
            pstmt.setBoolean(10, event.isActive());
            pstmt.setInt(11, event.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM events WHERE id = ?";

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
        event.setStartDate(LocalDateTime.parse(rs.getString("start_date")));

        String endDate = rs.getString("end_date");
        event.setEndDate(endDate != null ? LocalDateTime.parse(endDate) : null);

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
