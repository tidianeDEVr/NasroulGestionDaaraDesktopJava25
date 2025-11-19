package com.nasroul.service;

import com.nasroul.dao.ProjectDAO;
import com.nasroul.model.Project;

import java.sql.SQLException;
import java.util.List;

public class ProjectService {
    private final ProjectDAO projectDAO;

    public ProjectService() {
        this.projectDAO = new ProjectDAO();
    }

    public void createProject(Project project) throws SQLException {
        validateProject(project);
        projectDAO.create(project);
    }

    public Project getProjectById(int id) throws SQLException {
        return projectDAO.findById(id);
    }

    public List<Project> getAllProjects() throws SQLException {
        return projectDAO.findAll();
    }

    public void updateProject(Project project) throws SQLException {
        validateProject(project);
        projectDAO.update(project);
    }

    public void deleteProject(int id) throws SQLException {
        projectDAO.delete(id);
    }

    public void bulkCreate(List<Project> projects) throws SQLException {
        for (Project project : projects) {
            try {
                createProject(project);
            } catch (SQLException | IllegalArgumentException e) {
                System.err.println("Failed to import project: " + project.getName() + " - " + e.getMessage());
            }
        }
    }

    private void validateProject(Project project) {
        if (project.getName() == null || project.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Project name is required");
        }
    }
}
