package com.nasroul.sync;

import com.nasroul.sync.ConflictDetector.ConflictType;
import com.nasroul.sync.ConflictResolver.Resolution;
import com.nasroul.sync.ConflictResolver.ResolutionAction;
import com.nasroul.sync.ConflictResolver.ResolutionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires purs (sans base) du détecteur et du résolveur de conflits.
 */
class ConflictResolutionTest {

    private final ConflictDetector detector = new ConflictDetector();
    private final ConflictResolver resolver = new ConflictResolver();

    private static GenericSyncableEntity row(String name, String updatedAt, Integer version, String status) {
        GenericSyncableEntity e = new GenericSyncableEntity("groups");
        e.setField("id", 1);
        e.setField("name", name);
        e.setField("active", 1);
        e.setField("updated_at", updatedAt);
        e.setField("sync_version", version);
        e.setField("sync_status", status);
        return e;
    }

    private static GenericSyncableEntity deleted(GenericSyncableEntity e, String deletedAt) {
        e.setField("deleted_at", deletedAt);
        e.setField("updated_at", deletedAt);
        return e;
    }

    // ------------------------------------------------------------ détection

    @Test
    void sameVersionNumbersDoNotHideAConcurrentModification() {
        GenericSyncableEntity base = row("Daara", "2026-01-01 10:00:00", 2, "SYNCED");
        String baseHash = base.calculateHash();
        GenericSyncableEntity local = row("Daara A", "2026-01-02 10:00:00", 3, "PENDING");
        GenericSyncableEntity remote = row("Daara B", "2026-01-02 11:00:00", 3, "SYNCED");

        assertEquals(ConflictType.MODIFY_MODIFY_CONFLICT, detector.detectConflict(local, remote, baseHash));
    }

    @Test
    void onlyOneSideChangedIsNotAConflict() {
        GenericSyncableEntity base = row("Daara", "2026-01-01 10:00:00", 2, "SYNCED");
        String baseHash = base.calculateHash();
        GenericSyncableEntity changed = row("Daara A", "2026-01-02 10:00:00", 3, "PENDING");

        assertEquals(ConflictType.NO_CONFLICT, detector.detectConflict(changed, base, baseHash));
        assertEquals(ConflictType.NO_CONFLICT, detector.detectConflict(base, changed, baseHash));
    }

    @Test
    void noBaseMeansBothSidesAreConsideredModified() {
        GenericSyncableEntity local = row("Daara A", "2026-01-02 10:00:00", 1, "PENDING");
        GenericSyncableEntity remote = row("Daara B", "2026-01-02 10:00:00", 1, "SYNCED");
        assertEquals(ConflictType.MODIFY_MODIFY_CONFLICT, detector.detectConflict(local, remote, null));
    }

    @Test
    void deleteModifyConflictDependsOnTimestamps() {
        GenericSyncableEntity localDeleted = deleted(row("Daara", null, 2, "PENDING"), "2026-01-05 10:00:00");
        GenericSyncableEntity remoteAfter = row("Daara X", "2026-01-05 11:00:00", 2, "SYNCED");
        GenericSyncableEntity remoteBefore = row("Daara X", "2026-01-05 09:00:00", 2, "SYNCED");

        assertEquals(ConflictType.DELETE_MODIFY_CONFLICT, detector.detectConflict(localDeleted, remoteAfter, null));
        assertEquals(ConflictType.NO_CONFLICT, detector.detectConflict(localDeleted, remoteBefore, null));
        assertEquals(ConflictType.DELETE_MODIFY_CONFLICT, detector.detectConflict(remoteAfter, localDeleted, null));
        assertEquals(ConflictType.NO_CONFLICT, detector.detectConflict(remoteBefore, localDeleted, null));
    }

    @Test
    void missingTimestampsNeverThrow() {
        GenericSyncableEntity localDeleted = deleted(row("Daara", null, 2, "PENDING"), "2026-01-05 10:00:00");
        GenericSyncableEntity remoteNoDate = row("Daara X", null, 2, "SYNCED");
        assertDoesNotThrow(() -> detector.detectConflict(localDeleted, remoteNoDate, null));
        assertDoesNotThrow(() -> detector.detectConflict(remoteNoDate, localDeleted, null));
        assertDoesNotThrow(() -> resolver.resolveThreeWay(localDeleted, remoteNoDate, null));
        assertDoesNotThrow(() -> resolver.resolveThreeWay(remoteNoDate, localDeleted, null));
    }

    // ----------------------------------------------------------- résolution

    @Test
    void threeWayPrefersTheOnlySideThatChanged() {
        GenericSyncableEntity base = row("Daara", "2026-01-01 10:00:00", 2, "SYNCED");
        String baseHash = base.calculateHash();
        GenericSyncableEntity changed = row("Daara A", "2026-01-02 10:00:00", 3, "PENDING");

        assertEquals(ResolutionAction.TAKE_LOCAL, resolver.resolveThreeWay(changed, base, baseHash).getAction());
        assertEquals(ResolutionAction.TAKE_REMOTE, resolver.resolveThreeWay(base, changed, baseHash).getAction());
        assertEquals(ResolutionAction.NO_ACTION, resolver.resolveThreeWay(base, base, baseHash).getAction());
        assertFalse(resolver.resolveThreeWay(changed, base, baseHash).isConflict());
    }

