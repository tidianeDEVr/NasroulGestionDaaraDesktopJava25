package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;
import com.nasroul.sync.ConflictDetector.ConflictType;
import com.nasroul.util.DataHashCalculator;

/**
 * Résolution des conflits de synchronisation selon une stratégie configurable
 * (défaut : la dernière écriture gagne).
 *
 * Toutes les décisions sont déterministes et symétriques : deux postes qui
 * comparent les mêmes versions prennent la même décision, ce qui garantit la
 * convergence.
 */
public class ConflictResolver {

    private ResolutionStrategy defaultStrategy = ResolutionStrategy.LAST_WRITE_WINS;
    private final ConflictDetector detector;

    public ConflictResolver() {
        this.detector = new ConflictDetector();
    }

    public void setDefaultStrategy(ResolutionStrategy strategy) {
        this.defaultStrategy = strategy != null ? strategy : ResolutionStrategy.LAST_WRITE_WINS;
    }

    public ResolutionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /** Stratégie depuis la configuration (nom insensible à la casse, défaut LWW). */
    public static ResolutionStrategy strategyFromConfig(String name) {
        if (name == null) {
            return ResolutionStrategy.LAST_WRITE_WINS;
        }
        try {
            return ResolutionStrategy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResolutionStrategy.LAST_WRITE_WINS;
        }
    }

    /**
     * Décision complète à trois voies : détecte le conflit puis décide quelle
     * version appliquer. C'est le point d'entrée utilisé par SyncManager.
     *
     * @param local    version locale (null si inexistante)
     * @param remote   version distante exprimée en ids locaux (null si inexistante)
     * @param baseHash hash du contenu lors de la dernière sync, null si inconnu
     */
    public Resolution resolveThreeWay(SyncableEntity local, SyncableEntity remote, String baseHash) {
        return resolveThreeWay(local, remote, baseHash, defaultStrategy);
    }

