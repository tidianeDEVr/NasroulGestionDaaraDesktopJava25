package com.nasroul.controller;

import com.nasroul.service.DeviceRegistrationService;
import com.nasroul.service.SyncService;
import com.nasroul.sync.SyncManager;
import com.nasroul.ui.Dialogs;
import com.nasroul.ui.Phosphor;
import com.nasroul.ui.PhosphorIcon;
import com.nasroul.ui.Refreshable;
import com.nasroul.ui.ThemeManager;
import com.nasroul.util.ConfigManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

public class MainController {

    /** Les 5 sections de l'application. */
    private enum Section {
        DASHBOARD("/fxml/DashboardView.fxml", "Tableau de bord"),
        COLLECTES("/fxml/CollecteView.fxml", "Collectes"),
        MEMBRES("/fxml/MembersGroupsView.fxml", "Membres & Groupes"),
        COTISATIONS("/fxml/ContributionView.fxml", "Cotisations"),
        DEPENSES("/fxml/ExpenseView.fxml", "Dépenses");

        final String fxml;
        final String title;

        Section(String fxml, String title) {
            this.fxml = fxml;
            this.title = title;
        }
    }

    private static final PseudoClass PC_SYNCING = PseudoClass.getPseudoClass("syncing");
    private static final PseudoClass PC_FAILED = PseudoClass.getPseudoClass("failed");
    private static final PseudoClass PC_OFFLINE = PseudoClass.getPseudoClass("offline");

    @FXML private Label pageTitle;
    @FXML private Label dateLabel;
    @FXML private StackPane contentPane;
    @FXML private ToggleButton navDashboard;
    @FXML private ToggleButton navCollectes;
    @FXML private ToggleButton navMembers;
    @FXML private ToggleButton navContributions;
    @FXML private ToggleButton navExpenses;
    @FXML private MenuButton syncMenu;
    @FXML private PhosphorIcon syncIcon;
    @FXML private MenuItem menuSyncNow;
    @FXML private ProgressIndicator syncProgressIndicator;

    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<Section, Parent> viewCache = new EnumMap<>(Section.class);
    private final Map<Section, Object> controllerCache = new EnumMap<>(Section.class);
    private Section currentSection;

    private Timeline clockTimeline;
    private final SyncService syncService = SyncService.getInstance();
    private final DeviceRegistrationService deviceService = DeviceRegistrationService.getInstance();
    /** Sync inutilisable : mode hors ligne OU sync.enabled=false dans la config. */
    private final boolean offlineMode = ConfigManager.getInstance().isOfflineModeEnabled()
            || !ConfigManager.getInstance().isSyncEnabled();

    @FXML
    public void initialize() {
        setupNavigation();
        startClock();
        show(Section.DASHBOARD);

        if (offlineMode) {
            // Pas de serveur : l'indicateur de sync disparaît complètement
            syncMenu.setVisible(false);
            syncMenu.setManaged(false);
        } else {
            syncService.setStatusListener(status ->
                Platform.runLater(() -> updateSyncStatusUI(status)));
            registerDevice();
            setSyncPillState(null, Phosphor.CLOUD_CHECK,
                "Dernier sync : " + syncService.getLastSyncTimeFormatted());
        }
    }

    private void setupNavigation() {
        navDashboard.setToggleGroup(navGroup);
        navCollectes.setToggleGroup(navGroup);
        navMembers.setToggleGroup(navGroup);
        navContributions.setToggleGroup(navGroup);
        navExpenses.setToggleGroup(navGroup);
        navDashboard.setSelected(true);

        // Un ToggleGroup autorise la désélection au re-clic : on l'interdit
        navGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    @FXML private void showDashboard() { show(Section.DASHBOARD); }
    @FXML private void showCollectes() { show(Section.COLLECTES); }
    @FXML private void showMembers() { show(Section.MEMBRES); }
    @FXML private void showContributions() { show(Section.COTISATIONS); }
    @FXML private void showExpenses() { show(Section.DEPENSES); }

    private void show(Section section) {
        if (section == currentSection && viewCache.containsKey(section)) {
            return;
        }
        try {
            Parent view = viewCache.get(section);
            if (view == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(section.fxml));
                view = loader.load();
                viewCache.put(section, view);
                controllerCache.put(section, loader.getController());
            }
            contentPane.getChildren().setAll(view);
            pageTitle.setText(section.title);
            currentSection = section;

            Object controller = controllerCache.get(section);
            if (controller instanceof Refreshable refreshable) {
                refreshable.onShown();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(contentPane.getScene() != null ? contentPane.getScene().getWindow() : null,
                "Erreur", "Impossible de charger la vue « " + section.title + " ».");
        }
    }

    private void registerDevice() {
        try {
            deviceService.registerDevice();
            System.out.println("Device registered: " + deviceService.getCurrentDeviceId());
        } catch (Exception e) {
            System.err.println("Failed to register device: " + e.getMessage());
        }
    }

    private void startClock() {
        updateDateTime();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(30), event -> updateDateTime()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    private void updateDateTime() {
        if (dateLabel != null) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy · HH:mm", java.util.Locale.FRENCH);
            String formattedDate = now.format(formatter);
            dateLabel.setText(formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1));
        }
    }

    // ---------------------------------------------------------------- Sync

