package com.nasroul.controller;

import com.nasroul.dao.SyncLogDAO;
import com.nasroul.dao.SyncLogDAO.SyncLog;
import com.nasroul.ui.Dialogs;
import com.nasroul.util.ConfigManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Window;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for Sync History View
 * Displays detailed log of all synchronization operations
 */
public class SyncHistoryController {

    @FXML private TableView<SyncLog> historyTable;
    @FXML private TableColumn<SyncLog, String> sessionIdColumn;
    @FXML private TableColumn<SyncLog, String> tableNameColumn;
    @FXML private TableColumn<SyncLog, Integer> recordIdColumn;
    @FXML private TableColumn<SyncLog, String> operationColumn;
    @FXML private TableColumn<SyncLog, String> directionColumn;
    @FXML private TableColumn<SyncLog, String> statusColumn;
    @FXML private TableColumn<SyncLog, LocalDateTime> timestampColumn;
    @FXML private TableColumn<SyncLog, String> errorMessageColumn;

    @FXML private ComboBox<String> filterComboBox;
    @FXML private ComboBox<String> tableFilterComboBox;
    @FXML private Label statsLabel;
    @FXML private Label totalSuccessLabel;
    @FXML private Label totalFailedLabel;
    @FXML private Label totalPullLabel;
    @FXML private Label totalPushLabel;
    @FXML private Label lastSyncLabel;
    @FXML private Button btnRefresh;
    @FXML private Button btnCleanOldLogs;

    private final SyncLogDAO syncLogDAO;
    private ObservableList<SyncLog> allLogs;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public SyncHistoryController() {
        this.syncLogDAO = new SyncLogDAO();
        this.allLogs = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        loadHistory();
    }

