package com.nasroul.service;

import com.nasroul.dao.ExpenseDAO;
import com.nasroul.model.Expense;

import java.sql.SQLException;
import java.util.List;

public class ExpenseService {
    private final ExpenseDAO expenseDAO;

    public ExpenseService() {
        this.expenseDAO = new ExpenseDAO();
    }

    public void createExpense(Expense expense) throws SQLException {
        validateExpense(expense);
        expenseDAO.create(expense);
    }

    public Expense getExpenseById(int id) throws SQLException {
        return expenseDAO.findById(id);
    }

    public List<Expense> getAllExpenses() throws SQLException {
        return expenseDAO.findAll();
    }

    public List<Expense> getExpensesByEntity(String entityType, int entityId) throws SQLException {
        return expenseDAO.findByEntity(entityType, entityId);
    }

    public void updateExpense(Expense expense) throws SQLException {
        validateExpense(expense);
        expenseDAO.update(expense);
    }

    public void deleteExpense(int id) throws SQLException {
        expenseDAO.delete(id);
    }

    public void bulkCreate(List<Expense> expenses) throws SQLException {
        for (Expense expense : expenses) {
            try {
                createExpense(expense);
            } catch (SQLException | IllegalArgumentException e) {
                System.err.println("Failed to import expense: " + expense.getDescription() + " - " + e.getMessage());
            }
        }
    }

    private void validateExpense(Expense expense) {
        if (expense.getDescription() == null || expense.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (expense.getAmount() == null || expense.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (expense.getEntityType() == null || expense.getEntityId() == null) {
            throw new IllegalArgumentException("Entity type and ID are required");
        }
    }
}
