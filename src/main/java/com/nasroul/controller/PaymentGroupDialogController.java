package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Group;
import com.nasroul.model.PaymentGroup;
import com.nasroul.model.Project;
import com.nasroul.service.EventService;
import com.nasroul.service.GroupService;
import com.nasroul.service.ProjectService;
import com.nasroul.ui.Forms;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

public class PaymentGroupDialogController {

    @FXML private ComboBox<Group> cbGroup;
    @FXML private Label lblEntityType;
    @FXML private ComboBox<String> cbEntityType;
    @FXML private Label lblEntity;
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
            if (selectedType != null) {
                loadEntities(getEntityTypeCode(selectedType));
            } else {
                cbEntity.getItems().clear();
            }
        });

        // Affichage du nom, quel que soit le type (Event ou Project)
        cbEntity.setCellFactory(param -> entityCell());
        cbEntity.setButtonCell(entityCell());
    }

    private ListCell<Object> entityCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : entityName(item));
            }
        };
    }

    private String entityName(Object item) {
        if (item instanceof Event event) {
            return event.getName();
        }
        if (item instanceof Project project) {
            return project.getName();
        }
        return String.valueOf(item);
    }

    private Integer entityId(Object item) {
        if (item instanceof Event event) {
            return event.getId();
        }
        if (item instanceof Project project) {
            return project.getId();
        }
        return null;
    }

    /** Remplit la liste des collectes du type donné (appel synchrone). */
    private void loadEntities(String typeCode) {
        cbEntity.getItems().clear();
        try {
            if ("EVENT".equals(typeCode)) {
                cbEntity.getItems().addAll(eventService.getAllEvents());
            } else if ("PROJECT".equals(typeCode)) {
                cbEntity.getItems().addAll(projectService.getAllProjects());
            }
        } catch (SQLException e) {
            showError("Erreur lors du chargement des données: " + e.getMessage());
        }
    }

    /** Sélectionne la collecte correspondante dans la liste déjà chargée. */
    private void selectEntity(int entityId) {
        for (Object item : cbEntity.getItems()) {
            Integer itemId = entityId(item);
            if (itemId != null && itemId == entityId) {
                cbEntity.setValue(item);
                return;
            }
        }
    }

    /**
     * Ouvert depuis la fiche d'une collecte : le type et la collecte sont
     * connus, on les pré-remplit et on masque les deux champs.
     * Tout est fait de façon synchrone — ne pas dépendre de l'événement
     * onAction du combo Type, qui laissait la collecte non sélectionnée.
     */
    public void lockEntity(String entityType, int entityId) {
        cbEntityType.setValue(getEntityTypeLabel(entityType));
        loadEntities(entityType);
        selectEntity(entityId);

        for (javafx.scene.Node node : new javafx.scene.Node[]{lblEntityType, cbEntityType, lblEntity, cbEntity}) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }

    public void setPaymentGroup(PaymentGroup paymentGroup) {
        this.paymentGroup = paymentGroup;

        if (paymentGroup.getId() != null) {
            // Edit mode - populate fields
            cbGroup.getItems().stream()
                .filter(g -> g.getId().equals(paymentGroup.getGroupId()))
                .findFirst()
                .ifPresent(cbGroup::setValue);

            // Synchrone : même raison que dans lockEntity
            cbEntityType.setValue(getEntityTypeLabel(paymentGroup.getEntityType()));
            loadEntities(paymentGroup.getEntityType());
            selectEntity(paymentGroup.getEntityId());

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
            double amount = Double.parseDouble(Forms.text(txtAmount));

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
            // Champ masqué (ouvert depuis une fiche) : inutile de demander une
            // sélection que l'utilisateur ne peut pas faire
            showWarning("Validation", cbEntity.isVisible()
                ? "Veuillez sélectionner un événement ou projet."
                : "La collecte n'a pas pu être déterminée. Fermez la fiche et rouvrez-la.");
            return false;
        }

        if (txtAmount.getText() == null || Forms.text(txtAmount).isEmpty()) {
            showWarning("Validation", "Veuillez saisir un montant.");
            return false;
        }

        try {
            double amount = Double.parseDouble(Forms.text(txtAmount));
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
