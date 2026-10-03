package com.nasroul.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nasroul.dao.SyncLogDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base distante accédée à travers la passerelle {@code server/api.php} :
 * requêtes JSON en POST, clé dans l'en-tête {@code X-Api-Key}. Les postes
 * n'ont besoin ni d'identifiants MySQL ni d'autorisation d'IP.
 *
 * Les valeurs sont typées côté Java d'après le type déclaré des colonnes
 * (renvoyé par l'action {@code columns}) pour que le hash de contenu soit
 * identique à celui d'un accès JDBC direct ; les BLOB voyagent en base64
 * sous la forme {@code {"__blob": "..."}}.
 */
public class HttpRemoteStore implements RemoteStore {

    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final Map<String, Map<String, String>> columnTypes = new HashMap<>();

    public HttpRemoteStore(String url, String apiKey, Duration requestTimeout) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sync.api.url manquant");
        }
        this.endpoint = URI.create(url.trim());
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(300);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ------------------------------------------------------------ transport

    JsonObject call(String action, JsonObject body) throws SQLException {
        JsonObject request = body != null ? body : new JsonObject();
        request.addProperty("action", action);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("X-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SQLException("Passerelle injoignable (" + endpoint.getHost() + ") : " + e.getMessage(), "08S01", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Appel interrompu", e);
        }
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            json = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            json = null;
        }
        if (json == null) {
            String excerpt = response.body() != null ? response.body().strip() : "";
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "…";
            }
            throw new SQLException("Réponse inattendue de la passerelle (HTTP " + response.statusCode() + ") : "
                    + (excerpt.isEmpty() ? "vide" : excerpt) + " — vérifiez sync.api.url", "08S01");
        }
        if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
            String error = json.has("error") && !json.get("error").isJsonNull()
                    ? json.get("error").getAsString() : "erreur inconnue";
            int status = response.statusCode();
            int code = status == 401 || status == 403 ? 1045 : 0;
            throw new SQLException("Passerelle api.php : " + error + " (HTTP " + status + ")",
                    status == 401 || status == 403 ? "28000" : "HY000", code);
        }
        return json;
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String key = (String) kv[i];
            Object value = kv[i + 1];
            if (value instanceof JsonElement je) {
                o.add(key, je);
            } else if (value instanceof Number n) {
                o.addProperty(key, n);
            } else if (value == null) {
                o.add(key, null);
            } else {
                o.addProperty(key, value.toString());
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- store

    @Override
    public void ping() throws SQLException {
        call("ping", null);
    }

    @Override
    public void ensureSchema() throws SQLException {
        call("ensure_schema", null);
    }

    @Override
    public void registerDevice(String deviceId, String deviceName, String userName) throws SQLException {
        call("register_device", obj("device_id", deviceId, "device_name", deviceName, "user_name", userName));
    }

    private Map<String, String> columnTypes(String table) throws SQLException {
        Map<String, String> types = columnTypes.get(table);
        if (types == null) {
            types = new LinkedHashMap<>();
            JsonObject json = call("columns", obj("table", table));
            for (JsonElement e : json.getAsJsonArray("columns")) {
                JsonObject col = e.getAsJsonObject();
                String type = col.has("type") && !col.get("type").isJsonNull() ? col.get("type").getAsString() : "";
                types.put(col.get("name").getAsString().toLowerCase(), type.toLowerCase());
            }
            columnTypes.put(table, types);
        }
        return types;
    }

    @Override
    public Set<String> columns(String table) throws SQLException {
        return new LinkedHashSet<>(columnTypes(table).keySet());
    }

    @Override
    public List<GenericSyncableEntity> fetchAll(String table) throws SQLException {
        Map<String, String> types = columnTypes(table);
        JsonObject json = call("fetch_all", obj("table", table));
        List<GenericSyncableEntity> rows = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("rows")) {
            rows.add(toEntity(table, e.getAsJsonObject(), types));
        }
        if ("members".equals(table)) {
            Map<Integer, List<Integer>> byMember = new HashMap<>();
            if (json.has("member_groups") && json.get("member_groups").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("member_groups").entrySet()) {
                    byMember.put(Integer.parseInt(entry.getKey()), toIntList(entry.getValue()));
                }
            }
            for (GenericSyncableEntity row : rows) {
                row.setField(SyncManager.MEMBER_GROUPS_FIELD, byMember.getOrDefault(row.getId(), List.of()));
            }
        }
        return rows;
    }

    @Override
    public GenericSyncableEntity get(String table, int id) throws SQLException {
        Map<String, String> types = columnTypes(table);
        JsonObject json = call("get", obj("table", table, "id", id));
        if (!json.has("row") || json.get("row").isJsonNull()) {
            return null;
        }
        GenericSyncableEntity row = toEntity(table, json.getAsJsonObject("row"), types);
        if ("members".equals(table)) {
            row.setField(SyncManager.MEMBER_GROUPS_FIELD,
                    json.has("group_ids") ? toIntList(json.get("group_ids")) : List.of());
        }
        return row;
    }

    @Override
    public int insert(String table, Map<String, Object> fields) throws SQLException {
        JsonObject json = call("insert", obj("table", table, "fields", encodeFields(fields)));
        return json.get("id").getAsInt();
    }

    @Override
    public void update(String table, int id, Map<String, Object> fields) throws SQLException {
        if (fields.isEmpty()) {
            return;
        }
        call("update", obj("table", table, "id", id, "fields", encodeFields(fields)));
    }

    @Override
    public void writeMemberGroups(int memberId, List<Integer> groupIds) throws SQLException {
        JsonArray ids = new JsonArray();
        for (Integer id : groupIds) {
            ids.add(id);
        }
        call("set_member_groups", obj("member_id", memberId, "group_ids", ids));
    }

    @Override
    public void logBatch(List<SyncLogDAO.Entry> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (SyncLogDAO.Entry e : entries) {
            array.add(obj("sync_session_id", e.syncSessionId(), "table_name", e.tableName(),
                    "record_id", e.recordId(), "operation", e.operation(), "sync_direction", e.syncDirection(),
                    "status", e.status(), "error_message", e.errorMessage(), "synced_at", e.syncedAt()));
        }
        call("log_batch", obj("entries", array));
    }

    @Override
    public void close() {
        // HttpClient sans ressource à libérer
    }

    // ------------------------------------------------------------- codage

    static JsonObject encodeFields(Map<String, Object> fields) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            o.add(e.getKey(), encodeValue(e.getValue()));
        }
        return o;
    }

    static JsonElement encodeValue(Object value) {
        if (value == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value instanceof byte[] bytes) {
            JsonObject blob = new JsonObject();
            blob.addProperty("__blob", Base64.getEncoder().encodeToString(bytes));
            return blob;
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b ? 1 : 0);
        }
        if (value instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (value instanceof java.sql.Timestamp || value instanceof java.time.LocalDateTime
                || value instanceof java.util.Date) {
            return new JsonPrimitive(SyncValues.normalizeTemporal(value));
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object item : list) {
                array.add(encodeValue(item));
            }
            return array;
        }
        return new JsonPrimitive(value.toString());
    }

    static GenericSyncableEntity toEntity(String table, JsonObject row, Map<String, String> types) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : row.entrySet()) {
            String col = e.getKey().toLowerCase();
            fields.put(col, decodeValue(types.getOrDefault(col, ""), e.getValue()));
        }
        return GenericSyncableEntity.fromFields(table, fields);
    }

    /** Valeur JSON → type Java attendu pour cette colonne. */
    static Object decodeValue(String declaredType, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        String type = declaredType != null ? declaredType.toLowerCase() : "";
        if (value.isJsonObject() && value.getAsJsonObject().has("__blob")) {
            JsonElement b = value.getAsJsonObject().get("__blob");
            return b.isJsonNull() ? null : Base64.getDecoder().decode(b.getAsString());
        }
        if (type.contains("blob") || type.contains("binary")) {
            return Base64.getDecoder().decode(value.getAsString());
        }
        if (value.isJsonArray()) {
            return toIntList(value);
        }
        String text = value.getAsString();
        if (type.contains("int")) {
            try {
                long l = text.contains(".") ? (long) Double.parseDouble(text) : Long.parseLong(text.trim());
                return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if (type.contains("double") || type.contains("float") || type.contains("real")
                || type.contains("decimal") || type.contains("numeric")) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if (type.isEmpty() && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            Number n = value.getAsNumber();
            double d = n.doubleValue();
            return d == Math.rint(d) && !text.contains(".") ? (Object) n.intValue() : (Object) d;
        }
        return text;
    }

    private static List<Integer> toIntList(JsonElement array) {
        List<Integer> out = new ArrayList<>();
        if (array != null && array.isJsonArray()) {
            for (JsonElement e : array.getAsJsonArray()) {
                if (!e.isJsonNull()) {
                    out.add(e.getAsInt());
                }
            }
        }
        return out;
    }
}
