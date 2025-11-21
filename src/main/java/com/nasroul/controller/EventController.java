package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Expense;
import com.nasroul.service.EventService;
import com.nasroul.service.ExpenseService;
import com.nasroul.service.ContributionService;
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
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class EventController {

    @FXML
    private TableView<Event> eventTable;
    @FXML
    private TableColumn<Event, String> colId, colName, colStartDate, colLocation, colStatus, colTargetBudget, colCurrentBudget;

    private final EventService eventService;
    private final ExpenseService expenseService;
    private final ContributionService contributionService;
    private final ObservableList<Event> eventList;

    public EventController() {
        this.eventService = new EventService();
        this.expenseService = new ExpenseService();
        this.contributionService = new ContributionService();
        this.eventList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colStartDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartDate().format(formatter)));
        colLocation.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLocation()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(translateStatus(data.getValue().getStatus())));
        colTargetBudget.setCellValueFactory(data ->
            new SimpleStringProperty(numberFormat.format(data.getValue().getContributionTarget()) + " CFA"));
        colCurrentBudget.setCellValueFactory(data -> {
            try {
                Double total = contributionService.getTotalByEntity("EVENT", data.getValue().getId());
                double currentBudget = total != null ? total : 0.0;
                return new SimpleStringProperty(numberFormat.format(currentBudget) + " CFA");
            } catch (SQLException e) {
                return new SimpleStringProperty("0 CFA");
            }
        });

        eventTable.setItems(eventList);
        loadEvents();
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "PLANNED": return "Planifié";
            case "ONGOING": return "En cours";
            case "COMPLETED": return "Terminé";
            case "CANCELLED": return "Annulé";
            default: return status;
        }
    }

    private void loadEvents() {
        try {
            eventList.clear();
            eventList.addAll(eventService.getAllEvents());
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les événements: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showEventDialog(null);
    }

    @FXML
    private void handleEdit() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un événement à modifier");
            return;
        }
        showEventDialog(selected);
    }

    private void showEventDialog(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EventDialog.fxml"));
            Scene scene = new Scene(loader.load());

            EventDialogController controller = loader.getController();
            controller.setEvent(event != null ? event : new Event());

            Stage dialogStage = new Stage();
            dialogStage.setTitle(event != null ? "Modifier l'événement" : "Nouvel événement");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(eventTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Event savedEvent = controller.getEvent();
                    if (savedEvent.getId() == null) {
                        eventService.createEvent(savedEvent);
                        showInfo("Succès", "Événement créé avec succès");
                    } else {
                        eventService.updateEvent(savedEvent);
                        showInfo("Succès", "Événement modifié avec succès");
                    }
                    loadEvents();
                } catch (SQLException e) {
                    e.printStackTrace(); // Log full stack trace to console
                    showError("Erreur", "Impossible de sauvegarder l'événement: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDelete() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un événement à supprimer");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText("Supprimer l'événement: " + selected.getName());
        confirmation.setContentText("Êtes-vous sûr?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    eventService.deleteEvent(selected.getId());
                    loadEvents();
                    showInfo("Succès", "Événement supprimé avec succès");
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de supprimer l'événement: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadEvents();
    }

    @FXML
    private void handleSendSMS() {
        Event selectedEvent = eventTable.getSelectionModel().getSelectedItem();
        if (selectedEvent == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un événement pour envoyer des SMS");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SMSCampaignDialog.fxml"));
            Scene scene = new Scene(loader.load());

            SMSCampaignDialogController controller = loader.getController();
            controller.setEntity("EVENT", selectedEvent.getId(), selectedEvent.getName());

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Campagne SMS - " + selectedEvent.getName());
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(eventTable.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue SMS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewExpenses() {
        Event selectedEvent = eventTable.getSelectionModel().getSelectedItem();
        if (selectedEvent == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un événement pour voir les dépenses");
            return;
        }

        try {
            List<Expense> expenses = expenseService.getAllExpenses().stream()
                .filter(e -> "EVENT".equals(e.getEntityType()) && e.getEntityId().equals(selectedEvent.getId()))
                .collect(Collectors.toList());

            showExpensesDialog(selectedEvent.getName(), expenses);
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les dépenses: " + e.getMessage());
        }
    }

    private void showExpensesDialog(String eventName, List<Expense> expenses) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Dépenses - " + eventName);
        dialog.setHeaderText("Liste des dépenses pour l'événement : " + eventName);

        if (expenses.isEmpty()) {
            dialog.setContentText("Aucune dépense enregistrée pour cet événement.");
        } else {
            NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            double total = expenses.stream().mapToDouble(Expense::getAmount).sum();

            StringBuilder content = new StringBuilder();
            content.append(String.format("Total des dépenses : %s CFA\n\n", formatter.format(total)));
            content.append("Détails :\n");
            content.append("-".repeat(50)).append("\n");

            for (Expense expense : expenses) {
                content.append(String.format("• %s - %s CFA (%s)\n",
                    expense.getDescription(),
                    formatter.format(expense.getAmount()),
                    expense.getDate().format(dateFormatter)));
                if (expense.getCategory() != null) {
                    content.append(String.format("  Catégorie : %s\n", expense.getCategory()));
                }
            }

            dialog.setContentText(content.toString());
        }

        dialog.showAndWait();
    }

    @FXML
    private void handleImportExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer des événements depuis Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx"));

        File file = fileChooser.showOpenDialog(eventTable.getScene().getWindow());
        if (file != null) {
            try {
                List<Event> imported = ExcelUtil.importEvents(file);
                eventService.bulkCreate(imported);
                loadEvents();
                showInfo("Succès", imported.size() + " événements importés");
            } catch (Exception e) {
                showError("Erreur d'import", "Impossible d'importer les événements: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sauvegarder le modèle");
        fileChooser.setInitialFileName("modele_evenements.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx"));

        File file = fileChooser.showSaveDialog(eventTable.getScene().getWindow());
        if (file != null) {
            try {
                ExcelUtil.generateEventTemplate(file);
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
