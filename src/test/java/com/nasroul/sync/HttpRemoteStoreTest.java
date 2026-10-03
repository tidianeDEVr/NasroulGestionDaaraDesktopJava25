package com.nasroul.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Codage/décodage des valeurs JSON de la passerelle, sans réseau. */
class HttpRemoteStoreTest {

    @Test
    void decodesAccordingToDeclaredColumnTypes() {
        JsonObject row = JsonParser.parseString("""
            {"id": "7", "amount": "5000", "budget": 1500.5, "active": 1, "name": "Daara",
             "updated_at": "2026-01-02 10:00:00.0", "avatar": {"__blob": "%s"}, "phone": "771234567", "notes": null}
            """.formatted(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}))).getAsJsonObject();
        Map<String, String> types = new LinkedHashMap<>();
        types.put("id", "int(11)");
        types.put("amount", "double");
        types.put("budget", "REAL");
        types.put("active", "INTEGER");
        types.put("name", "varchar(255)");
        types.put("updated_at", "datetime");
        types.put("avatar", "longblob");
        types.put("phone", "TEXT");
        types.put("notes", "text");

        GenericSyncableEntity e = HttpRemoteStore.toEntity("members", row, types);

        assertEquals(7, e.getId());
        assertEquals(5000.0, e.getField("amount"));
        assertEquals(1500.5, e.getField("budget"));
        assertEquals(1, e.getField("active"));
        assertEquals("Daara", e.getField("name"));
        assertEquals("2026-01-02 10:00:00", e.getField("updated_at"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) e.getField("avatar"));
        assertEquals("771234567", e.getField("phone"));
        assertNull(e.getField("notes"));
    }

    @Test
    void hashMatchesJdbcRepresentation() {
        // Même contenu lu via JDBC (types natifs) et via JSON (chaînes) → même hash
        GenericSyncableEntity jdbc = new GenericSyncableEntity("contributions");
        jdbc.setField("id", 3);
        jdbc.setField("amount", 5000.0);
        jdbc.setField("member_id", 12);
        jdbc.setField("notes", null);
        jdbc.setField("date", "2026-02-01");

        JsonObject row = JsonParser.parseString("{\"id\": 99, \"amount\": \"5000\", \"member_id\": \"12\", \"notes\": null, \"date\": \"2026-02-01\"}").getAsJsonObject();
        Map<String, String> types = Map.of("id", "int", "amount", "double", "member_id", "int", "notes", "text", "date", "varchar(255)");
        GenericSyncableEntity json = HttpRemoteStore.toEntity("contributions", row, types);

        assertEquals(jdbc.calculateHash(), json.calculateHash());
    }

    @Test
    void encodesBlobsNumbersAndLists() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("avatar", new byte[]{9, 8});
        fields.put("amount", 12.5);
        fields.put("active", true);
        fields.put("name", "X");
        fields.put("nothing", null);
        fields.put(SyncManager.MEMBER_GROUPS_FIELD, List.of(1, 2));

        JsonObject o = HttpRemoteStore.encodeFields(fields);

        assertEquals(Base64.getEncoder().encodeToString(new byte[]{9, 8}), o.getAsJsonObject("avatar").get("__blob").getAsString());
        assertEquals(12.5, o.get("amount").getAsDouble());
        assertEquals(1, o.get("active").getAsInt());
        assertEquals("X", o.get("name").getAsString());
        assertTrue(o.get("nothing").isJsonNull());
        assertEquals(2, o.getAsJsonArray(SyncManager.MEMBER_GROUPS_FIELD).size());
    }
}
