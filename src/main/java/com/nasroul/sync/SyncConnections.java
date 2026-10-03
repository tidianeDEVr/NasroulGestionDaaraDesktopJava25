package com.nasroul.sync;

import com.nasroul.dao.DatabaseManager;
import com.nasroul.util.ConfigManager;
import com.nasroul.util.DeviceIdGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Source des connexions utilisées par la synchronisation : la base locale
 * (SQLite) et la base distante partagée, vue à travers un {@link RemoteStore}.
 *
 * Production : MySQL en direct ({@link #fromDatabaseManager}) ou passerelle
 * api.php ({@link #fromApi}) selon la configuration ({@link #fromConfig}).
 * Tests : deux bases SQLite indépendantes.
 */
public interface SyncConnections {

    /** Connexion vers la base locale (SQLite, source de vérité du poste). */
    Connection openLocal() throws SQLException;

    /** Accès à la base distante partagée (à fermer après usage). */
    RemoteStore openRemote() throws SQLException;

    /** Le distant est-il joignable ? */
    default boolean isRemoteAvailable() {
        try (RemoteStore store = openRemote()) {
            store.ping();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Mise à niveau du schéma distant + déclaration du poste, avant la sync. */
    default void prepareRemoteSchema() {
        try (RemoteStore store = openRemote()) {
            store.ensureSchema();
            store.registerDevice(DeviceIdGenerator.getDeviceId(), hostname(),
                    System.getProperty("user.name", "Unknown"));
        } catch (SQLException e) {
            System.err.println("Préparation du distant impossible : " + e.getMessage());
        }
    }

    static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    /** Choix du transport d'après la configuration (api.php si sync.api.url est renseignée). */
    static SyncConnections fromConfig(ConfigManager config, DatabaseManager dbManager) {
        if (config.isApiSyncMode()) {
            return fromApi(config.getSyncApiUrl(), config.getSyncApiKey(),
                    Duration.ofSeconds(Math.max(30, config.getSyncTimeout())), dbManager);
        }
        return fromDatabaseManager(dbManager);
    }

    /** MySQL en direct : SQLite local + MySQL distant via DatabaseManager. */
    static SyncConnections fromDatabaseManager(DatabaseManager dbManager) {
        return new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return dbManager.getSQLiteConnection();
            }

            @Override
            public RemoteStore openRemote() throws SQLException {
                // MySQL a pu être hors ligne au démarrage : le schéma doit être
                // à jour avant que PULL/PUSH ne touchent de nouvelles colonnes.
                return new JdbcRemoteStore(dbManager.getMySQLConnection(), dbManager::ensureMySQLSchema);
            }
        };
    }

    /** Passerelle api.php : SQLite local + HTTP(S) distant. */
    static SyncConnections fromApi(String url, String apiKey, Duration timeout, DatabaseManager dbManager) {
        return new SyncConnections() {
            @Override
            public Connection openLocal() throws SQLException {
                return dbManager.getSQLiteConnection();
            }

            @Override
            public RemoteStore openRemote() {
                return new HttpRemoteStore(url, apiKey, timeout);
            }
        };
    }
}
