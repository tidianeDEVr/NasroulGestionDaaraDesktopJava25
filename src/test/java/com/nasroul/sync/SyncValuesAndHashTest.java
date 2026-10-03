package com.nasroul.sync;

import com.nasroul.util.DataHashCalculator;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncValuesAndHashTest {

    @Test
    void parsesEveryTemporalRepresentation() {
        LocalDateTime expected = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        assertEquals(expected, SyncValues.parseDateTime("2026-01-01 10:00:00"));
        assertEquals(expected, SyncValues.parseDateTime("2026-01-01T10:00:00"));
        assertEquals(expected, SyncValues.parseDateTime("2026-01-01 10:00:00.0"));
        assertEquals(expected, SyncValues.parseDateTime(Timestamp.valueOf(expected)));
        assertEquals(expected, SyncValues.parseDateTime(expected));
        assertEquals(expected, SyncValues.parseDateTime(1767261600000L));
        assertEquals(expected, SyncValues.parseDateTime("1767261600000"));
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), SyncValues.parseDateTime("2026-01-01"));
        assertNull(SyncValues.parseDateTime(null));
        assertNull(SyncValues.parseDateTime(""));
        assertNull(SyncValues.parseDateTime("pas une date"));
        assertEquals("2026-01-01 10:00:00", SyncValues.normalizeTemporal("1767261600000"));
    }

    @Test
    void hashIsIndependentOfDriverTypes() {
        String fromSqlite = DataHashCalculator.calculateHash(Map.of("amount", 5000.0, "active", 1, "name", "X"));
        String fromMysql = DataHashCalculator.calculateHash(Map.of("amount", 5000, "active", 1L, "name", "X"));
        assertEquals(fromSqlite, fromMysql);

        String blob1 = DataHashCalculator.calculateHash(Map.of("avatar", new byte[]{1, 2, 3}));
        String blob2 = DataHashCalculator.calculateHash(Map.of("avatar", new byte[]{1, 2, 3}));
        String blob3 = DataHashCalculator.calculateHash(Map.of("avatar", new byte[]{1, 2, 4}));
        assertEquals(blob1, blob2);
        assertNotEquals(blob1, blob3);

        assertNotEquals(DataHashCalculator.calculateHash(Map.of("amount", 5000.5)),
                DataHashCalculator.calculateHash(Map.of("amount", 5000)));
    }

    @Test
    void entityHashIgnoresIdAndSyncMetadata() {
        GenericSyncableEntity a = new GenericSyncableEntity("members");
        a.setField("id", 1);
        a.setField("first_name", "Modou");
        a.setField("group_id", 3);
        a.setField("updated_at", "2026-01-01 10:00:00");
        a.setField("sync_status", "PENDING");
        a.setField("sync_version", 4);
        a.setField("last_modified_by", "poste-A");

        GenericSyncableEntity b = new GenericSyncableEntity("members");
        b.setField("id", 57);
        b.setField("first_name", "Modou");
        b.setField("group_id", 3);
        b.setField("updated_at", "2026-02-01 10:00:00");
        b.setField("sync_status", "SYNCED");
        b.setField("sync_version", 9);
        b.setField("last_modified_by", "poste-B");

        assertEquals(a.calculateHash(), b.calculateHash());
        b.setField("group_id", 4);
        assertNotEquals(a.calculateHash(), b.calculateHash());
    }

    @Test
    void entityExposesParsedSyncMetadata() {
        GenericSyncableEntity e = new GenericSyncableEntity("groups");
        e.setField("updated_at", "2026-01-01 10:00:00");
        e.setField("deleted_at", "2026-01-02 10:00:00");
        e.setField("sync_version", 3);
        e.setField("sync_status", "CONFLICT");
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), e.getUpdatedAt());
        assertTrue(e.isDeleted());
        assertEquals(3, e.getSyncVersion());
        assertTrue(e.needsSync());
    }
}
