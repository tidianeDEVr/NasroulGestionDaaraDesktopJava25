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
     * Get connection - ALWAYS returns SQLite for offline-first architecture
     * MySQL is ONLY used by SyncManager via getMySQLConnection()
     */
    public Connection getConnection() throws SQLException {
        // OFFLINE-FIRST: Always use SQLite as primary database
        return getSQLiteConnection();
    }

    /**
     * Get SQLite connection (local, primary database)
     */
    public Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + config.getSQLitePath());
    }

    /**
     * Get MySQL connection (remote, sync target only)
     * Used ONLY by SyncManager for synchronization
     */
    public Connection getMySQLConnection() throws SQLException {
        return DriverManager.getConnection(
            config.getMySQLConnectionUrl(),
            config.getMySQLUsername(),
            config.getMySQLPassword()
        );
    }

    /**
     * Check if MySQL is available for sync
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
        try (Connection conn = getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            // OFFLINE-FIRST: Always initialize SQLite (local database)
            createTablesSQLite(stmt);
            createSyncTablesSQLite(stmt);

            System.out.println("SQLite database initialized successfully (offline-first)");
            connectionError = null;

            // Also initialize MySQL if available (for sync)
            if (isMySQLAvailable()) {
                try (Connection mysqlConn = getMySQLConnection();
                     Statement mysqlStmt = mysqlConn.createStatement()) {
                    createTablesMySQL(mysqlStmt);
                    createSyncTablesMySQL(mysqlStmt);
                    System.out.println("MySQL database also initialized (for sync)");
                } catch (SQLException e) {
                    System.out.println("MySQL not available (offline mode): " + e.getMessage());
                }
            } else {
                System.out.println("MySQL not configured - running in offline mode only");
            }

        } catch (SQLException e) {
            connectionError = e.getMessage();
            System.err.println("SQLite database initialization failed: " + e.getMessage());
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
                `contribution_target` DOUBLE DEFAULT 0
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
                FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `member_groups` (
                `member_id` INT NOT NULL,
                `group_id` INT NOT NULL,
                PRIMARY KEY (`member_id`, `group_id`),
                FOREIGN KEY (`member_id`) REFERENCES `members`(`id`) ON DELETE CASCADE,
                FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE CASCADE
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
                contribution_target REAL DEFAULT 0
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
                FOREIGN KEY (group_id) REFERENCES groups(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS member_groups (
                member_id INTEGER NOT NULL,
                group_id INTEGER NOT NULL,
                PRIMARY KEY (member_id, group_id),
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
            )
        """);
    }

    private void createSyncTablesMySQL(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                table_name VARCHAR(255) NOT NULL,
                record_id INT NOT NULL,
                sync_version INT DEFAULT 1,
                local_hash VARCHAR(255),
                remote_hash VARCHAR(255),
                last_sync_at DATETIME,
                sync_status VARCHAR(50) DEFAULT 'PENDING',
                conflict_resolution TEXT,
                PRIMARY KEY (table_name, record_id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_log (
                id INT PRIMARY KEY AUTO_INCREMENT,
                sync_session_id VARCHAR(255) NOT NULL,
                table_name VARCHAR(255) NOT NULL,
                record_id INT NOT NULL,
                operation VARCHAR(50) NOT NULL,
                sync_direction VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL,
                error_message TEXT,
                synced_at DATETIME NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_devices (
                device_id VARCHAR(255) PRIMARY KEY,
                device_name VARCHAR(255) NOT NULL,
                user_name VARCHAR(255),
                last_sync_at DATETIME,
                is_active INT DEFAULT 1
            )
        """);
    }

    private void createSyncTablesSQLite(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                table_name TEXT NOT NULL,
                record_id INTEGER NOT NULL,
                sync_version INTEGER DEFAULT 1,
                local_hash TEXT,
                remote_hash TEXT,
                last_sync_at TEXT,
                sync_status TEXT DEFAULT 'PENDING',
                conflict_resolution TEXT,
                PRIMARY KEY (table_name, record_id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_session_id TEXT NOT NULL,
                table_name TEXT NOT NULL,
                record_id INTEGER NOT NULL,
                operation TEXT NOT NULL,
                sync_direction TEXT NOT NULL,
                status TEXT NOT NULL,
                error_message TEXT,
                synced_at TEXT NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sync_devices (
                device_id TEXT PRIMARY KEY,
                device_name TEXT NOT NULL,
                user_name TEXT,
                last_sync_at TEXT,
                is_active INTEGER DEFAULT 1
            )
        """);
    }
}
