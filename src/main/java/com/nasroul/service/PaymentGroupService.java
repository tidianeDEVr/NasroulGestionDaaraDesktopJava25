package com.nasroul.service;

import com.nasroul.dao.PaymentGroupDAO;
import com.nasroul.model.PaymentGroup;

import java.sql.SQLException;
import java.util.List;

public class PaymentGroupService {
    private final PaymentGroupDAO paymentGroupDAO;
    private final ContributionCalculator calculator;

    public PaymentGroupService() {
        this.paymentGroupDAO = new PaymentGroupDAO();
        this.calculator = new ContributionCalculator();
    }

    public void createPaymentGroup(PaymentGroup paymentGroup) throws SQLException {
        validatePaymentGroup(paymentGroup);
        ensureUniqueTarget(paymentGroup);
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
        ensureUniqueTarget(paymentGroup);
        paymentGroupDAO.update(paymentGroup);
    }

    public void deletePaymentGroup(int id) throws SQLException {
        paymentGroupDAO.delete(id);
    }

    public Double getTotalAmountByEntity(String entityType, int entityId) throws SQLException {
        return paymentGroupDAO.getTotalByEntity(entityType, entityId);
    }

    /**
     * Total expected across all live entities: for each payment target,
     * target amount × active member count of the group. Delegates to
     * ContributionCalculator, the single source of truth for these figures.
     */
    public double getTotalExpectedAmount() throws SQLException {
        return calculator.totalExpectedAll();
    }

    /**
     * Reject a second payment target for the same (group, entity): duplicates
     * used to make every downstream amount ambiguous. The partial unique index
     * in SQLite is the safety net; this gives the user a readable message.
     */
    private void ensureUniqueTarget(PaymentGroup paymentGroup) throws SQLException {
        List<PaymentGroup> existing = paymentGroupDAO.findByEntityAndGroup(
                paymentGroup.getEntityType(), paymentGroup.getEntityId(), paymentGroup.getGroupId());
        for (PaymentGroup pg : existing) {
            if (paymentGroup.getId() == null || !pg.getId().equals(paymentGroup.getId())) {
                throw new IllegalArgumentException(
                        "Un objectif de cotisation existe déjà pour ce groupe et cette entité ("
                        + String.format("%.0f", pg.getAmount()) + " CFA). "
                        + "Modifiez l'objectif existant au lieu d'en créer un nouveau.");
            }
        }
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
