package com.nasroul.controller;

import com.nasroul.model.PaymentGroup;
import com.nasroul.service.PaymentGroupService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

public class PaymentGroupController implements com.nasroul.ui.Refreshable {

    /** Filtre optionnel : la vue est réutilisée dans la fiche Collecte. */
    private String filterEntityType;
    private Integer filterEntityId;

    /** Restreint la vue aux objectifs d'un événement/projet (fiche Collecte). */
    public void setEntityFilter(String entityType, Integer entityId) {
        this.filterEntityType = entityType;
        this.filterEntityId = entityId;
        loadPaymentGroups();
    }

    @Override
    public void onShown() {
        loadPaymentGroups();
    }

    @FXML
    private TableView<PaymentGroup> paymentGroupTable;
    @FXML
    private TableColumn<PaymentGroup, String> colId, colGroup, colEntityType, colEntity, colAmount;

    private final PaymentGroupService paymentGroupService;
    private final ObservableList<PaymentGroup> paymentGroupList;

    public PaymentGroupController() {
        this.paymentGroupService = new PaymentGroupService();
        this.paymentGroupList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        paymentGroupTable.setItems(paymentGroupList);
        loadPaymentGroups();
    }

    private void setupTableColumns() {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colGroup.setCellValueFactory(data -> {
            PaymentGroup pg = data.getValue();
            String groupName = pg.getGroupName() != null ? pg.getGroupName() : "";
            String entityName = pg.getEntityName() != null ? pg.getEntityName() : "";
            if (!groupName.isEmpty() && !entityName.isEmpty()) {
                return new SimpleStringProperty(groupName + " - " + entityName);
            }
            return new SimpleStringProperty(groupName);
        });
        colEntityType.setCellValueFactory(data -> new SimpleStringProperty(translateEntityType(data.getValue().getEntityType())));
        colEntity.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEntityName()));
        colAmount.setCellValueFactory(data -> new SimpleStringProperty(numberFormat.format(data.getValue().getAmount()) + " CFA"));
    }

    private String translateEntityType(String entityType) {
        if (entityType == null) return "";
        return switch (entityType) {
            case "EVENT" -> "Événement";
            case "PROJECT" -> "Projet";
            default -> entityType;
        };
    }

    private void loadPaymentGroups() {
        try {
            paymentGroupList.clear();
            if (filterEntityType != null && filterEntityId != null) {
                paymentGroupList.addAll(paymentGroupService.getPaymentGroupsByEntity(filterEntityType, filterEntityId));
            } else {
                paymentGroupList.addAll(paymentGroupService.getAllPaymentGroups());
            }
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les objectifs de cotisation: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showPaymentGroupDialog(null);
    }

    @FXML
    private void handleEdit() {
        PaymentGroup selected = paymentGroupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un objectif à modifier");
            return;
        }
        showPaymentGroupDialog(selected);
    }

    private void showPaymentGroupDialog(PaymentGroup paymentGroup) {
        try {
            com.nasroul.ui.Dialogs.Modal<PaymentGroupDialogController> modal =
                com.nasroul.ui.Dialogs.openModal("/fxml/PaymentGroupDialog.fxml",
                    paymentGroup != null ? "Modifier l'objectif" : "Nouvel objectif",
                    paymentGroupTable.getScene().getWindow());

            modal.controller().setPaymentGroup(paymentGroup != null ? paymentGroup : new PaymentGroup());
            // Ouvert depuis la fiche d'une collecte : type + collecte connus, masqués
            if (paymentGroup == null && filterEntityType != null && filterEntityId != null) {
                modal.controller().lockEntity(filterEntityType, filterEntityId);
            }
            modal.stage().setResizable(false);
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                try {
                    PaymentGroup savedPaymentGroup = modal.controller().getPaymentGroup();
                    if (savedPaymentGroup.getId() == null) {
                        paymentGroupService.createPaymentGroup(savedPaymentGroup);
                        showInfo("Succès", "Objectif créé avec succès");
                    } else {
                        paymentGroupService.updatePaymentGroup(savedPaymentGroup);
                        showInfo("Succès", "Objectif modifié avec succès");
                    }
                    loadPaymentGroups();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible d'enregistrer l'objectif: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    showError("Validation", e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        PaymentGroup selected = paymentGroupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un objectif à supprimer");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Supprimer l'objectif du groupe \"" + selected.getGroupName() + "\" pour \"" + selected.getEntityName() + "\" ?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    paymentGroupService.deletePaymentGroup(selected.getId());
                    showInfo("Succès", "Objectif supprimé avec succès");
                    loadPaymentGroups();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de supprimer l'objectif: " + e.getMessage());
                }
            }
        });
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
