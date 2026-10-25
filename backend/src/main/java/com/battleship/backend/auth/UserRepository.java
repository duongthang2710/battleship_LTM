package com.battleship.backend.auth;

import com.battleship.backend.database.Database;
import com.battleship.shared.auth.AccountProfile;
import java.sql.SQLException;
import java.util.UUID;

public final class UserRepository {
    private final Database database;
    public UserRepository(Database database) { this.database = database; }

    public AccountProfile create(String username, String passwordHash, String displayName) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var connection = database.open(); var query = connection.prepareStatement(
                "INSERT INTO users(id, username, password_hash, display_name) VALUES (?, ?, ?, ?)")) {
            query.setObject(1, id);
            query.setString(2, username);
            query.setString(3, passwordHash);
            query.setString(4, displayName);
            query.executeUpdate();
        }
        return new AccountProfile(id, username, displayName);
    }

    public StoredUser find(String username) throws SQLException {
        try (var connection = database.open(); var query = connection.prepareStatement(
                "SELECT id, username, display_name, password_hash FROM users WHERE username = ?")) {
            query.setString(1, username);
            try (var row = query.executeQuery()) {
                return row.next() ? new StoredUser(new AccountProfile(row.getObject("id", UUID.class),
                        row.getString("username"), row.getString("display_name")), row.getString("password_hash")) : null;
            }
        }
    }

    public record StoredUser(AccountProfile profile, String passwordHash) {
        @Override public String toString() { return "StoredUser[credentials redacted]"; }
    }
}
