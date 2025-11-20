package com.nasroul.service;

import com.nasroul.dao.SyncLogDAO;
import com.nasroul.sync.SyncManager;
import com.nasroul.util.DeviceIdGenerator;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level synchronization service
 * Provides simple API for UI components to trigger sync operations
 */
public class SyncService {

    private static SyncService instance;
    private final SyncManager syncManager;
    private final SyncLogDAO syncLogDAO;
    private final ExecutorService executorService;

    private boolean isSyncing = false;
    private LocalDateTime lastSyncTime = null;
    private SyncManager.SyncResult lastSyncResult = null;
    private SyncStatusListener statusListener;

    private SyncService() {
        this.syncManager = new SyncManager();
        this.syncLogDAO = new SyncLogDAO();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SyncService-Thread");
            return t;
        });
    }

    public static synchronized SyncService getInstance() {
        if (instance == null) {
            instance = new SyncService();
        }
        return instance;
    }

    /**
     * Set listener for sync status updates
     */
    public void setStatusListener(SyncStatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Perform synchronization asynchronously
     *
     * @return Task that can be monitored for progress
     */
    public Task<SyncManager.SyncResult> synchronizeAsync() {
        Task<SyncManager.SyncResult> task = new Task<>() {
            @Override
            protected SyncManager.SyncResult call() throws Exception {
                updateMessage("Démarrage de la synchronisation...");
                updateProgress(0, 100);

                try {
                    isSyncing = true;
                    notifyStatusChange(SyncStatus.SYNCING);

                    updateMessage("Connexion au serveur MySQL...");
                    updateProgress(10, 100);

                    // Perform synchronization
                    SyncManager.SyncResult result = syncManager.synchronize();

                    if (result.isSuccess()) {
                        updateMessage("Synchronisation réussie!");
                        updateProgress(100, 100);

                        lastSyncTime = LocalDateTime.now();
                        lastSyncResult = result;
                        notifyStatusChange(SyncStatus.SUCCESS);
                    } else {
                        updateMessage("Synchronisation échouée: " + result.getErrorMessage());
                        notifyStatusChange(SyncStatus.FAILED);
                    }

                    return result;

                } catch (SQLException e) {
                    updateMessage("Erreur: " + e.getMessage());
                    notifyStatusChange(SyncStatus.FAILED);
                    throw e;
                } finally {
                    isSyncing = false;
                }
            }
        };

        executorService.submit(task);
        return task;
    }

    /**
     * Perform synchronization synchronously (blocking)
     */
    public SyncManager.SyncResult synchronize() throws SQLException {
        if (isSyncing) {
            throw new IllegalStateException("Synchronisation déjà en cours");
        }

        try {
            isSyncing = true;
            notifyStatusChange(SyncStatus.SYNCING);

            SyncManager.SyncResult result = syncManager.synchronize();

            lastSyncTime = LocalDateTime.now();
            lastSyncResult = result;

            if (result.isSuccess()) {
                notifyStatusChange(SyncStatus.SUCCESS);
            } else {
                notifyStatusChange(SyncStatus.FAILED);
            }

            return result;

        } finally {
            isSyncing = false;
        }
    }

    /**
     * Check if synchronization is currently running
     */
    public boolean isSyncing() {
        return isSyncing;
    }

    /**
     * Get last synchronization time
     */
    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * Get last synchronization time as formatted string
     */
    public String getLastSyncTimeFormatted() {
        if (lastSyncTime == null) {
            return "Jamais synchronisé";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return lastSyncTime.format(formatter);
    }

    /**
     * Get last synchronization result
     */
    public SyncManager.SyncResult getLastSyncResult() {
        return lastSyncResult;
    }

    /**
     * Get current device ID
     */
    public String getDeviceId() {
        return DeviceIdGenerator.getDeviceId();
    }

    /**
     * Get recent sync logs
     */
    public List<SyncLogDAO.SyncLog> getRecentLogs(int limit) throws SQLException {
        return syncLogDAO.getRecentLogs(limit);
    }

    /**
     * Get failed sync operations
     */
    public List<SyncLogDAO.SyncLog> getFailedSyncs() throws SQLException {
        return syncLogDAO.getFailedSyncs();
    }

    /**
     * Clean old sync logs (keep only last N days)
     */
    public void cleanOldLogs(int daysToKeep) throws SQLException {
        syncLogDAO.cleanOldLogs(daysToKeep);
    }

    /**
     * Get sync statistics summary
     */
    public String getSyncSummary() {
        if (lastSyncResult == null) {
            return "Aucune synchronisation effectuée";
        }

        return String.format(
            "Dernier sync: %s\n" +
            "Statut: %s\n" +
            "Records téléchargés: %d\n" +
            "Records envoyés: %d\n" +
            "Conflits: %d",
            getLastSyncTimeFormatted(),
            lastSyncResult.isSuccess() ? "Réussi" : "Échoué",
            lastSyncResult.getRecordsPulled(),
            lastSyncResult.getRecordsPushed(),
            lastSyncResult.getConflicts()
        );
    }

    private void notifyStatusChange(SyncStatus status) {
        if (statusListener != null) {
            // Run on JavaFX Application Thread
            Platform.runLater(() -> statusListener.onStatusChanged(status));
        }
    }

    /**
     * Shutdown service and cleanup resources
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Sync status enum
     */
    public enum SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        FAILED,
        OFFLINE
    }

    /**
     * Listener interface for sync status updates
     */
    public interface SyncStatusListener {
        void onStatusChanged(SyncStatus status);
    }
}
