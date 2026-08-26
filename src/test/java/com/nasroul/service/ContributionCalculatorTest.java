package com.nasroul.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ContributionCalculatorTest {

    private Path dbFile;
    private String url;
    private ContributionCalculator calculator;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("calculator-test", ".db");
        url = "jdbc:sqlite:" + dbFile;
        calculator = new ContributionCalculator(() -> DriverManager.getConnection(url));

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE members (id INTEGER PRIMARY KEY, first_name TEXT, last_name TEXT, phone TEXT, active INTEGER DEFAULT 1, deleted_at TEXT)");
            stmt.execute("CREATE TABLE member_groups (member_id INTEGER, group_id INTEGER, PRIMARY KEY (member_id, group_id))");
            stmt.execute("CREATE TABLE payment_groups (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER, entity_type TEXT, entity_id INTEGER, amount REAL, deleted_at TEXT)");
            stmt.execute("CREATE TABLE contributions (id INTEGER PRIMARY KEY AUTOINCREMENT, member_id INTEGER, entity_type TEXT, entity_id INTEGER, amount REAL, status TEXT, group_id INTEGER, deleted_at TEXT)");
            stmt.execute("CREATE TABLE events (id INTEGER PRIMARY KEY, deleted_at TEXT)");
            stmt.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY, deleted_at TEXT)");
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    private void exec(String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    void noTargetIsDistinctFromZeroTarget() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");

        // Aucun objectif défini
        var status = calculator.forMember(1, "EVENT", 10, 5);
        assertFalse(status.targetDefined());
        assertNull(status.targetPerMember());
        assertEquals(0.0, status.remaining());

        // Objectif défini à 0 : ce n'est PAS la même chose
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 0)");
        status = calculator.forMember(1, "EVENT", 10, 5);
        assertTrue(status.targetDefined());
        assertEquals(0.0, status.targetPerMember());
    }

    @Test
    void paidIsRealSumAndSurplusIsReported() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 3000, 'PAID', 5)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 5000, 'PAID', 5)");

        var status = calculator.forMember(1, "EVENT", 10, 5);
        // Sur-paiement : payé = somme réelle (8000), jamais dérivé de la cible
        assertEquals(8000.0, status.paid());
        assertEquals(0.0, status.remaining());
        assertEquals(3000.0, status.surplus());
    }

    @Test
    void pendingContributionsAreExcluded() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 2000, 'PENDING', 5)");

        var status = calculator.forMember(1, "EVENT", 10, 5);
        assertEquals(0.0, status.paid());
        assertEquals(5000.0, status.remaining());
    }

    @Test
    void multiGroupMemberPaymentsDoNotLeakAcrossGroups() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO member_groups VALUES (1, 5), (1, 6)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 10000)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 4000, 'PAID', 5)");

        // Le paiement rattaché au groupe 5 ne solde pas la cible du groupe 6
        var statusA = calculator.forMember(1, "EVENT", 10, 5);
        assertEquals(4000.0, statusA.paid());
        assertEquals(6000.0, statusA.remaining());

        var statusB = calculator.forMember(1, "EVENT", 10, 6);
        assertEquals(0.0, statusB.paid());
        assertEquals(5000.0, statusB.remaining());
    }

    @Test
    void legacyContributionsWithoutGroupCountForTheMember() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 2000, 'PAID', NULL)");

        var status = calculator.forMember(1, "EVENT", 10, 5);
        assertEquals(2000.0, status.paid());
        assertEquals(3000.0, status.remaining());
    }

    @Test
    void softDeletedRowsAreIgnored() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount, deleted_at) VALUES (5, 'EVENT', 10, 9999, '2026-01-01')");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id, deleted_at) VALUES (1, 'EVENT', 10, 4000, 'PAID', 5, '2026-01-01')");

        var status = calculator.forMember(1, "EVENT", 10, 5);
        assertEquals(5000.0, status.targetPerMember());
        assertEquals(0.0, status.paid());
    }

    @Test
    void expectedForGroupMultipliesTargetByActiveMembers() throws Exception {
        exec("INSERT INTO members (id, active) VALUES (1, 1), (2, 1), (3, 0)");
        exec("INSERT INTO members (id, active, deleted_at) VALUES (4, 1, '2026-01-01')");
        exec("INSERT INTO member_groups VALUES (1, 5), (2, 5), (3, 5), (4, 5)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");

        // 2 membres actifs non supprimés × 5000 (le bug d'origine affichait 5000)
        assertEquals(10000.0, calculator.expectedForGroup("EVENT", 10, 5));
    }

    @Test
    void expectedForEntitySumsAllGroups() throws Exception {
        exec("INSERT INTO members (id) VALUES (1), (2), (3)");
        exec("INSERT INTO member_groups VALUES (1, 5), (2, 5), (3, 6)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (6, 'EVENT', 10, 2000)");

        assertEquals(2 * 5000.0 + 1 * 2000.0, calculator.expectedForEntity("EVENT", 10));
    }

    @Test
    void recoveryForGroupMatchesForMemberForEveryRow() throws Exception {
        // Jeu de données varié : payé exact, surplus, retard, legacy NULL, sans objectif
        exec("INSERT INTO members (id, first_name, last_name) VALUES (1, 'Awa', 'Diallo'), (2, 'Moussa', 'Ndiaye'), (3, 'Fatou', 'Sarr'), (4, 'Ibrahima', 'Fall')");
        exec("INSERT INTO member_groups VALUES (1, 5), (2, 5), (3, 5), (4, 5)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (1, 'EVENT', 10, 5000, 'PAID', 5)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (2, 'EVENT', 10, 8000, 'PAID', 5)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (3, 'EVENT', 10, 2000, 'PAID', NULL)");
        exec("INSERT INTO contributions (member_id, entity_type, entity_id, amount, status, group_id) VALUES (4, 'EVENT', 10, 1000, 'PENDING', 5)");

        var rows = calculator.recoveryForGroup("EVENT", 10, 5);
        assertEquals(4, rows.size());

        // Chaque ligne de la vue agrégée == le calcul individuel : la source
        // unique de vérité est verrouillée
        for (var row : rows) {
            var status = calculator.forMember(row.memberId(), "EVENT", 10, 5);
            assertEquals(status.targetPerMember(), row.targetPerMember(), "cible " + row.fullName());
            assertEquals(status.paid(), row.paid(), "payé " + row.fullName());
            assertEquals(status.remaining(), row.remaining(), "restant " + row.fullName());
            assertEquals(status.surplus(), row.surplus(), "surplus " + row.fullName());
        }
    }

    @Test
    void totalExpectedAllExcludesDeletedEntities() throws Exception {
        exec("INSERT INTO members (id) VALUES (1)");
        exec("INSERT INTO member_groups VALUES (1, 5)");
        exec("INSERT INTO events (id) VALUES (10)");
        exec("INSERT INTO events (id, deleted_at) VALUES (11, '2026-01-01')");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 10, 5000)");
        exec("INSERT INTO payment_groups (group_id, entity_type, entity_id, amount) VALUES (5, 'EVENT', 11, 7000)");

        assertEquals(5000.0, calculator.totalExpectedAll());
    }
}
