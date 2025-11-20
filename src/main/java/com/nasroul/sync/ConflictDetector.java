package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;
import com.nasroul.util.DataHashCalculator;

import java.time.LocalDateTime;

/**
 * Detects sync conflicts using three-way merge strategy
 * Compares local, remote, and last-synced versions
 */
public class ConflictDetector {

    /**
     * Detect if there's a conflict between local and remote versions
     *
     * @param local Local entity
     * @param remote Remote entity
     * @param lastSyncedHash Hash of the last synced version
     * @return ConflictType indicating what kind of conflict (if any)
     */
    public ConflictType detectConflict(SyncableEntity local, SyncableEntity remote, String lastSyncedHash) {

        if (local == null && remote == null) {
            return ConflictType.NO_CONFLICT;
        }

        // Case 1: Record deleted locally
        if (local != null && local.isDeleted()) {
            if (remote == null || remote.isDeleted()) {
                return ConflictType.NO_CONFLICT; // Both deleted
            }
            // Check if remote was modified after local deletion
            if (remote.getUpdatedAt().isAfter(local.getDeletedAt())) {
                return ConflictType.DELETE_MODIFY_CONFLICT; // Modified remotely after local deletion
            }
            return ConflictType.NO_CONFLICT; // Local delete wins
        }

        // Case 2: Record deleted remotely
        if (remote != null && remote.isDeleted()) {
            if (local != null && !local.isDeleted()) {
                // Check if local was modified after remote deletion
                if (local.getUpdatedAt().isAfter(remote.getDeletedAt())) {
                    return ConflictType.DELETE_MODIFY_CONFLICT; // Modified locally after remote deletion
                }
            }
            return ConflictType.NO_CONFLICT; // Remote delete wins
        }

        // Case 3: New record on one side only
        if (local == null) {
            return ConflictType.NO_CONFLICT; // Take remote
        }
        if (remote == null) {
            return ConflictType.NO_CONFLICT; // Push local
        }

        // Case 4: Compare hashes to detect modifications
        String localHash = local.calculateHash();
        String remoteHash = remote.calculateHash();

        // No conflict if hashes match
        if (DataHashCalculator.hashesEqual(localHash, remoteHash)) {
            return ConflictType.NO_CONFLICT;
        }

        // Check if local was modified since last sync
        boolean localModified = lastSyncedHash == null ||
                !DataHashCalculator.hashesEqual(localHash, lastSyncedHash);

        // Check if remote was modified since last sync
        boolean remoteModified = lastSyncedHash == null ||
                !DataHashCalculator.hashesEqual(remoteHash, lastSyncedHash);

        // Both modified = conflict
        if (localModified && remoteModified) {
            // Use version numbers to detect true conflicts
            Integer localVersion = local.getSyncVersion();
            Integer remoteVersion = remote.getSyncVersion();

            if (localVersion != null && remoteVersion != null && !localVersion.equals(remoteVersion)) {
                return ConflictType.MODIFY_MODIFY_CONFLICT;
            }
        }

        // Only one side modified = no conflict
        if (localModified && !remoteModified) {
            return ConflictType.NO_CONFLICT; // Local changes win
        }
        if (remoteModified && !localModified) {
            return ConflictType.NO_CONFLICT; // Remote changes win
        }

        return ConflictType.NO_CONFLICT;
    }

    /**
     * Determine which version is newer based on timestamps
     *
     * @param local Local entity
     * @param remote Remote entity
     * @return true if local is newer, false if remote is newer
     */
    public boolean isLocalNewer(SyncableEntity local, SyncableEntity remote) {
        if (local == null) return false;
        if (remote == null) return true;

        LocalDateTime localTime = local.getUpdatedAt();
        LocalDateTime remoteTime = remote.getUpdatedAt();

        if (localTime == null) return false;
        if (remoteTime == null) return true;

        return localTime.isAfter(remoteTime);
    }

    /**
     * Check if versions are compatible (same version number)
     */
    public boolean areVersionsCompatible(SyncableEntity local, SyncableEntity remote) {
        if (local == null || remote == null) return true;

        Integer localVersion = local.getSyncVersion();
        Integer remoteVersion = remote.getSyncVersion();

        if (localVersion == null || remoteVersion == null) return true;

        return localVersion.equals(remoteVersion);
    }

    /**
     * Types of conflicts that can occur
     */
    public enum ConflictType {
        NO_CONFLICT,                // No conflict detected
        MODIFY_MODIFY_CONFLICT,     // Both local and remote modified
        DELETE_MODIFY_CONFLICT      // One side deleted, other modified
    }
}