    public Resolution resolveThreeWay(SyncableEntity local, SyncableEntity remote, String baseHash,
                                      ResolutionStrategy strategy) {
        if (local == null && remote == null) {
            return new Resolution(ResolutionAction.NO_ACTION, "Aucune version", ConflictType.NO_CONFLICT);
        }
        if (local == null) {
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Pas de version locale", ConflictType.NO_CONFLICT);
        }
        if (remote == null) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, "Pas de version distante", ConflictType.NO_CONFLICT);
        }

        ConflictType type = detector.detectConflict(local, remote, baseHash);
        if (type != ConflictType.NO_CONFLICT) {
            return resolve(local, remote, type, strategy);
        }

        // Suppression d'un seul côté, sans modification postérieure de l'autre
        if (local.isDeleted() && !remote.isDeleted()) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, "Suppression locale à propager", type);
        }
        if (remote.isDeleted() && !local.isDeleted()) {
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Suppression distante à appliquer", type);
        }

        String localHash = local.calculateHash();
        String remoteHash = remote.calculateHash();
        if (DataHashCalculator.hashesEqual(localHash, remoteHash)) {
            return new Resolution(ResolutionAction.NO_ACTION, "Versions identiques", type);
        }

        boolean localChanged = baseHash == null || !DataHashCalculator.hashesEqual(localHash, baseHash);
        boolean remoteChanged = baseHash == null || !DataHashCalculator.hashesEqual(remoteHash, baseHash);
        if (localChanged && !remoteChanged) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, "Seule la version locale a changé", type);
        }
        if (remoteChanged && !localChanged) {
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Seule la version distante a changé", type);
        }
        // Cas résiduel (ex. deux suppressions aux contenus différents) : LWW
        return resolveLastWriteWins(local, remote, ConflictType.MODIFY_MODIFY_CONFLICT);
    }

    /**
     * Resolve a conflict between local and remote versions
     */
    public Resolution resolve(SyncableEntity local, SyncableEntity remote, ConflictType conflictType) {
        return resolve(local, remote, conflictType, defaultStrategy);
    }

    /**
     * Resolve a conflict using a specific strategy
     */
    public Resolution resolve(SyncableEntity local, SyncableEntity remote,
                             ConflictType conflictType, ResolutionStrategy strategy) {

        if (conflictType == ConflictType.NO_CONFLICT) {
            if (local == null) return new Resolution(ResolutionAction.TAKE_REMOTE, "No local version", conflictType);
            if (remote == null) return new Resolution(ResolutionAction.TAKE_LOCAL, "No remote version", conflictType);

            String localHash = local.calculateHash();
            String remoteHash = remote.calculateHash();
            if (DataHashCalculator.hashesEqual(localHash, remoteHash) && local.isDeleted() == remote.isDeleted()) {
                return new Resolution(ResolutionAction.NO_ACTION, "Versions are identical", conflictType);
            }
            if (local.needsSync()) {
                return new Resolution(ResolutionAction.TAKE_LOCAL, "Local has pending changes", conflictType);
            }
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote version is newer", conflictType);
        }

        switch (strategy) {
            case LOCAL_WINS:
                return new Resolution(ResolutionAction.TAKE_LOCAL, "Local wins strategy", conflictType);
            case REMOTE_WINS:
                return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote wins strategy", conflictType);
            case MANUAL:
                return new Resolution(ResolutionAction.MANUAL_RESOLUTION, "Manual resolution required", conflictType);
            case HIGHER_VERSION_WINS:
                return resolveHigherVersionWins(local, remote, conflictType);
            case LAST_WRITE_WINS:
            default:
                return resolveLastWriteWins(local, remote, conflictType);
        }
    }

    private Resolution resolveLastWriteWins(SyncableEntity local, SyncableEntity remote,
                                           ConflictType conflictType) {

        if (conflictType == ConflictType.DELETE_MODIFY_CONFLICT) {
            if (local != null && local.isDeleted() && remote != null && !remote.isDeleted()) {
                // Local supprimé, distant modifié
                if (ConflictDetector.isAfter(remote.getUpdatedAt(), local.getDeletedAt())) {
                    return new Resolution(ResolutionAction.TAKE_REMOTE,
                            "Remote modified after local deletion", conflictType);
                }
                return new Resolution(ResolutionAction.TAKE_LOCAL,
                        "Local deleted after remote modification", conflictType);
            }
            if (remote != null && remote.isDeleted() && local != null && !local.isDeleted()) {
                // Distant supprimé, local modifié
                if (ConflictDetector.isAfter(local.getUpdatedAt(), remote.getDeletedAt())) {
                    return new Resolution(ResolutionAction.TAKE_LOCAL,
                            "Local modified after remote deletion", conflictType);
                }
                return new Resolution(ResolutionAction.TAKE_REMOTE,
                        "Remote deleted after local modification", conflictType);
            }
        }

        int freshness = detector.compareFreshness(local, remote);
        if (freshness > 0) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, "Local version is newer", conflictType);
        }
        if (freshness < 0) {
            return new Resolution(ResolutionAction.TAKE_REMOTE, "Remote version is newer", conflictType);
        }
        return tieBreak(local, remote, conflictType, "Same timestamp");
    }

    private Resolution resolveHigherVersionWins(SyncableEntity local, SyncableEntity remote,
                                               ConflictType conflictType) {
        int localVersion = versionOf(local);
        int remoteVersion = versionOf(remote);

        if (localVersion > remoteVersion) {
            return new Resolution(ResolutionAction.TAKE_LOCAL,
                    "Local version (" + localVersion + ") is higher", conflictType);
        }
        if (remoteVersion > localVersion) {
            return new Resolution(ResolutionAction.TAKE_REMOTE,
                    "Remote version (" + remoteVersion + ") is higher", conflictType);
        }
        return resolveLastWriteWins(local, remote, conflictType);
    }

    /**
     * Départage déterministe quand les horodatages sont égaux (ou absents des
     * deux côtés) : version la plus haute, puis hash de contenu le plus grand.
     * Les deux postes arrivent ainsi au même résultat.
     */
    private Resolution tieBreak(SyncableEntity local, SyncableEntity remote,
                                ConflictType conflictType, String why) {
        int localVersion = versionOf(local);
        int remoteVersion = versionOf(remote);
        if (localVersion != remoteVersion) {
            return localVersion > remoteVersion
                    ? new Resolution(ResolutionAction.TAKE_LOCAL, why + ", higher local version", conflictType)
                    : new Resolution(ResolutionAction.TAKE_REMOTE, why + ", higher remote version", conflictType);
        }
        String localHash = local != null ? local.calculateHash() : "";
        String remoteHash = remote != null ? remote.calculateHash() : "";
        if (localHash.compareTo(remoteHash) > 0) {
            return new Resolution(ResolutionAction.TAKE_LOCAL, why + ", deterministic tie-break", conflictType);
        }
        return new Resolution(ResolutionAction.TAKE_REMOTE, why + ", deterministic tie-break", conflictType);
    }

    private static int versionOf(SyncableEntity entity) {
        if (entity == null || entity.getSyncVersion() == null) {
            return 0;
        }
        return entity.getSyncVersion();
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
        private final ConflictType conflictType;

        public Resolution(ResolutionAction action, String reason) {
            this(action, reason, ConflictType.NO_CONFLICT);
        }

        public Resolution(ResolutionAction action, String reason, ConflictType conflictType) {
            this.action = action;
            this.reason = reason;
            this.conflictType = conflictType != null ? conflictType : ConflictType.NO_CONFLICT;
        }

        public ResolutionAction getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public ConflictType getConflictType() {
            return conflictType;
        }

        /** Un vrai conflit (les deux côtés ont divergé) a-t-il été arbitré ? */
        public boolean isConflict() {
            return conflictType != ConflictType.NO_CONFLICT;
        }

        @Override
        public String toString() {
            return action + ": " + reason;
        }
    }
}
