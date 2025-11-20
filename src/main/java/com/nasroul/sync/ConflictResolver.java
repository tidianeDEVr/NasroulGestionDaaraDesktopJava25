package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;
import com.nasroul.sync.ConflictDetector.ConflictType;

/**
 * Resolves sync conflicts using configurable strategies
 * Default strategy: Last-Write-Wins
 */
public class ConflictResolver {

    private ResolutionStrategy defaultStrategy = ResolutionStrategy.LAST_WRITE_WINS;
    private final ConflictDetector detector;

    public ConflictResolver() {
        this.detector = new ConflictDetector();
    }

    /**
     * Set the default conflict resolution strategy
     */
    public void setDefaultStrategy(ResolutionStrategy strategy) {
        this.defaultStrategy = strategy;
    }

    /**
     * Resolve a conflict between local and remote versions
     *
     * @param local Local entity
     * @param remote Remote entity
     * @param conflictType Type of conflict detected
     * @return Resolution indicating which version to keep
     */
    public Resolution resolve(SyncableEntity local, SyncableEntity remote,
                             ConflictType conflictType) {
        return resolve(local, remote, conflictType, defaultStrategy);
    }

    /**
     * Resolve a conflict using a specific strategy
     */
    public Resolution resolve(SyncableEntity local, SyncableEntity remote,
                             ConflictType conflictType, ResolutionStrategy strategy) {

        if (conflictType == ConflictType.NO_CONFLICT) {
            // No conflict, determine which version to use
            if (local == null) return new Resolution(ResolutionAction.TAKE_REMOTE, "No local version");
            if (remote == null) return new Resolution(ResolutionAction.TAKE_LOCAL, "No remote version");

            // Both exist, take the one that was modified
            String localHash = local.calculateHash();
            String remoteHash = remote.calculateHash();

            if (localHash.equals(remoteHash)) {
                return new Resolution(ResolutionAction.NO_ACTION, "Versions are identical");
            }

            // Determine based on sync status
            if ("PENDING".equals(local.getSyncStatus())) {
                return new Resolution(ResolutionAction.TAKE_LOCAL, "Local has pending changes");
            } else {
                return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote version is newer");
            }
        }

        // Handle conflicts based on strategy
        switch (strategy) {
            case LAST_WRITE_WINS:
                return resolveLastWriteWins(local, remote, conflictType);

            case LOCAL_WINS:
                return new Resolution(ResolutionAction.TAKE_LOCAL, "Local wins strategy");

            case REMOTE_WINS:
                return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote wins strategy");

            case MANUAL:
                return new Resolution(ResolutionAction.MANUAL_RESOLUTION, "Manual resolution required");

            case HIGHER_VERSION_WINS:
                return resolveHigherVersionWins(local, remote);

            default:
                return resolveLastWriteWins(local, remote, conflictType);
        }
    }

    private Resolution resolveLastWriteWins(SyncableEntity local, SyncableEntity remote,
                                           ConflictType conflictType) {

        if (conflictType == ConflictType.DELETE_MODIFY_CONFLICT) {
            // For delete-modify conflicts, check timestamps
            if (local != null && local.isDeleted()) {
                // Local deleted, remote modified
                if (remote.getUpdatedAt().isAfter(local.getDeletedAt())) {
                    return new Resolution(ResolutionAction.TAKE_REMOTE,
                            "Remote modified after local deletion");
                } else {
                    return new Resolution(ResolutionAction.TAKE_LOCAL,
                            "Local deleted after remote modification");
                }
            } else if (remote != null && remote.isDeleted()) {
                // Remote deleted, local modified
                if (local.getUpdatedAt().isAfter(remote.getDeletedAt())) {
                    return new Resolution(ResolutionAction.TAKE_LOCAL,
                            "Local modified after remote deletion");
                } else {
                    return new Resolution(ResolutionAction.TAKE_REMOTE,
                            "Remote deleted after local modification");
                }
            }
        }

        // For modify-modify conflicts, use timestamp
        boolean localNewer = detector.isLocalNewer(local, remote);
        if (localNewer) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, "Local version is newer");
        } else {
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote version is newer");
        }
    }

    private Resolution resolveHigherVersionWins(SyncableEntity local, SyncableEntity remote) {
        Integer localVersion = local != null ? local.getSyncVersion() : 0;
        Integer remoteVersion = remote != null ? remote.getSyncVersion() : 0;

        if (localVersion == null) localVersion = 0;
        if (remoteVersion == null) remoteVersion = 0;

        if (localVersion > remoteVersion) {
            return new Resolution(ResolutionAction.TAKE_LOCAL,
                    "Local version (" + localVersion + ") is higher");
        } else if (remoteVersion > localVersion) {
            return new Resolution(ResolutionAction.TAKE_REMOTE,
                    "Remote version (" + remoteVersion + ") is higher");
        } else {
            // Same version, use timestamp
            return resolveLastWriteWins(local, remote, ConflictType.MODIFY_MODIFY_CONFLICT);
        }
    }

    /**
     * Resolution strategies
     */
    public enum ResolutionStrategy {
        LAST_WRITE_WINS,      // Use the most recently modified version (default)
        LOCAL_WINS,           // Always prefer local version
        REMOTE_WINS,          // Always prefer remote version
        MANUAL,               // Require manual resolution
        HIGHER_VERSION_WINS   // Use version with higher sync_version number
    }

    /**
     * Resolution actions
     */
    public enum ResolutionAction {
        TAKE_LOCAL,           // Keep local version
        TAKE_REMOTE,          // Take remote version
        MANUAL_RESOLUTION,    // Requires manual intervention
        NO_ACTION             // No action needed (versions identical)
    }

    /**
     * Resolution result
     */
    public static class Resolution {
        private final ResolutionAction action;
        private final String reason;

        public Resolution(ResolutionAction action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public ResolutionAction getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return action + ": " + reason;
        }
    }
}
