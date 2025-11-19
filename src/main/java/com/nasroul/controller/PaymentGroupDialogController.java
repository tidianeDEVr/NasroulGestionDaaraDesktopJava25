package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Group;
import com.nasroul.model.PaymentGroup;
import com.nasroul.model.Project;
import com.nasroul.service.EventService;
import com.nasroul.service.GroupService;
import com.nasroul.service.ProjectService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class PaymentGroupDialogController {

    @FXML private ComboBox<Group> cbGroup;
    @FXML private ComboBox<String> cbEntityType;
    @FXML private ComboBox<Object> cbEntity;
    @FXML private TextField txtAmount;

    private PaymentGroup paymentGroup;
    private boolean saved = false;

    private final GroupService groupService;
    private final EventService eventService;
    private final ProjectService projectService;

    public PaymentGroupDialogController() {
        this.groupService = new GroupService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
    }

    @FXML
    public void initialize() {
        setupComboBoxes();
        setupEntityTypeListener();
    }

    private void setupComboBoxes() {
        // Load groups
        try {
            List<Group> groups = groupService.getAllGroups();
            cbGroup.getItems().setAll(groups);
            cbGroup.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Group group, boolean empty) {
                    super.updateItem(group, empty);
                    setText(empty || group == null ? "" : group.getName());
                }
            });
            cbGroup.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Group group, boolean empty) {
                    super.updateItem(group, empty);
                    setText(empty || group == null ? "" : group.getName());
                }
            });
        } catch (SQLException e) {
            showError("Erreur lors du chargement des groupes: " + e.getMessage());
        }

        // Entity types
        cbEntityType.getItems().addAll("Événement", "Projet");
    }

    private void setupEntityTypeListener() {
        cbEntityType.setOnAction(event -> {
            String selectedType = cbEntityType.getValue();
            cbEntity.getItems().clear();

            if (selectedType == null) {
                return;
            }

            String typeCode = getEntityTypeCode(selectedType);

            try {
                if ("EVENT".equals(typeCode)) {
                    List<Event> events = eventService.getAllEvents();
                    cbEntity.getItems().addAll(events);
                    cbEntity.setCellFactory(param -> new ListCell<>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                    cbEntity.setButtonCell(new ListCell<>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                } else if ("PROJECT".equals(typeCode)) {
                    List<Project> projects = projectService.getAllProjects();
                    cbEntity.getItems().addAll(projects);
                    cbEntity.setCellFactory(param -> new ListCell<>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Project) item).getName());
                        }
                    });
                    cbEntity.setButtonCell(new ListCell<>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Project) item).getName());
                        }
                    });
                }
            } catch (SQLException e) {
                showError("Erreur lors du chargement des données: " + e.getMessage());
            }
        });
    }

    public void setPaymentGroup(PaymentGroup paymentGroup) {
        this.paymentGroup = paymentGroup;

        if (paymentGroup.getId() != null) {
            // Edit mode - populate fields
            cbGroup.getItems().stream()
                .filter(g -> g.getId().equals(paymentGroup.getGroupId()))
                .findFirst()
                .ifPresent(cbGroup::setValue);

            cbEntityType.setValue(getEntityTypeLabel(paymentGroup.getEntityType()));

            javafx.application.Platform.runLater(() -> {
                try {
                    if ("EVENT".equals(paymentGroup.getEntityType())) {
                        Event event = eventService.getEventById(paymentGroup.getEntityId());
                        for (Object item : cbEntity.getItems()) {
                            if (item instanceof Event && ((Event) item).getId().equals(event.getId())) {
                                cbEntity.setValue(item);
                                break;
                            }
                        }
                    } else if ("PROJECT".equals(paymentGroup.getEntityType())) {
                        Project project = projectService.getProjectById(paymentGroup.getEntityId());
                        for (Object item : cbEntity.getItems()) {
                            if (item instanceof Project && ((Project) item).getId().equals(project.getId())) {
                                cbEntity.setValue(item);
                                break;
                            }
                        }
                    }
                } catch (SQLException e) {
                    showError("Erreur lors du chargement de l'entité: " + e.getMessage());
                }
            });

            txtAmount.setText(String.valueOf(paymentGroup.getAmount()));
        }
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        try {
            Group selectedGroup = cbGroup.getValue();
            String entityTypeFr = cbEntityType.getValue();
            Object selectedEntity = cbEntity.getValue();
            double amount = Double.parseDouble(txtAmount.getText().trim());

            // Convert labels to codes
            String entityType = getEntityTypeCode(entityTypeFr);

            int entityId = 0;
            if ("EVENT".equals(entityType)) {
                entityId = ((Event) selectedEntity).getId();
            } else if ("PROJECT".equals(entityType)) {
                entityId = ((Project) selectedEntity).getId();
            }

            if (paymentGroup == null) {
                paymentGroup = new PaymentGroup();
            }

            paymentGroup.setGroupId(selectedGroup.getId());
            paymentGroup.setEntityType(entityType);
            paymentGroup.setEntityId(entityId);
            paymentGroup.setAmount(amount);

            saved = true;
            closeDialog();
        } catch (Exception e) {
            showError("Erreur lors de l'enregistrement: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeDialog();
    }

    private boolean validateInput() {
        if (cbGroup.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un groupe.");
            return false;
        }

        if (cbEntityType.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un type (Événement ou Projet).");
            return false;
        }

        if (cbEntity.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un événement ou projet.");
            return false;
        }

        if (txtAmount.getText() == null || txtAmount.getText().trim().isEmpty()) {
            showWarning("Validation", "Veuillez saisir un montant.");
            return false;
        }

        try {
            double amount = Double.parseDouble(txtAmount.getText().trim());
            if (amount <= 0) {
                showWarning("Validation", "Le montant par membre doit être positif.");
                return false;
            }
        } catch (NumberFormatException e) {
            showWarning("Validation", "Le montant doit être un nombre valide.");
            return false;
        }

        return true;
    }

    public boolean isSaved() {
        return saved;
    }

    public PaymentGroup getPaymentGroup() {
        return paymentGroup;
    }

    private String getEntityTypeCode(String frenchLabel) {
        return switch (frenchLabel) {
            case "Événement" -> "EVENT";
            case "Projet" -> "PROJECT";
            default -> frenchLabel;
        };
    }

    private String getEntityTypeLabel(String code) {
        return switch (code) {
            case "EVENT" -> "Événement";
            case "PROJECT" -> "Projet";
            default -> code;
        };
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
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

    private void closeDialog() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) cbGroup.getScene().getWindow();
    }
}
