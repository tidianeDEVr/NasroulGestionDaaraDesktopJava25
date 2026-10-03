package com.nasroul.sync;

import com.nasroul.sync.ConflictResolver.ResolutionAction;
import com.nasroul.sync.ConflictResolver.ResolutionStrategy;
import com.nasroul.sync.SyncManager.SyncResult;
import com.nasroul.sync.SyncTestSupport.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static com.nasroul.sync.SyncTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration de la synchronisation sur de vraies bases SQLite :
 * poste A, poste B et serveur partagé. Aucune interface, aucun MySQL.
 */
class SyncManagerTest {

    private Db a;      // poste A
    private Db b;      // poste B
    private Db server; // base partagée (rôle de MySQL)

    @BeforeEach
    void setUp() throws Exception {
        a = new Db("A");
        b = new Db("B");
        server = new Db("server");
    }

    @AfterEach
    void tearDown() throws Exception {
        a.close();
        b.close();
        server.close();
    }

    // ------------------------------------------------------------ PUSH

    @Test
    void pushCreatesRemoteRowsWithConvertedForeignKeysAndKeepsMapping() throws Exception {
        // Décalage volontaire des ids côté serveur : un groupe étranger occupe l'id 1
        server.insert("groups", group("Autre poste", "2025-12-01 00:00:00"));

        int g = a.insert("groups", group("Daara Touba", "2026-01-02 10:00:00"));
        int m = a.insert("members", member("Modou", "Fall", "771234567", g, "2026-01-02 10:00:00"));
        int e = a.insert("events", event("Gamou", m, "2026-01-02 10:00:00"));
        int p = a.insert("projects", project("Forage", m, "2026-01-02 10:00:00"));
        int c = a.insert("contributions", contribution(m, "EVENT", e, g, 5000, "2026-01-02 10:00:00"));
        int x = a.insert("expenses", expense("Sono", "PROJECT", p, m, 25000, "2026-01-02 10:00:00"));
        int pg = a.insert("payment_groups", paymentGroup(g, "EVENT", e, 5000, "2026-01-02 10:00:00"));

        SyncResult r = manager(a, server).synchronize();

        assertTrue(r.isSuccess(), r.getErrors().toString());
        assertEquals(7, r.getRecordsPushed());
        assertEquals(1, r.getRecordsPulled(), "le groupe étranger est tiré");
        assertTrue(r.getErrors().isEmpty());

        // Mapping persisté pour chaque ligne poussée
        int rg = a.remoteIdOf("groups", g);
        int rm = a.remoteIdOf("members", m);
        int re = a.remoteIdOf("events", e);
        int rp = a.remoteIdOf("projects", p);
        assertEquals(2, rg, "le groupe local #1 devient le distant #2");
        assertNotNull(a.remoteIdOf("contributions", c));
        assertNotNull(a.remoteIdOf("expenses", x));
        assertNotNull(a.remoteIdOf("payment_groups", pg));

        // Clés étrangères exprimées en ids distants
        assertEquals(rg, server.integer("SELECT group_id FROM members WHERE id = ?", rm));
        assertEquals(rm, server.integer("SELECT organizer_id FROM events WHERE id = ?", re));
        assertEquals(rm, server.integer("SELECT manager_id FROM projects WHERE id = ?", rp));
        assertEquals(rm, server.integer("SELECT member_id FROM contributions WHERE entity_id = ?", re));
        assertEquals(rg, server.integer("SELECT group_id FROM contributions WHERE entity_id = ?", re));
        assertEquals(rp, server.integer("SELECT entity_id FROM expenses WHERE description = 'Sono'"));
        assertEquals(rm, server.integer("SELECT member_id FROM expenses WHERE description = 'Sono'"));
        assertEquals(rg, server.integer("SELECT group_id FROM payment_groups WHERE amount = 5000"));
        assertEquals(re, server.integer("SELECT entity_id FROM payment_groups WHERE amount = 5000"),
                "payment_groups.entity_id est polymorphe et doit être converti");

        // L'état de sync n'est pas recopié tel quel : le serveur ne contient pas de PENDING
        assertEquals(0, server.count("members", "sync_status = 'PENDING'"));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM members WHERE id = ?", m));
        assertNotNull(a.string("SELECT last_sync_at FROM members WHERE id = ?", m));
    }

    @Test
    void secondSyncIsIdempotentAndNeverDuplicates() throws Exception {
        int g = a.insert("groups", group("Daara Touba", "2026-01-02 10:00:00"));
        a.insert("members", member("Modou", "Fall", "771234567", g, "2026-01-02 10:00:00"));

        manager(a, server).synchronize();
        SyncResult second = manager(a, server).synchronize();
        SyncResult third = manager(a, server).synchronize();

        for (SyncResult r : new SyncResult[]{second, third}) {
            assertTrue(r.isSuccess());
            assertEquals(0, r.getRecordsPulled(), r.toString());
            assertEquals(0, r.getRecordsPushed(), r.toString());
            assertEquals(0, r.getConflicts());
        }
        assertEquals(1, server.count("groups", null));
        assertEquals(1, server.count("members", null));
        assertEquals(1, a.count("groups", null));
        assertEquals(1, a.count("members", null));
    }

