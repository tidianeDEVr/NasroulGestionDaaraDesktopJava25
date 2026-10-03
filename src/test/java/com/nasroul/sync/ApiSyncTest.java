package com.nasroul.sync;

import com.nasroul.sync.ConflictResolver.ResolutionStrategy;
import com.nasroul.sync.SyncManager.SyncResult;
import com.nasroul.sync.SyncTestSupport.Db;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

import static com.nasroul.sync.SyncTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Synchronisation de bout en bout à travers la vraie passerelle
 * {@code server/api.php}, servie par le serveur PHP intégré et adossée à une
 * base SQLite jetable (le rôle du MySQL de l'hébergeur). Ignoré si PHP n'est
 * pas installé sur la machine.
 */
class ApiSyncTest {

    private static final String API_KEY = "cle-de-test-suffisamment-longue-0123456789";

    private static Process php;
    private static String apiUrl;
    private static Db serverDb;

    private Db a;
    private Db b;

    @BeforeAll
    static void startPhp() throws Exception {
        String phpBinary = findPhp();
        Assumptions.assumeTrue(phpBinary != null, "php introuvable : test de la passerelle ignoré");

        serverDb = new Db("api-server");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        File docRoot = new File("server").getAbsoluteFile();
        assertTrue(new File(docRoot, "api.php").isFile(), "server/api.php introuvable");

        ProcessBuilder pb = new ProcessBuilder(phpBinary, "-S", "127.0.0.1:" + port, "-t", docRoot.getPath());
        pb.environment().put("NASROUL_API_KEY", API_KEY);
        pb.environment().put("NASROUL_DB_DRIVER", "sqlite");
        pb.environment().put("NASROUL_DB_PATH", serverDb.file.toString());
        Path log = Files.createTempFile("php-server", ".log");
        pb.redirectErrorStream(true).redirectOutput(log.toFile());
        php = pb.start();
        apiUrl = "http://127.0.0.1:" + port + "/api.php";

        // Attendre que le serveur réponde
        SQLException last = null;
        for (int i = 0; i < 50; i++) {
            try {
                new HttpRemoteStore(apiUrl, API_KEY, Duration.ofSeconds(10)).ping();
                last = null;
                break;
            } catch (SQLException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        if (last != null) {
            php.destroy();
            fail("Le serveur PHP ne répond pas : " + last.getMessage() + "\n" + Files.readString(log));
        }
    }

    @AfterAll
    static void stopPhp() throws Exception {
        if (php != null) {
            php.destroy();
            php.waitFor();
        }
        if (serverDb != null) {
            serverDb.close();
        }
    }

    private static String findPhp() {
        for (String candidate : new String[]{"/usr/local/bin/php", "/opt/homebrew/bin/php", "/usr/bin/php"}) {
            if (new File(candidate).canExecute()) {
                return candidate;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, "php");
                if (f.canExecute()) {
                    return f.getPath();
                }
            }
        }
        return null;
    }

    @BeforeEach
    void setUp() throws Exception {
        a = new Db("api-A");
        b = new Db("api-B");
        // Serveur remis à zéro entre les tests
        for (String t : new String[]{"member_groups", "contributions", "expenses", "payment_groups", "events",
                "projects", "members", "groups", "sync_log", "sync_devices"}) {
            serverDb.exec("DELETE FROM " + t);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        a.close();
        b.close();
    }

    private static SyncManager viaApi(Db local) {
        return viaApi(local, API_KEY, ResolutionStrategy.LAST_WRITE_WINS);
    }

    private static SyncManager viaApi(Db local, String key, ResolutionStrategy strategy) {
        SyncConnections conns = new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return local.open();
            }

            @Override
            public RemoteStore openRemote() {
                return new HttpRemoteStore(apiUrl, key, Duration.ofSeconds(30));
            }
        };
        return new SyncManager(conns, strategy);
    }

    @Test
    void fullRoundTripThroughTheGateway() throws Exception {
        int g1 = a.insert("groups", group("Daara Touba", "2026-01-02 10:00:00"));
        int g2 = a.insert("groups", group("Daara Dakar", "2026-01-02 10:00:00"));
        Map<String, Object> m = member("Modou", "Fall", "771234567", g1, "2026-01-02 10:00:00");
        m.put("avatar", new byte[]{0, 1, 2, (byte) 200, (byte) 255});
        int ma = a.insert("members", m);
        a.exec("INSERT INTO member_groups (member_id, group_id) VALUES (?, ?), (?, ?)", ma, g1, ma, g2);
        int gone = a.insert("members", member("Ancien", "Membre", "770000001", g1, "2026-01-02 10:00:00"));
        a.exec("UPDATE members SET deleted_at = '2026-01-03 10:00:00', updated_at = '2026-01-03 10:00:00' WHERE id = ?", gone);
        int e = a.insert("events", event("Gamou", gone, "2026-01-02 10:00:00"));
        int p = a.insert("projects", project("Forage", ma, "2026-01-02 10:00:00"));
        a.insert("contributions", contribution(ma, "EVENT", e, g1, 5000.5, "2026-01-02 10:00:00"));
        a.insert("contributions", contribution(gone, "PROJECT", p, g1, 2000, "2026-01-02 10:00:00"));
        a.insert("expenses", expense("Sono", "EVENT", e, ma, 25000, "2026-01-02 10:00:00"));
        a.insert("payment_groups", paymentGroup(g1, "EVENT", e, 5000, "2026-01-02 10:00:00"));

        SyncResult ra = viaApi(a).synchronize();
        assertTrue(ra.isSuccess(), ra.getErrorMessage());
        assertTrue(ra.getErrors().isEmpty(), ra.getErrors().toString());
        assertEquals(10, ra.getRecordsPushed());
        assertEquals(0, ra.getRecordsFailed());

        // Côté serveur : FK converties, BLOB intact, appartenances, journal, poste déclaré
        int rm = a.remoteIdOf("members", ma);
        int re = a.remoteIdOf("events", e);
        assertEquals(a.remoteIdOf("groups", g1), serverDb.integer("SELECT group_id FROM members WHERE id = ?", rm));
        assertEquals(re, serverDb.integer("SELECT entity_id FROM payment_groups WHERE amount = 5000"));
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 200, (byte) 255},
                (byte[]) serverDb.scalar("SELECT avatar FROM members WHERE id = ?", rm));
        assertEquals(2, serverDb.count("member_groups", "member_id = ?", rm));
        assertEquals(5000.5, ((Number) serverDb.scalar("SELECT amount FROM contributions WHERE amount > 5000")).doubleValue());
        assertEquals(10, serverDb.count("sync_log", "sync_session_id = ?", ra.getSyncSessionId()));
        assertEquals(1, serverDb.count("sync_devices", null));

        // Idempotence côté A
        SyncResult again = viaApi(a).synchronize();
        assertEquals(0, again.getRecordsPulled(), again.toString());
        assertEquals(0, again.getRecordsPushed(), again.toString());

        // Poste B vierge : tout arrive, identique
        SyncResult rb = viaApi(b).synchronize();
        assertTrue(rb.getErrors().isEmpty(), rb.getErrors().toString());
        assertEquals(10, rb.getRecordsPulled());
        int mb = b.integer("SELECT id FROM members WHERE first_name = 'Modou'");
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 200, (byte) 255}, (byte[]) b.scalar("SELECT avatar FROM members WHERE id = ?", mb));
        assertEquals(2, b.count("member_groups", "member_id = ?", mb));
        int goneB = b.integer("SELECT id FROM members WHERE first_name = 'Ancien'");
        assertNotNull(b.string("SELECT deleted_at FROM members WHERE id = ?", goneB));
        assertEquals(goneB, b.integer("SELECT organizer_id FROM events WHERE name = 'Gamou'"));
        assertEquals(b.integer("SELECT id FROM events WHERE name = 'Gamou'"),
                b.integer("SELECT entity_id FROM payment_groups WHERE amount = 5000"));
        assertEquals(5000.5, ((Number) b.scalar("SELECT amount FROM contributions WHERE amount > 5000")).doubleValue());
        assertEquals(0, viaApi(b).synchronize().getRecordsPulled());

        // B modifie, A reçoit ; puis conflit tranché par LWW des deux côtés
        b.exec("UPDATE members SET phone = '770000000', updated_at = '2026-01-05 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", mb);
        assertEquals(1, viaApi(b).synchronize().getRecordsPushed());
        assertEquals(1, viaApi(a).synchronize().getRecordsPulled());
        assertEquals("770000000", a.string("SELECT phone FROM members WHERE id = ?", ma));

        a.exec("UPDATE members SET role = 'TRESORIER', updated_at = '2026-01-06 10:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", ma);
        b.exec("UPDATE members SET role = 'SECRETAIRE', updated_at = '2026-01-06 11:00:00', "
                + "sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = ?", mb);
        viaApi(a).synchronize();
        SyncResult conflict = viaApi(b).synchronize();
        assertEquals(1, conflict.getConflicts());
        viaApi(a).synchronize();
        assertEquals("SECRETAIRE", a.string("SELECT role FROM members WHERE id = ?", ma));
        assertEquals("SECRETAIRE", serverDb.string("SELECT role FROM members WHERE id = ?", rm));
    }

    @Test
    void wrongApiKeyIsReportedClearly() throws Exception {
        a.insert("groups", group("Daara", "2026-01-02 10:00:00"));
        SyncResult r = viaApi(a, "mauvaise-cle-mais-assez-longue-123456", ResolutionStrategy.LAST_WRITE_WINS).synchronize();
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("clé API"), r.getErrorMessage());
        assertTrue(r.getErrorMessage().contains("sync.api.key"), r.getErrorMessage());
        assertEquals(0, serverDb.count("groups", null));
    }

    @Test
    void wrongUrlIsReportedClearly() throws Exception {
        SyncConnections conns = new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return a.open();
            }

            @Override
            public RemoteStore openRemote() {
                return new HttpRemoteStore(apiUrl.replace("api.php", "inexistant.php"), API_KEY, Duration.ofSeconds(10));
            }
        };
        SyncResult r = new SyncManager(conns, ResolutionStrategy.LAST_WRITE_WINS).synchronize();
        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("sync.api.url"), r.getErrorMessage());
    }
}