    @Test
    void threeWayDeletionWithoutLaterModificationPropagates() {
        GenericSyncableEntity base = row("Daara", "2026-01-01 10:00:00", 2, "SYNCED");
        String baseHash = base.calculateHash();
        GenericSyncableEntity localDeleted = deleted(row("Daara", null, 3, "PENDING"), "2026-01-05 10:00:00");
        GenericSyncableEntity remoteDeleted = deleted(row("Daara", null, 3, "SYNCED"), "2026-01-05 10:00:00");

        Resolution pushDelete = resolver.resolveThreeWay(localDeleted, base, baseHash);
        assertEquals(ResolutionAction.TAKE_LOCAL, pushDelete.getAction());
        Resolution pullDelete = resolver.resolveThreeWay(base, remoteDeleted, baseHash);
        assertEquals(ResolutionAction.TAKE_REMOTE, pullDelete.getAction());
        assertEquals(ResolutionAction.NO_ACTION, resolver.resolveThreeWay(localDeleted, remoteDeleted, baseHash).getAction());
    }

    @Test
    void lastWriteWinsUsesUpdatedAt() {
        GenericSyncableEntity local = row("Daara A", "2026-01-02 10:00:00", 3, "PENDING");
        GenericSyncableEntity remote = row("Daara B", "2026-01-02 11:00:00", 3, "SYNCED");

        Resolution r = resolver.resolveThreeWay(local, remote, null);
        assertEquals(ResolutionAction.TAKE_REMOTE, r.getAction());
        assertTrue(r.isConflict());
        assertEquals(ResolutionAction.TAKE_LOCAL, resolver.resolveThreeWay(remote, local, null).getAction());
    }

    @Test
    void tieBreakIsDeterministicAndSymmetric() {
        GenericSyncableEntity x = row("Daara A", "2026-01-02 10:00:00", 3, "PENDING");
        GenericSyncableEntity y = row("Daara B", "2026-01-02 10:00:00", 3, "PENDING");

        ResolutionAction fromX = resolver.resolveThreeWay(x, y, null).getAction();
        ResolutionAction fromY = resolver.resolveThreeWay(y, x, null).getAction();
        // Vu de X, « prendre local » = garder X ; vu de Y, cela doit donner « prendre distant » = X
        assertNotEquals(fromX, fromY, "les deux postes doivent désigner la même version gagnante");

        GenericSyncableEntity higher = row("Daara A", null, 5, "PENDING");
        GenericSyncableEntity lower = row("Daara B", null, 2, "PENDING");
        assertEquals(ResolutionAction.TAKE_LOCAL, resolver.resolveThreeWay(higher, lower, null).getAction());
        assertEquals(ResolutionAction.TAKE_REMOTE, resolver.resolveThreeWay(lower, higher, null).getAction());
    }

    @Test
    void strategiesAreHonoured() {
        GenericSyncableEntity local = row("Daara A", "2026-01-02 10:00:00", 2, "PENDING");
        GenericSyncableEntity remote = row("Daara B", "2026-01-02 11:00:00", 7, "SYNCED");

        assertEquals(ResolutionAction.TAKE_LOCAL,
                resolver.resolveThreeWay(local, remote, null, ResolutionStrategy.LOCAL_WINS).getAction());
        assertEquals(ResolutionAction.TAKE_REMOTE,
                resolver.resolveThreeWay(local, remote, null, ResolutionStrategy.REMOTE_WINS).getAction());
        assertEquals(ResolutionAction.MANUAL_RESOLUTION,
                resolver.resolveThreeWay(local, remote, null, ResolutionStrategy.MANUAL).getAction());
        assertEquals(ResolutionAction.TAKE_REMOTE,
                resolver.resolveThreeWay(local, remote, null, ResolutionStrategy.HIGHER_VERSION_WINS).getAction());
        // Stratégie manuelle : pas de conflit → aucune intervention demandée
        GenericSyncableEntity base = row("Daara", "2026-01-01 10:00:00", 2, "SYNCED");
        assertEquals(ResolutionAction.TAKE_LOCAL,
                resolver.resolveThreeWay(local, base, base.calculateHash(), ResolutionStrategy.MANUAL).getAction());
    }

    @Test
    void strategyFromConfigIsLenient() {
        assertEquals(ResolutionStrategy.MANUAL, ConflictResolver.strategyFromConfig(" manual "));
        assertEquals(ResolutionStrategy.LAST_WRITE_WINS, ConflictResolver.strategyFromConfig(null));
        assertEquals(ResolutionStrategy.LAST_WRITE_WINS, ConflictResolver.strategyFromConfig("n'importe quoi"));
    }
}
