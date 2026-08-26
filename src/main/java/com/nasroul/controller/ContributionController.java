package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.service.ContributionService;
import com.nasroul.ui.Forms;
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

public class ContributionController implements com.nasroul.ui.Refreshable {

    /** Filtre optionnel : la vue est réutilisée dans la fiche Collecte. */
    private String filterEntityType;
    private Integer filterEntityId;

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

    @FXML
    private javafx.scene.layout.VBox rootBox;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> cbTypeFilter;

    @FXML
    private ComboBox<String> cbStatusFilter;

    @FXML
    private Button btnEdit;

    @FXML
    private Button btnDelete;

    private javafx.collections.transformation.FilteredList<Contribution> filteredContributions;

    private final ContributionService contributionService;
    private final NumberFormat formatter;
    private final ObservableList<Contribution> contributionList;

    public ContributionController() {
        this.contributionService = new ContributionService();
        this.formatter = NumberFormat.getInstance(Locale.FRANCE);
        this.contributionList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();

        // Boutons actifs seulement quand une ligne est sélectionnée
        var noSelection = contributionTable.getSelectionModel().selectedItemProperty().isNull();
        btnEdit.disableProperty().bind(noSelection);
        btnDelete.disableProperty().bind(noSelection);

        // Double-clic = modifier
        contributionTable.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<Contribution>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditContribution();
                }
            });
            return row;
        });

        try {
            loadContributions();
        } catch (SQLException e) {
            System.err.println("Error loading contributions: " + e.getMessage());
        }
    }

    private void setupFilters() {
        filteredContributions = new javafx.collections.transformation.FilteredList<>(contributionList);
        contributionTable.setItems(filteredContributions);

        cbTypeFilter.getItems().setAll("Tous les types", "Événements", "Projets");
        cbTypeFilter.setValue("Tous les types");
        cbStatusFilter.getItems().setAll("Tous les statuts", "Payé", "En attente");
        cbStatusFilter.setValue("Tous les statuts");

        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        cbTypeFilter.setOnAction(e -> applyFilters());
        cbStatusFilter.setOnAction(e -> applyFilters());
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : Forms.text(searchField).toLowerCase();
        String type = cbTypeFilter.getValue();
        String status = cbStatusFilter.getValue();

        filteredContributions.setPredicate(c -> {
            if ("Événements".equals(type) && !"EVENT".equals(c.getEntityType())) return false;
            if ("Projets".equals(type) && !"PROJECT".equals(c.getEntityType())) return false;
            if ("Payé".equals(status) && !"PAID".equals(c.getStatus())) return false;
            if ("En attente".equals(status) && !"PENDING".equals(c.getStatus())) return false;
            if (!search.isEmpty()) {
                String haystack = ((c.getMemberName() != null ? c.getMemberName() : "") + " "
                    + (c.getEntityName() != null ? c.getEntityName() : "")).toLowerCase();
                return haystack.contains(search);
            }
            return true;
        });
    }

    private void setupTableColumns() {
        // Les noms sont déjà joints par le DAO : aucune requête par cellule
        colMember.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getMemberName() != null ? data.getValue().getMemberName() : "Inconnu"));

        colEntityType.setCellValueFactory(data ->
            new SimpleStringProperty(getEntityTypeLabel(data.getValue().getEntityType())));

        colEntityName.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getEntityName() != null ? data.getValue().getEntityName() : "Inconnu"));

        colAmount.setCellValueFactory(data ->
            new SimpleStringProperty(formatter.format(data.getValue().getAmount()) + " CFA"));

        colDate.setCellValueFactory(data -> {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return new SimpleStringProperty(data.getValue().getDate().format(dateFormatter));
        });

        colStatus.setCellValueFactory(data ->
            new SimpleStringProperty(getStatusLabel(data.getValue().getStatus())));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String label, boolean empty) {
                super.updateItem(label, empty);
                if (empty || label == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(CollecteController.badge(label,
                    "Payé".equals(label) ? "badge-success" : "badge-warning"));
            }
        });

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
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            case "BANK_TRANSFER" -> "Virement";
            default -> code;
        };
    }

    private void loadContributions() throws SQLException {
        contributionList.clear();
        if (filterEntityType != null && filterEntityId != null) {
            contributionList.addAll(contributionService.getContributionsByEntity(filterEntityType, filterEntityId));
        } else {
            contributionList.addAll(contributionService.getAllContributions());
        }
    }

    /** Restreint la vue aux cotisations d'un événement/projet (fiche Collecte). */
    public void setEntityFilter(String entityType, Integer entityId) {
        this.filterEntityType = entityType;
        this.filterEntityId = entityId;
        // Mode embarqué dans la fiche : padding réduit, filtres redondants masqués
        if (rootBox != null) {
            rootBox.getStyleClass().remove("container");
            rootBox.getStyleClass().add("embedded");
        }
        if (cbTypeFilter != null) {
            cbTypeFilter.setVisible(false);
            cbTypeFilter.setManaged(false);
        }
        onShown();
    }

    @Override
    public void onShown() {
        try {
            loadContributions();
        } catch (SQLException e) {
            System.err.println("Error loading contributions: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddContribution() {
        try {
            com.nasroul.ui.Dialogs.Modal<ContributionDialogController> modal =
                com.nasroul.ui.Dialogs.openModal("/fxml/ContributionDialog.fxml",
                    "Nouvelle cotisation", contributionTable.getScene().getWindow());
            modal.controller().setDialogStage(modal.stage());
            // Ouvert depuis la fiche d'une collecte : type + collecte connus, masqués
            if (filterEntityType != null && filterEntityId != null) {
                modal.controller().lockEntity(filterEntityType, filterEntityId);
            }
            modal.stage().showAndWait();

            if (modal.controller().isConfirmed()) {
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
            com.nasroul.ui.Dialogs.Modal<ContributionDialogController> modal =
                com.nasroul.ui.Dialogs.openModal("/fxml/ContributionDialog.fxml",
                    "Modifier la cotisation", contributionTable.getScene().getWindow());
            modal.controller().setDialogStage(modal.stage());
            modal.controller().setContribution(selected);
            // Dans la fiche d'une collecte, la cotisation lui appartient déjà :
            // inutile d'exposer le choix du type et de la collecte
            if (filterEntityType != null && filterEntityId != null) {
                modal.controller().lockEntity(filterEntityType, filterEntityId);
            }
            modal.stage().showAndWait();

            if (modal.controller().isConfirmed()) {
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