    @FXML
    private void handleSync() {
        if (offlineMode) {
            return;
        }
        if (syncService.isSyncing()) {
            Dialogs.warn(window(), "Synchronisation en cours",
                "Une synchronisation est déjà en cours…");
            return;
        }

        menuSyncNow.setDisable(true);
        syncProgressIndicator.setVisible(true);
        syncProgressIndicator.setManaged(true);

        Task<SyncManager.SyncResult> syncTask = syncService.synchronizeAsync();

        syncTask.setOnSucceeded(event -> {
            SyncManager.SyncResult result = syncTask.getValue();
            stopSyncUI();
            if (result.isSuccess()) {
                showSyncResultDialog(result);
                setSyncPillState(null, Phosphor.CLOUD_CHECK,
                    "Synchronisé · " + syncService.getLastSyncTimeFormatted());
                try {
                    deviceService.updateLastSyncTime();
                } catch (Exception e) {
                    System.err.println("Failed to update device sync time: " + e.getMessage());
                }
            } else {
                setSyncPillState(PC_FAILED, Phosphor.CLOUD_SLASH, "Échec de la synchronisation");
                Dialogs.error(window(), "Échec de la synchronisation",
                    result.getErrorMessage() != null ? result.getErrorMessage() : "Erreur inconnue");
            }
        });

        syncTask.setOnFailed(event -> {
            stopSyncUI();
            setSyncPillState(PC_FAILED, Phosphor.CLOUD_SLASH, "Échec de la synchronisation");
            Throwable exception = syncTask.getException();
            Dialogs.error(window(), "Erreur de synchronisation",
                exception != null ? exception.getMessage() : "Une erreur est survenue");
        });
    }

    private void stopSyncUI() {
        Platform.runLater(() -> {
            menuSyncNow.setDisable(false);
            syncProgressIndicator.setVisible(false);
            syncProgressIndicator.setManaged(false);
        });
    }

    private void updateSyncStatusUI(SyncService.SyncStatus status) {
        switch (status) {
            case SYNCING -> setSyncPillState(PC_SYNCING, Phosphor.CLOUD_ARROW_UP, "Synchronisation…");
            case SUCCESS -> setSyncPillState(null, Phosphor.CLOUD_CHECK,
                "Synchronisé · " + syncService.getLastSyncTimeFormatted());
            case FAILED -> setSyncPillState(PC_FAILED, Phosphor.CLOUD_SLASH, "Échec de la synchronisation");
            case OFFLINE -> setSyncPillState(PC_OFFLINE, Phosphor.CLOUD_SLASH, "Hors ligne");
            default -> setSyncPillState(null, Phosphor.CLOUD_CHECK,
                "Dernier sync : " + syncService.getLastSyncTimeFormatted());
        }
    }

    private void setSyncPillState(PseudoClass state, String icon, String text) {
        syncMenu.pseudoClassStateChanged(PC_SYNCING, state == PC_SYNCING);
        syncMenu.pseudoClassStateChanged(PC_FAILED, state == PC_FAILED);
        syncMenu.pseudoClassStateChanged(PC_OFFLINE, state == PC_OFFLINE);
        syncIcon.setText(icon);
        syncMenu.setText(text);
    }

    private void showSyncResultDialog(SyncManager.SyncResult result) {
        boolean hasConflicts = result.getConflicts() > 0;
        boolean hasErrors = !result.getErrors().isEmpty();

        StringBuilder content = new StringBuilder();

        content.append("Données reçues du serveur\n");
        Map<String, Integer> pullByTable = result.getPullByTable();
        if (pullByTable.isEmpty() || result.getRecordsPulled() == 0) {
            content.append("   Vos données sont à jour.\n");
        } else {
            pullByTable.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> content.append(String.format("   %s : %d%n",
                    formatTableName(entry.getKey()), entry.getValue())));
            content.append(String.format("   Total : %d%n", result.getRecordsPulled()));
        }
        content.append("\nDonnées envoyées au serveur\n");
        Map<String, Integer> pushByTable = result.getPushByTable();
        if (pushByTable.isEmpty() || result.getRecordsPushed() == 0) {
            content.append("   Aucune modification à envoyer.\n");
        } else {
            pushByTable.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> content.append(String.format("   %s : %d%n",
                    formatTableName(entry.getKey()), entry.getValue())));
            content.append(String.format("   Total : %d%n", result.getRecordsPushed()));
        }

        if (hasConflicts) {
            content.append(String.format("%nConflits résolus automatiquement : %d%n", result.getConflicts()));
        }
        if (hasErrors) {
            content.append("\nErreurs rencontrées\n");
            for (String error : result.getErrors()) {
                content.append("   • ").append(error).append("\n");
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Synchronisation");
        alert.setHeaderText(hasConflicts || hasErrors
            ? "Synchronisation terminée avec des avertissements"
            : "Synchronisation terminée avec succès");
        alert.setContentText(content.toString());
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(480);
        alert.initOwner(window());
        ThemeManager.applyTo(alert.getDialogPane());
        alert.showAndWait();
    }

    private String formatTableName(String tableName) {
        Map<String, String> tableLabels = Map.of(
            "groups", "Groupes",
            "members", "Membres",
            "events", "Événements",
            "projects", "Projets",
            "expenses", "Dépenses",
            "contributions", "Cotisations",
            "payment_groups", "Objectifs de cotisation"
        );
        return tableLabels.getOrDefault(tableName, tableName);
    }

    @FXML
    private void showSyncHistory() {
        if (offlineMode) {
            return;
        }
        try {
            Dialogs.Modal<Object> modal = Dialogs.openModal("/fxml/SyncHistoryView.fxml",
                "Historique de synchronisation", window());
            modal.stage().setResizable(true);
            modal.stage().setMinWidth(900);
            modal.stage().setMinHeight(600);
            modal.stage().showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible d'ouvrir l'historique de synchronisation.");
        }
    }

    /** Appelé à la fermeture de la fenêtre principale. */
    public void shutdown() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        syncService.shutdown();
    }

    private javafx.stage.Window window() {
        return contentPane.getScene() != null ? contentPane.getScene().getWindow() : null;
    }
}
