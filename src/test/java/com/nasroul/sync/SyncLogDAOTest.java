package com.nasroul.sync;

import com.nasroul.dao.SyncLogDAO;
import com.nasroul.sync.SyncTestSupport.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncLogDAOTest {

    private Db db;
    private SyncLogDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        db = new Db("log");
        dao = new SyncLogDAO(db::open);
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void cleaningRespectsRetentionAndReportsCounts() throws Exception {
        dao.log("s1", "groups", 1, "INSERT", "PUSH", "SUCCESS", null);          // aujourd'hui
        db.exec("INSERT INTO sync_log (sync_session_id, table_name, record_id, operation, sync_direction, status, synced_at) "
                + "VALUES ('s0', 'groups', 2, 'INSERT', 'PUSH', 'SUCCESS', datetime('now', '-45 days'))");
        db.exec("INSERT INTO sync_log (sync_session_id, table_name, record_id, operation, sync_direction, status, synced_at) "
                + "VALUES ('s0', 'groups', 3, 'INSERT', 'PUSH', 'SUCCESS', datetime('now', '-31 days'))");

        assertEquals(3, dao.countAll());
        assertEquals(2, dao.countOlderThan(30));
        assertEquals(0, dao.countOlderThan(60));

        assertEquals(2, dao.cleanOldLogs(30), "renvoie le nombre supprimé");
        assertEquals(1, dao.countAll());
        assertEquals(0, dao.cleanOldLogs(30), "rien de plus à supprimer");

        assertEquals(1, dao.deleteAll());
        assertEquals(0, dao.countAll());
    }
}
