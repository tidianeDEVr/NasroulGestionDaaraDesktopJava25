package com.nasroul.controller;

import com.nasroul.model.Member;
import com.nasroul.service.MemberService;
import com.nasroul.util.ExcelUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MemberController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Member> memberTable;

    @FXML
    private TableColumn<Member, String> colId;

    @FXML
    private TableColumn<Member, String> colFirstName;

    @FXML
    private TableColumn<Member, String> colLastName;

    @FXML
    private TableColumn<Member, String> colEmail;

    @FXML
    private TableColumn<Member, String> colPhone;

    @FXML
    private TableColumn<Member, String> colRole;

    @FXML
    private TableColumn<Member, String> colJoinDate;

    @FXML
    private TableColumn<Member, String> colActive;

    private final MemberService memberService;
    private final ObservableList<Member> memberList;

    public MemberController() {
        this.memberService = new MemberService();
        this.memberList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        memberTable.setItems(memberList);
        loadMembers();

        searchField.textProperty().addListener((obs, old, newVal) -> filterMembers(newVal));
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colFirstName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFirstName()));
        colLastName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLastName()));
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEmail()));
        colPhone.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPhone()));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole()));
        colJoinDate.setCellValueFactory(data -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return new SimpleStringProperty(data.getValue().getJoinDate().format(formatter));
        });
        colActive.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isActive() ? "Yes" : "No"));
    }

    private void loadMembers() {
        try {
            memberList.clear();
            memberList.addAll(memberService.getAllMembers());
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les membres: " + e.getMessage());
        }
    }

    private void filterMembers(String searchText) {
        try {
            List<Member> allMembers = memberService.getAllMembers();
            if (searchText == null || searchText.trim().isEmpty()) {
                memberList.setAll(allMembers);
            } else {
                String search = searchText.toLowerCase();
                List<Member> filtered = allMembers.stream()
                    .filter(m -> m.getFirstName().toLowerCase().contains(search) ||
                                m.getLastName().toLowerCase().contains(search) ||
                                (m.getEmail() != null && m.getEmail().toLowerCase().contains(search)))
                    .toList();
                memberList.setAll(filtered);
            }
        } catch (SQLException e) {
            showError("Erreur", "Impossible de filtrer les membres: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showMemberDialog(null);
    }

    @FXML
    private void handleDetails() {
        Member selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un membre pour voir les détails");
            return;
        }
        showMemberDetailsDialog(selected);
    }

    @FXML
    private void handleEdit() {
        Member selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un membre à modifier");
            return;
        }
        showMemberDialog(selected);
    }

    private void showMemberDetailsDialog(Member member) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MemberDetailsDialog.fxml"));
            Scene scene = new Scene(loader.load());

            MemberDetailsDialogController controller = loader.getController();
            controller.setMember(member);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Détails du membre - " + member.getFullName());
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(memberTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(true);

            dialogStage.showAndWait();
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showMemberDialog(Member member) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MemberDialog.fxml"));
            Scene scene = new Scene(loader.load());

            MemberDialogController controller = loader.getController();
            controller.setMember(member != null ? member : new Member());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(member != null ? "Modifier le membre" : "Nouveau membre");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(memberTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Member savedMember = controller.getMember();
                    if (savedMember.getId() == null) {
                        memberService.createMember(savedMember);
                        showInfo("Succès", "Membre créé avec succès");
                    } else {
                        memberService.updateMember(savedMember);
                        showInfo("Succès", "Membre modifié avec succès");
                    }
                    loadMembers();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder le membre: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        Member selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un membre à supprimer");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText("Supprimer le membre: " + selected.getFullName());
        confirmation.setContentText("Êtes-vous sûr?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    memberService.deleteMember(selected.getId());
                    loadMembers();
                    showInfo("Succès", "Membre supprimé avec succès");
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de supprimer le membre: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        loadMembers();
    }

    @FXML
    private void handleImportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer des membres depuis Excel");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx")
        );

        File file = fileChooser.showOpenDialog(memberTable.getScene().getWindow());
        if (file != null) {
            try {
                List<Member> importedMembers = ExcelUtil.importMembers(file);
                memberService.bulkCreate(importedMembers);
                loadMembers();
                showInfo("Succès", importedMembers.size() + " membres importés");
            } catch (Exception e) {
                showError("Erreur d'import", "Impossible d'importer les membres: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sauvegarder le modèle");
        fileChooser.setInitialFileName("modele_membres.xlsx");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(memberTable.getScene().getWindow());
        if (file != null) {
            try {
                ExcelUtil.generateMemberTemplate(file);
                showInfo("Succès", "Modèle exporté avec succès");
            } catch (Exception e) {
                showError("Erreur d'export", "Impossible d'exporter le modèle: " + e.getMessage());
            }
        }
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
