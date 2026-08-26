package com.nasroul.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie les migrations sur une base "ancienne" : ajout et backfill de
 * contributions.group_id, dédoublonnage de payment_groups, index d'unicité,
 * et idempotence (la migration tourne à chaque démarrage).
 */
class MigrationTest {

    private Path dbFile;
    private Connection conn;
    private Statement stmt;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("migration-test", ".db");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        stmt = conn.createStatement();

        // Schéma "ancien" : contributions sans group_id, avec colonnes sync
        stmt.execute("""
            CREATE TABLE contributions (id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER, entity_type TEXT, entity_id INTEGER, amount REAL,
                status TEXT, deleted_at TEXT, sync_status TEXT DEFAULT 'SYNCED', sync_version INTEGER DEFAULT 1)
            """);
        stmt.execute("""
            CREATE TABLE payment_groups (id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER, entity_type TEXT, entity_id INTEGER, amount REAL,
                deleted_at TEXT, updated_at TEXT, sync_status TEXT DEFAULT 'SYNCED', sync_version INTEGER DEFAULT 1)
            """);
        stmt.execute("CREATE TABLE member_groups (member_id INTEGER, group_id INTEGER, PRIMARY KEY (member_id, group_id))");
    }

    @AfterEach
    void tearDown() throws SQLException, IOException {
        stmt.close();
        conn.close();
        Files.deleteIfExists(dbFile);
    }

    private void runMigrations() throws SQLException {
        DatabaseManager.migrateContributionGroupColumn(stmt);
        DatabaseManager.deduplicatePaymentGroups(stmt);
        DatabaseManager.createSmsLogTableSQLite(stmt);
    }

    @Test
    void backfillsSingleGroupMembersAndMarksThemPending() throws Exception {
        stmt.execute("INSERT INTO member_groups VALUES (1, 5)");
        stmt.execute("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status) VALUES (1, 'EVENT', 10, 5000, 'PAID')");

        runMigrations();

        try (ResultSet rs = stmt.executeQuery("SELECT group_id, sync_status FROM contributions WHERE member_id = 1")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt("group_id"));
            // PENDING pour que le backfill se propage vers MySQL au prochain PUSH
            assertEquals("PENDING", rs.getString("sync_status"));
        }
    }

    @Test
    void backfillsMultiGroupMemberWhenOnlyOneGroupHasTarget() throws Exception {
        stmt.execute("INSERT INTO member_groups VALUES (1, 5), (1, 6)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        stmt.execute("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status) VALUES (1, 'EVENT', 10, 3000, 'PAID')");

        runMigrations();

        try (ResultSet rs = stmt.executeQuery("SELECT group_id FROM contributions WHERE member_id = 1")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt("group_id"));
        }
    }

    @Test
    void leavesAmbiguousMultiGroupContributionsNull() throws Exception {
        stmt.execute("INSERT INTO member_groups VALUES (1, 5), (1, 6)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 2000)");
        stmt.execute("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status) VALUES (1, 'EVENT', 10, 3000, 'PAID')");

        runMigrations();

        try (ResultSet rs = stmt.executeQuery("SELECT group_id, sync_status FROM contributions WHERE member_id = 1")) {
            assertTrue(rs.next());
            assertNull(rs.getObject("group_id"));
            // Non modifiée : ne doit pas être marquée PENDING pour rien
            assertEquals("SYNCED", rs.getString("sync_status"));
        }
    }

    @Test
    void deduplicatesPaymentGroupsKeepingTheMostRecent() throws Exception {
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 10000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 2000)");

        runMigrations();

        try (ResultSet rs = stmt.executeQuery(
                "SELECT amount FROM payment_groups WHERE group_id = 5 AND deleted_at IS NULL")) {
            assertTrue(rs.next());
            assertEquals(10000.0, rs.getDouble("amount"));
            assertFalse(rs.next(), "un seul objectif actif doit rester");
        }

        // L'ancien doublon est soft-deleted (propagation par sync), pas détruit
        try (ResultSet rs = stmt.executeQuery(
                "SELECT sync_status FROM payment_groups WHERE group_id = 5 AND deleted_at IS NOT NULL")) {
            assertTrue(rs.next());
            assertEquals("PENDING", rs.getString("sync_status"));
        }
    }

    @Test
    void uniqueIndexBlocksNewDuplicates() throws Exception {
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        runMigrations();

        assertThrows(SQLException.class, () -> stmt.execute(
            "INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 8000)"));

        // Mais un objectif pour un autre groupe ou une autre entité passe
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 8000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'PROJECT', 10, 8000)");
    }

    @Test
    void migrationsAreIdempotent() throws Exception {
        stmt.execute("INSERT INTO member_groups VALUES (1, 5)");
        stmt.execute("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status) VALUES (1, 'EVENT', 10, 5000, 'PAID')");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 8000)");

        runMigrations();

        // Figer l'état puis rejouer la migration : rien ne doit changer
        stmt.execute("UPDATE contributions SET sync_status = 'SYNCED'");
        stmt.execute("UPDATE payment_groups SET sync_status = 'SYNCED'");

        runMigrations();

        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM contributions WHERE sync_status = 'PENDING'")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "une seconde exécution ne doit rien re-modifier");
        }
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM payment_groups WHERE sync_status = 'PENDING'")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM payment_groups WHERE deleted_at IS NULL")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void reportsDuplicateTargetsWithDivergentAmounts() throws Exception {
        stmt.execute("CREATE TABLE `groups` (id INTEGER PRIMARY KEY, name TEXT, deleted_at TEXT)");
        stmt.execute("CREATE TABLE events (id INTEGER PRIMARY KEY, name TEXT, contribution_target REAL, deleted_at TEXT)");
        stmt.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT, contribution_target REAL, deleted_at TEXT)");
        stmt.execute("INSERT INTO `groups` VALUES (5, 'Dahira Fass', NULL)");
        stmt.execute("INSERT INTO events VALUES (10, 'Gamou 2026', 0, NULL)");
        // Deux objectifs pour le même groupe/collecte, montants différents
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 10000)");
        // Doublon aux montants identiques : rien à signaler
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 2000)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 2000)");

        java.util.List<String> report = new java.util.ArrayList<>();
        DatabaseManager.collectDuplicateTargetWarnings(stmt, report);

        assertEquals(1, report.size(), "seul le doublon aux montants divergents est signalé");
        assertTrue(report.get(0).contains("Dahira Fass"));
        assertTrue(report.get(0).contains("Gamou 2026"));
        assertTrue(report.get(0).contains("10000"), "le montant conservé est indiqué");
    }

    @Test
    void reportsLegacyTargetsAndUnattachedContributions() throws Exception {
        stmt.execute("CREATE TABLE `groups` (id INTEGER PRIMARY KEY, name TEXT, deleted_at TEXT)");
        stmt.execute("CREATE TABLE events (id INTEGER PRIMARY KEY, name TEXT, contribution_target REAL, deleted_at TEXT)");
        stmt.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT, contribution_target REAL, deleted_at TEXT)");
        // Ancien budget cible saisi à la main, sans objectif par groupe
        stmt.execute("INSERT INTO events VALUES (10, 'Magal 2025', 1500000, NULL)");
        // Collecte déjà équipée d'un objectif : rien à signaler
        stmt.execute("INSERT INTO events VALUES (11, 'Gamou 2026', 800000, NULL)");
        stmt.execute("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 11, 5000)");
        // Cotisation d'un membre sans groupe : le backfill ne pourra pas la rattacher
        stmt.execute("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status) VALUES (1, 'EVENT', 10, 3000, 'PAID')");

        // Ordre réel : le rapport est établi après l'ajout/backfill de group_id
        DatabaseManager.migrateContributionGroupColumn(stmt);

        java.util.List<String> report = new java.util.ArrayList<>();
        DatabaseManager.collectPostMigrationWarnings(stmt, report);

        assertEquals(2, report.size());
        assertTrue(report.stream().anyMatch(r -> r.contains("Magal 2025") && r.contains("OBJECTIF À DÉFINIR")));
        assertFalse(report.stream().anyMatch(r -> r.contains("Gamou 2026")), "collecte déjà équipée : pas d'alerte");
        assertTrue(report.stream().anyMatch(r -> r.contains("COTISATIONS SANS GROUPE") && r.contains("1 cotisation")));
    }

    @Test
    void smsLogEnforcesOnePhonePerCampaign() throws Exception {
        runMigrations();

        stmt.execute("INSERT INTO sms_log (campaign_id, phone, status, created_at) VALUES ('c1', '+221771234567', 'SENT', datetime('now'))");
        // INSERT OR IGNORE (utilisé par SmsLogDAO) ne crée pas de doublon
        stmt.execute("INSERT OR IGNORE INTO sms_log (campaign_id, phone, status, created_at) VALUES ('c1', '+221771234567', 'PENDING', datetime('now'))");

        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*), MAX(status) FROM sms_log WHERE campaign_id = 'c1'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
            assertEquals("SENT", rs.getString(2), "la ligne SENT d'origine est préservée");
        }
    }
}
