package com.nasroul.util;

import org.apache.commons.codec.digest.DigestUtils;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility class for calculating SHA-256 hashes of data records
 * Used for sync conflict detection
 */
public class DataHashCalculator {

    /**
     * Calculate SHA-256 hash from a map of field values
     * Keys are sorted alphabetically for consistent hashing
     *
     * @param fieldValues Map of field names to values
     * @return SHA-256 hash as hex string
     */
    public static String calculateHash(Map<String, Object> fieldValues) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return "";
        }

        // Use TreeMap for sorted keys (consistent ordering)
        TreeMap<String, Object> sortedFields = new TreeMap<>(fieldValues);

        // Build canonical string representation
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedFields.entrySet()) {
            sb.append(entry.getKey()).append("=");
            Object value = entry.getValue();
            if (value != null) {
                sb.append(value.toString());
            }
            sb.append("|");
        }

        // Calculate SHA-256 hash
        return DigestUtils.sha256Hex(sb.toString());
    }

    /**
     * Calculate hash from varargs of key-value pairs
     * Usage: calculateHash("name", "John", "age", 30, "email", "john@example.com")
     *
     * @param keyValuePairs Alternating keys and values
     * @return SHA-256 hash as hex string
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
     * Compare two hashes for equality
     *
     * @param hash1 First hash
     * @param hash2 Second hash
     * @return true if hashes are equal (case-insensitive)
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
