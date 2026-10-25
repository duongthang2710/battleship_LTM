package com.battleship.backend.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class Database {
    private final String url;
    private final Properties credentials;

    private Database(String url, Properties credentials) {
        this.url = url;
        this.credentials = credentials;
    }

    public static Database fromEnvironment() throws IOException {
        Map<String, String> settings = new HashMap<>();
        Path env = Path.of(".env");
        if (!Files.exists(env) && Path.of("").toAbsolutePath().getFileName().toString().equals("backend"))
            env = Path.of("..", ".env");
        if (Files.exists(env)) for (String line : Files.readAllLines(env, StandardCharsets.UTF_8)) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            int separator = entry.indexOf('=');
            if (separator <= 0) throw new IOException("Invalid .env entry");
            String value = entry.substring(separator + 1).strip();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
            settings.put(entry.substring(0, separator).strip(), value);
        }
        settings.putAll(System.getenv());
        String password = settings.get("DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IOException("Set DB_PASSWORD in .env or the server environment.");
        String host = settings.getOrDefault("DB_HOST", "localhost");
        int port = Integer.parseInt(settings.getOrDefault("DB_PORT", "5432"));
        String name = settings.getOrDefault("DB_NAME", "battleship");
        if (!host.matches("[a-zA-Z0-9.:-]+") || !name.matches("[a-zA-Z0-9_]+") || port < 1 || port > 65535)
            throw new IOException("Invalid DB_HOST, DB_PORT or DB_NAME.");
        Properties properties = new Properties();
        properties.setProperty("user", settings.getOrDefault("DB_USER", "battleship"));
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        return new Database("jdbc:postgresql://" + host + ":" + port + "/" + name, properties);
    }

    public Connection open() throws SQLException { return DriverManager.getConnection(url, credentials); }

    public void migrate() throws SQLException, IOException {
        try (Connection connection = open(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("SELECT pg_advisory_xact_lock(2209001)");
                for (String migration : new String[] {"V001__accounts_and_matches.sql", "V002__match_summary.sql"}) {
                    try (var resource = Database.class.getResourceAsStream("/db/" + migration)) {
                        if (resource == null) throw new IOException("Missing database migration: " + migration);
                        statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                connection.commit();
            } catch (SQLException | IOException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
