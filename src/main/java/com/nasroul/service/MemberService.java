package com.nasroul.service;

import com.nasroul.dao.MemberDAO;
import com.nasroul.model.Member;

import java.sql.SQLException;
import java.util.List;

public class MemberService {
    private final MemberDAO memberDAO;

    public MemberService() {
        this.memberDAO = new MemberDAO();
    }

    public void createMember(Member member) throws SQLException {
        validateMember(member);
        memberDAO.create(member);
    }

    public Member getMemberById(int id) throws SQLException {
        return memberDAO.findById(id);
    }

    public List<Member> getAllMembers() throws SQLException {
        return memberDAO.findAll();
    }

    public List<Member> getActiveMembers() throws SQLException {
        return memberDAO.findActive();
    }

    public void updateMember(Member member) throws SQLException {
        validateMember(member);
        memberDAO.update(member);
    }

    public void deleteMember(int id) throws SQLException {
        memberDAO.delete(id);
    }

    public void bulkCreate(List<Member> members) throws SQLException {
        for (Member member : members) {
            try {
                createMember(member);
            } catch (SQLException e) {
                System.err.println("Failed to import member: " + member.getFullName() + " - " + e.getMessage());
            }
        }
    }

    private void validateMember(Member member) {
        if (member.getFirstName() == null || member.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (member.getLastName() == null || member.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
    }
}
