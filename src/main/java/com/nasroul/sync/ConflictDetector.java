package com.nasroul.sync;

import com.nasroul.model.SyncableEntity;
import com.nasroul.util.DataHashCalculator;

import java.time.LocalDateTime;

/**
 * Détection de conflit par fusion à trois voies : version locale, version
 * distante et hash de la dernière version synchronisée (base commune).
 *
 * Les deux entités comparées doivent être exprimées dans le même espace
 * d'identifiants (voir la conversion des clés étrangères dans SyncManager).
 */
public class ConflictDetector {

    /**
     * @param local          entité locale (peut être null)
     * @param remote         entité distante, FK converties en ids locaux (peut être null)
     * @param lastSyncedHash hash du contenu lors de la dernière sync (null = jamais synchronisé)
     */
    public ConflictType detectConflict(SyncableEntity local, SyncableEntity remote, String lastSyncedHash) {

        if (local == null && remote == null) {
            return ConflictType.NO_CONFLICT;
        }

        // Cas 1 : supprimé localement
        if (local != null && local.isDeleted()) {
            if (remote == null || remote.isDeleted()) {
                return ConflictType.NO_CONFLICT; // supprimé des deux côtés
            }
            if (isAfter(remote.getUpdatedAt(), local.getDeletedAt())) {
                return ConflictType.DELETE_MODIFY_CONFLICT; // modifié à distance après la suppression locale
            }
            return ConflictType.NO_CONFLICT; // la suppression locale l'emporte
        }

        // Cas 2 : supprimé à distance
        if (remote != null && remote.isDeleted()) {
            if (local != null && isAfter(local.getUpdatedAt(), remote.getDeletedAt())) {
                return ConflictType.DELETE_MODIFY_CONFLICT; // modifié localement après la suppression distante
            }
            return ConflictType.NO_CONFLICT; // la suppression distante l'emporte
        }

        // Cas 3 : nouveau d'un seul côté
        if (local == null || remote == null) {
            return ConflictType.NO_CONFLICT;
        }

        // Cas 4 : comparaison des contenus
        String localHash = local.calculateHash();
        String remoteHash = remote.calculateHash();

        if (DataHashCalculator.hashesEqual(localHash, remoteHash)) {
            return ConflictType.NO_CONFLICT;
        }

        boolean localModified = lastSyncedHash == null
                || !DataHashCalculator.hashesEqual(localHash, lastSyncedHash);
        boolean remoteModified = lastSyncedHash == null
                || !DataHashCalculator.hashesEqual(remoteHash, lastSyncedHash);

        // Les deux côtés ont divergé depuis la base commune : conflit réel.
        // (Les numéros de version sont incrémentés indépendamment sur chaque
        // poste : leur égalité n'indique en rien l'absence de conflit.)
        if (localModified && remoteModified) {
            return ConflictType.MODIFY_MODIFY_CONFLICT;
        }

        return ConflictType.NO_CONFLICT;
    }

    /**
     * Compare les horodatages de modification. Un horodatage absent est
     * considéré plus ancien que n'importe quel horodatage présent.
     *
     * @return &gt; 0 si local plus récent, &lt; 0 si distant plus récent, 0 si égalité
     */
    public int compareFreshness(SyncableEntity local, SyncableEntity remote) {
        if (local == null && remote == null) return 0;
        if (local == null) return -1;
        if (remote == null) return 1;
        return compare(local.getUpdatedAt(), remote.getUpdatedAt());
    }

    /**
     * @return true si local est strictement plus récent que remote
     */
    public boolean isLocalNewer(SyncableEntity local, SyncableEntity remote) {
        return compareFreshness(local, remote) > 0;
    }

    static boolean isAfter(LocalDateTime a, LocalDateTime b) {
        return a != null && b != null && a.isAfter(b);
    }

    static int compare(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
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