    /**
     * Setup table columns with cell value factories
     */
    private void setupTableColumns() {
        sessionIdColumn.setCellValueFactory(new PropertyValueFactory<>("syncSessionId"));
        tableNameColumn.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        recordIdColumn.setCellValueFactory(new PropertyValueFactory<>("recordId"));
        operationColumn.setCellValueFactory(new PropertyValueFactory<>("operation"));
        directionColumn.setCellValueFactory(new PropertyValueFactory<>("syncDirection"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        errorMessageColumn.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        // Custom cell factory for timestamp
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("syncedAt"));
        timestampColumn.setCellFactory(column -> new TableCell<SyncLog, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(dateFormatter.format(item));
                }
            }
        });

        // Custom cell factory for status with colors
        statusColumn.setCellFactory(column -> new TableCell<SyncLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if ("SUCCESS".equals(item)) {
                        setText("Succès");
                        setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                    } else if ("FAILED".equals(item)) {
                        setText("Échec");
                        setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;");
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: #8A5A00;");
                    }
                }
            }
        });

        // Custom cell factory for direction with icons
        directionColumn.setCellFactory(column -> new TableCell<SyncLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("PULL".equals(item) ? "Reçu" : "Envoyé");
                }
            }
        });

        // Truncate session ID for display
        sessionIdColumn.setCellFactory(column -> new TableCell<SyncLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String truncated = item.length() > 12 ? item.substring(0, 12) + "..." : item;
                    setText(truncated);
                    setTooltip(new Tooltip(item)); // Full ID in tooltip
                }
            }
        });
    }

    /**
     * Setup filter combo boxes
     */
    private void setupFilters() {
        filterComboBox.setValue("Tous");
        tableFilterComboBox.setValue("Toutes");
    }

    /**
     * Load sync history from database
     */
    private void loadHistory() {
        try {
            // Load last 1000 logs
            List<SyncLog> logs = syncLogDAO.getRecentLogs(1000);
            allLogs = FXCollections.observableArrayList(logs);
            applyFilters();
            updateStatistics();
        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger l'historique: " + e.getMessage());
        }
    }

    /**
     * Apply filters to the table
     */
    private void applyFilters() {
        String statusFilter = filterComboBox.getValue();
        String tableFilter = tableFilterComboBox.getValue();

        List<SyncLog> filtered = allLogs.stream()
            .filter(log -> {
                // Status filter
                if (!"Tous".equals(statusFilter) && !statusFilter.equals(log.getStatus())) {
                    return false;
                }
                // Table filter
                if (!"Toutes".equals(tableFilter) && !tableFilter.equals(log.getTableName())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        historyTable.setItems(FXCollections.observableArrayList(filtered));
        statsLabel.setText(String.format("Total : %d (affichés : %d)", allLogs.size(), filtered.size()));
    }

    /**
     * Update statistics labels
     */
    private void updateStatistics() {
        long successCount = allLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long failedCount = allLogs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
        long pullCount = allLogs.stream().filter(l -> "PULL".equals(l.getSyncDirection())).count();
        long pushCount = allLogs.stream().filter(l -> "PUSH".equals(l.getSyncDirection())).count();

        totalSuccessLabel.setText(String.format("Succès : %d", successCount));
        totalFailedLabel.setText(String.format("Échecs : %d", failedCount));
        totalPullLabel.setText(String.format("Reçus : %d", pullCount));
        totalPushLabel.setText(String.format("Envoyés : %d", pushCount));

        // Update last sync time
        if (!allLogs.isEmpty()) {
            LocalDateTime lastSync = allLogs.get(0).getSyncedAt();
            lastSyncLabel.setText(lastSync != null ? dateFormatter.format(lastSync) : "Inconnu");
        } else {
            lastSyncLabel.setText("Jamais");
        }
    }

    @FXML
    private void handleRefresh() {
        loadHistory();
    }

    @FXML
    private void handleFilterChange() {
        applyFilters();
    }

    @FXML
    private void handleCleanOldLogs() {
        int retentionDays = ConfigManager.getInstance().getSyncLogRetentionDays();
        try {
            int total = syncLogDAO.countAll();
            if (total == 0) {
                Dialogs.info(window(), "Historique vide", "Il n'y a aucun journal de synchronisation à nettoyer.");
                return;
            }
            int old = syncLogDAO.countOlderThan(retentionDays);
            int deleted;
            if (old > 0) {
                boolean ok = Dialogs.confirm(window(), "Confirmer le nettoyage",
                    "Nettoyer les anciens journaux de synchronisation",
                    String.format("Supprimer les %d journaux de plus de %d jours (sur %d) ?%n%n"
                        + "Cette opération est irréversible.", old, retentionDays, total));
                if (!ok) {
                    return;
                }
                deleted = syncLogDAO.cleanOldLogs(retentionDays);
            } else {
                boolean ok = Dialogs.confirm(window(), "Aucun journal ancien",
                    "Aucun journal n'a plus de " + retentionDays + " jours",
                    String.format("Le délai de conservation configuré (sync.log.retention.days) est de %d jours "
                        + "et tous les journaux sont plus récents.%n%n"
                        + "Voulez-vous supprimer tout l'historique local (%d journaux) ?%n"
                        + "Le journal partagé sur le serveur n'est pas concerné.", retentionDays, total));
                if (!ok) {
                    return;
                }
                deleted = syncLogDAO.deleteAll();
            }
            loadHistory();
            Dialogs.info(window(), "Nettoyage effectué",
                deleted + " journal" + (deleted > 1 ? "aux" : "") + " supprimé" + (deleted > 1 ? "s" : "") + ".");
        } catch (SQLException e) {
            showError("Erreur de nettoyage", "Impossible de nettoyer les journaux : " + e.getMessage());
        }
    }

    /** Fenêtre propriétaire des dialogues : la fenêtre modale de l'historique. */
    private Window window() {
        return btnCleanOldLogs != null && btnCleanOldLogs.getScene() != null
            ? btnCleanOldLogs.getScene().getWindow() : null;
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        if (Platform.isFxApplicationThread()) {
            Dialogs.error(window(), title, message);
        } else {
            Platform.runLater(() -> Dialogs.error(window(), title, message));
        }
    }

    /**
     * Show info dialog
     */
    private void showInfo(String title, String message) {
        if (Platform.isFxApplicationThread()) {
            Dialogs.info(window(), title, message);
        } else {
            Platform.runLater(() -> Dialogs.info(window(), title, message));
        }
    }
}
