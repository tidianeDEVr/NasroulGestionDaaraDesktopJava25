package com.nasroul.controller;

import com.nasroul.model.Expense;
import com.nasroul.model.Project;
import com.nasroul.service.ExpenseService;
import com.nasroul.service.ProjectService;
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
import java.util.stream.Collectors;

public class ProjectController {

    @FXML
    private TableView<Project> projectTable;
    @FXML
    private TableColumn<Project, String> colId, colName, colEndDate, colBudget, colStatus, colManager;

    private final ProjectService projectService;
    private final ExpenseService expenseService;
    private final ObservableList<Project> projectList;

    public ProjectController() {
        this.projectService = new ProjectService();
        this.expenseService = new ExpenseService();
        this.projectList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colEndDate.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getEndDate() != null ? data.getValue().getEndDate().format(formatter) : ""));
        colBudget.setCellValueFactory(data -> new SimpleStringProperty(numberFormat.format(data.getValue().getBudget()) + " CFA"));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(translateStatus(data.getValue().getStatus())));
        colManager.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getManagerName()));

        projectTable.setItems(projectList);
        loadProjects();
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "PLANNING": return "Planification";
            case "ONGOING": return "En cours";
            case "COMPLETED": return "Terminé";
            case "ON_HOLD": return "En attente";
            case "CANCELLED": return "Annulé";
            default: return status;
        }
    }

    private void loadProjects() {
        try {
            projectList.clear();
            projectList.addAll(projectService.getAllProjects());
        } catch (SQLException e) {
            showError("Error", "Could not load projects: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showProjectDialog(null);
    }

    @FXML
    private void handleEdit() {
        Project selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un projet à modifier");
            return;
        }
        showProjectDialog(selected);
    }

    private void showProjectDialog(Project project) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ProjectDialog.fxml"));
            Scene scene = new Scene(loader.load());

            ProjectDialogController controller = loader.getController();
            controller.setProject(project != null ? project : new Project());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(project != null ? "Modifier le projet" : "Nouveau projet");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(projectTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Project savedProject = controller.getProject();
                    if (savedProject.getId() == null) {
                        projectService.createProject(savedProject);
                        showInfo("Succès", "Projet créé avec succès");
                    } else {
                        projectService.updateProject(savedProject);
                        showInfo("Succès", "Projet modifié avec succès");
                    }
                    loadProjects();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder le projet: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        Project selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Delete");
        confirmation.setContentText("Delete project: " + selected.getName() + "?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    projectService.deleteProject(selected.getId());
                    loadProjects();
                } catch (SQLException e) {
                    showError("Error", "Could not delete project: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadProjects();
    }

    @FXML
    private void handleViewExpenses() {
        Project selectedProject = projectTable.getSelectionModel().getSelectedItem();
        if (selectedProject == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un projet pour voir les dépenses");
            return;
        }

        try {
            List<Expense> expenses = expenseService.getAllExpenses().stream()
                .filter(e -> "PROJECT".equals(e.getEntityType()) && e.getEntityId().equals(selectedProject.getId()))
                .collect(Collectors.toList());

            showExpensesDialog(selectedProject.getName(), expenses);
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les dépenses: " + e.getMessage());
        }
    }

    private void showExpensesDialog(String projectName, List<Expense> expenses) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Dépenses - " + projectName);
        dialog.setHeaderText("Liste des dépenses pour le projet : " + projectName);

        if (expenses.isEmpty()) {
            dialog.setContentText("Aucune dépense enregistrée pour ce projet.");
        } else {
            NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            double total = expenses.stream().mapToDouble(Expense::getAmount).sum();

            StringBuilder content = new StringBuilder();
            content.append(String.format("Total des dépenses : %s CFA\n\n", formatter.format(total)));
            content.append("Détails :\n");
            content.append("-".repeat(50)).append("\n");

            for (Expense expense : expenses) {
                content.append(String.format("• %s - %s CFA (%s)\n",
                    expense.getDescription(),
                    formatter.format(expense.getAmount()),
                    expense.getDate().format(dateFormatter)));
                if (expense.getCategory() != null) {
                    content.append(String.format("  Catégorie : %s\n", expense.getCategory()));
                }
            }

            dialog.setContentText(content.toString());
        }

        dialog.showAndWait();
    }

    @FXML
    private void handleImportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Projects");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showOpenDialog(projectTable.getScene().getWindow());
        if (file != null) {
            try {
                List<Project> imported = ExcelUtil.importProjects(file);
                projectService.bulkCreate(imported);
                loadProjects();
                showInfo("Success", "Imported " + imported.size() + " projects");
            } catch (Exception e) {
                showError("Import Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Template");
        fileChooser.setInitialFileName("projects_template.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(projectTable.getScene().getWindow());
        if (file != null) {
            try {
                ExcelUtil.generateProjectTemplate(file);
                showInfo("Success", "Template exported");
            } catch (Exception e) {
                showError("Export Error", e.getMessage());
            }
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
