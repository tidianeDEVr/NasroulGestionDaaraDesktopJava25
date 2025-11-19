package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Member;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ContributionController {

    @FXML
    private TableView<Contribution> contributionTable;

    @FXML
    private TableColumn<Contribution, String> colMember;

    @FXML
    private TableColumn<Contribution, String> colEntityType;

    @FXML
    private TableColumn<Contribution, String> colEntityName;

    @FXML
    private TableColumn<Contribution, String> colAmount;

    @FXML
    private TableColumn<Contribution, String> colDate;

    @FXML
    private TableColumn<Contribution, String> colStatus;

    @FXML
    private TableColumn<Contribution, String> colPaymentMethod;

    private final ContributionService contributionService;
    private final EventService eventService;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final NumberFormat formatter;
    private final ObservableList<Contribution> contributionList;

    public ContributionController() {
        this.contributionService = new ContributionService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.memberService = new MemberService();
        this.formatter = NumberFormat.getInstance(Locale.FRANCE);
        this.contributionList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        contributionTable.setItems(contributionList);

        try {
            loadContributions();
        } catch (SQLException e) {
            System.err.println("Error loading contributions: " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        colMember.setCellValueFactory(data -> {
            try {
                Member member = memberService.getMemberById(data.getValue().getMemberId());
                return new SimpleStringProperty(member != null ? member.getFullName() : "Inconnu");
            } catch (SQLException e) {
                return new SimpleStringProperty("Erreur");
            }
        });

        colEntityType.setCellValueFactory(data ->
            new SimpleStringProperty(getEntityTypeLabel(data.getValue().getEntityType())));

        colEntityName.setCellValueFactory(data -> {
            try {
                String entityType = data.getValue().getEntityType();
                int entityId = data.getValue().getEntityId();
                if ("EVENT".equals(entityType)) {
                    Event event = eventService.getEventById(entityId);
                    return new SimpleStringProperty(event != null ? event.getName() : "Inconnu");
                } else if ("PROJECT".equals(entityType)) {
                    var project = projectService.getProjectById(entityId);
                    return new SimpleStringProperty(project != null ? project.getName() : "Inconnu");
                }
                return new SimpleStringProperty("N/A");
            } catch (SQLException e) {
                return new SimpleStringProperty("Erreur");
            }
        });

        colAmount.setCellValueFactory(data ->
            new SimpleStringProperty(formatter.format(data.getValue().getAmount()) + " CFA"));

        colDate.setCellValueFactory(data -> {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return new SimpleStringProperty(data.getValue().getDate().format(dateFormatter));
        });

        colStatus.setCellValueFactory(data ->
            new SimpleStringProperty(getStatusLabel(data.getValue().getStatus())));

        colPaymentMethod.setCellValueFactory(data ->
            new SimpleStringProperty(getPaymentMethodLabel(data.getValue().getPaymentMethod())));
    }

    private String getEntityTypeLabel(String code) {
        if (code == null) return "N/A";
        return switch (code) {
            case "EVENT" -> "Événement";
            case "PROJECT" -> "Projet";
            default -> code;
        };
    }

    private String getStatusLabel(String code) {
        if (code == null) return "N/A";
        return switch (code) {
            case "PAID" -> "Payé";
            case "PENDING" -> "En attente";
            default -> code;
        };
    }

    private String getPaymentMethodLabel(String code) {
        if (code == null) return "N/A";
        return switch (code) {
            case "CASH" -> "Espèces";
            case "MOBILE_MONEY" -> "Mobile Money";
            case "BANK_TRANSFER" -> "Virement bancaire";
            case "CHECK" -> "Chèque";
            default -> code;
        };
    }

    private void loadContributions() throws SQLException {
        contributionList.clear();
        contributionList.addAll(contributionService.getAllContributions());
    }

    @FXML
    private void handleAddContribution() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContributionDialog.fxml"));
            Parent root = loader.load();

            ContributionDialogController controller = loader.getController();
            controller.setDialogStage(new javafx.stage.Stage());
            controller.getDialogStage().setTitle("Nouvelle Cotisation");
            controller.getDialogStage().initModality(javafx.stage.Modality.APPLICATION_MODAL);
            controller.getDialogStage().setScene(new javafx.scene.Scene(root));
            controller.getDialogStage().showAndWait();

            if (controller.isConfirmed()) {
                loadContributions();
            }
        } catch (Exception e) {
            showError("Erreur lors de l'ouverture du dialogue", e.getMessage());
        }
    }

    @FXML
    private void handleEditContribution() {
        Contribution selected = contributionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner une cotisation à modifier.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContributionDialog.fxml"));
            Parent root = loader.load();

            ContributionDialogController controller = loader.getController();
            controller.setDialogStage(new javafx.stage.Stage());
            controller.setContribution(selected);
            controller.getDialogStage().setTitle("Modifier Cotisation");
            controller.getDialogStage().initModality(javafx.stage.Modality.APPLICATION_MODAL);
            controller.getDialogStage().setScene(new javafx.scene.Scene(root));
            controller.getDialogStage().showAndWait();

            if (controller.isConfirmed()) {
                loadContributions();
            }
        } catch (Exception e) {
            showError("Erreur lors de l'ouverture du dialogue", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteContribution() {
        Contribution selected = contributionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner une cotisation à supprimer.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmer la suppression");
        alert.setHeaderText("Supprimer cette cotisation ?");
        alert.setContentText("Cette action est irréversible.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    contributionService.deleteContribution(selected.getId());
                    loadContributions();
                    showInfo("Succès", "Cotisation supprimée avec succès.");
                } catch (SQLException e) {
                    showError("Erreur lors de la suppression", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        try {
            loadContributions();
        } catch (SQLException e) {
            showError("Erreur lors du rafraîchissement", e.getMessage());
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
