package com.nasroul.controller;

import com.nasroul.model.Group;
import com.nasroul.service.GroupService;
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

public class GroupController {

    @FXML private TableView<Group> groupTable;
    @FXML private TableColumn<Group, String> colId;
    @FXML private TableColumn<Group, String> colName;
    @FXML private TableColumn<Group, String> colDescription;
    @FXML private TableColumn<Group, String> colMemberCount;
    @FXML private TableColumn<Group, String> colActive;

    private final GroupService groupService;
    private final ObservableList<Group> groupList;

    public GroupController() {
        this.groupService = new GroupService();
        this.groupList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        groupTable.setItems(groupList);
        loadGroups();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        colMemberCount.setCellValueFactory(data -> {
            try {
                int count = groupService.getMemberCount(data.getValue().getId());
                return new SimpleStringProperty(String.valueOf(count));
            } catch (SQLException e) {
                return new SimpleStringProperty("0");
            }
        });
        colActive.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isActive() ? "Oui" : "Non"));
    }

    private void loadGroups() {
        try {
            groupList.clear();
            groupList.addAll(groupService.getAllGroups());
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les groupes: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showGroupDialog(null);
    }

    @FXML
    private void handleEdit() {
        Group selected = groupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un groupe à modifier");
            return;
        }
        showGroupDialog(selected);
    }

    private void showGroupDialog(Group group) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GroupDialog.fxml"));
            Scene scene = new Scene(loader.load());

            GroupDialogController controller = loader.getController();
            controller.setGroup(group != null ? group : new Group());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(group != null ? "Modifier le groupe" : "Nouveau groupe");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(groupTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Group savedGroup = controller.getGroup();
                    if (savedGroup.getId() == null) {
                        groupService.createGroup(savedGroup);
                        showInfo("Succès", "Groupe créé avec succès");
                    } else {
                        groupService.updateGroup(savedGroup);
                        showInfo("Succès", "Groupe modifié avec succès");
                    }
                    loadGroups();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder le groupe: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        Group selected = groupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un groupe à supprimer");
            return;
        }

        try {
            int memberCount = groupService.getMemberCount(selected.getId());
            if (memberCount > 0) {
                showWarning("Suppression impossible",
                    "Ce groupe contient " + memberCount + " membre(s). Veuillez d'abord réaffecter les membres.");
                return;
            }
        } catch (SQLException e) {
            showError("Erreur", "Impossible de vérifier les membres: " + e.getMessage());
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText("Supprimer le groupe: " + selected.getName());
        confirmation.setContentText("Êtes-vous sûr?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    groupService.deleteGroup(selected.getId());
                    loadGroups();
                    showInfo("Succès", "Groupe supprimé avec succès");
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de supprimer le groupe: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadGroups();
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