    @Test
    void rowsReferencingADeletedMemberStillSyncEverywhere() throws Exception {
        // Cas réel : membre supprimé (logiquement) avant la première sync, mais
        // toujours organisateur d'un événement, responsable d'un projet et
        // auteur de cotisations. Tout doit arriver sur le serveur puis sur B.
        int g = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int gone = a.insert("members", member("Ancien", "Membre", "770000001", g, "2026-01-02 10:00:00"));
        int e = a.insert("events", event("Gamou", gone, "2026-01-02 10:00:00"));
        int p = a.insert("projects", project("Forage", gone, "2026-01-02 10:00:00"));
        a.insert("contributions", contribution(gone, "EVENT", e, g, 5000, "2026-01-02 10:00:00"));
        a.insert("expenses", expense("Sono", "EVENT", e, gone, 3000, "2026-01-02 10:00:00"));
        a.exec("UPDATE members SET deleted_at = '2026-01-03 10:00:00', updated_at = '2026-01-03 10:00:00' WHERE id = ?", gone);

        SyncResult ra = manager(a, server).synchronize();

        assertTrue(ra.getErrors().isEmpty(), ra.getErrors().toString());
        assertEquals(6, ra.getRecordsPushed());
        assertEquals(0, a.count("sync_log", "status = 'FAILED'"));
        assertEquals(0, a.count("contributions", "sync_status <> 'SYNCED'"));
        assertNotNull(server.string("SELECT deleted_at FROM members WHERE first_name = 'Ancien'"));
        assertEquals(1, server.count("contributions", null));

        SyncResult rb = manager(b, server).synchronize();

        assertTrue(rb.getErrors().isEmpty(), rb.getErrors().toString());
        assertEquals(6, rb.getRecordsPulled());
        int goneB = b.integer("SELECT id FROM members WHERE first_name = 'Ancien'");
        assertNotNull(b.string("SELECT deleted_at FROM members WHERE id = ?", goneB), "le tombstone est importé");
        assertEquals(goneB, b.integer("SELECT organizer_id FROM events WHERE name = 'Gamou'"));
        assertEquals(goneB, b.integer("SELECT manager_id FROM projects WHERE name = 'Forage'"));
        assertEquals(goneB, b.integer("SELECT member_id FROM contributions WHERE amount = 5000"));
        assertEquals(goneB, b.integer("SELECT member_id FROM expenses WHERE description = 'Sono'"));
        assertEquals(0, b.count("members", "deleted_at IS NULL AND first_name = 'Ancien'"), "invisible dans l'application");

        assertEquals(0, manager(a, server).synchronize().getRecordsPulled());
        assertEquals(0, manager(b, server).synchronize().getRecordsPulled());
    }

    @Test
    void orphanForeignKeyFailsOnlyThatRowAndIsReportedInTheResult() throws Exception {
        int g = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int m = a.insert("members", member("Modou", "Fall", "771234567", g, "2026-01-02 10:00:00"));
        int e = a.insert("events", event("Gamou", m, "2026-01-02 10:00:00"));
        // Cotisation orpheline : membre #999 inexistant → référence impossible à convertir
        int orphan = a.insert("contributions", contribution(999, "EVENT", e, g, 1000, "2026-01-02 10:00:00"));
        int ok = a.insert("contributions", contribution(m, "EVENT", e, g, 2000, "2026-01-02 10:00:00"));

        SyncResult r = manager(a, server).synchronize();

        assertTrue(r.isSuccess());
        assertEquals(1, server.count("contributions", null));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM contributions WHERE id = ?", ok));
        assertEquals("PENDING", a.string("SELECT sync_status FROM contributions WHERE id = ?", orphan),
                "la ligne en échec reste en attente pour la prochaine sync");
        assertEquals(1, a.count("sync_log", "table_name = 'contributions' AND status = 'FAILED'"));
        assertEquals(1, r.getRecordsFailed(), "l'échec est visible dans le résultat");
        assertEquals(1, r.getFailedByTable().get("contributions"));
        assertEquals(1, r.getErrors().size());
        assertTrue(r.getErrors().get(0).contains("contributions local #" + orphan), r.getErrors().get(0));
    }

