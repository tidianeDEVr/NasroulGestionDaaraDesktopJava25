package com.nasroul.controller;

import com.nasroul.service.DeviceRegistrationService;
import com.nasroul.service.SyncService;
import com.nasroul.sync.SyncManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML
    private Label dateLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Button btnDashboard;

    @FXML
    private Button btnContributions;

    @FXML
    private Button btnMembers;

    @FXML
    private Button btnEvents;

    @FXML
    private Button btnProjects;

    @FXML
    private Button btnExpenses;

    @FXML
    private Button btnGroups;

    @FXML
    private Button btnSync;

    @FXML
    private Label syncStatusLabel;

    private Timeline clockTimeline;
    private final SyncService syncService = SyncService.getInstance();
    private final DeviceRegistrationService deviceService = DeviceRegistrationService.getInstance();

    @FXML
    public void initialize() {
        startClock();
        showDashboard();
        setupSyncService();
        registerDevice();
        updateSyncStatus();
    }

    /**
     * Setup sync service listener
     */
    private void setupSyncService() {
        syncService.setStatusListener(status -> {
            Platform.runLater(() -> updateSyncStatusUI(status));
        });
    }

    /**
     * Register current device
     */
    private void registerDevice() {
        try {
            deviceService.registerDevice();
            System.out.println("Device registered: " + deviceService.getCurrentDeviceId());
        } catch (Exception e) {
            System.err.println("Failed to register device: " + e.getMessage());
        }
    }

    private void startClock() {
        // Mettre à jour immédiatement
        updateDateTime();

        // Créer un Timeline qui s'exécute chaque seconde
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateDateTime()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    private void updateDateTime() {
        if (dateLabel != null) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy - HH:mm:ss", java.util.Locale.FRENCH);
            String formattedDate = now.format(formatter);
            String capitalizedDate = formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1);
            dateLabel.setText(capitalizedDate);
        }
    }

    private void setActiveButton(Button activeButton) {
        btnDashboard.getStyleClass().remove("menu-button-active");
        btnContributions.getStyleClass().remove("menu-button-active");
        btnMembers.getStyleClass().remove("menu-button-active");
        btnEvents.getStyleClass().remove("menu-button-active");
        btnProjects.getStyleClass().remove("menu-button-active");
        btnExpenses.getStyleClass().remove("menu-button-active");
        btnGroups.getStyleClass().remove("menu-button-active");

        if (!activeButton.getStyleClass().contains("menu-button-active")) {
            activeButton.getStyleClass().add("menu-button-active");
        }
    }

    @FXML
    private void showDashboard() {
        setActiveButton(btnDashboard);
        loadView("/fxml/DashboardView.fxml", "Tableau de bord");
    }

    @FXML
    private void showMembers() {
        setActiveButton(btnMembers);
        loadView("/fxml/MemberView.fxml", "Membres");
    }

    @FXML
    private void showEvents() {
        setActiveButton(btnEvents);
        loadView("/fxml/EventView.fxml", "Événements");
    }

    @FXML
    private void showProjects() {
        setActiveButton(btnProjects);
        loadView("/fxml/ProjectView.fxml", "Projets");
    }

    @FXML
    private void showExpenses() {
        setActiveButton(btnExpenses);
        loadView("/fxml/ExpenseView.fxml", "Dépenses");
    }

    @FXML
    private void showGroups() {
        setActiveButton(btnGroups);
        loadView("/fxml/GroupView.fxml", "Groupes");
    }

    @FXML
    private void showContributions() {
        setActiveButton(btnContributions);
        loadView("/fxml/ContributionView.fxml", "Cotisations");
    }

    @FXML
    private void handleSync() {
        if (syncService.isSyncing()) {
            showAlert("Synchronisation en cours", "Une synchronisation est déjà en cours...", Alert.AlertType.WARNING);
            return;
        }

        // Disable sync button during sync
        if (btnSync != null) {
            btnSync.setDisable(true);
        }

        // Perform sync asynchronously
        Task<SyncManager.SyncResult> syncTask = syncService.synchronizeAsync();

        syncTask.setOnSucceeded(event -> {
            SyncManager.SyncResult result = syncTask.getValue();

            if (btnSync != null) {
                btnSync.setDisable(false);
            }

            if (result.isSuccess()) {
                showSyncResultDialog(result);
                updateSyncStatus();

                // Update device last sync time
                try {
                    deviceService.updateLastSyncTime();
                } catch (Exception e) {
                    System.err.println("Failed to update device sync time: " + e.getMessage());
                }
            } else {
                showAlert("Échec de la Synchronisation",
                         result.getErrorMessage() != null ? result.getErrorMessage() : "Erreur inconnue",
                         Alert.AlertType.ERROR);
            }
        });

        syncTask.setOnFailed(event -> {
            if (btnSync != null) {
                btnSync.setDisable(false);
            }

            Throwable exception = syncTask.getException();
            showAlert("Erreur de Synchronisation",
                     exception != null ? exception.getMessage() : "Une erreur est survenue",
                     Alert.AlertType.ERROR);
        });
    }

    /**
     * Update sync status display
     */
    private void updateSyncStatus() {
        if (syncStatusLabel != null) {
            String lastSync = syncService.getLastSyncTimeFormatted();
            syncStatusLabel.setText("Dernier sync: " + lastSync);
        }
    }

    /**
     * Update sync status UI based on sync status
     */
    private void updateSyncStatusUI(SyncService.SyncStatus status) {
        if (syncStatusLabel == null) return;

        switch (status) {
            case SYNCING:
                syncStatusLabel.setText("🔄 Synchronisation en cours...");
                syncStatusLabel.setStyle("-fx-text-fill: #2196f3;");
                break;
            case SUCCESS:
                syncStatusLabel.setText("✅ Synchronisation réussie - " + syncService.getLastSyncTimeFormatted());
                syncStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                break;
            case FAILED:
                syncStatusLabel.setText("❌ Échec de la synchronisation");
                syncStatusLabel.setStyle("-fx-text-fill: #f44336;");
                break;
            case OFFLINE:
                syncStatusLabel.setText("📴 Mode hors ligne");
                syncStatusLabel.setStyle("-fx-text-fill: #ff9800;");
                break;
            default:
                syncStatusLabel.setText("Dernier sync: " + syncService.getLastSyncTimeFormatted());
                syncStatusLabel.setStyle("-fx-text-fill: #666;");
        }
    }

    /**
     * Show sync result dialog
     */
    private void showSyncResultDialog(SyncManager.SyncResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Synchronisation Réussie");
        alert.setHeaderText("La synchronisation s'est terminée avec succès");

        String content = String.format(
            "📥 Records téléchargés (PULL): %d\n" +
            "📤 Records envoyés (PUSH): %d\n" +
            "⚠️ Conflits détectés: %d\n\n" +
            "Session: %s",
            result.getRecordsPulled(),
            result.getRecordsPushed(),
            result.getConflicts(),
            result.getSyncSessionId()
        );

        if (!result.getErrors().isEmpty()) {
            content += "\n\n❌ Erreurs:\n";
            for (String error : result.getErrors()) {
                content += "• " + error + "\n";
            }
        }

        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show alert dialog
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleExit() {
        // Arrêter le timer avant de quitter
        if (clockTimeline != null) {
            clockTimeline.stop();
        }

        // Shutdown sync service
        syncService.shutdown();

        Platform.exit();
    }

    private void loadView(String fxmlPath, String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(view);
            statusLabel.setText("Vue active : " + viewName);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view: " + viewName);
        }
    }
}
