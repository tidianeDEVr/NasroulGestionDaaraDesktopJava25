package com.nasroul.controller;

import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.MemberService;
import com.nasroul.ui.Forms;
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
            Forms.setText(txtName, project.getName());
            Forms.setText(txtDescription, project.getDescription());

            if (project.getEndDate() != null) {
                dpEndDate.setValue(project.getEndDate());
            }

            cbStatus.setValue(translateStatusToFrench(project.getStatus()));

            if (project.getBudget() != null) {
                txtBudget.setText(project.getBudget().toString());
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
        if (Forms.text(txtName).isEmpty()) {
            showError("Le nom est obligatoire");
            return;
        }

        // Validate budget
        Double budget = 0.0;
        if (!Forms.text(txtBudget).isEmpty()) {
            try {
                budget = Double.parseDouble(Forms.text(txtBudget));
                if (budget < 0) {
                    showError("Le budget ne peut pas être négatif");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Format de budget invalide");
                return;
            }
        }

        // Update project object
        if (project == null) {
            project = new Project();
        }

        project.setName(Forms.text(txtName));
        project.setDescription(Forms.textOrNull(txtDescription));
        project.setStartDate(null); // Date de début supprimée
        project.setEndDate(dpEndDate.getValue());
        // Le budget cible n'est plus saisi ici : il est dérivé des objectifs de
        // cotisation par groupe (montant par membre × effectif)
        project.setStatus(translateStatusToEnglish(cbStatus.getValue()));
        project.setBudget(budget);

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
