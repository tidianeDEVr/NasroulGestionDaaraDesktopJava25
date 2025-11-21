package com.nasroul.controller;

import com.nasroul.dao.SyncLogDAO;
import com.nasroul.dao.SyncLogDAO.SyncLog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

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
                    setText(item);
                    if ("SUCCESS".equals(item)) {
                        setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
                    } else if ("FAILED".equals(item)) {
                        setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #ff9800;");
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
                    String icon = "PULL".equals(item) ? "📥" : "📤";
                    setText(icon + " " + item);
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
        statsLabel.setText(String.format("Total: %d logs (affichés: %d)", allLogs.size(), filtered.size()));
    }

    /**
     * Update statistics labels
     */
    private void updateStatistics() {
        long successCount = allLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long failedCount = allLogs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
        long pullCount = allLogs.stream().filter(l -> "PULL".equals(l.getSyncDirection())).count();
        long pushCount = allLogs.stream().filter(l -> "PUSH".equals(l.getSyncDirection())).count();

        totalSuccessLabel.setText(String.format("✅ Succès: %d", successCount));
        totalFailedLabel.setText(String.format("❌ Échecs: %d", failedCount));
        totalPullLabel.setText(String.format("📥 PULL: %d", pullCount));
        totalPushLabel.setText(String.format("📤 PUSH: %d", pushCount));

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
        // Show confirmation dialog
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmer le nettoyage");
        confirmAlert.setHeaderText("Nettoyer les anciens logs de synchronisation");
        confirmAlert.setContentText("Voulez-vous supprimer les logs de plus de 30 jours ?\n\n" +
                "Cette opération est irréversible.");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    syncLogDAO.cleanOldLogs(30);
                    loadHistory();
                    showInfo("Nettoyage réussi", "Les logs de plus de 30 jours ont été supprimés.");
                } catch (SQLException e) {
                    showError("Erreur de nettoyage", "Impossible de nettoyer les logs: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Show info dialog
     */
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
