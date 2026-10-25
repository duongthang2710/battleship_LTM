package com.battleship.backend.auth;

import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.auth.RegisterRequest;
import com.battleship.shared.auth.LoginRequest;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {
    private final UserRepository users;
    private final PasswordHasher passwords = new PasswordHasher();
    private final String dummyHash = passwords.hash("Account lookup timing protection");
    private final Map<java.util.UUID, String> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository users) { this.users = users; }

    public AccountProfile register(RegisterRequest request) throws SQLException {
        if (request == null) throw new IllegalArgumentException("Missing registration data.");
        String username = username(request.username());
        password(request.password());
        if (!request.password().equals(request.confirmPassword()))
            throw new IllegalArgumentException("Passwords do not match.");
        try { return users.create(username, passwords.hash(request.password()), request.username().strip()); }
        catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) throw new IllegalArgumentException("Username is already taken.");
            throw exception;
        }
    }

    public AccountProfile login(String clientId, LoginRequest request) throws SQLException {
        if (request == null) throw new IllegalArgumentException("Missing login data.");
        String username = username(request.username());
        password(request.password());
        UserRepository.StoredUser user = users.find(username);
        boolean valid = passwords.verify(request.password(), user == null ? dummyHash : user.passwordHash());
        if (!valid || user == null) throw new IllegalArgumentException("Incorrect username or password.");
        String owner = sessions.putIfAbsent(user.profile().id(), clientId);
        if (owner != null && !owner.equals(clientId)) throw new IllegalArgumentException("This account is already signed in.");
        return user.profile();
    }

    public void logout(String clientId, AccountProfile account) {
        if (account != null) sessions.remove(account.id(), clientId);
    }

    private String username(String value) {
        if (value == null || !value.strip().matches("[A-Za-z0-9_]{3,24}"))
            throw new IllegalArgumentException("Username: 3-24 letters, numbers or underscore.");
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void password(String value) {
        if (value == null || value.length() < 12 || value.length() > 128 || value.isBlank())
            throw new IllegalArgumentException("Password must contain 12-128 characters.");
    }
}
