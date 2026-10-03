package com.nasroul.sync;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Normalisation des valeurs qui transitent entre SQLite et MySQL.
 *
 * Les deux moteurs ne renvoient pas les mêmes types Java pour une même
 * colonne (DATETIME → Timestamp/LocalDateTime côté MySQL, TEXT côté SQLite ;
 * un Timestamp lié via setObject sur SQLite est stocké en millisecondes
 * epoch…). Toutes les dates de sync sont donc ramenées au format canonique
 * « yyyy-MM-dd HH:mm:ss » en UTC, identique à ce que produit
 * {@code datetime('now')} dans SQLite.
 */
public final class SyncValues {

    public static final DateTimeFormatter CANONICAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Colonnes temporelles de l'infrastructure de sync. */
    public static final Set<String> TEMPORAL_COLUMNS = Set.of("created_at", "updated_at", "deleted_at", "last_sync_at");

    /** Colonnes de métadonnées de sync (jamais hashées). */
    public static final Set<String> SYNC_META_COLUMNS = Set.of(
            "created_at", "updated_at", "deleted_at", "last_modified_by",
            "sync_status", "sync_version", "last_sync_at");

    /** État de sync propre à chaque poste : jamais recopié d'un côté à l'autre. */
    public static final Set<String> LOCAL_STATE_COLUMNS = Set.of("sync_status", "last_sync_at");

    private SyncValues() {
    }

    /** Horodatage courant au format canonique (UTC, comme datetime('now')). */
    public static String nowUtc() {
        return format(LocalDateTime.now(ZoneOffset.UTC));
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.withNano(0).format(CANONICAL);
    }

    /**
     * Interprète une valeur temporelle quelle que soit sa représentation
     * (String SQLite/MySQL, Timestamp, LocalDateTime, Date, epoch millis).
     * Retourne null si la valeur est vide ou illisible.
     */
    public static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneOffset.UTC);
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit) && text.length() >= 12) {
            // Millisecondes epoch écrites par un ancien setObject(Timestamp) sur SQLite
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(text)), ZoneOffset.UTC);
        }
        String iso = text.replace(' ', 'T');
        try {
            return LocalDateTime.parse(iso);
        } catch (DateTimeParseException ignored) {
            // formats suivants
        }
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // formats suivants
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Valeur temporelle → chaîne canonique (ou null). */
    public static String normalizeTemporal(Object value) {
        return format(parseDateTime(value));
    }

    /**
     * Représentation textuelle stable d'une valeur pour le hash de contenu.
     * Même contenu → même texte quel que soit le moteur d'origine.
     */
    public static String canonicalString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return "blob:" + org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        }
        if (value instanceof java.util.Collection<?> items) {
            StringBuilder sb = new StringBuilder("[");
            for (Object item : items) {
                if (sb.length() > 1) sb.append(',');
                sb.append(canonicalString(item));
            }
            return sb.append(']').toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return Double.toString(d);
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Timestamp || value instanceof LocalDateTime || value instanceof java.util.Date) {
            String formatted = normalizeTemporal(value);
            return formatted != null ? formatted : value.toString();
        }
        return value.toString();
    }

    /**
     * Liaison d'un paramètre JDBC indépendante du moteur : null explicite,
     * BLOB via setBytes, types simples via leurs setters dédiés.
     */
    public static void bind(PreparedStatement pstmt, int index, Object value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.NULL);
        } else if (value instanceof byte[] bytes) {
            pstmt.setBytes(index, bytes);
        } else if (value instanceof String s) {
            pstmt.setString(index, s);
        } else if (value instanceof Integer i) {
            pstmt.setInt(index, i);
        } else if (value instanceof Long l) {
            pstmt.setLong(index, l);
        } else if (value instanceof Double d) {
            pstmt.setDouble(index, d);
        } else if (value instanceof Float f) {
            pstmt.setDouble(index, f.doubleValue());
        } else if (value instanceof Boolean b) {
            pstmt.setInt(index, b ? 1 : 0);
        } else if (value instanceof Timestamp || value instanceof LocalDateTime || value instanceof java.util.Date) {
            pstmt.setString(index, normalizeTemporal(value));
        } else {
            pstmt.setObject(index, value);
        }
    }
}
