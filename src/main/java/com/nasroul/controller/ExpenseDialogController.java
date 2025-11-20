package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Expense;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.EventService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ExpenseDialogController {

    @FXML private TextField txtDescription;
    @FXML private TextField txtAmount;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> cbCategory;
    @FXML private ComboBox<String> cbEntityType;
    @FXML private ComboBox<EntityItem> cbEntity;
    @FXML private ComboBox<MemberItem> cbMember;

    private Expense expense;
    private boolean saved = false;

    private final EventService eventService;
    private final ProjectService projectService;
    private final MemberService memberService;

    public ExpenseDialogController() {
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.memberService = new MemberService();
    }

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());

        // Initialize category combobox
        cbCategory.getItems().addAll(
            "Transport",
            "Nourriture",
            "Matériel",
            "Location",
            "Communication",
            "Services",
            "Autre"
        );

        // Initialize entity type combobox
        cbEntityType.getItems().addAll("Événement", "Projet");

        // Load members for the member combobox
        loadMembers();

        // Setup listener for entity type changes
        cbEntityType.setOnAction(e -> loadEntities());
    }

    public void setExpense(Expense expense) {
        this.expense = expense;

        if (expense.getId() != null) {
            // Edit mode - populate fields
            txtDescription.setText(expense.getDescription());
            txtAmount.setText(expense.getAmount().toString());
            datePicker.setValue(expense.getDate());
            cbCategory.setValue(expense.getCategory());
            cbEntityType.setValue(translateEntityTypeToFrench(expense.getEntityType()));

            // Load entities for the selected type
            loadEntities();

            // Select the entity
            if (expense.getEntityId() != null) {
                cbEntity.getItems().stream()
                    .filter(item -> item.id != null && item.id.equals(expense.getEntityId()))
                    .findFirst()
                    .ifPresent(cbEntity::setValue);
            }

            // Select the member
            if (expense.getMemberId() != null) {
                cbMember.getItems().stream()
                    .filter(item -> item.id != null && item.id.equals(expense.getMemberId()))
                    .findFirst()
                    .ifPresent(cbMember::setValue);
            }
        }
    }

    private void loadMembers() {
        try {
            List<Member> members = memberService.getActiveMembers();
            cbMember.getItems().clear();
            cbMember.getItems().add(new MemberItem(null, "Aucun"));
            for (Member member : members) {
                cbMember.getItems().add(new MemberItem(
                    member.getId(),
                    member.getFirstName() + " " + member.getLastName()
                ));
            }
        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger les membres: " + e.getMessage());
        }
    }

    private void loadEntities() {
        String entityType = cbEntityType.getValue();
        if (entityType == null) {
            cbEntity.getItems().clear();
            return;
        }

        cbEntity.getItems().clear();

        try {
            String entityTypeCode = translateEntityTypeToEnglish(entityType);
            if ("EVENT".equals(entityTypeCode)) {
                List<Event> events = eventService.getAllEvents();
                for (Event event : events) {
                    cbEntity.getItems().add(new EntityItem(event.getId(), event.getName()));
                }
            } else if ("PROJECT".equals(entityTypeCode)) {
                List<Project> projects = projectService.getAllProjects();
                for (Project project : projects) {
                    cbEntity.getItems().add(new EntityItem(project.getId(), project.getName()));
                }
            }
        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger les entités: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        // Validate required fields
        if (txtDescription.getText().trim().isEmpty()) {
            showError("Champ obligatoire", "La description est obligatoire");
            return;
        }

        if (txtAmount.getText().trim().isEmpty()) {
            showError("Champ obligatoire", "Le montant est obligatoire");
            return;
        }

        if (datePicker.getValue() == null) {
            showError("Champ obligatoire", "La date est obligatoire");
            return;
        }

        if (cbEntityType.getValue() == null) {
            showError("Champ obligatoire", "Le type d'entité est obligatoire");
            return;
        }

        if (cbEntity.getValue() == null) {
            showError("Champ obligatoire", "L'entité est obligatoire");
            return;
        }

        // Validate amount
        Double amount;
        try {
            amount = Double.parseDouble(txtAmount.getText().trim());
            if (amount < 0) {
                showError("Montant invalide", "Le montant ne peut pas être négatif");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Montant invalide", "Format de montant invalide");
            return;
        }

        // Update expense object
        if (expense == null) {
            expense = new Expense();
        }

        expense.setDescription(txtDescription.getText().trim());
        expense.setAmount(amount);
        expense.setDate(datePicker.getValue());
        expense.setCategory(cbCategory.getValue());
        expense.setEntityType(translateEntityTypeToEnglish(cbEntityType.getValue()));
        expense.setEntityId(cbEntity.getValue().id);

        MemberItem selectedMember = cbMember.getValue();
        if (selectedMember != null && selectedMember.id != null) {
            expense.setMemberId(selectedMember.id);
        } else {
            expense.setMemberId(null);
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

    public Expense getExpense() {
        return expense;
    }

    private String translateEntityTypeToFrench(String entityType) {
        if (entityType == null) return "Événement";
        switch (entityType) {
            case "EVENT": return "Événement";
            case "PROJECT": return "Projet";
            default: return entityType;
        }
    }

    private String translateEntityTypeToEnglish(String entityType) {
        if (entityType == null) return "EVENT";
        switch (entityType) {
            case "Événement": return "EVENT";
            case "Projet": return "PROJECT";
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

    private void closeDialog() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) txtDescription.getScene().getWindow();
    }

    // Helper class for entity combo box items
    private static class EntityItem {
        final Integer id;
        final String name;

        EntityItem(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Helper class for member combo box items
    private static class MemberItem {
        final Integer id;
        final String name;

        MemberItem(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
