package com.battleship.backend.leaderboard;

import com.battleship.backend.database.Database;
import com.battleship.shared.leaderboard.*;
import java.sql.SQLException;
import java.util.ArrayList;

public final class LeaderboardRepository {
    public static final int PAGE_SIZE = 10;
    public static final int MIN_WIN_RATE_MATCHES = 10;
    private final Database database;
    public LeaderboardRepository(Database database) { this.database = database; }

    public LeaderboardPage page(LeaderboardRequest request) throws SQLException {
        // Only fixed enum-owned fragments enter the SQL. All request values are bound parameters.
        String order = switch (request.sort()) {
            case WINS -> "wins DESC, rate DESC, games DESC, username ASC, user_id ASC";
            case WIN_RATE -> "rate DESC, wins DESC, games DESC, username ASC, user_id ASC";
            case MATCHES -> "games DESC, wins DESC, rate DESC, username ASC, user_id ASC";
        };
        String sql = """
                WITH valid_matches AS (
                    SELECT m.id, m.ended_at, m.started_at
                    FROM matches m JOIN match_players p ON p.match_id=m.id
                    WHERE m.mode='PVP' AND m.status='FINISHED' AND m.ended_at IS NOT NULL
                      AND m.end_reason IN ('FLEET_DESTROYED', 'TURN_TIMEOUT', 'PLAYER_LEFT')
                    GROUP BY m.id
                    HAVING COUNT(*)=2
                       AND COUNT(*) FILTER (WHERE p.player_type='HUMAN')=2
                       AND COUNT(*) FILTER (WHERE p.result='VICTORY')=1
                       AND COUNT(*) FILTER (WHERE p.result='DEFEAT')=1
                       AND MAX(p.player_slot) FILTER (WHERE p.result='VICTORY')=m.winner_slot
                ), results AS (
                    SELECT p.user_id, p.result, v.id, v.ended_at, v.started_at
                    FROM valid_matches v JOIN match_players p ON p.match_id=v.id
                ), stats AS (
                    SELECT u.id AS user_id, u.username, u.display_name, COUNT(*) AS games,
                           COUNT(*) FILTER (WHERE r.result='VICTORY') AS wins,
                           COUNT(*) FILTER (WHERE r.result='DEFEAT') AS losses,
                           COUNT(*) FILTER (WHERE r.result='VICTORY')::NUMERIC / COUNT(*) AS rate
                    FROM users u JOIN results r ON r.user_id=u.id
                    GROUP BY u.id HAVING COUNT(*) >= ?
                ), ranked AS (
                    SELECT *, ROW_NUMBER() OVER (ORDER BY %s) AS rank FROM stats
                ), page AS (
                    SELECT * FROM ranked ORDER BY rank LIMIT ? OFFSET ?
                )
                SELECT totals.total_players, page.*, streak.current_streak
                FROM (SELECT COUNT(*) AS total_players FROM stats) totals
                LEFT JOIN page ON TRUE
                LEFT JOIN LATERAL (
                    SELECT CASE WHEN COALESCE(BOOL_OR(wins>0 AND losses>0 AND seen_losses=losses), FALSE)
                                THEN NULL ELSE COALESCE(SUM(wins) FILTER (WHERE seen_losses=0), 0) END AS current_streak
                    FROM (
                        SELECT *, SUM(losses) OVER (ORDER BY ended_at DESC, started_at DESC ROWS UNBOUNDED PRECEDING) AS seen_losses
                        FROM (
                            SELECT ended_at, started_at,
                                   COUNT(*) FILTER (WHERE result='VICTORY') AS wins,
                                   COUNT(*) FILTER (WHERE result='DEFEAT') AS losses
                            FROM results WHERE user_id=page.user_id GROUP BY ended_at, started_at
                        ) buckets
                    ) chronological
                ) streak ON page.user_id IS NOT NULL
                ORDER BY page.rank
                """.formatted(order);
        var entries = new ArrayList<LeaderboardEntry>();
        long total = 0;
        try (var connection = database.open(); var query = connection.prepareStatement(sql)) {
            query.setQueryTimeout(10);
            query.setInt(1, request.sort() == LeaderboardSort.WIN_RATE ? MIN_WIN_RATE_MATCHES : 1);
            query.setInt(2, PAGE_SIZE);
            query.setLong(3, (long) request.page() * PAGE_SIZE);
            try (var row = query.executeQuery()) {
                while (row.next()) {
                    total = row.getLong("total_players");
                    if (row.getObject("user_id") == null) continue;
                    long streak = row.getLong("current_streak");
                    Long current = row.wasNull() ? null : streak;
                    entries.add(new LeaderboardEntry(row.getLong("rank"), row.getString("display_name"),
                            row.getString("username"), row.getLong("games"), row.getLong("wins"),
                            row.getLong("losses"), row.getBigDecimal("rate").doubleValue() * 100, current));
                }
            }
        }
        return new LeaderboardPage(request.sort(), request.page(), PAGE_SIZE, total, MIN_WIN_RATE_MATCHES, entries);
    }
}
