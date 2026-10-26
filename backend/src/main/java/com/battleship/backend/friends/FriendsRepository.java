package com.battleship.backend.friends;

import com.battleship.backend.database.Database;
import com.battleship.shared.friends.*;
import java.sql.*;
import java.util.*;

public final class FriendsRepository {
    public static final int PAGE_SIZE = 8;
    private final Database database;

    public FriendsRepository(Database database) { this.database = database; }

    public FriendsResult list(UUID viewer, FriendsRequest request) throws SQLException {
        String filter = switch (request.action()) {
            case SEARCH -> "strpos(u.username, ?) > 0";
            case FRIENDS -> "f.status = 'ACCEPTED'";
            case RECEIVED -> "f.status = 'PENDING' AND f.requester_id <> v.me";
            case SENT -> "f.requester_id = v.me";
            default -> throw new IllegalArgumentException("Invalid list.");
        };
        // One statement gives the count and page the same database snapshot.
        String sql = """
                WITH viewer AS (SELECT ?::uuid AS me), candidates AS (
                    SELECT u.id, u.username, u.display_name, f.requested_at,
                        CASE WHEN f.status = 'ACCEPTED' THEN 'FRIEND'
                             WHEN f.status = 'PENDING' AND f.requester_id = v.me THEN 'OUTGOING'
                             WHEN f.status = 'PENDING' THEN 'INCOMING'
                             ELSE COALESCE(f.status, 'NONE') END AS relationship
                    FROM users u CROSS JOIN viewer v
                    LEFT JOIN friendships f ON f.user_low = LEAST(u.id, v.me) AND f.user_high = GREATEST(u.id, v.me)
                    WHERE u.id <> v.me AND %s
                ), page AS (
                    SELECT * FROM candidates ORDER BY username, id LIMIT ? OFFSET ?
                )
                SELECT totals.total, page.* FROM (SELECT count(*) AS total FROM candidates) totals
                LEFT JOIN page ON true ORDER BY page.username, page.id
                """.formatted(filter);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            statement.setObject(1, viewer);
            int parameter = 2;
            if (request.action() == FriendsAction.SEARCH) statement.setString(parameter++, request.query());
            statement.setInt(parameter++, PAGE_SIZE);
            statement.setInt(parameter, request.page() * PAGE_SIZE);
            List<FriendEntry> entries = new ArrayList<>();
            long total = 0;
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    total = rows.getLong("total");
                    UUID id = rows.getObject("id", UUID.class);
                    if (id == null) continue;
                    Timestamp sent = rows.getTimestamp("requested_at");
                    entries.add(new FriendEntry(id, rows.getString("username"), rows.getString("display_name"),
                            rows.getString("relationship"), sent == null ? null : sent.toInstant().toString(), false));
                }
            }
            return new FriendsResult(request.action(), request.page(), PAGE_SIZE, total, entries, "");
        }
    }

    public void change(UUID actor, UUID target, FriendsAction action) throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (action == FriendsAction.SEND) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO friendships(user_low, user_high, requester_id, status)
                            VALUES (LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid), ?, 'REMOVED')
                            ON CONFLICT (user_low, user_high) DO NOTHING
                            """)) {
                        pair(statement, actor, target); statement.setObject(5, actor);
                        statement.setQueryTimeout(10); statement.executeUpdate();
                    }
                }
                String status; UUID requester;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status, requester_id FROM friendships
                        WHERE user_low = LEAST(?::uuid, ?::uuid) AND user_high = GREATEST(?::uuid, ?::uuid) FOR UPDATE
                        """)) {
                    pair(statement, actor, target); statement.setQueryTimeout(10);
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("This relationship no longer exists. Refresh the list.");
                        status = row.getString(1); requester = row.getObject(2, UUID.class);
                    }
                }
                boolean outgoing = requester.equals(actor);
                String next = switch (action) {
                    case SEND -> {
                        if (status.equals("ACCEPTED")) throw new IllegalArgumentException("You are already friends.");
                        if (status.equals("PENDING")) throw new IllegalArgumentException(outgoing
                                ? "Invitation already sent." : "This player already invited you. Accept it in Received.");
                        yield "PENDING";
                    }
                    case ACCEPT, DECLINE -> {
                        if (!status.equals("PENDING") || outgoing) throw new IllegalArgumentException("Only the recipient can answer a pending invitation.");
                        yield action == FriendsAction.ACCEPT ? "ACCEPTED" : "DECLINED";
                    }
                    case CANCEL -> {
                        if (!status.equals("PENDING") || !outgoing) throw new IllegalArgumentException("Only the sender can cancel a pending invitation.");
                        yield "CANCELLED";
                    }
                    case REMOVE -> {
                        if (!status.equals("ACCEPTED")) throw new IllegalArgumentException("This player is not on your friends list.");
                        yield "REMOVED";
                    }
                    default -> throw new IllegalArgumentException("Invalid friends action.");
                };
                String update = action == FriendsAction.SEND
                        ? "status = ?, requester_id = ?, requested_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"
                        : "status = ?, updated_at = CURRENT_TIMESTAMP";
                try (PreparedStatement statement = connection.prepareStatement("UPDATE friendships SET " + update
                        + " WHERE user_low = LEAST(?::uuid, ?::uuid) AND user_high = GREATEST(?::uuid, ?::uuid)")) {
                    statement.setQueryTimeout(10); statement.setString(1, next);
                    int start = 2;
                    if (action == FriendsAction.SEND) statement.setObject(start++, actor);
                    pair(statement, start, actor, target); statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            }
        }
    }

    public boolean areFriends(UUID a, UUID b) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM friendships WHERE user_low = LEAST(?::uuid, ?::uuid)
                AND user_high = GREATEST(?::uuid, ?::uuid) AND status = 'ACCEPTED'
                """)) {
            pair(statement, a, b); statement.setQueryTimeout(10);
            try (ResultSet row = statement.executeQuery()) { return row.next(); }
        }
    }

    public List<UUID> friendIds(UUID account) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement("""
                SELECT CASE WHEN user_low = ? THEN user_high ELSE user_low END FROM friendships
                WHERE status = 'ACCEPTED' AND (user_low = ? OR user_high = ?)
                """)) {
            statement.setQueryTimeout(10);
            for (int i = 1; i <= 3; i++) statement.setObject(i, account);
            List<UUID> ids = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) ids.add(rows.getObject(1, UUID.class)); }
            return ids;
        }
    }

    private static void pair(PreparedStatement statement, UUID a, UUID b) throws SQLException { pair(statement, 1, a, b); }
    private static void pair(PreparedStatement statement, int start, UUID a, UUID b) throws SQLException {
        statement.setObject(start, a); statement.setObject(start + 1, b);
        statement.setObject(start + 2, a); statement.setObject(start + 3, b);
    }
}
