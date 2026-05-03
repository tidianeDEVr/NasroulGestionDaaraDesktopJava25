package com.nasroul.controller;

import com.nasroul.service.DeviceRegistrationService;
import com.nasroul.service.SyncService;
import com.nasroul.sync.SyncManager;
import com.nasroul.util.ConfigManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
    private HBox syncButtonContainer;

    @FXML
    private Button btnSyncHistory;

    @FXML
    private Label lblSystemSection;

    @FXML
    private Label syncStatusLabel;

    @FXML
    private ProgressIndicator syncProgressIndicator;

    private Timeline clockTimeline;
    private final SyncService syncService = SyncService.getInstance();
    private final DeviceRegistrationService deviceService = DeviceRegistrationService.getInstance();
    private final boolean offlineMode = ConfigManager.getInstance().isOfflineModeEnabled();

    @FXML
    public void initialize() {
        startClock();
        showDashboard();

        if (offlineMode) {
            hideSyncUI();
        } else {
            setupSyncService();
            registerDevice();
            updateSyncStatus();
        }
    }

    private void hideSyncUI() {
        if (syncButtonContainer != null) {
            syncButtonContainer.setVisible(false);
            syncButtonContainer.setManaged(false);
        }
        if (btnSyncHistory != null) {
            btnSyncHistory.setVisible(false);
            btnSyncHistory.setManaged(false);
        }
        if (lblSystemSection != null) {
            lblSystemSection.setVisible(false);
            lblSystemSection.setManaged(false);
        }
        if (syncStatusLabel != null) {
            syncStatusLabel.setVisible(false);
            syncStatusLabel.setManaged(false);
        }
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
        btnSyncHistory.getStyleClass().remove("menu-button-active");

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
        if (offlineMode) {
            return;
        }
        if (syncService.isSyncing()) {
            showAlert("Synchronisation en cours", "Une synchronisation est déjà en cours...", Alert.AlertType.WARNING);
            return;
        }

        // Start sync UI feedback
        startSyncUI();

        // Perform sync asynchronously
        Task<SyncManager.SyncResult> syncTask = syncService.synchronizeAsync();

        syncTask.setOnSucceeded(event -> {
            SyncManager.SyncResult result = syncTask.getValue();

            // Stop sync UI feedback
            stopSyncUI();

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
            // Stop sync UI feedback
            stopSyncUI();

            Throwable exception = syncTask.getException();
            showAlert("Erreur de Synchronisation",
                     exception != null ? exception.getMessage() : "Une erreur est survenue",
                     Alert.AlertType.ERROR);
        });
    }

    /**
     * Start sync UI feedback - disable button, show spinner
     */
    private void startSyncUI() {
        Platform.runLater(() -> {
            if (btnSync != null) {
                btnSync.setDisable(true);
                // Keep button text as "Synchroniser" - status is shown in footer
            }
            if (syncProgressIndicator != null) {
                syncProgressIndicator.setVisible(true);
                syncProgressIndicator.setManaged(true);
            }
            // Note: syncStatusLabel in footer shows "🔄 Synchronisation en cours..."
        });
    }

    /**
     * Stop sync UI feedback - enable button, hide spinner
     */
    private void stopSyncUI() {
        Platform.runLater(() -> {
            if (btnSync != null) {
                btnSync.setDisable(false);
                // Button text stays "Synchroniser"
            }
            if (syncProgressIndicator != null) {
                syncProgressIndicator.setVisible(false);
                syncProgressIndicator.setManaged(false);
            }
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
     * Show sync result dialog with detailed information
     */
    private void showSyncResultDialog(SyncManager.SyncResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Synchronisation Réussie");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(700);

        // Determine if there are any issues to report
        boolean hasConflicts = result.getConflicts() > 0;
        boolean hasErrors = !result.getErrors().isEmpty();

        if (hasConflicts || hasErrors) {
            alert.setHeaderText("La synchronisation s'est terminée avec quelques avertissements");
        } else {
            alert.setHeaderText("La synchronisation s'est terminée avec succès");
        }

        // Build detailed content
        StringBuilder content = new StringBuilder();

        // Summary section
        content.append("═══════════════════════════════════════════════════════════\n");
        content.append("                  RÉSUMÉ DE LA SYNCHRONISATION\n");
        content.append("═══════════════════════════════════════════════════════════\n\n");

        // Downloaded data statistics
        content.append("📥 DONNÉES REÇUES DU SERVEUR\n");
        content.append("───────────────────────────────────────\n");

        Map<String, Integer> pullByTable = result.getPullByTable();
        if (pullByTable.isEmpty() || result.getRecordsPulled() == 0) {
            content.append("   ✓ Vos données sont à jour\n");
        } else {
            pullByTable.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String tableName = formatTableName(entry.getKey());
                    content.append(String.format("   ├─ %s: %d donnée%s\n",
                        tableName, entry.getValue(), entry.getValue() > 1 ? "s" : ""));
                });
            content.append(String.format("   └─ TOTAL: %d donnée%s reçue%s\n",
                result.getRecordsPulled(),
                result.getRecordsPulled() > 1 ? "s" : "",
                result.getRecordsPulled() > 1 ? "s" : ""));
        }
        content.append("\n");

        // Uploaded data statistics
        content.append("📤 DONNÉES ENVOYÉES AU SERVEUR\n");
        content.append("───────────────────────────────────────\n");

        Map<String, Integer> pushByTable = result.getPushByTable();
        if (pushByTable.isEmpty() || result.getRecordsPushed() == 0) {
            content.append("   ✓ Aucune modification à envoyer\n");
        } else {
            pushByTable.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String tableName = formatTableName(entry.getKey());
                    content.append(String.format("   ├─ %s: %d donnée%s\n",
                        tableName, entry.getValue(), entry.getValue() > 1 ? "s" : ""));
                });
            content.append(String.format("   └─ TOTAL: %d donnée%s envoyée%s\n",
                result.getRecordsPushed(),
                result.getRecordsPushed() > 1 ? "s" : "",
                result.getRecordsPushed() > 1 ? "s" : ""));
        }
        content.append("\n");

        // Total processed
        int totalProcessed = result.getRecordsPulled() + result.getRecordsPushed();
        content.append("📊 STATISTIQUES GLOBALES\n");
        content.append("───────────────────────────────────────\n");
        content.append(String.format("   • Total traité: %d record%s\n",
            totalProcessed, totalProcessed > 1 ? "s" : ""));

        // Conflicts section with table details
        if (hasConflicts) {
            Map<String, Integer> conflictsByTable = result.getConflictsByTable();
            content.append(String.format("   • Conflits: %d (résolus automatiquement)\n", result.getConflicts()));

            if (!conflictsByTable.isEmpty()) {
                conflictsByTable.forEach((table, count) -> {
                    String tableName = formatTableName(table);
                    content.append(String.format("     - %s: %d conflit%s\n",
                        tableName, count, count > 1 ? "s" : ""));
                });
            }
        } else {
            content.append("   • Conflits: 0 ✅\n");
        }
        content.append("\n");

        // Session info
        content.append("ℹ️ INFORMATIONS DE SESSION\n");
        content.append("───────────────────────────────────────\n");
        content.append(String.format("   • Session ID: %s\n", result.getSyncSessionId()));
        content.append(String.format("   • Heure: %s\n",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))));
        content.append(String.format("   • Appareil: %s\n", syncService.getDeviceId()));

        // Errors section (if any)
        if (hasErrors) {
            content.append("\n❌ ERREURS RENCONTRÉES\n");
            content.append("───────────────────────────────────────\n");
            for (String error : result.getErrors()) {
                content.append(String.format("   • %s\n", error));
            }
        }

        alert.setContentText(content.toString());
        alert.showAndWait();

        // Update status bar with summary
        if (statusLabel != null) {
            statusLabel.setText(String.format("✅ Sync terminée: %d reçu%s, %d envoyé%s, %d conflit%s",
                result.getRecordsPulled(), result.getRecordsPulled() > 1 ? "s" : "",
                result.getRecordsPushed(), result.getRecordsPushed() > 1 ? "s" : "",
                result.getConflicts(), result.getConflicts() > 1 ? "s" : ""));
        }
    }

    /**
     * Format table name for display
     */
    private String formatTableName(String tableName) {
        Map<String, String> tableLabels = Map.of(
            "groups", "Groupes",
            "members", "Membres",
            "events", "Événements",
            "projects", "Projets",
            "expenses", "Dépenses",
            "contributions", "Cotisations",
            "payment_groups", "Groupes de Paiement"
        );
        return tableLabels.getOrDefault(tableName, tableName);
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
    private void showSyncHistory() {
        if (offlineMode) {
            return;
        }
        setActiveButton(btnSyncHistory);
        loadView("/fxml/SyncHistoryView.fxml", "Historique de Synchronisation");
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
