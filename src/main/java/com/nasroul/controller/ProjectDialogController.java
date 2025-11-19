package com.nasroul.controller;

import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.MemberService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ProjectDialogController {

    @FXML private TextField txtName;
    @FXML private TextArea txtDescription;
    @FXML private DatePicker dpEndDate;
    @FXML private ComboBox<Member> cbManager;
    @FXML private TextField txtBudget;
    @FXML private TextField txtTargetBudget;
    @FXML private TextField txtContributionTarget;
    @FXML private ComboBox<String> cbStatus;

    private Project project;
    private boolean saved = false;
    private final MemberService memberService;

    public ProjectDialogController() {
        this.memberService = new MemberService();
    }

    public void initialize() {
        // Populate status options
        cbStatus.getItems().addAll("Planification", "En cours", "Terminé", "En attente", "Annulé");
        cbStatus.setValue("Planification");

        // Load members for manager dropdown
        loadMembers();

        // Set custom cell factory to display member names
        cbManager.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Member member, boolean empty) {
                super.updateItem(member, empty);
                setText(empty || member == null ? "" : member.getFullName());
            }
        });
        cbManager.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Member member, boolean empty) {
                super.updateItem(member, empty);
                setText(empty || member == null ? "" : member.getFullName());
            }
        });
    }

    private void loadMembers() {
        try {
            List<Member> members = memberService.getAllMembers();
            cbManager.getItems().setAll(members);
        } catch (SQLException e) {
            showError("Erreur lors du chargement des membres: " + e.getMessage());
        }
    }

    public void setProject(Project project) {
        this.project = project;

        if (project.getId() != null) {
            // Edit mode - populate fields
            txtName.setText(project.getName());
            txtDescription.setText(project.getDescription());

            if (project.getEndDate() != null) {
                dpEndDate.setValue(project.getEndDate());
            }

            cbStatus.setValue(translateStatusToFrench(project.getStatus()));

            if (project.getBudget() != null) {
                txtBudget.setText(project.getBudget().toString());
            }

            if (project.getTargetBudget() != null) {
                txtTargetBudget.setText(project.getTargetBudget().toString());
            }

            if (project.getContributionTarget() != null) {
                txtContributionTarget.setText(project.getContributionTarget().toString());
            }

            // Select manager
            if (project.getManagerId() != null) {
                cbManager.getItems().stream()
                    .filter(m -> m.getId().equals(project.getManagerId()))
                    .findFirst()
                    .ifPresent(cbManager::setValue);
            }
        }
    }

    @FXML
    private void handleSave() {
        // Validate required fields
        if (txtName.getText().trim().isEmpty()) {
            showError("Le nom est obligatoire");
            return;
        }

        if (txtTargetBudget.getText().trim().isEmpty()) {
            showError("Le budget cible est obligatoire");
            return;
        }

        // Validate budget
        Double budget = 0.0;
        if (!txtBudget.getText().trim().isEmpty()) {
            try {
                budget = Double.parseDouble(txtBudget.getText().trim());
                if (budget < 0) {
                    showError("Le budget ne peut pas être négatif");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Format de budget invalide");
                return;
            }
        }

        // Validate target budget
        Double targetBudget;
        try {
            targetBudget = Double.parseDouble(txtTargetBudget.getText().trim());
            if (targetBudget <= 0) {
                showError("Le budget cible doit être supérieur à 0");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Format de budget cible invalide");
            return;
        }

        // Validate contribution target
        Double contributionTarget = 0.0;
        String contributionTargetText = txtContributionTarget.getText();
        if (contributionTargetText != null && !contributionTargetText.trim().isEmpty()) {
            try {
                contributionTarget = Double.parseDouble(contributionTargetText.trim());
                if (contributionTarget < 0) {
                    showError("Le budget de cotisation ne peut pas être négatif");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Format de budget de cotisation invalide");
                return;
            }
        }

        // Update project object
        if (project == null) {
            project = new Project();
        }

        project.setName(txtName.getText().trim());
        project.setDescription(txtDescription.getText().trim().isEmpty() ? null : txtDescription.getText().trim());
        project.setStartDate(null); // Date de début supprimée
        project.setEndDate(dpEndDate.getValue());
        project.setStatus(translateStatusToEnglish(cbStatus.getValue()));
        project.setBudget(budget);
        project.setTargetBudget(targetBudget);
        project.setContributionTarget(contributionTarget);

        if (cbManager.getValue() != null) {
            project.setManagerId(cbManager.getValue().getId());
            project.setManagerName(cbManager.getValue().getFullName());
        }

        saved = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeDialog();
    }

    public boolean isSaved() {
        return saved;
    }

    public Project getProject() {
        return project;
    }

    private String translateStatusToFrench(String status) {
        if (status == null) return "Planification";
        switch (status) {
            case "PLANNING": return "Planification";
            case "ONGOING": return "En cours";
            case "COMPLETED": return "Terminé";
            case "ON_HOLD": return "En attente";
            case "CANCELLED": return "Annulé";
            default: return status;
        }
    }

    private String translateStatusToEnglish(String status) {
        if (status == null) return "PLANNING";
        switch (status) {
            case "Planification": return "PLANNING";
            case "En cours": return "ONGOING";
            case "Terminé": return "COMPLETED";
            case "En attente": return "ON_HOLD";
            case "Annulé": return "CANCELLED";
            default: return status;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeDialog() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) txtName.getScene().getWindow();
    }
}
