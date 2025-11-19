package com.nasroul.service;

import com.nasroul.dao.ContributionDAO;
import com.nasroul.model.Contribution;

import java.sql.SQLException;
import java.util.List;

public class ContributionService {
    private final ContributionDAO contributionDAO;

    public ContributionService() {
        this.contributionDAO = new ContributionDAO();
    }

    public void createContribution(Contribution contribution) throws SQLException {
        contributionDAO.create(contribution);
    }

    public List<Contribution> getContributionsByMember(int memberId) throws SQLException {
        return contributionDAO.findByMember(memberId);
    }

    public List<Contribution> getContributionsByEntity(String entityType, int entityId) throws SQLException {
        return contributionDAO.findByEntity(entityType, entityId);
    }

    public List<Contribution> getPendingContributions() throws SQLException {
        return contributionDAO.findPending();
    }

    public List<Contribution> getAllContributions() throws SQLException {
        return contributionDAO.findAll();
    }

    public void updateContribution(Contribution contribution) throws SQLException {
        contributionDAO.update(contribution);
    }

    public void deleteContribution(int id) throws SQLException {
        contributionDAO.delete(id);
    }

    public Double getTotalByEntity(String entityType, int entityId) throws SQLException {
        return contributionDAO.getTotalByEntity(entityType, entityId);
    }

    public Double getTotalContributions() throws SQLException {
        List<Contribution> contributions = contributionDAO.findAll();
        return contributions.stream()
            .filter(c -> "PAID".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();
    }
}
