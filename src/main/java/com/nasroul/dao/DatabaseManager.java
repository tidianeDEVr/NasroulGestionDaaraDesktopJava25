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

    public Connection getConnection() throws SQLException {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return DriverManager.getConnection(
                config.getMySQLConnectionUrl(),
                config.getMySQLUsername(),
                config.getMySQLPassword()
            );
        } else {
            // SQLite
            return DriverManager.getConnection("jdbc:sqlite:" + config.getSQLitePath());
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
            } else {
                createTablesSQLite(stmt);
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
}
