package com.nasroul.sync;

import com.nasroul.dao.SyncMetadataDAO;
import com.nasroul.sync.SyncTestSupport.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncMetadataDAOTest {

    private Db db;
    private SyncMetadataDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        db = new Db("meta");
        dao = new SyncMetadataDAO(db::open);
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void setRemoteIdCreatesTheRowWhenMissing() throws Exception {
        // Le bug d'origine : UPDATE sur une ligne inexistante → mapping perdu
        dao.setRemoteId("groups", 1, 42);
        assertEquals(42, dao.getRemoteId("groups", 1));
        assertEquals(1, dao.getLocalIdByRemoteId("groups", 42));
    }

    @Test
    void saveNeverErasesAnExistingMapping() throws Exception {
        try (Connection c = db.open()) {
            dao.save(c, "members", 7, 99, 1, "h1", "SYNCED");
            dao.save(c, "members", 7, null, 2, "h2", "SYNCED");
            SyncMetadataDAO.SyncMetadata meta = dao.get(c, "members", 7);
            assertEquals(99, meta.getRemoteId());
            assertEquals(2, meta.getSyncVersion());
            assertEquals("h2", meta.getLocalHash());
            assertNotNull(meta.getLastSyncAt());

            dao.save(c, "members", 7, 100, 3, "h3", "SYNCED");
            assertEquals(100, dao.get(c, "members", 7).getRemoteId());

            Map<Integer, Integer> map = dao.getLocalToRemoteMap(c, "members");
            assertEquals(Map.of(7, 100), map);
        }
    }

    @Test
    void legacySaveSignatureStillWorks() throws Exception {
        dao.save("events", 3, 1, "h", "h", "SYNCED");
        assertTrue(dao.exists("events", 3));
        assertNull(dao.getRemoteId("events", 3));
        dao.setRemoteId("events", 3, 8);
        assertEquals(8, dao.get("events", 3).getRemoteId());
    }
}
