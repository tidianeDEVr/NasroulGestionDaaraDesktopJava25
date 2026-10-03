package com.nasroul.sync;

import com.nasroul.dao.DatabaseManager;
import com.nasroul.dao.SyncLogDAO;
import com.nasroul.dao.SyncMetadataDAO;
import com.nasroul.dao.SyncMetadataDAO.SyncMetadata;
import com.nasroul.sync.ConflictResolver.Resolution;
import com.nasroul.sync.ConflictResolver.ResolutionAction;
import com.nasroul.sync.ConflictResolver.ResolutionStrategy;
import com.nasroul.util.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrateur de synchronisation : PULL (distant → local) puis PUSH
 * (local → distant) entre la base SQLite du poste et la base MySQL partagée.
 *
 * Principes :
 * <ul>
 *   <li>chaque ligne locale est reliée à sa ligne distante par
 *       {@code sync_metadata.remote_id} ; les ids auto-incrémentés diffèrent
 *       d'un poste à l'autre, toutes les clés étrangères sont converties ;</li>
 *   <li>le contenu est comparé par hash métier (sans id ni métadonnées) dans
 *       l'espace d'ids local ; le hash de la dernière sync sert de base à une
 *       fusion à trois voies ({@link ConflictResolver#resolveThreeWay}) ;</li>
 *   <li>une seule connexion locale et une seule distante par session ; chaque
 *       ligne est traitée dans sa propre transaction locale (ligne + mapping +
 *       métadonnées + journal), une erreur n'affecte que cette ligne ;</li>
 *   <li>l'état de sync ({@code sync_status}, {@code last_sync_at}) est propre
 *       à chaque base et n'est jamais recopié d'un côté à l'autre.</li>
 * </ul>
 */
public class SyncManager {

    /** Tables synchronisées, dans l'ordre des dépendances (parents d'abord). */
    static final String[] SYNC_TABLES = {
        "groups", "members", "events", "projects", "expenses", "contributions", "payment_groups"
    };

    private final SyncConnections connections;
    private final SyncMetadataDAO syncMetadataDAO;
    private final SyncLogDAO syncLogDAO;
    private final ConflictResolver conflictResolver;

    private String currentSyncSession;

    /** Constructeur de production : SQLite + MySQL via DatabaseManager, stratégie depuis la config. */
    public SyncManager() {
        this(SyncConnections.fromConfig(ConfigManager.getInstance(), DatabaseManager.getInstance()),
             ConflictResolver.strategyFromConfig(ConfigManager.getInstance().getSyncConflictStrategy()));
    }

    public SyncManager(SyncConnections connections, ResolutionStrategy strategy) {
        this.connections = connections;
        this.syncMetadataDAO = new SyncMetadataDAO(connections::openLocal);
        this.syncLogDAO = new SyncLogDAO(connections::openLocal);
        this.conflictResolver = new ConflictResolver();
        this.conflictResolver.setDefaultStrategy(strategy);
    }

    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    // =========================================================== orchestration

    /**
     * Perform full synchronization: PULL then PUSH
     */
    public SyncResult synchronize() throws SQLException {
        currentSyncSession = UUID.randomUUID().toString();
        SyncResult result = new SyncResult();
        result.setSyncSessionId(currentSyncSession);

        SQLException unavailable = probeRemote();
        if (unavailable != null) {
            result.setSuccess(false);
            result.setErrorMessage(buildUnavailableMessage(unavailable));
            return result;
        }

        try {
            connections.prepareRemoteSchema();
        } catch (RuntimeException e) {
            System.err.println("Préparation du schéma distant impossible : " + e.getMessage());
        }

        try (Connection local = connections.openLocal();
             RemoteStore remote = connections.openRemote()) {

            Session session = new Session(currentSyncSession, local, remote, result);
            local.setAutoCommit(false);

            try {
                // PHASE 1 : PULL — changements distants vers le local
                for (String table : SYNC_TABLES) {
                    try {
                        result.addPulled(table, pullTable(session, table));
                    } catch (SQLException e) {
                        rollbackQuietly(local);
                        result.addError(table + " pull failed: " + e.getMessage());
                        session.logLocal(table, 0, "PULL", "PULL", "FAILED", e.getMessage());
                        local.commit();
                    }
                }

                // PHASE 2 : PUSH — changements locaux vers le distant
                for (String table : SYNC_TABLES) {
                    try {
                        result.addPushed(table, pushTable(session, table));
                    } catch (SQLException e) {
                        rollbackQuietly(local);
                        result.addError(table + " push failed: " + e.getMessage());
                        session.logLocal(table, 0, "PUSH", "PUSH", "FAILED", e.getMessage());
                        local.commit();
                    }
                }

                local.commit();
            } finally {
                try {
                    local.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connexion en cours de fermeture
                }
            }

            // Journal partagé côté serveur : un seul aller-retour, en meilleur effort
            try {
                remote.logBatch(session.remoteLogs);
            } catch (SQLException e) {
                System.err.println("Journal de sync distant indisponible : " + e.getMessage());
            }

            result.setSuccess(true);
            return result;

        } catch (SQLException e) {
            result.setSuccess(false);
            result.setErrorMessage(getUserFriendlyErrorMessage(e));
            throw e;
        }
    }

    /** Tente d'ouvrir le distant ; renvoie l'erreur (null si joignable). */
    private SQLException probeRemote() {
        try (RemoteStore probe = connections.openRemote()) {
            probe.ping();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    /**
     * Message « serveur injoignable » enrichi de la cause réelle et, pour les
     * erreurs connues, de la marche à suivre.
     */
    static String buildUnavailableMessage(SQLException e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Impossible de se connecter au serveur.\n\n");
        sb.append("L'application continue de fonctionner en mode hors ligne.\n");
        sb.append("Vos données sont sauvegardées localement et seront synchronisées\n");
        sb.append("lors de la prochaine connexion.\n\n");

        String detail = e.getMessage() != null ? e.getMessage().split("\n")[0].trim() : "";
        int code = e.getErrorCode();
        if (code == 1130 || detail.contains("is not allowed to connect")) {
            String ip = extractIp(detail);
            sb.append("Cause : le serveur MySQL refuse ce poste")
              .append(ip != null ? " (adresse IP " + ip + ")" : "")
              .append(".\n")
              .append("Dans cPanel, ouvrez « MySQL distant » (Remote MySQL) et ajoutez\n")
              .append("cette adresse IP aux hôtes autorisés, puis réessayez.\n");
        } else if (detail.contains("Passerelle api.php") && (code == 1045 || "28000".equals(e.getSQLState()))) {
            sb.append("Cause : la passerelle refuse la clé API.\n")
              .append("Vérifiez que sync.api.key dans config.properties est identique à la clé\n")
              .append("définie dans api.config.php sur le serveur.\n");
        } else if (detail.contains("Passerelle injoignable") || detail.contains("Réponse inattendue")) {
            sb.append("Cause : la passerelle de synchronisation ne répond pas correctement.\n")
              .append("Vérifiez sync.api.url (adresse complète de api.php) et que le fichier\n")
              .append("est bien déposé sur l'hébergement.\n");
        } else if (code == 1045 || detail.toLowerCase().contains("access denied")) {
            sb.append("Cause : identifiants refusés par le serveur.\n")
              .append("Vérifiez db.mysql.username et db.mysql.password dans config.properties.\n");
        } else if (code == 1049 || detail.toLowerCase().contains("unknown database")) {
            sb.append("Cause : la base db.mysql.database n'existe pas sur le serveur.\n");
        } else {
            sb.append("Veuillez vérifier:\n")
              .append("• Votre connexion Internet\n")
              .append("• Les paramètres de connexion au serveur (hôte sans https://, port 3306)\n")
              .append("• Que le serveur est bien démarré\n");
        }
        if (!detail.isEmpty()) {
            sb.append("\nDétail technique : ").append(detail);
        }
        return sb.toString();
    }

    private static String extractIp(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,3}(?:\\.\\d{1,3}){3})").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Résolution manuelle d'un conflit laissé en statut CONFLICT (stratégie
     * MANUAL). TAKE_LOCAL : la ligne locale repart en PENDING avec pour base
     * le contenu distant actuel, le prochain PUSH l'enverra sans re-détecter
     * de conflit. TAKE_REMOTE : la version distante est appliquée localement.
     */
    public void resolveConflictManually(String tableName, int localId, ResolutionAction action) throws SQLException {
        if (action != ResolutionAction.TAKE_LOCAL && action != ResolutionAction.TAKE_REMOTE) {
            throw new IllegalArgumentException("Action de résolution invalide : " + action);
        }
        try (Connection local = connections.openLocal();
             RemoteStore remote = connections.openRemote()) {
            Session session = new Session(UUID.randomUUID().toString(), local, remote, new SyncResult());
            local.setAutoCommit(false);
            try {
                GenericSyncableEntity localRow = getLocalEntity(session, tableName, localId);
                if (localRow == null) {
                    throw new SQLException("Ligne locale introuvable : " + tableName + " #" + localId);
                }
                Integer remoteId = session.mapping(tableName).localToRemote.get(localId);
                GenericSyncableEntity remoteRow = remoteId != null ? session.getRemote(tableName, remoteId) : null;
                GenericSyncableEntity remoteLocal = remoteRow != null ? toLocalSpace(session, tableName, remoteRow) : null;

                if (action == ResolutionAction.TAKE_REMOTE && remoteLocal == null) {
                    throw new SQLException("Version distante introuvable pour " + tableName + " #" + localId);
                }
                if (action == ResolutionAction.TAKE_REMOTE) {
                    applyRemoteToLocal(session, tableName, localId, remoteLocal);
                    syncMetadataDAO.save(local, tableName, localId, remoteId,
                            versionOf(remoteLocal), remoteLocal.calculateHash(), "SYNCED");
                    syncMetadataDAO.markConflictResolved(local, tableName, localId, "TAKE_REMOTE");
                    session.logLocal(tableName, localId, "RESOLVE", "PULL", "SUCCESS", "Résolution manuelle : version distante");
                } else {
                    // Base := contenu distant actuel → au PUSH, seule la version locale aura changé
                    String base = remoteLocal != null ? remoteLocal.calculateHash() : null;
                    syncMetadataDAO.save(local, tableName, localId, remoteId, versionOf(localRow), base, "PENDING");
                    syncMetadataDAO.markConflictResolved(local, tableName, localId, "TAKE_LOCAL");
                    setSyncStatus(local, tableName, localId, "PENDING");
                    session.logLocal(tableName, localId, "RESOLVE", "PUSH", "SUCCESS", "Résolution manuelle : version locale");
                }
                local.commit();
            } catch (SQLException e) {
                rollbackQuietly(local);
                throw e;
            } finally {
                local.setAutoCommit(true);
            }
        }
    }

    // ===================================================================== PULL

    private int pullTable(Session s, String table) throws SQLException {
        List<GenericSyncableEntity> remoteRows = s.fetchRemote(table);
        int pulled = 0;
        for (GenericSyncableEntity remoteRow : remoteRows) {
            Integer remoteId = remoteRow.getId();
            if (remoteId == null) {
                continue;
            }
            try {
                if (pullRow(s, table, remoteRow)) {
                    pulled++;
                }
                s.local.commit();
            } catch (Exception e) {
                rollbackQuietly(s.local);
                s.logLocal(table, remoteId, "PULL", "PULL", "FAILED", e.getMessage());
                s.result.addRowFailure(table, "distant #" + remoteId, e.getMessage());
                s.local.commit();
            }
        }
        return pulled;
    }

    /**
     * Traite une ligne distante. Retourne true si la base locale a été modifiée.
     */
    private boolean pullRow(Session s, String table, GenericSyncableEntity remoteRow) throws SQLException {
        int remoteId = remoteRow.getId();
        GenericSyncableEntity remoteLocal = toLocalSpace(s, table, remoteRow);
        TableMapping mapping = s.mapping(table);

        Integer localId = mapping.remoteToLocal.get(remoteId);
        GenericSyncableEntity localRow = localId != null ? getLocalEntity(s, table, localId) : null;

        if (localId != null && localRow == null) {
            // Mapping périmé (ligne locale supprimée physiquement) : on l'oublie
            syncMetadataDAO.delete(s.local, table, localId);
            mapping.unmap(localId, remoteId);
            localId = null;
        }

        if (localId == null) {
            // Ligne inconnue ici : déjà présente sans mapping (créée des deux
            // côtés, ou antérieure à la sync) ? On la relie plutôt que la dupliquer.
            GenericSyncableEntity match = findUnmappedLocalMatch(s, table, remoteLocal);
            if (match != null) {
                localId = match.getId();
                localRow = match;
                mapping.map(localId, remoteId);
                s.forgetUnmapped(table, localId);
                syncMetadataDAO.setRemoteId(s.local, table, localId, remoteId);
                s.logLocal(table, localId, "LINK", "PULL", "SUCCESS",
                        "Ligne locale reliée à la ligne distante #" + remoteId);
            } else {
                // Même supprimée, la ligne est créée localement (avec deleted_at) :
                // les lignes qui la référencent doivent pouvoir être importées.
                if ("payment_groups".equals(table) && !remoteRow.isDeleted()
                        && !resolvePaymentGroupCollision(s, remoteLocal)) {
                    s.logLocal(table, remoteId, "SKIP", "PULL", "SUCCESS",
                            "Objectif en double : la version locale plus récente est conservée");
                    return false;
                }
                int newLocalId = insertLocal(s, table, remoteLocal);
                mapping.map(newLocalId, remoteId);
                syncMetadataDAO.save(s.local, table, newLocalId, remoteId,
                        versionOf(remoteLocal), remoteLocal.calculateHash(), "SYNCED");
                s.logLocal(table, newLocalId, "INSERT", "PULL", "SUCCESS", null);
                s.logRemote(table, remoteId, "INSERT", "PULL", "SUCCESS", null);
                return true;
            }
        }

        // Ligne connue : fusion à trois voies
        SyncMetadata meta = syncMetadataDAO.get(s.local, table, localId);
        String base = meta != null ? meta.getLocalHash() : null;
        Resolution resolution = conflictResolver.resolveThreeWay(localRow, remoteLocal, base);

        switch (resolution.getAction()) {
            case NO_ACTION -> {
                if (localRow.needsSync()) {
                    setSyncStatus(s.local, table, localId, "SYNCED");
                }
                String hash = remoteLocal.calculateHash();
                boolean metaUpToDate = meta != null && meta.getRemoteId() != null
                        && hash.equals(meta.getLocalHash()) && "SYNCED".equals(meta.getSyncStatus());
                if (!metaUpToDate) {
                    syncMetadataDAO.save(s.local, table, localId, remoteId, versionOf(remoteLocal), hash, "SYNCED");
                }
                return false;
            }
            case TAKE_REMOTE -> {
                applyRemoteToLocal(s, table, localId, remoteLocal);
                syncMetadataDAO.save(s.local, table, localId, remoteId,
                        versionOf(remoteLocal), remoteLocal.calculateHash(), "SYNCED");
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, remoteLocal.isDeleted() ? "DELETE" : "UPDATE", "PULL", "SUCCESS",
                        resolution.isConflict() ? "Conflit : " + resolution.getReason() : null);
                s.logRemote(table, remoteId, "UPDATE", "PULL", "SUCCESS", null);
                return true;
            }
            case TAKE_LOCAL -> {
                // La version locale l'emporte : elle doit partir au PUSH
                if (!localRow.needsSync()) {
                    setSyncStatus(s.local, table, localId, "PENDING");
                }
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, "SKIP", "PULL", "SUCCESS",
                        "Version locale conservée pour le PUSH : " + resolution.getReason());
                return false;
            }
            case MANUAL_RESOLUTION -> {
                setSyncStatus(s.local, table, localId, "CONFLICT");
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, "CONFLICT", "PULL", "SUCCESS", resolution.getReason());
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    // ===================================================================== PUSH

    private int pushTable(Session s, String table) throws SQLException {
        repairUnmappedSyncedRows(s, table);
        List<GenericSyncableEntity> pendingRows = fetchLocal(s, table, "sync_status IN ('PENDING', 'CONFLICT')");
        int pushed = 0;
        for (GenericSyncableEntity localRow : pendingRows) {
            Integer localId = localRow.getId();
            if (localId == null) {
                continue;
            }
            try {
                if (pushRow(s, table, localRow)) {
                    pushed++;
                }
                s.local.commit();
            } catch (Exception e) {
                rollbackQuietly(s.local);
                s.logLocal(table, localId, "PUSH", "PUSH", "FAILED", e.getMessage());
                s.result.addRowFailure(table, "local #" + localId, e.getMessage());
                s.local.commit();
            }
        }
        return pushed;
    }

    /**
     * Une ligne marquée SYNCED sans id distant est incohérente (ancienne version
     * du moteur, mapping perdu) : le PULL vient de relier tout ce qui existait
     * déjà sur le serveur, ce qui reste n'y est donc pas et doit être envoyé.
     */
    private void repairUnmappedSyncedRows(Session s, String table) throws SQLException {
        String sql = "UPDATE `" + table + "` SET sync_status = 'PENDING' WHERE sync_status = 'SYNCED' "
                + "AND id NOT IN (SELECT record_id FROM sync_metadata WHERE table_name = ? AND remote_id IS NOT NULL)";
        try (PreparedStatement pstmt = s.local.prepareStatement(sql)) {
            pstmt.setString(1, table);
            int repaired = pstmt.executeUpdate();
            if (repaired > 0) {
                s.local.commit();
                s.logLocal(table, 0, "REPAIR", "PUSH", "SUCCESS",
                        repaired + " ligne(s) marquée(s) synchronisée(s) sans id distant : renvoyée(s)");
            }
        }
    }

    /**
     * Traite une ligne locale en attente. Retourne true si le distant a été modifié.
     */
    private boolean pushRow(Session s, String table, GenericSyncableEntity localRow) throws SQLException {
        int localId = localRow.getId();
        TableMapping mapping = s.mapping(table);
        Integer remoteId = mapping.localToRemote.get(localId);
        GenericSyncableEntity remoteRow = remoteId != null ? s.getRemote(table, remoteId) : null;

        if (remoteRow == null) {
            // Une ligne supprimée (suppression logique) part aussi : d'autres
            // lignes peuvent la référencer (cotisations d'un membre parti,
            // organisateur d'un événement…), elle doit exister sur chaque poste.
            int newRemoteId = insertRemote(s, table, localRow);
            mapping.map(localId, newRemoteId);
            syncMetadataDAO.save(s.local, table, localId, newRemoteId,
                    versionOf(localRow), localRow.calculateHash(), "SYNCED");
            setSyncStatus(s.local, table, localId, "SYNCED");
            s.logLocal(table, localId, "INSERT", "PUSH", "SUCCESS", null);
            s.logRemote(table, newRemoteId, "INSERT", "PUSH", "SUCCESS", null);
            return true;
        }

        GenericSyncableEntity remoteLocal = toLocalSpace(s, table, remoteRow);
        SyncMetadata meta = syncMetadataDAO.get(s.local, table, localId);
        String base = meta != null ? meta.getLocalHash() : null;
        Resolution resolution = conflictResolver.resolveThreeWay(localRow, remoteLocal, base);

        switch (resolution.getAction()) {
            case NO_ACTION -> {
                setSyncStatus(s.local, table, localId, "SYNCED");
                syncMetadataDAO.save(s.local, table, localId, remoteId,
                        versionOf(localRow), localRow.calculateHash(), "SYNCED");
                return false;
            }
            case TAKE_LOCAL -> {
                updateRemote(s, table, remoteId, localRow);
                setSyncStatus(s.local, table, localId, "SYNCED");
                syncMetadataDAO.save(s.local, table, localId, remoteId,
                        versionOf(localRow), localRow.calculateHash(), "SYNCED");
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, localRow.isDeleted() ? "DELETE" : "UPDATE", "PUSH", "SUCCESS",
                        resolution.isConflict() ? "Conflit : " + resolution.getReason() : null);
                s.logRemote(table, remoteId, "UPDATE", "PUSH", "SUCCESS", null);
                return true;
            }
            case TAKE_REMOTE -> {
                applyRemoteToLocal(s, table, localId, remoteLocal);
                syncMetadataDAO.save(s.local, table, localId, remoteId,
                        versionOf(remoteLocal), remoteLocal.calculateHash(), "SYNCED");
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, "UPDATE", "PULL", "SUCCESS",
                        "Conflit : " + resolution.getReason());
                return false;
            }
            case MANUAL_RESOLUTION -> {
                setSyncStatus(s.local, table, localId, "CONFLICT");
                s.countConflict(table, localId, resolution);
                s.logLocal(table, localId, "CONFLICT", "PUSH", "SUCCESS", resolution.getReason());
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    // ====================================================== liaison sans mapping

    /**
     * Cherche parmi les lignes locales SANS mapping une ligne correspondant à
     * la ligne distante : même contenu (hash), puis clé naturelle (nom de
     * groupe, email de membre…). Évite les doublons quand la même donnée a été
     * saisie sur deux postes ou existait avant la mise en place de la sync.
     */
    private GenericSyncableEntity findUnmappedLocalMatch(Session s, String table,
                                                         GenericSyncableEntity remoteLocal) throws SQLException {
        List<GenericSyncableEntity> candidates = s.unmappedLocalRows(table);
        if (candidates.isEmpty()) {
            return null;
        }

        String remoteHash = remoteLocal.calculateHash();
        for (GenericSyncableEntity candidate : candidates) {
            if (candidate.isDeleted() == remoteLocal.isDeleted()
                    && remoteHash.equals(candidate.calculateHash())) {
                return candidate;
            }
        }

        List<String> key = naturalKey(table, remoteLocal);
        if (key == null) {
            return null;
        }
        GenericSyncableEntity deletedMatch = null;
        for (GenericSyncableEntity candidate : candidates) {
            if (key.equals(naturalKey(table, candidate))) {
                if (!candidate.isDeleted()) {
                    return candidate;
                }
                if (deletedMatch == null) {
                    deletedMatch = candidate;
                }
            }
        }
        // Une ligne locale supprimée mais portant la même clé unique bloquerait
        // l'insertion (contrainte UNIQUE) : on la relie, LWW tranchera.
        return deletedMatch;
    }

    /**
     * Clé naturelle (en ids locaux) servant à reconnaître une même entité
     * saisie sur deux postes. null si la table n'en a pas d'exploitable.
     */
    static List<String> naturalKey(String table, GenericSyncableEntity row) {
        switch (table) {
            case "groups":
            case "projects": {
                String name = trimToNull(row.getString("name"));
                return name == null ? null : List.of(name.toLowerCase());
            }
            case "members": {
                String email = trimToNull(row.getString("email"));
                if (email != null) {
                    return List.of("email", email.toLowerCase());
                }
                String first = trimToNull(row.getString("first_name"));
                String last = trimToNull(row.getString("last_name"));
                String phone = trimToNull(row.getString("phone"));
                if (first == null || last == null || phone == null) {
                    return null;
                }
                return List.of("identity", first.toLowerCase(), last.toLowerCase(), phone.replaceAll("\\s+", ""));
            }
            case "events": {
                String name = trimToNull(row.getString("name"));
                String start = trimToNull(row.getString("start_date"));
                return name == null || start == null ? null : List.of(name.toLowerCase(), start);
            }
            case "payment_groups": {
                String groupId = row.getString("group_id");
                String entityType = row.getString("entity_type");
                String entityId = row.getString("entity_id");
                if (groupId == null || entityType == null || entityId == null) {
                    return null;
                }
                return List.of(groupId, entityType, entityId);
            }
            default:
                return null; // contributions, expenses : contenu uniquement
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Un objectif distant entre en collision avec un objectif local ACTIF déjà
     * relié à une autre ligne distante (même groupe / entité) : « le plus
     * récent gagne ». Distant plus récent → l'objectif local est soft-deleted
     * (PENDING, la suppression se propagera au PUSH) et l'insertion peut avoir
     * lieu (true). Local plus récent → on saute le distant (false) ; l'autre
     * poste fera le raisonnement inverse et les deux convergent.
     */
    private boolean resolvePaymentGroupCollision(Session s, GenericSyncableEntity remoteLocal) throws SQLException {
        Object groupId = remoteLocal.getField("group_id");
        Object entityType = remoteLocal.getField("entity_type");
        Object entityId = remoteLocal.getField("entity_id");
        if (groupId == null || entityType == null || entityId == null) {
            return true; // FK non résolue : laisser le flux normal gérer
        }

        String sql = """
            SELECT id, updated_at FROM payment_groups
            WHERE group_id = ? AND entity_type = ? AND entity_id = ? AND deleted_at IS NULL
            """;
        Integer localId = null;
        String localUpdatedAt = null;
        try (PreparedStatement pstmt = s.local.prepareStatement(sql)) {
            SyncValues.bind(pstmt, 1, groupId);
            SyncValues.bind(pstmt, 2, entityType);
            SyncValues.bind(pstmt, 3, entityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    localId = rs.getInt("id");
                    localUpdatedAt = SyncValues.normalizeTemporal(rs.getString("updated_at"));
                }
            }
        }
        if (localId == null) {
            return true; // pas de collision
        }

        String remoteUpdated = SyncValues.normalizeTemporal(remoteLocal.getField("updated_at"));
        boolean remoteWins = remoteUpdated != null
                && (localUpdatedAt == null || remoteUpdated.compareTo(localUpdatedAt) > 0);
        if (!remoteWins) {
            return false;
        }

        String now = SyncValues.nowUtc();
        String deleteSql = """
            UPDATE payment_groups SET deleted_at = ?, updated_at = ?,
                sync_status = 'PENDING', sync_version = COALESCE(sync_version, 1) + 1
            WHERE id = ?
            """;
        try (PreparedStatement pstmt = s.local.prepareStatement(deleteSql)) {
            pstmt.setString(1, now);
            pstmt.setString(2, now);
            pstmt.setInt(3, localId);
            pstmt.executeUpdate();
        }
        s.logLocal("payment_groups", localId, "DELETE", "PULL", "SUCCESS",
                "Objectif remplacé par la version distante plus récente");
        return true;
    }

    // ================================================== conversion des FK

    /**
     * Clés étrangères de chaque table : colonne → table référencée.
     * {@code entity_id} (polymorphe EVENT/PROJECT) est traité à part.
     */
    static Map<String, String> getForeignKeyMappings(String tableName) {
        Map<String, String> mappings = new LinkedHashMap<>();
        switch (tableName) {
            case "members" -> mappings.put("group_id", "groups");
            case "events" -> mappings.put("organizer_id", "members");
            case "projects" -> mappings.put("manager_id", "members");
            case "contributions" -> {
                mappings.put("member_id", "members");
                mappings.put("group_id", "groups");
            }
            case "expenses" -> mappings.put("member_id", "members");
            case "payment_groups" -> mappings.put("group_id", "groups");
            default -> { }
        }
        return mappings;
    }

    static boolean hasPolymorphicEntity(String tableName) {
        return "contributions".equals(tableName) || "expenses".equals(tableName)
                || "payment_groups".equals(tableName);
    }

    private static String entityTable(Object entityType) {
        if (entityType == null) return null;
        String type = entityType.toString().trim().toUpperCase();
        if (type.equals("EVENT")) return "events";
        if (type.equals("PROJECT")) return "projects";
        return null;
    }

    /** Copie de la ligne distante avec ses FK converties en ids locaux (inconnu → NULL). */
    GenericSyncableEntity toLocalSpace(Session s, String table, GenericSyncableEntity remoteRow) throws SQLException {
        Map<String, Object> fields = remoteRow.getAllFields();
        for (Map.Entry<String, String> fk : getForeignKeyMappings(table).entrySet()) {
            Object value = fields.get(fk.getKey());
            if (value instanceof Number n) {
                Integer localId = s.mapping(fk.getValue()).remoteToLocal.get(n.intValue());
                if (localId == null) {
                    System.err.println("WARNING: " + table + "." + fk.getKey() + " → " + fk.getValue()
                            + " distant #" + n.intValue() + " inconnu localement, FK mise à NULL");
                }
                fields.put(fk.getKey(), localId);
            }
        }
        if (hasPolymorphicEntity(table)) {
            String target = entityTable(fields.get("entity_type"));
            Object value = fields.get("entity_id");
            if (target != null && value instanceof Number n) {
                Integer localId = s.mapping(target).remoteToLocal.get(n.intValue());
                if (localId == null) {
                    System.err.println("WARNING: " + table + ".entity_id → " + target
                            + " distant #" + n.intValue() + " inconnu localement, FK mise à NULL");
                }
                fields.put("entity_id", localId);
            }
        }
        if (fields.get(MEMBER_GROUPS_FIELD) instanceof List<?> remoteGroupIds) {
            List<Integer> localGroupIds = new ArrayList<>();
            TableMapping groups = s.mapping("groups");
            for (Object id : remoteGroupIds) {
                Integer localId = id instanceof Number n ? groups.remoteToLocal.get(n.intValue()) : null;
                if (localId != null) {
                    localGroupIds.add(localId); // groupe inconnu ici : ignoré jusqu'à sa propre sync
                }
            }
            java.util.Collections.sort(localGroupIds);
            fields.put(MEMBER_GROUPS_FIELD, localGroupIds);
        }
        return remoteRow.withFields(fields);
    }

    /** Champs de la ligne locale avec ses FK converties en ids distants (inconnu → erreur). */
    Map<String, Object> toRemoteSpace(Session s, String table, GenericSyncableEntity localRow) throws SQLException {
        Map<String, Object> fields = localRow.getAllFields();
        for (Map.Entry<String, String> fk : getForeignKeyMappings(table).entrySet()) {
            Object value = fields.get(fk.getKey());
            if (value instanceof Number n) {
                fields.put(fk.getKey(), remoteIdFor(s, fk.getValue(), n.intValue(), table + "." + fk.getKey()));
            }
        }
        if (hasPolymorphicEntity(table)) {
            String target = entityTable(fields.get("entity_type"));
            Object value = fields.get("entity_id");
            if (target != null && value instanceof Number n) {
                fields.put("entity_id", remoteIdFor(s, target, n.intValue(), table + ".entity_id"));
            }
        }
        if (fields.get(MEMBER_GROUPS_FIELD) instanceof List<?> localGroupIds) {
            List<Integer> remoteGroupIds = new ArrayList<>();
            for (Object id : localGroupIds) {
                if (id instanceof Number n) {
                    remoteGroupIds.add(remoteIdFor(s, "groups", n.intValue(), "member_groups.group_id"));
                }
            }
            java.util.Collections.sort(remoteGroupIds);
            fields.put(MEMBER_GROUPS_FIELD, remoteGroupIds);
        }
        return fields;
    }

    /**
     * Id distant d'une ligne locale référencée. Si elle n'a pas encore de
     * mapping (ligne supprimée jamais envoyée, échec antérieur…) elle est
     * poussée immédiatement, récursivement pour ses propres références : une
     * ligne ne doit jamais attendre une sync de plus parce que sa cible manque.
     */
    private int remoteIdFor(Session s, String fkTable, int localId, String reference) throws SQLException {
        Integer remoteId = s.mapping(fkTable).localToRemote.get(localId);
        if (remoteId != null) {
            return remoteId;
        }
        GenericSyncableEntity target = getLocalEntity(s, fkTable, localId);
        if (target == null) {
            throw new SQLException("Référence introuvable : " + reference + " → " + fkTable + " local #" + localId
                    + " n'existe pas (ligne à corriger dans l'application)");
        }
        int newRemoteId = insertRemote(s, fkTable, target);
        s.mapping(fkTable).map(localId, newRemoteId);
        syncMetadataDAO.save(s.local, fkTable, localId, newRemoteId,
                versionOf(target), target.calculateHash(), "SYNCED");
        setSyncStatus(s.local, fkTable, localId, "SYNCED");
        s.result.addPushed(fkTable, 1);
        s.logLocal(fkTable, localId, "INSERT", "PUSH", "SUCCESS", "Poussée comme dépendance de " + reference);
        s.logRemote(fkTable, newRemoteId, "INSERT", "PUSH", "SUCCESS", null);
        return newRemoteId;
    }

    // ========================================================= lecture / écriture

    /**
     * Champ synthétique porté par chaque ligne « members » : la liste triée des
     * groupes du membre (table de jointure member_groups, sans id ni colonnes de
     * sync). Il participe au hash et aux conversions d'ids, et est réécrit avec
     * la ligne membre de chaque côté. Les effectifs par groupe convergent ainsi
     * entre postes.
     */
    static final String MEMBER_GROUPS_FIELD = "member_group_ids";

    private List<GenericSyncableEntity> fetchLocal(Session s, String table, String where) throws SQLException {
        List<GenericSyncableEntity> rows = JdbcRows.fetchAll(s.local, table, where);
        List<GenericSyncableEntity> out = new ArrayList<>(rows.size());
        for (GenericSyncableEntity row : rows) {
            out.add(s.restrictToSharedColumns(table, row));
        }
        return out;
    }

    private GenericSyncableEntity getLocalEntity(Session s, String table, int id) throws SQLException {
        GenericSyncableEntity row = JdbcRows.get(s.local, table, id);
        return row != null ? s.restrictToSharedColumns(table, row) : null;
    }

    /**
     * Colonnes à écrire dans la base cible : tout sauf l'id et l'état de sync
     * propre au poste, restreint aux colonnes qui existent réellement côté cible.
     */
    private static Map<String, Object> writableFields(Set<String> columns, Map<String, Object> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            String col = e.getKey();
            if (col.equals("id") || SyncValues.LOCAL_STATE_COLUMNS.contains(col) || !columns.contains(col)) {
                continue;
            }
            Object value = e.getValue();
            if (SyncValues.TEMPORAL_COLUMNS.contains(col)) {
                value = SyncValues.normalizeTemporal(value);
            }
            out.put(col, value);
        }
        if (columns.contains("sync_status")) {
            out.put("sync_status", "SYNCED");
        }
        if (columns.contains("last_sync_at")) {
            out.put("last_sync_at", SyncValues.nowUtc());
        }
        return out;
    }

    private int insertLocal(Session s, String table, GenericSyncableEntity remoteLocal) throws SQLException {
        Map<String, Object> fields = remoteLocal.getAllFields();
        int newId = JdbcRows.insert(s.local, table, writableFields(s.localColumns(table), fields));
        if ("members".equals(table)) {
            JdbcRows.writeMemberGroups(s.local, newId, JdbcRows.toIntList(fields.get(MEMBER_GROUPS_FIELD)));
        }
        return newId;
    }

    private void applyRemoteToLocal(Session s, String table, int localId, GenericSyncableEntity remoteLocal)
            throws SQLException {
        Map<String, Object> fields = remoteLocal.getAllFields();
        JdbcRows.update(s.local, table, localId, writableFields(s.localColumns(table), fields));
        if ("members".equals(table)) {
            JdbcRows.writeMemberGroups(s.local, localId, JdbcRows.toIntList(fields.get(MEMBER_GROUPS_FIELD)));
        }
    }

    private int insertRemote(Session s, String table, GenericSyncableEntity localRow) throws SQLException {
        Map<String, Object> fields = toRemoteSpace(s, table, localRow);
        int newId = s.remote.insert(table, writableFields(s.remoteColumns(table), fields));
        if ("members".equals(table)) {
            s.remote.writeMemberGroups(newId, JdbcRows.toIntList(fields.get(MEMBER_GROUPS_FIELD)));
        }
        return newId;
    }

    private void updateRemote(Session s, String table, int remoteId, GenericSyncableEntity localRow)
            throws SQLException {
        Map<String, Object> fields = toRemoteSpace(s, table, localRow);
        s.remote.update(table, remoteId, writableFields(s.remoteColumns(table), fields));
        if ("members".equals(table)) {
            s.remote.writeMemberGroups(remoteId, JdbcRows.toIntList(fields.get(MEMBER_GROUPS_FIELD)));
        }
    }

    private static void setSyncStatus(Connection local, String table, int id, String status) throws SQLException {
        String sql = "UPDATE `" + table + "` SET sync_status = ?"
                + ("SYNCED".equals(status) ? ", last_sync_at = ?" : "") + " WHERE id = ?";
        try (PreparedStatement pstmt = local.prepareStatement(sql)) {
            int i = 1;
            pstmt.setString(i++, status);
            if ("SYNCED".equals(status)) {
                pstmt.setString(i++, SyncValues.nowUtc());
            }
            pstmt.setInt(i, id);
            pstmt.executeUpdate();
        }
    }

    private static int versionOf(GenericSyncableEntity row) {
        Integer v = row != null ? row.getSyncVersion() : null;
        return v != null ? v : 1;
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            if (!conn.getAutoCommit()) {
                conn.rollback();
            }
        } catch (SQLException ignored) {
            // rien à faire de plus
        }
    }

    // ================================================================= session

    /** Mappings d'une table, tenus à jour au fil de la session. */
    static final class TableMapping {
        final Map<Integer, Integer> localToRemote = new HashMap<>();
        final Map<Integer, Integer> remoteToLocal = new HashMap<>();

        void map(int localId, int remoteId) {
            Integer oldRemote = localToRemote.put(localId, remoteId);
            if (oldRemote != null) {
                remoteToLocal.remove(oldRemote);
            }
            remoteToLocal.put(remoteId, localId);
        }

        void unmap(int localId, int remoteId) {
            localToRemote.remove(localId);
            remoteToLocal.remove(remoteId);
        }
    }

    /** État d'une session de synchronisation : connexions, caches, résultat. */
    final class Session {
        final String id;
        final Connection local;
        final RemoteStore remote;
        final SyncResult result;
        final List<SyncLogDAO.Entry> remoteLogs = new ArrayList<>();

        private final Map<String, TableMapping> mappings = new HashMap<>();
        private final Map<String, List<GenericSyncableEntity>> unmapped = new HashMap<>();
        private final Map<String, Set<String>> localColumnCache = new HashMap<>();
        private final Map<String, Set<String>> remoteColumnCache = new HashMap<>();
        private final Set<String> countedConflicts = new HashSet<>();

        Session(String id, Connection local, RemoteStore remote, SyncResult result) {
            this.id = id;
            this.local = local;
            this.remote = remote;
            this.result = result;
        }

        TableMapping mapping(String table) throws SQLException {
            TableMapping m = mappings.get(table);
            if (m == null) {
                m = new TableMapping();
                for (Map.Entry<Integer, Integer> e : syncMetadataDAO.getLocalToRemoteMap(local, table).entrySet()) {
                    m.map(e.getKey(), e.getValue());
                }
                mappings.put(table, m);
            }
            return m;
        }

        /** Lignes locales sans id distant (candidates à une liaison). */
        List<GenericSyncableEntity> unmappedLocalRows(String table) throws SQLException {
            List<GenericSyncableEntity> rows = unmapped.get(table);
            if (rows == null) {
                Set<Integer> mappedIds = mapping(table).localToRemote.keySet();
                rows = new ArrayList<>();
                for (GenericSyncableEntity row : fetchLocal(this, table, null)) {
                    if (row.getId() != null && !mappedIds.contains(row.getId())) {
                        rows.add(row);
                    }
                }
                unmapped.put(table, rows);
            }
            return rows;
        }

        void forgetUnmapped(String table, int localId) {
            List<GenericSyncableEntity> rows = unmapped.get(table);
            if (rows != null) {
                rows.removeIf(r -> Objects.equals(r.getId(), localId));
            }
        }

        Set<String> localColumns(String table) throws SQLException {
            Set<String> cols = localColumnCache.get(table);
            if (cols == null) {
                cols = JdbcRows.columns(local, table);
                localColumnCache.put(table, cols);
            }
            return cols;
        }

        Set<String> remoteColumns(String table) throws SQLException {
            Set<String> cols = remoteColumnCache.get(table);
            if (cols == null) {
                cols = remote.columns(table);
                remoteColumnCache.put(table, cols);
            }
            return cols;
        }

        List<GenericSyncableEntity> fetchRemote(String table) throws SQLException {
            List<GenericSyncableEntity> rows = remote.fetchAll(table);
            List<GenericSyncableEntity> out = new ArrayList<>(rows.size());
            for (GenericSyncableEntity row : rows) {
                out.add(restrictToSharedColumns(table, row));
            }
            return out;
        }

        GenericSyncableEntity getRemote(String table, int remoteId) throws SQLException {
            GenericSyncableEntity row = remote.get(table, remoteId);
            return row != null ? restrictToSharedColumns(table, row) : null;
        }

        /**
         * Ne garde que les colonnes présentes des deux côtés (plus le champ
         * synthétique des groupes) : une colonne héritée d'un seul schéma
         * (ex. projects.target_budget) ne doit ni entrer dans le hash, ni être
         * considérée comme une modification perpétuelle.
         */
        GenericSyncableEntity restrictToSharedColumns(String table, GenericSyncableEntity row) throws SQLException {
            Set<String> localCols = localColumns(table);
            Set<String> remoteCols = remoteColumns(table);
            Map<String, Object> fields = row.getAllFields();
            boolean changed = fields.keySet().removeIf(col ->
                    !col.equals(MEMBER_GROUPS_FIELD) && !(localCols.contains(col) && remoteCols.contains(col)));
            return changed ? row.withFields(fields) : row;
        }

        void countConflict(String table, int localId, Resolution resolution) {
            if (resolution.isConflict() && countedConflicts.add(table + "#" + localId)) {
                result.addConflict(table);
            }
        }

        void logLocal(String table, int recordId, String operation, String direction,
                      String status, String message) {
            try {
                syncLogDAO.log(local, id, table, recordId, operation, direction, status, message);
            } catch (SQLException e) {
                System.err.println("Journal de sync local indisponible : " + e.getMessage());
            }
        }

        void logRemote(String table, int remoteId, String operation, String direction,
                       String status, String message) {
            remoteLogs.add(new SyncLogDAO.Entry(id, table, remoteId, operation, direction, status,
                    message, SyncValues.nowUtc()));
        }
    }

    // ================================================================ messages

    /**
     * Convert technical SQL errors to user-friendly messages
     */
    private String getUserFriendlyErrorMessage(SQLException e) {
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (errorMsg.contains("connection") || errorMsg.contains("timeout") ||
            errorMsg.contains("refused") || errorMsg.contains("unreachable")) {
            return "Erreur de connexion au serveur.\n\n" +
                   "Le serveur de synchronisation n'est pas accessible.\n\n" +
                   "Veuillez vérifier:\n" +
                   "• Votre connexion Internet\n" +
                   "• Que le serveur est bien en ligne\n" +
                   "• Les paramètres de connexion dans le fichier de configuration";
        }

        if (errorMsg.contains("access denied") || errorMsg.contains("authentication") ||
            errorMsg.contains("password")) {
            return "Erreur d'authentification.\n\n" +
                   "Les identifiants de connexion au serveur sont incorrects.\n\n" +
                   "Veuillez vérifier:\n" +
                   "• Le nom d'utilisateur\n" +
                   "• Le mot de passe\n" +
                   "• Les droits d'accès à la base de données";
        }

        if (errorMsg.contains("unknown database") || errorMsg.contains("database") && errorMsg.contains("not found")) {
            return "Base de données introuvable.\n\n" +
                   "La base de données spécifiée n'existe pas sur le serveur.\n\n" +
                   "Veuillez contacter l'administrateur système.";
        }

        if (errorMsg.contains("network") || errorMsg.contains("host")) {
            return "Erreur réseau.\n\n" +
                   "Impossible de joindre le serveur de synchronisation.\n\n" +
                   "Veuillez vérifier votre connexion Internet.";
        }

        return "Erreur de synchronisation.\n\n" +
               "Une erreur technique s'est produite lors de la synchronisation.\n\n" +
               "Détails techniques: " + e.getMessage() + "\n\n" +
               "Si le problème persiste, veuillez contacter le support.";
    }

    // ================================================================== result

    /**
     * Sync result statistics
     */
    public static class SyncResult {
        private boolean success;
        private int recordsPulled;
        private int recordsPushed;
        private int conflicts;
        private int recordsFailed;
        private static final int MAX_REPORTED_ROW_ERRORS = 15;
        private final Map<String, Integer> failedByTable = new HashMap<>();
        private final List<String> errors;
        private String errorMessage;
        private String syncSessionId;

        private final Map<String, Integer> pullByTable;
        private final Map<String, Integer> pushByTable;
        private final Map<String, Integer> conflictsByTable;

        public SyncResult() {
            this.errors = new ArrayList<>();
            this.pullByTable = new HashMap<>();
            this.pushByTable = new HashMap<>();
            this.conflictsByTable = new HashMap<>();
        }

        public void merge(SyncResult other) {
            this.recordsPulled += other.recordsPulled;
            this.recordsPushed += other.recordsPushed;
            this.conflicts += other.conflicts;
            this.recordsFailed += other.recordsFailed;
            other.failedByTable.forEach((table, count) -> failedByTable.merge(table, count, Integer::sum));
            this.errors.addAll(other.errors);
            other.pullByTable.forEach((table, count) -> pullByTable.merge(table, count, Integer::sum));
            other.pushByTable.forEach((table, count) -> pushByTable.merge(table, count, Integer::sum));
            other.conflictsByTable.forEach((table, count) -> conflictsByTable.merge(table, count, Integer::sum));
        }

        public void addPulled(int count) {
            this.recordsPulled += count;
        }

        public void addPulled(String tableName, int count) {
            this.recordsPulled += count;
            pullByTable.merge(tableName, count, Integer::sum);
        }

        public void addPushed(int count) {
            this.recordsPushed += count;
        }

        public void addPushed(String tableName, int count) {
            this.recordsPushed += count;
            pushByTable.merge(tableName, count, Integer::sum);
        }

        public void addConflict() {
            this.conflicts++;
        }

        public void addConflict(String tableName) {
            this.conflicts++;
            conflictsByTable.merge(tableName, 1, Integer::sum);
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        /** Échec d'une ligne : compté, et décrit dans les erreurs (sans inonder le dialogue). */
        public void addRowFailure(String tableName, String rowLabel, String message) {
            recordsFailed++;
            failedByTable.merge(tableName, 1, Integer::sum);
            if (errors.size() < MAX_REPORTED_ROW_ERRORS) {
                errors.add(tableName + " " + rowLabel + " : " + (message != null ? message : "erreur inconnue"));
            } else if (errors.size() == MAX_REPORTED_ROW_ERRORS) {
                errors.add("… d'autres lignes ont échoué, voir l'historique de synchronisation");
            }
        }

        public int getRecordsFailed() { return recordsFailed; }
        public Map<String, Integer> getFailedByTable() { return failedByTable; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getRecordsPulled() { return recordsPulled; }
        public int getRecordsPushed() { return recordsPushed; }
        public int getConflicts() { return conflicts; }
        public List<String> getErrors() { return errors; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getSyncSessionId() { return syncSessionId; }
        public void setSyncSessionId(String syncSessionId) { this.syncSessionId = syncSessionId; }

        public Map<String, Integer> getPullByTable() { return pullByTable; }
        public Map<String, Integer> getPushByTable() { return pushByTable; }
        public Map<String, Integer> getConflictsByTable() { return conflictsByTable; }

        @Override
        public String toString() {
            return String.format("Sync Result: %s | Pulled: %d, Pushed: %d, Conflicts: %d, Failed rows: %d, Errors: %d",
                    success ? "SUCCESS" : "FAILED",
                    recordsPulled, recordsPushed, conflicts, recordsFailed, errors.size());
        }
    }
}