    @Test
    void referencedRowWithoutMappingIsPushedOnTheFly() throws Exception {
        // État hérité d'une ancienne version : membre supprimé marqué SYNCED
        // sans id distant, mais toujours référencé par un événement et des cotisations
        int g = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int gone = a.insert("members", member("Ancien", "Membre", "770000001", g, "2026-01-02 10:00:00"));
        a.exec("UPDATE members SET deleted_at = '2026-01-03 10:00:00', updated_at = '2026-01-03 10:00:00', "
                + "sync_status = 'SYNCED' WHERE id = ?", gone);
        a.exec("INSERT INTO sync_metadata (table_name, record_id, remote_id, sync_version, sync_status) VALUES ('members', ?, NULL, 1, 'SYNCED')", gone);
        int e = a.insert("events", event("Gamou", gone, "2026-01-02 10:00:00"));
        a.insert("contributions", contribution(gone, "EVENT", e, g, 5000, "2026-01-02 10:00:00"));

        SyncResult r = manager(a, server).synchronize();

        assertTrue(r.getErrors().isEmpty(), r.getErrors().toString());
        assertEquals(0, r.getRecordsFailed());
        assertEquals(4, r.getRecordsPushed(), "groupe, événement, cotisation + le membre poussé comme dépendance");
        assertNotNull(a.remoteIdOf("members", gone));
        assertEquals(1, server.count("members", "deleted_at IS NOT NULL"));
        assertEquals(a.remoteIdOf("members", gone), server.integer("SELECT organizer_id FROM events WHERE name = 'Gamou'"));
        assertEquals(1, server.count("contributions", null));
        assertEquals(0, manager(a, server).synchronize().getRecordsPushed());
    }

    // ------------------------------------------------------------ PULL

    @Test
    void pullCreatesLocalRowsAsSyncedWithLocalForeignKeys() throws Exception {
        // Le serveur a été alimenté par un autre poste (ids distants quelconques)
        server.exec("INSERT INTO groups (id, name, active, contribution_target, updated_at, sync_status, sync_version) "
                + "VALUES (40, 'Daara Kaolack', 1, 0, '2026-01-03 09:00:00', 'PENDING', 3)");
        server.exec("INSERT INTO members (id, first_name, last_name, phone, join_date, active, group_id, updated_at, sync_status, sync_version) "
                + "VALUES (70, 'Awa', 'Ndiaye', '761112233', '2026-01-01', 1, 40, '2026-01-03 09:00:00', 'PENDING', 2)");
        server.exec("INSERT INTO events (id, name, start_date, status, organizer_id, active, contribution_target, updated_at, sync_status, sync_version) "
                + "VALUES (12, 'Ziarra', '2026-04-01', 'PLANNED', 70, 1, 0, '2026-01-03 09:00:00', 'PENDING', 1)");
        server.exec("INSERT INTO contributions (id, member_id, entity_type, entity_id, group_id, amount, date, status, updated_at, sync_status, sync_version) "
                + "VALUES (500, 70, 'EVENT', 12, 40, 2500, '2026-02-01', 'PAID', '2026-01-03 09:00:00', 'PENDING', 1)");

        SyncResult r = manager(b, server).synchronize();

        assertTrue(r.isSuccess(), r.getErrors().toString());
        assertEquals(4, r.getRecordsPulled());
        assertEquals(0, r.getRecordsPushed(), "les lignes tirées ne repartent pas vers le serveur");

        int lg = b.integer("SELECT id FROM groups WHERE name = 'Daara Kaolack'");
        int lm = b.integer("SELECT id FROM members WHERE first_name = 'Awa'");
        int le = b.integer("SELECT id FROM events WHERE name = 'Ziarra'");
        assertEquals(1, lg, "ids locaux auto-incrémentés, indépendants du serveur");
        assertEquals(lg, b.integer("SELECT group_id FROM members WHERE id = ?", lm));
        assertEquals(lm, b.integer("SELECT organizer_id FROM events WHERE id = ?", le));
        assertEquals(lm, b.integer("SELECT member_id FROM contributions WHERE amount = 2500"));
        assertEquals(le, b.integer("SELECT entity_id FROM contributions WHERE amount = 2500"));
        assertEquals(lg, b.integer("SELECT group_id FROM contributions WHERE amount = 2500"));

        // Statut local SYNCED malgré le PENDING stocké côté serveur
        assertEquals(0, b.count("members", "sync_status <> 'SYNCED'"));
        assertEquals(40, b.remoteIdOf("groups", lg));
        assertEquals(70, b.remoteIdOf("members", lm));
        assertEquals(3, b.integer("SELECT sync_version FROM groups WHERE id = ?", lg), "la version se propage");

        // Aucun doublon au tour suivant
        SyncResult again = manager(b, server).synchronize();
        assertEquals(0, again.getRecordsPulled());
        assertEquals(0, again.getRecordsPushed());
        assertEquals(1, b.count("members", null));
        assertEquals(1, server.count("members", null));
    }

