package com.nasroul.util;

import com.nasroul.sync.SyncValues;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Map;
import java.util.TreeMap;

/**
 * Hash SHA-256 du contenu métier d'un enregistrement, utilisé pour détecter
 * les modifications et les conflits de synchronisation.
 *
 * Le rendu de chaque valeur est canonique ({@link SyncValues#canonicalString})
 * pour qu'un même contenu lu depuis SQLite ou MySQL produise le même hash
 * (5000 et 5000.0, Timestamp et texte, BLOB…).
 */
public class DataHashCalculator {

    /**
     * Calculate SHA-256 hash from a map of field values
     * Keys are sorted alphabetically for consistent hashing
     */
    public static String calculateHash(Map<String, Object> fieldValues) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return "";
        }

        TreeMap<String, Object> sortedFields = new TreeMap<>(fieldValues);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedFields.entrySet()) {
            sb.append(entry.getKey()).append("=");
            sb.append(SyncValues.canonicalString(entry.getValue()));
            sb.append("|");
        }

        return DigestUtils.sha256Hex(sb.toString());
    }

    /**
     * Calculate hash from varargs of key-value pairs
     * Usage: calculateHash("name", "John", "age", 30, "email", "john@example.com")
     */
    public static String calculateHash(Object... keyValuePairs) {
        if (keyValuePairs == null || keyValuePairs.length == 0 || keyValuePairs.length % 2 != 0) {
            return "";
        }

        Map<String, Object> fieldValues = new TreeMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = keyValuePairs[i].toString();
            Object value = keyValuePairs[i + 1];
            fieldValues.put(key, value);
        }

        return calculateHash(fieldValues);
    }

    /**
     * Compare two hashes for equality (case-insensitive, null-safe)
     */
    public static boolean hashesEqual(String hash1, String hash2) {
        if (hash1 == null && hash2 == null) {
            return true;
        }
        if (hash1 == null || hash2 == null) {
            return false;
        }
        return hash1.equalsIgnoreCase(hash2);
    }
}
