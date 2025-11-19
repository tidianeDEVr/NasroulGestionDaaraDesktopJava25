package com.nasroul.controller;

import com.nasroul.model.Expense;
import com.nasroul.service.ExpenseService;
import com.nasroul.util.ExcelUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ExpenseController {

    @FXML
    private TableView<Expense> expenseTable;
    @FXML
    private TableColumn<Expense, String> colId, colDescription, colAmount, colDate, colCategory, colEntityType, colEntityName, colMember;
    @FXML
    private Label totalLabel;

    private final ExpenseService expenseService;
    private final ObservableList<Expense> expenseList;

    public ExpenseController() {
        this.expenseService = new ExpenseService();
        this.expenseList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        colAmount.setCellValueFactory(data -> new SimpleStringProperty(numberFormat.format(data.getValue().getAmount()) + " CFA"));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDate().format(formatter)));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        colEntityType.setCellValueFactory(data -> new SimpleStringProperty(translateEntityType(data.getValue().getEntityType())));
        colEntityName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEntityName()));
        colMember.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMemberName()));

        expenseTable.setItems(expenseList);
        loadExpenses();
    }

    private void loadExpenses() {
        try {
            expenseList.clear();
            List<Expense> expenses = expenseService.getAllExpenses();
            expenseList.addAll(expenses);

            double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
            NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);
            totalLabel.setText("Total: " + formatter.format(total) + " CFA");
        } catch (SQLException e) {
            showError("Error", "Could not load expenses: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showExpenseDialog(null);
    }

    @FXML
    private void handleEdit() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner une dépense à modifier");
            return;
        }
        showExpenseDialog(selected);
    }

    private void showExpenseDialog(Expense expense) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ExpenseDialog.fxml"));
            Scene scene = new Scene(loader.load());

            ExpenseDialogController controller = loader.getController();
            controller.setExpense(expense != null ? expense : new Expense());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(expense != null ? "Modifier la dépense" : "Nouvelle dépense");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(expenseTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Expense savedExpense = controller.getExpense();
                    if (savedExpense.getId() == null) {
                        expenseService.createExpense(savedExpense);
                        showInfo("Succès", "Dépense créée avec succès");
                    } else {
                        expenseService.updateExpense(savedExpense);
                        showInfo("Succès", "Dépense modifiée avec succès");
                    }
                    loadExpenses();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder la dépense: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Delete");
        confirmation.setContentText("Delete expense: " + selected.getDescription() + "?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    expenseService.deleteExpense(selected.getId());
                    loadExpenses();
                } catch (SQLException e) {
                    showError("Error", "Could not delete expense: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadExpenses();
    }

    @FXML
    private void handleImportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Expenses");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showOpenDialog(expenseTable.getScene().getWindow());
        if (file != null) {
            try {
                List<Expense> imported = ExcelUtil.importExpenses(file);
                expenseService.bulkCreate(imported);
                loadExpenses();
                showInfo("Success", "Imported " + imported.size() + " expenses");
            } catch (Exception e) {
                showError("Import Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Template");
        fileChooser.setInitialFileName("expenses_template.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(expenseTable.getScene().getWindow());
        if (file != null) {
            try {
                ExcelUtil.generateExpenseTemplate(file);
                showInfo("Success", "Template exported");
            } catch (Exception e) {
                showError("Export Error", e.getMessage());
            }
        }
    }

    private String translateEntityType(String entityType) {
        if (entityType == null) return "";
        switch (entityType) {
            case "EVENT": return "Événement";
            case "PROJECT": return "Projet";
            default: return entityType;
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