    @Test
    void remoteTombstoneForUnknownRowIsImportedAsDeleted() throws Exception {
        server.exec("INSERT INTO groups (id, name, active, updated_at, deleted_at, sync_status) "
                + "VALUES (9, 'Supprimé ailleurs', 1, '2026-01-03 09:00:00', '2026-01-03 09:00:00', 'SYNCED')");

        SyncResult r = manager(b, server).synchronize();

        assertTrue(r.isSuccess());
        assertEquals(1, r.getRecordsPulled());
        assertEquals(1, b.count("groups", "deleted_at IS NOT NULL AND name = 'Supprimé ailleurs'"));
        assertEquals("SYNCED", b.string("SELECT sync_status FROM groups WHERE name = 'Supprimé ailleurs'"));
        assertEquals(9, b.remoteIdOf("groups", b.integer("SELECT id FROM groups WHERE name = 'Supprimé ailleurs'")));
        assertEquals(0, manager(b, server).synchronize().getRecordsPulled());
    }

    // ------------------------------------------------- deux postes, aller-retour

    @Test
    void twoDevicesConvergeOnUpdatesAndDeletions() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int ma = a.insert("members", member("Modou", "Fall", "771234567", ga, "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();

        int mb = b.integer("SELECT id FROM members WHERE first_name = 'Modou'");
        assertEquals("Fall", b.string("SELECT last_name FROM members WHERE id = ?", mb));

        // B modifie le téléphone (comme le ferait MemberDAO.update)
        b.exec("UPDATE members SET phone = '770000000', updated_at = '2026-01-05 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", mb);
        SyncResult rb = manager(b, server).synchronize();
        assertEquals(1, rb.getRecordsPushed());
        assertEquals(0, rb.getConflicts());

        SyncResult ra = manager(a, server).synchronize();
        assertEquals(1, ra.getRecordsPulled());
        assertEquals("770000000", a.string("SELECT phone FROM members WHERE id = ?", ma));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM members WHERE id = ?", ma));

        // A supprime le membre : B doit voir la suppression (soft delete)
        a.exec("UPDATE members SET deleted_at = '2026-01-06 10:00:00', updated_at = '2026-01-06 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ma);
        manager(a, server).synchronize();
        manager(b, server).synchronize();

        assertNotNull(server.string("SELECT deleted_at FROM members WHERE phone = '770000000'"));
        assertEquals("2026-01-06 10:00:00", b.string("SELECT deleted_at FROM members WHERE id = ?", mb),
                "la date de suppression d'origine est conservée");
        assertEquals("SYNCED", b.string("SELECT sync_status FROM members WHERE id = ?", mb));

        // Tout le monde est stable
        assertEquals(0, manager(a, server).synchronize().getRecordsPulled());
        assertEquals(0, manager(b, server).synchronize().getRecordsPulled());
        assertEquals(1, server.count("members", null));
        assertEquals(1, a.count("members", null));
        assertEquals(1, b.count("members", null));
    }

    @Test
    void localPendingChangeIsNotOverwrittenByPullWhenRemoteUnchanged() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();

        // Modification locale non encore envoyée
        a.exec("UPDATE groups SET description = 'Nouvelle description', updated_at = '2026-01-04 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);

        SyncResult r = manager(a, server).synchronize();

        assertEquals(0, r.getRecordsPulled(), "le PULL ne doit pas écraser la modification locale");
        assertEquals(1, r.getRecordsPushed());
        assertEquals(0, r.getConflicts(), "un seul côté a changé : pas de conflit");
        assertEquals("Nouvelle description", a.string("SELECT description FROM groups WHERE id = ?", ga));
        assertEquals("Nouvelle description", server.string("SELECT description FROM groups WHERE id = 1"));
    }

    // ------------------------------------------------------------ conflits

    @Test
    void concurrentModificationsResolvedByLastWriteWinsOnBothDevices() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int ma = a.insert("members", member("Modou", "Fall", "771234567", ga, "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int mb = b.integer("SELECT id FROM members WHERE first_name = 'Modou'");

        // Les deux postes modifient le même membre hors ligne ; B est plus récent
        a.exec("UPDATE members SET role = 'TRESORIER', updated_at = '2026-01-05 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ma);
        b.exec("UPDATE members SET role = 'SECRETAIRE', updated_at = '2026-01-05 11:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", mb);

        // A synchronise d'abord : pas encore de conflit visible, sa version part au serveur
        SyncResult ra1 = manager(a, server).synchronize();
        assertEquals(1, ra1.getRecordsPushed());
        assertEquals("TRESORIER", server.string("SELECT role FROM members WHERE id = 1"));

        // B synchronise : conflit, sa version (plus récente) l'emporte
        SyncResult rb = manager(b, server).synchronize();
        assertEquals(1, rb.getConflicts(), rb.toString());
        assertEquals(1, rb.getConflictsByTable().getOrDefault("members", 0));
        assertEquals(1, rb.getRecordsPushed());
        assertEquals("SECRETAIRE", server.string("SELECT role FROM members WHERE id = 1"));
        assertEquals("SECRETAIRE", b.string("SELECT role FROM members WHERE id = ?", mb));

        // A récupère la version gagnante
        SyncResult ra2 = manager(a, server).synchronize();
        assertEquals(1, ra2.getRecordsPulled());
        assertEquals("SECRETAIRE", a.string("SELECT role FROM members WHERE id = ?", ma));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM members WHERE id = ?", ma));
    }

    @Test
    void olderLocalChangeLosesAgainstNewerRemoteChange() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");

        // B modifie (plus récent) et envoie ; A a une modification plus ancienne en attente
        b.exec("UPDATE groups SET description = 'Version B', updated_at = '2026-01-05 12:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", gb);
        manager(b, server).synchronize();
        a.exec("UPDATE groups SET description = 'Version A', updated_at = '2026-01-05 09:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);

        SyncResult ra = manager(a, server).synchronize();

        assertEquals(1, ra.getConflicts());
        assertEquals(0, ra.getRecordsPushed(), "la version locale perdante ne doit pas écraser le serveur");
        assertEquals("Version B", a.string("SELECT description FROM groups WHERE id = ?", ga));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        assertEquals("Version B", server.string("SELECT description FROM groups WHERE id = 1"));
    }

    @Test
    void remoteModificationAfterLocalDeletionRestoresTheRow() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");

        // A supprime à 10h, B modifie à 11h (plus récent) et envoie
        a.exec("UPDATE groups SET deleted_at = '2026-01-05 10:00:00', updated_at = '2026-01-05 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);
        b.exec("UPDATE groups SET description = 'Toujours actif', updated_at = '2026-01-05 11:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", gb);
        manager(b, server).synchronize();

        SyncResult ra = manager(a, server).synchronize();

        assertEquals(1, ra.getConflicts());
        assertNull(a.string("SELECT deleted_at FROM groups WHERE id = ?", ga), "la modification plus récente ressuscite la ligne");
        assertEquals("Toujours actif", a.string("SELECT description FROM groups WHERE id = ?", ga));
        assertNull(server.string("SELECT deleted_at FROM groups WHERE id = 1"));
    }

    @Test
    void localDeletionAfterRemoteModificationWins() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");

        // B modifie à 10h et envoie ; A supprime à 11h (plus récent)
        b.exec("UPDATE groups SET description = 'Modifié par B', updated_at = '2026-01-05 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", gb);
        manager(b, server).synchronize();
        a.exec("UPDATE groups SET deleted_at = '2026-01-05 11:00:00', updated_at = '2026-01-05 11:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);

        SyncResult ra = manager(a, server).synchronize();

        assertEquals(1, ra.getRecordsPushed());
        assertEquals("2026-01-05 11:00:00", server.string("SELECT deleted_at FROM groups WHERE id = 1"));
        manager(b, server).synchronize();
        assertNotNull(b.string("SELECT deleted_at FROM groups WHERE id = ?", gb));
    }

    @Test
    void manualStrategyFlagsConflictAndManualResolutionUnblocksIt() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");

        b.exec("UPDATE groups SET description = 'Version B', updated_at = '2026-01-05 12:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", gb);
        manager(b, server).synchronize();
        a.exec("UPDATE groups SET description = 'Version A', updated_at = '2026-01-05 09:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);

        SyncManager manual = manager(a, server, ResolutionStrategy.MANUAL);
        SyncResult r1 = manual.synchronize();
        assertEquals(1, r1.getConflicts());
        assertEquals("CONFLICT", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        assertEquals("Version A", a.string("SELECT description FROM groups WHERE id = ?", ga), "rien n'est écrasé");
        assertEquals("Version B", server.string("SELECT description FROM groups WHERE id = 1"));

        // Tant que personne ne tranche, la ligne reste en conflit, sans doublon
        SyncResult r2 = manual.synchronize();
        assertEquals(1, r2.getConflicts());
        assertEquals("CONFLICT", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        assertEquals(1, server.count("groups", null));

        // L'utilisateur choisit la version locale : elle part au prochain PUSH sans re-conflit
        manual.resolveConflictManually("groups", ga, ResolutionAction.TAKE_LOCAL);
        assertEquals("PENDING", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        SyncResult r3 = manual.synchronize();
        assertEquals(0, r3.getConflicts());
        assertEquals(1, r3.getRecordsPushed());
        assertEquals("Version A", server.string("SELECT description FROM groups WHERE id = 1"));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        assertEquals("TAKE_LOCAL", a.string("SELECT conflict_resolution FROM sync_metadata WHERE table_name = 'groups' AND record_id = ?", ga));
    }

    @Test
    void manualResolutionTakeRemoteAppliesServerVersion() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        server.exec("UPDATE groups SET description = 'Serveur', updated_at = '2026-01-05 12:00:00', sync_version = 5 WHERE id = 1");
        a.exec("UPDATE groups SET description = 'Local', updated_at = '2026-01-05 13:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ga);

        SyncManager manual = manager(a, server, ResolutionStrategy.MANUAL);
        manual.synchronize();
        assertEquals("CONFLICT", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));

        manual.resolveConflictManually("groups", ga, ResolutionAction.TAKE_REMOTE);

        assertEquals("Serveur", a.string("SELECT description FROM groups WHERE id = ?", ga));
        assertEquals("SYNCED", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
        SyncResult after = manual.synchronize();
        assertEquals(0, after.getConflicts());
        assertEquals(0, after.getRecordsPushed());
    }

    // --------------------------------------------- liaison sans doublon

    @Test
    void sameGroupCreatedOnTwoDevicesIsLinkedNotDuplicated() throws Exception {
        a.insert("groups", group("Daara Touba", "2026-01-02 10:00:00"));
        int gb = b.insert("groups", group("Daara Touba", "2026-01-02 12:00:00"));
        b.exec("UPDATE groups SET description = 'Saisi sur B' WHERE id = ?", gb);

        manager(a, server).synchronize();
        SyncResult rb = manager(b, server).synchronize();

        assertTrue(rb.getErrors().isEmpty(), rb.getErrors().toString());
        assertEquals(1, server.count("groups", null), "un seul groupe sur le serveur");
        assertEquals(1, b.count("groups", null));
        assertEquals(1, b.remoteIdOf("groups", gb), "la ligne de B est reliée à celle du serveur");
        // B est plus récent : sa description gagne et part au serveur
        assertEquals("Saisi sur B", server.string("SELECT description FROM groups WHERE id = 1"));
        assertEquals(1, rb.getConflicts());

        manager(a, server).synchronize();
        assertEquals("Saisi sur B", a.string("SELECT description FROM groups WHERE id = 1"));
        assertEquals(1, a.count("groups", null));
    }

    @Test
    void identicalContributionCreatedOnTwoDevicesIsLinkedByContent() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int ma = a.insert("members", member("Modou", "Fall", "771234567", ga, "2026-01-02 10:00:00"));
        int ea = a.insert("events", event("Gamou", ma, "2026-01-02 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int mb = b.integer("SELECT id FROM members WHERE first_name = 'Modou'");
        int eb = b.integer("SELECT id FROM events WHERE name = 'Gamou'");
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");

        // Même cotisation saisie des deux côtés (ids locaux différents pour les FK)
        a.insert("contributions", contribution(ma, "EVENT", ea, ga, 5000, "2026-01-03 10:00:00"));
        b.insert("contributions", contribution(mb, "EVENT", eb, gb, 5000, "2026-01-03 10:00:00"));

        manager(a, server).synchronize();
        SyncResult rb = manager(b, server).synchronize();

        assertEquals(0, rb.getRecordsPulled());
        assertEquals(0, rb.getRecordsPushed());
        assertEquals(0, rb.getConflicts());
        assertEquals(1, server.count("contributions", null));
        assertEquals(1, b.count("contributions", null));
        assertEquals(1, a.count("contributions", null));
    }

    @Test
    void paymentTargetCollisionKeepsTheMostRecentTarget() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int ma = a.insert("members", member("Modou", "Fall", "771234567", ga, "2026-01-02 10:00:00"));
        int ea = a.insert("events", event("Gamou", ma, "2026-01-02 10:00:00"));
        int pga = a.insert("payment_groups", paymentGroup(ga, "EVENT", ea, 5000, "2026-01-03 10:00:00"));
        manager(a, server).synchronize();
        manager(b, server).synchronize();
        int gb = b.integer("SELECT id FROM groups WHERE name = 'Daara'");
        int eb = b.integer("SELECT id FROM events WHERE name = 'Gamou'");

        // Un second objectif distant (plus récent) pour le même couple groupe/événement,
        // créé par un troisième poste directement sur le serveur
        int rg = a.remoteIdOf("groups", ga);
        int re = a.remoteIdOf("events", ea);
        // MySQL n'a pas l'index d'unicité partiel de SQLite : le serveur peut porter deux objectifs actifs
        server.exec("DROP INDEX IF EXISTS ux_payment_groups_target");
        server.exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount, updated_at, created_at, sync_status, sync_version) "
                + "VALUES (?, 'EVENT', ?, 7500, '2026-01-04 10:00:00', '2026-01-04 10:00:00', 'SYNCED', 1)", rg, re);

        SyncResult rb = manager(b, server).synchronize();

        assertTrue(rb.getErrors().isEmpty(), rb.getErrors().toString());
        assertEquals(1, b.count("payment_groups", "deleted_at IS NULL AND group_id = ? AND entity_id = ?", gb, eb),
                "un seul objectif actif par groupe/entité");
        assertEquals(7500.0, ((Number) b.scalar("SELECT amount FROM payment_groups WHERE deleted_at IS NULL")).doubleValue());
        // La suppression de l'ancien objectif est propagée au serveur
        assertEquals(1, server.count("payment_groups", "deleted_at IS NOT NULL AND amount = 5000"));

        manager(a, server).synchronize();
        assertEquals(1, a.count("payment_groups", "deleted_at IS NULL"));
        assertEquals(7500.0, ((Number) a.scalar("SELECT amount FROM payment_groups WHERE deleted_at IS NULL")).doubleValue());
        assertNotNull(a.string("SELECT deleted_at FROM payment_groups WHERE id = ?", pga));
    }

    @Test
    void columnsPresentOnOneSideOnlyDoNotCauseEndlessResync() throws Exception {
        // Colonne héritée d'anciennes versions, présente sur le poste mais pas sur le serveur
        a.exec("ALTER TABLE projects ADD COLUMN target_budget REAL DEFAULT 0");
        int g = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        int m = a.insert("members", member("Modou", "Fall", "771234567", g, "2026-01-02 10:00:00"));
        a.insert("projects", project("Forage", m, "2026-01-02 10:00:00"));

        SyncResult first = manager(a, server).synchronize();
        assertEquals(3, first.getRecordsPushed());
        for (int i = 0; i < 3; i++) {
            SyncResult again = manager(a, server).synchronize();
            assertEquals(0, again.getRecordsPulled(), "passe " + i + " : " + again);
            assertEquals(0, again.getRecordsPushed(), "passe " + i + " : " + again);
        }
        assertEquals("Forage", server.string("SELECT name FROM projects WHERE id = 1"));
    }

    @Test
    void syncedRowsWithoutRemoteIdAreRepairedAndPushed() throws Exception {
        // Héritage d'un ancien moteur : ligne SYNCED, métadonnée sans remote_id, absente du serveur
        int g = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        a.exec("UPDATE groups SET sync_status = 'SYNCED' WHERE id = ?", g);
        a.exec("INSERT INTO sync_metadata (table_name, record_id, remote_id, sync_version, sync_status) VALUES ('groups', ?, NULL, 1, 'SYNCED')", g);
        // Et une ligne SYNCED sans métadonnée du tout, mais déjà présente sur le serveur : reliée, pas dupliquée
        int g2 = a.insert("groups", group("Daara 2", "2026-01-02 10:00:00"));
        a.exec("UPDATE groups SET sync_status = 'SYNCED' WHERE id = ?", g2);
        server.insert("groups", group("Daara 2", "2026-01-02 10:00:00"));

        SyncResult r = manager(a, server).synchronize();

        assertEquals(1, r.getRecordsPushed());
        assertEquals(2, server.count("groups", null));
        assertNotNull(a.remoteIdOf("groups", g));
        assertNotNull(a.remoteIdOf("groups", g2));
        assertEquals(0, a.count("groups", "sync_status <> 'SYNCED'"));
        assertEquals(0, manager(a, server).synchronize().getRecordsPushed());
    }

    // --------------------------------------------------- member_groups

    @Test
    void memberGroupMembershipsTravelWithTheMemberRow() throws Exception {
        int g1 = a.insert("groups", group("Daara 1", "2026-01-02 10:00:00"));
        int g2 = a.insert("groups", group("Daara 2", "2026-01-02 10:00:00"));
        int ma = a.insert("members", member("Modou", "Fall", "771234567", g1, "2026-01-02 10:00:00"));
        a.exec("INSERT INTO member_groups (member_id, group_id) VALUES (?, ?), (?, ?)", ma, g1, ma, g2);
        // Décalage d'ids côté serveur
        server.insert("groups", group("Autre", "2025-12-01 00:00:00"));

        manager(a, server).synchronize();

        int rm = a.remoteIdOf("members", ma);
        int rg1 = a.remoteIdOf("groups", g1);
        int rg2 = a.remoteIdOf("groups", g2);
        assertEquals(2, server.count("member_groups", "member_id = ?", rm));
        assertEquals(1, server.count("member_groups", "member_id = ? AND group_id = ?", rm, rg1));
        assertEquals(1, server.count("member_groups", "member_id = ? AND group_id = ?", rm, rg2));

        manager(b, server).synchronize();
        int mb = b.integer("SELECT id FROM members WHERE first_name = 'Modou'");
        int gb2 = b.integer("SELECT id FROM groups WHERE name = 'Daara 2'");
        assertEquals(2, b.count("member_groups", "member_id = ?", mb));
        assertEquals(1, b.count("member_groups", "member_id = ? AND group_id = ?", mb, gb2));

        // B retire le membre du second groupe (seule la jointure change, comme MemberDAO.update)
        b.exec("DELETE FROM member_groups WHERE member_id = ? AND group_id = ?", mb, gb2);
        b.exec("UPDATE members SET updated_at = '2026-01-05 10:00:00', sync_status = 'PENDING', "
                + "sync_version = sync_version + 1 WHERE id = ?", mb);
        SyncResult rb = manager(b, server).synchronize();
        assertEquals(1, rb.getRecordsPushed(), "le changement d'appartenance doit partir au serveur");
        assertEquals(1, server.count("member_groups", "member_id = ?", rm));

        SyncResult ra = manager(a, server).synchronize();
        assertEquals(1, ra.getRecordsPulled());
        assertEquals(1, a.count("member_groups", "member_id = ?", ma));
        assertEquals(1, a.count("member_groups", "member_id = ? AND group_id = ?", ma, g1));

        // Stable ensuite
        assertEquals(0, manager(a, server).synchronize().getRecordsPulled());
        assertEquals(0, manager(b, server).synchronize().getRecordsPulled());
    }

    // --------------------------------------------------- robustesse

    @Test
    void legacyRowsWithoutTimestampsOrWithEpochMillisDoNotBreakSync() throws Exception {
        // Ligne historique : aucune colonne de sync renseignée
        a.exec("INSERT INTO groups (name, active) VALUES ('Legacy', 1)");
        a.exec("UPDATE groups SET sync_status = 'PENDING', updated_at = NULL, created_at = NULL WHERE name = 'Legacy'");
        // Date écrite en millisecondes epoch par un ancien setObject(Timestamp)
        server.exec("INSERT INTO groups (id, name, active, updated_at, created_at, sync_status, sync_version) "
                + "VALUES (5, 'Epoch', 1, '1767261600000', '1767261600000', 'SYNCED', 1)");

        SyncResult r = manager(a, server).synchronize();

        assertTrue(r.isSuccess(), r.getErrors().toString());
        assertTrue(r.getErrors().isEmpty());
        assertEquals(1, r.getRecordsPulled());
        assertEquals(1, r.getRecordsPushed());
        assertEquals("2026-01-01 10:00:00", a.string("SELECT updated_at FROM groups WHERE name = 'Epoch'"),
                "les dates sont normalisées au format canonique");
        assertEquals(0, a.count("sync_log", "status = 'FAILED'"));

        // Conflit sur la ligne sans horodatage : décision déterministe, sans NPE
        int lg = a.integer("SELECT id FROM groups WHERE name = 'Legacy'");
        server.exec("UPDATE groups SET description = 'Serveur' WHERE name = 'Legacy'");
        a.exec("UPDATE groups SET description = 'Local', sync_status = 'PENDING' WHERE id = ?", lg);
        SyncResult r2 = manager(a, server).synchronize();
        assertTrue(r2.isSuccess());
        assertEquals(1, r2.getConflicts());
        assertEquals(server.string("SELECT description FROM groups WHERE name = 'Legacy'"),
                a.string("SELECT description FROM groups WHERE id = ?", lg), "les deux bases convergent");
    }

    @Test
    void blobAvatarsAreComparedByContentNotByReference() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        Map<String, Object> m = member("Modou", "Fall", "771234567", ga, "2026-01-02 10:00:00");
        m.put("avatar", new byte[]{1, 2, 3, 4, 5});
        int ma = a.insert("members", m);

        manager(a, server).synchronize();
        SyncResult again = manager(a, server).synchronize();

        assertEquals(0, again.getRecordsPulled(), "même avatar → aucune ré-écriture");
        assertEquals(0, again.getRecordsPushed());
        byte[] remoteAvatar = (byte[]) server.scalar("SELECT avatar FROM members WHERE id = 1");
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, remoteAvatar);
        assertEquals("SYNCED", a.string("SELECT sync_status FROM members WHERE id = ?", ma));
    }

    @Test
    void unavailableRemoteYieldsFailureWithoutTouchingLocalData() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        SyncConnections offline = new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return a.open();
            }

            @Override
            public RemoteStore openRemote() throws SQLException {
                throw new SQLException("Communications link failure");
            }
        };

        SyncResult r = new SyncManager(offline, ResolutionStrategy.LAST_WRITE_WINS).synchronize();

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("hors ligne"));
        assertTrue(r.getErrorMessage().contains("Communications link failure"), "la cause réelle est affichée");
        assertEquals("PENDING", a.string("SELECT sync_status FROM groups WHERE id = ?", ga));
    }

    @Test
    void unavailableMessageExplainsKnownServerRefusals() {
        String notAllowed = SyncManager.buildUnavailableMessage(new SQLException(
                "null,  message from server: \"Host '41.82.181.236' is not allowed to connect to this MariaDB server\"",
                "HY000", 1130));
        assertTrue(notAllowed.contains("41.82.181.236"));
        assertTrue(notAllowed.contains("MySQL distant"));

        String denied = SyncManager.buildUnavailableMessage(new SQLException(
                "Access denied for user 'x'@'host' (using password: YES)", "28000", 1045));
        assertTrue(denied.contains("identifiants"));
    }

    @Test
    void syncLogRecordsEveryOperationLocallyAndOnTheServer() throws Exception {
        int ga = a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        SyncResult r = manager(a, server).synchronize();

        assertEquals(1, a.count("sync_log", "sync_session_id = ? AND table_name = 'groups' AND operation = 'INSERT' AND sync_direction = 'PUSH'",
                r.getSyncSessionId()));
        assertEquals(1, server.count("sync_log", "sync_session_id = ? AND table_name = 'groups' AND operation = 'INSERT'",
                r.getSyncSessionId()));
        assertNotNull(a.remoteIdOf("groups", ga));
    }
}
