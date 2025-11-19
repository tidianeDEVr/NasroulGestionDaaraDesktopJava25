package com.nasroul.service;

import com.nasroul.dao.GroupDAO;
import com.nasroul.model.Group;

import java.sql.SQLException;
import java.util.List;

public class GroupService {
    private final GroupDAO groupDAO;

    public GroupService() {
        this.groupDAO = new GroupDAO();
    }

    public void createGroup(Group group) throws SQLException {
        groupDAO.create(group);
    }

    public Group getGroupById(int id) throws SQLException {
        return groupDAO.findById(id);
    }

    public List<Group> getAllGroups() throws SQLException {
        return groupDAO.findAll();
    }

    public List<Group> getActiveGroups() throws SQLException {
        return groupDAO.findActive();
    }

    public void updateGroup(Group group) throws SQLException {
        groupDAO.update(group);
    }

    public void deleteGroup(int id) throws SQLException {
        groupDAO.delete(id);
    }

    public int getMemberCount(int groupId) throws SQLException {
        return groupDAO.getMemberCount(groupId);
    }
}
