package com.nasroul.service;

import com.nasroul.dao.PaymentGroupDAO;
import com.nasroul.dao.ContributionDAO;
import com.nasroul.model.PaymentGroup;
import com.nasroul.model.Contribution;

import java.sql.SQLException;
import java.util.List;

public class PaymentGroupService {
    private final PaymentGroupDAO paymentGroupDAO;
    private final ContributionDAO contributionDAO;

    public PaymentGroupService() {
        this.paymentGroupDAO = new PaymentGroupDAO();
        this.contributionDAO = new ContributionDAO();
    }

    public void createPaymentGroup(PaymentGroup paymentGroup) throws SQLException {
        validatePaymentGroup(paymentGroup);
        paymentGroupDAO.create(paymentGroup);
    }

    public PaymentGroup getPaymentGroupById(int id) throws SQLException {
        return paymentGroupDAO.findById(id);
    }

    public List<PaymentGroup> getAllPaymentGroups() throws SQLException {
        return paymentGroupDAO.findAll();
    }

    public List<PaymentGroup> getPaymentGroupsByGroup(int groupId) throws SQLException {
        return paymentGroupDAO.findByGroup(groupId);
    }

    public List<PaymentGroup> getPaymentGroupsByEntity(String entityType, int entityId) throws SQLException {
        return paymentGroupDAO.findByEntity(entityType, entityId);
    }

    public void updatePaymentGroup(PaymentGroup paymentGroup) throws SQLException {
        validatePaymentGroup(paymentGroup);
        paymentGroupDAO.update(paymentGroup);
    }

    public void deletePaymentGroup(int id) throws SQLException {
        paymentGroupDAO.delete(id);
    }

    public Double getTotalAmountByEntity(String entityType, int entityId) throws SQLException {
        return paymentGroupDAO.getTotalByEntity(entityType, entityId);
    }

    /**
     * Calculate the total expected amount across all payment groups
     * This sums up all payment group amounts (amount per member for each group/entity combination)
     *
     * @return the total expected amount
     * @throws SQLException if database error
     */
    public double getTotalExpectedAmount() throws SQLException {
        List<PaymentGroup> paymentGroups = paymentGroupDAO.findAll();
        return paymentGroups.stream()
            .mapToDouble(pg -> pg.getAmount() != null ? pg.getAmount() : 0.0)
            .sum();
    }

    /**
     * Calculate the remaining amount for a specific member for a given entity (event or project)
     * Formula: PaymentGroup.amount - SUM(Contributions WHERE status='PAID' AND memberId=X)
     *
     * @param memberId the member ID
     * @param entityType the entity type ("EVENT" or "PROJECT")
     * @param entityId the entity ID
     * @return the remaining amount to pay, or 0.0 if no payment group defined
     * @throws SQLException if database error
     */
    public double calculateRemainingAmount(int memberId, String entityType, int entityId) throws SQLException {
        // Get all payment groups for this entity
        List<PaymentGroup> paymentGroups = paymentGroupDAO.findByEntity(entityType, entityId);

        if (paymentGroups.isEmpty()) {
            return 0.0; // No payment group defined for this entity
        }

        // Get the expected amount per member (from payment groups)
        // Assume we take the first payment group's amount (there should only be one per entity)
        double expectedAmount = paymentGroups.get(0).getAmount();

        // Get all PAID contributions for this member and entity
        List<Contribution> contributions = contributionDAO.findByMember(memberId);
        double totalPaid = contributions.stream()
            .filter(c -> "PAID".equals(c.getStatus()))
            .filter(c -> entityType.equals(c.getEntityType()) && entityId == c.getEntityId())
            .mapToDouble(Contribution::getAmount)
            .sum();

        // Calculate remaining amount
        double remaining = expectedAmount - totalPaid;
        return remaining > 0 ? remaining : 0.0;
    }

    private void validatePaymentGroup(PaymentGroup paymentGroup) {
        if (paymentGroup.getGroupId() == null) {
            throw new IllegalArgumentException("Le groupe est obligatoire");
        }
        if (paymentGroup.getEntityType() == null || paymentGroup.getEntityType().trim().isEmpty()) {
            throw new IllegalArgumentException("Le type d'entité est obligatoire");
        }
        if (paymentGroup.getEntityId() == null) {
            throw new IllegalArgumentException("L'entité est obligatoire");
        }
        if (paymentGroup.getAmount() == null || paymentGroup.getAmount() <= 0) {
            throw new IllegalArgumentException("Le montant par membre doit être supérieur à 0");
        }
    }
}
