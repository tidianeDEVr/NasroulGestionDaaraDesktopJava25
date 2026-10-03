package com.nasroul.sync;

import com.nasroul.dao.SyncLogDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accès à la base distante partagée, vu du moteur de synchronisation.
 *
 * Deux implémentations : {@link JdbcRemoteStore} (MySQL en direct) et
 * {@link HttpRemoteStore} (passerelle api.php : les postes n'ont que l'URL
 * et une clé, les identifiants MySQL restent sur le serveur).
 */
public interface RemoteStore extends AutoCloseable {

    /** Vérifie que le distant répond ; lève une SQLException sinon. */
    void ping() throws SQLException;

    /** Colonnes réelles d'une table distante (noms en minuscules). */
    Set<String> columns(String table) throws SQLException;

    /** Toutes les lignes d'une table (membres : avec leurs groupes). */
    List<GenericSyncableEntity> fetchAll(String table) throws SQLException;

    /** Une ligne par id distant, ou null. */
    GenericSyncableEntity get(String table, int id) throws SQLException;

    /** Insère et renvoie l'id distant généré. */
    int insert(String table, Map<String, Object> fields) throws SQLException;

    void update(String table, int id, Map<String, Object> fields) throws SQLException;

    /** Remplace les appartenances d'un membre (ids distants). */
    void writeMemberGroups(int memberId, List<Integer> groupIds) throws SQLException;

    /** Journal de sync partagé (meilleur effort). */
    void logBatch(List<SyncLogDAO.Entry> entries) throws SQLException;

    /** Met le schéma distant à niveau (no-op si non supporté). */
    default void ensureSchema() throws SQLException {
    }

    /** Déclare ce poste dans sync_devices (no-op si non supporté). */
    default void registerDevice(String deviceId, String deviceName, String userName) throws SQLException {
    }

    @Override
    void close() throws SQLException;
}
