package com.nasroul.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static DatabaseManager instance;
    private static final String DB_URL = "jdbc:sqlite:association.db";

    private DatabaseManager() {
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

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

            System.out.println("Database initialized successfully");

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }
}
