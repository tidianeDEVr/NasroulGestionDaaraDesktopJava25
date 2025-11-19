package com.nasroul.dao;

import com.nasroul.model.Member;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MemberDAO {
    private final DatabaseManager dbManager;

    public MemberDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public void create(Member member) throws SQLException {
        String sql = """
            INSERT INTO members (first_name, last_name, email, phone, birth_date, address, join_date, role, avatar, active, group_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, member.getFirstName());
            pstmt.setString(2, member.getLastName());
            pstmt.setString(3, member.getEmail());
            pstmt.setString(4, member.getPhone());
            pstmt.setString(5, member.getBirthDate() != null ? member.getBirthDate().toString() : null);
            pstmt.setString(6, member.getAddress());
            pstmt.setString(7, member.getJoinDate().toString());
            pstmt.setString(8, member.getRole());
            pstmt.setBytes(9, member.getAvatar());
            pstmt.setInt(10, member.isActive() ? 1 : 0);
            pstmt.setObject(11, member.getGroupId());

            pstmt.executeUpdate();

            try (PreparedStatement idStmt = conn.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = idStmt.executeQuery()) {
                if (rs.next()) {
                    member.setId(rs.getInt(1));
                }
            }
        }
    }

    public Member findById(int id) throws SQLException {
        String sql = """
            SELECT m.*, g.name AS group_name
            FROM members m
            LEFT JOIN groups g ON m.group_id = g.id
            WHERE m.id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractMember(rs);
            }
        }

        return null;
    }

    public List<Member> findAll() throws SQLException {
        List<Member> members = new ArrayList<>();
        String sql = """
            SELECT m.*, g.name AS group_name
            FROM members m
            LEFT JOIN groups g ON m.group_id = g.id
            ORDER BY m.last_name, m.first_name
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                members.add(extractMember(rs));
            }
        }

        return members;
    }

    public List<Member> findActive() throws SQLException {
        List<Member> members = new ArrayList<>();
        String sql = """
            SELECT m.*, g.name AS group_name
            FROM members m
            LEFT JOIN groups g ON m.group_id = g.id
            WHERE m.active = 1
            ORDER BY m.last_name, m.first_name
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                members.add(extractMember(rs));
            }
        }

        return members;
    }

    public void update(Member member) throws SQLException {
        String sql = """
            UPDATE members
            SET first_name = ?, last_name = ?, email = ?, phone = ?, birth_date = ?,
                address = ?, join_date = ?, role = ?, avatar = ?, active = ?, group_id = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, member.getFirstName());
            pstmt.setString(2, member.getLastName());
            pstmt.setString(3, member.getEmail());
            pstmt.setString(4, member.getPhone());
            pstmt.setString(5, member.getBirthDate() != null ? member.getBirthDate().toString() : null);
            pstmt.setString(6, member.getAddress());
            pstmt.setString(7, member.getJoinDate().toString());
            pstmt.setString(8, member.getRole());
            pstmt.setBytes(9, member.getAvatar());
            pstmt.setInt(10, member.isActive() ? 1 : 0);
            pstmt.setObject(11, member.getGroupId());
            pstmt.setInt(12, member.getId());

            pstmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM members WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    private Member extractMember(ResultSet rs) throws SQLException {
        Member member = new Member();
        member.setId(rs.getInt("id"));
        member.setFirstName(rs.getString("first_name"));
        member.setLastName(rs.getString("last_name"));
        member.setEmail(rs.getString("email"));
        member.setPhone(rs.getString("phone"));

        String birthDate = rs.getString("birth_date");
        member.setBirthDate(birthDate != null ? LocalDate.parse(birthDate) : null);

        member.setAddress(rs.getString("address"));
        member.setJoinDate(LocalDate.parse(rs.getString("join_date")));
        member.setRole(rs.getString("role"));
        member.setAvatar(rs.getBytes("avatar"));
        member.setActive(rs.getInt("active") == 1);

        int groupId = rs.getInt("group_id");
        member.setGroupId(rs.wasNull() ? null : groupId);
        member.setGroupName(rs.getString("group_name"));

        return member;
    }
}
