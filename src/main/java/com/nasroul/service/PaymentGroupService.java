package com.nasroul.service;

import com.nasroul.dao.PaymentGroupDAO;
import com.nasroul.model.PaymentGroup;

import java.sql.SQLException;
import java.util.List;

public class PaymentGroupService {
    private final PaymentGroupDAO paymentGroupDAO;

    public PaymentGroupService() {
        this.paymentGroupDAO = new PaymentGroupDAO();
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
