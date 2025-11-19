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

public class PaymentGroupController {

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
        colGroup.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGroupName()));
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
            paymentGroupList.addAll(paymentGroupService.getAllPaymentGroups());
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les cotisations: " + e.getMessage());
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
            showWarning("Aucune sélection", "Veuillez sélectionner une cotisation à modifier");
            return;
        }
        showPaymentGroupDialog(selected);
    }

    private void showPaymentGroupDialog(PaymentGroup paymentGroup) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PaymentGroupDialog.fxml"));
            Scene scene = new Scene(loader.load());

            PaymentGroupDialogController controller = loader.getController();
            controller.setPaymentGroup(paymentGroup != null ? paymentGroup : new PaymentGroup());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(paymentGroup != null ? "Modifier la cotisation" : "Nouvelle cotisation");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(paymentGroupTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    PaymentGroup savedPaymentGroup = controller.getPaymentGroup();
                    if (savedPaymentGroup.getId() == null) {
                        paymentGroupService.createPaymentGroup(savedPaymentGroup);
                        showInfo("Succès", "Cotisation créée avec succès");
                    } else {
                        paymentGroupService.updatePaymentGroup(savedPaymentGroup);
                        showInfo("Succès", "Cotisation modifiée avec succès");
                    }
                    loadPaymentGroups();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder la cotisation: " + e.getMessage());
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
            showWarning("Aucune sélection", "Veuillez sélectionner une cotisation à supprimer");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Supprimer la cotisation du groupe \"" + selected.getGroupName() + "\" pour \"" + selected.getEntityName() + "\" ?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    paymentGroupService.deletePaymentGroup(selected.getId());
                    showInfo("Succès", "Cotisation supprimée avec succès");
                    loadPaymentGroups();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de supprimer la cotisation: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadPaymentGroups();
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
