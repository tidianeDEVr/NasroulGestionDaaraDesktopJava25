package com.nasroul.model;

import com.nasroul.util.DataHashCalculator;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base class for all entities that support synchronization
 * Provides sync metadata fields and hash calculation
 */
public abstract class SyncableEntity {

    // Sync metadata fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String lastModifiedBy;
    private String syncStatus;  // PENDING, SYNCED, CONFLICT
    private Integer syncVersion;
    private LocalDateTime lastSyncAt;

    // Constructor
    public SyncableEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.syncStatus = "PENDING";
        this.syncVersion = 1;
    }

    /**
     * Get map of field values for hash calculation
     * Must be implemented by each entity to return their specific fields
     *
     * @return Map of field names to values (excluding sync metadata)
     */
    public abstract Map<String, Object> getFieldValuesForHash();

    /**
     * Calculate hash of this entity's data
     * Uses all business fields (excluding ID and sync metadata)
     *
     * @return SHA-256 hash as hex string
     */
    public String calculateHash() {
        return DataHashCalculator.calculateHash(getFieldValuesForHash());
    }

    /**
     * Mark this entity as modified
     * Updates the updatedAt timestamp, sync status, and increments version
     *
     * @param deviceId ID of the device making the modification
     */
    public void markAsModified(String deviceId) {
        this.updatedAt = LocalDateTime.now();
        this.lastModifiedBy = deviceId;
        this.syncStatus = "PENDING";
        this.syncVersion = (this.syncVersion != null ? this.syncVersion : 0) + 1;
    }

    /**
     * Mark this entity as soft-deleted
     *
     * @param deviceId ID of the device making the deletion
     */
    public void markAsDeleted(String deviceId) {
        this.deletedAt = LocalDateTime.now();
        markAsModified(deviceId);
    }

    /**
     * Mark this entity as synced
     */
    public void markAsSynced() {
        this.syncStatus = "SYNCED";
        this.lastSyncAt = LocalDateTime.now();
    }

    /**
     * Mark this entity as having a conflict
     */
    public void markAsConflict() {
        this.syncStatus = "CONFLICT";
    }

    /**
     * Check if this entity has been soft-deleted
     *
     * @return true if deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Check if this entity needs to be synced
     *
     * @return true if sync status is PENDING or CONFLICT
     */
    public boolean needsSync() {
        return "PENDING".equals(syncStatus) || "CONFLICT".equals(syncStatus);
    }

    // Getters and Setters

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Integer getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(Integer syncVersion) {
        this.syncVersion = syncVersion;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
