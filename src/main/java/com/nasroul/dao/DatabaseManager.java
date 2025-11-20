package com.nasroul.dao;

import com.nasroul.util.ConfigManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final ConfigManager config;
    private final String dbType;
    private String connectionError = null;

    private DatabaseManager() {
        config = ConfigManager.getInstance();
        dbType = config.getDbType();
        System.out.println("Database type: " + dbType);
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Get connection based on configured database type (for backward compatibility)
     */
    public Connection getConnection() throws SQLException {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return getMySQLConnection();
        } else {
            return getSQLiteConnection();
        }
    }

    /**
     * Always get SQLite connection (local, offline-first)
     */
    public Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + config.getSQLitePath());
    }

    /**
     * Always get MySQL connection (remote, sync target)
     */
    public Connection getMySQLConnection() throws SQLException {
        return DriverManager.getConnection(
            config.getMySQLConnectionUrl(),
            config.getMySQLUsername(),
            config.getMySQLPassword()
        );
    }

    /**
     * Check if MySQL is configured and available
     */
    public boolean isMySQLAvailable() {
        try (Connection conn = getMySQLConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean hasConnectionError() {
        return connectionError != null;
    }

    public String getConnectionError() {
        return connectionError;
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if ("mysql".equalsIgnoreCase(dbType)) {
                createTablesMySQL(stmt);
                createSyncTablesMySQL(stmt);
            } else {
                createTablesSQLite(stmt);
                createSyncTablesSQLite(stmt);
            }

            System.out.println("Database initialized successfully");
            connectionError = null;

        } catch (SQLException e) {
            connectionError = e.getMessage();
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTablesMySQL(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `groups` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `name` VARCHAR(255) NOT NULL UNIQUE,
                `description` TEXT,
                `active` INT DEFAULT 1,
                `contribution_target` DOUBLE DEFAULT 0,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `members` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `first_name` VARCHAR(255) NOT NULL,
                `last_name` VARCHAR(255) NOT NULL,
                `email` VARCHAR(255) UNIQUE,
                `phone` VARCHAR(255),
                `birth_date` VARCHAR(255),
                `address` TEXT,
                `join_date` VARCHAR(255) NOT NULL,
                `role` VARCHAR(255),
                `avatar` LONGBLOB,
                `active` INT DEFAULT 1,
                `group_id` INT,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `events` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `name` VARCHAR(255) NOT NULL,
                `description` TEXT,
                `start_date` VARCHAR(255) NOT NULL,
                `end_date` VARCHAR(255),
                `location` VARCHAR(255),
                `status` VARCHAR(255) DEFAULT 'PLANNED',
                `organizer_id` INT,
                `max_capacity` INT,
                `active` INT DEFAULT 1,
                `contribution_target` DOUBLE DEFAULT 0,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`organizer_id`) REFERENCES `members`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `name` VARCHAR(255) NOT NULL,
                `description` TEXT,
                `start_date` VARCHAR(255),
                `end_date` VARCHAR(255),
                `status` VARCHAR(255) DEFAULT 'PLANNING',
                `budget` DOUBLE DEFAULT 0,
                `target_budget` DOUBLE DEFAULT 0,
                `manager_id` INT,
                `contribution_target` DOUBLE DEFAULT 0,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`manager_id`) REFERENCES `members`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `expenses` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `description` VARCHAR(255) NOT NULL,
                `amount` DOUBLE NOT NULL,
                `date` VARCHAR(255) NOT NULL,
                `category` VARCHAR(255),
                `entity_type` VARCHAR(255) NOT NULL,
                `entity_id` INT NOT NULL,
                `member_id` INT,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`member_id`) REFERENCES `members`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `contributions` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `member_id` INT NOT NULL,
                `entity_type` VARCHAR(255) NOT NULL,
                `entity_id` INT NOT NULL,
                `amount` DOUBLE NOT NULL,
                `date` VARCHAR(255) NOT NULL,
                `status` VARCHAR(255) DEFAULT 'PENDING',
                `payment_method` VARCHAR(255),
                `notes` TEXT,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`member_id`) REFERENCES `members`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `payment_groups` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `group_id` INT NOT NULL,
                `entity_type` VARCHAR(255) NOT NULL,
                `entity_id` INT NOT NULL,
                `amount` DOUBLE NOT NULL,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `member_groups` (
                `member_id` INT NOT NULL,
                `group_id` INT NOT NULL,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                `deleted_at` TIMESTAMP NULL,
                `last_modified_by` VARCHAR(255),
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `sync_version` INT DEFAULT 1,
                `last_sync_at` TIMESTAMP NULL,
                PRIMARY KEY (`member_id`, `group_id`),
                FOREIGN KEY (`member_id`) REFERENCES `members`(`id`) ON DELETE CASCADE,
                FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE CASCADE
            )
        """);
    }

    private void createSyncTablesMySQL(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `sync_metadata` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `table_name` VARCHAR(255) NOT NULL,
                `record_id` INT NOT NULL,
                `sync_version` INT DEFAULT 1,
                `local_hash` VARCHAR(64),
                `remote_hash` VARCHAR(64),
                `last_sync_at` TIMESTAMP NULL,
                `sync_status` VARCHAR(50) DEFAULT 'PENDING',
                `conflict_resolution` VARCHAR(50),
                UNIQUE KEY `unique_record` (`table_name`, `record_id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `sync_log` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `sync_session_id` VARCHAR(255) NOT NULL,
                `table_name` VARCHAR(255) NOT NULL,
                `record_id` INT NOT NULL,
                `operation` VARCHAR(50) NOT NULL,
                `status` VARCHAR(50) NOT NULL,
                `error_message` TEXT,
                `sync_direction` VARCHAR(50),
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `completed_at` TIMESTAMP NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `sync_devices` (
                `id` INT PRIMARY KEY AUTO_INCREMENT,
                `device_id` VARCHAR(255) NOT NULL UNIQUE,
                `device_name` VARCHAR(255) NOT NULL,
                `user_name` VARCHAR(255),
                `last_sync_at` TIMESTAMP NULL,
                `is_active` INT DEFAULT 1,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    private void createTablesSQLite(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                description TEXT,
                active INTEGER DEFAULT 1,
                contribution_target REAL DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT NOT NULL,
                last_name TEXT NOT NULL,
                email TEXT UNIQUE,
                phone TEXT,
                birth_date TEXT,
                address TEXT,
                join_date TEXT NOT NULL,
                role TEXT,
                avatar BLOB,
                active INTEGER DEFAULT 1,
                group_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (group_id) REFERENCES groups(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                start_date TEXT NOT NULL,
                end_date TEXT,
                location TEXT,
                status TEXT DEFAULT 'PLANNED',
                organizer_id INTEGER,
                max_capacity INTEGER,
                active INTEGER DEFAULT 1,
                contribution_target REAL DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (organizer_id) REFERENCES members(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                start_date TEXT,
                end_date TEXT,
                status TEXT DEFAULT 'PLANNING',
                budget REAL DEFAULT 0,
                target_budget REAL DEFAULT 0,
                manager_id INTEGER,
                contribution_target REAL DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (manager_id) REFERENCES members(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                date TEXT NOT NULL,
                category TEXT,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                member_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (member_id) REFERENCES members(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS contributions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                amount REAL NOT NULL,
                date TEXT NOT NULL,
                status TEXT DEFAULT 'PENDING',
                payment_method TEXT,
                notes TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (member_id) REFERENCES members(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS payment_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                amount REAL NOT NULL,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                FOREIGN KEY (group_id) REFERENCES groups(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS member_groups (
                member_id INTEGER NOT NULL,
                group_id INTEGER NOT NULL,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                last_modified_by TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                last_sync_at TEXT,
                PRIMARY KEY (member_id, group_id),
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
            )
        """);
    }

    private void createSyncTablesSQLite(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                record_id INTEGER NOT NULL,
                sync_version INTEGER DEFAULT 1,
                local_hash TEXT,
                remote_hash TEXT,
                last_sync_at TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                conflict_resolution TEXT,
                UNIQUE (table_name, record_id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_session_id TEXT NOT NULL,
                table_name TEXT NOT NULL,
                record_id INTEGER NOT NULL,
                operation TEXT NOT NULL,
                status TEXT NOT NULL,
                error_message TEXT,
                sync_direction TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                completed_at TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL UNIQUE,
                device_name TEXT NOT NULL,
                user_name TEXT,
                last_sync_at TEXT,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }
}
