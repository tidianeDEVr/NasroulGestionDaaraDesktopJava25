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
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    member.setId(rs.getInt(1));
                    // Insert member_groups associations
                    if (member.getGroupIds() != null && !member.getGroupIds().isEmpty()) {
                        saveMemberGroups(conn, member.getId(), member.getGroupIds());
                    }
                }
            }
        }
    }

    public Member findById(int id) throws SQLException {
        String sql = """
            SELECT m.*, g.name AS group_name
            FROM members m
            LEFT JOIN `groups` g ON m.group_id = g.id
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
            LEFT JOIN `groups` g ON m.group_id = g.id
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
            LEFT JOIN `groups` g ON m.group_id = g.id
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

            // Update member_groups associations
            deleteMemberGroups(conn, member.getId());
            if (member.getGroupIds() != null && !member.getGroupIds().isEmpty()) {
                saveMemberGroups(conn, member.getId(), member.getGroupIds());
            }
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

        // Load member groups
        try {
            loadMemberGroups(member);
        } catch (SQLException e) {
            // Continue without groups if there's an error
        }

        return member;
    }

    private void loadMemberGroups(Member member) throws SQLException {
        String sql = """
            SELECT mg.group_id, g.name
            FROM member_groups mg
            LEFT JOIN `groups` g ON mg.group_id = g.id
            WHERE mg.member_id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, member.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Integer> groupIds = new ArrayList<>();
                List<String> groupNames = new ArrayList<>();

                while (rs.next()) {
                    groupIds.add(rs.getInt("group_id"));
                    String name = rs.getString("name");
                    if (name != null) {
                        groupNames.add(name);
                    }
                }

                member.setGroupIds(groupIds);
                member.setGroupNames(groupNames);
            }
        }
    }

    private void saveMemberGroups(Connection conn, Integer memberId, List<Integer> groupIds) throws SQLException {
        String sql = "INSERT INTO member_groups (member_id, group_id) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Integer groupId : groupIds) {
                pstmt.setInt(1, memberId);
                pstmt.setInt(2, groupId);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private void deleteMemberGroups(Connection conn, Integer memberId) throws SQLException {
        String sql = "DELETE FROM member_groups WHERE member_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, memberId);
            pstmt.executeUpdate();
        }
    }

    public List<Member> findByGroupId(int groupId) throws SQLException {
        List<Member> members = new ArrayList<>();
        String sql = """
            SELECT DISTINCT m.*, NULL AS group_name
            FROM members m
            INNER JOIN member_groups mg ON m.id = mg.member_id
            WHERE mg.group_id = ?
            ORDER BY m.last_name, m.first_name
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(extractMember(rs));
                }
            }
        }

        return members;
    }
}
