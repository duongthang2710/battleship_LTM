package com.battleship.backend.match;

import com.battleship.backend.database.Database;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.CellState;
import com.battleship.shared.game.Side;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Ordered JDBC writes run outside the room/timer lock. No fleet coordinates are stored. */
public final class MatchRepository implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(MatchRepository.class.getName());
    private final Database database;
    private final ThreadPoolExecutor writes = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1024), Thread.ofVirtual().name("match-history").factory());

    public MatchRepository(Database database) { this.database = database; }

    public record Participant(UUID userId, Side side, int slot) {}
    public record Statistics(UUID userId, String result, int shots, int hits, int sunk) {
        public static Statistics from(UUID userId, String result, Board target) {
            int hits = (int) target.attackedCells().stream().filter(cell -> target.cell(cell).state() != CellState.MISS).count();
            int sunk = (int) target.ships().stream().filter(ship -> ship.isSunk()).count();
            return new Statistics(userId, result, target.attackedCells().size(), hits, sunk);
        }
    }

    public void start(UUID matchId, Instant started, List<Participant> players) {
        enqueue(() -> {
            try (var connection = database.open()) {
                connection.setAutoCommit(false);
                try (var match = connection.prepareStatement("INSERT INTO matches(id, started_at, status) VALUES (?, ?, 'BATTLE')");
                     var player = connection.prepareStatement("INSERT INTO match_players(match_id, user_id, player_slot, side) VALUES (?, ?, ?, ?)")) {
                    match.setObject(1, matchId);
                    match.setTimestamp(2, java.sql.Timestamp.from(started));
                    match.executeUpdate();
                    for (Participant participant : players) {
                        player.setObject(1, matchId);
                        player.setObject(2, participant.userId());
                        player.setInt(3, participant.slot());
                        player.setString(4, participant.side().name());
                        player.addBatch();
                    }
                    player.executeBatch();
                    connection.commit();
                } catch (SQLException exception) { connection.rollback(); throw exception; }
            }
        });
    }

    public void finish(UUID matchId, Instant ended, String reason, Integer winnerSlot, int totalTurns, List<Statistics> statistics) {
        enqueue(() -> {
            try (var connection = database.open()) {
                connection.setAutoCommit(false);
                try (var match = connection.prepareStatement("UPDATE matches SET ended_at=?, status='FINISHED', end_reason=?, winner_slot=?, total_turn=? WHERE id=? AND status='BATTLE'");
                     var player = connection.prepareStatement("UPDATE match_players SET result=?, shots=?, hits=?, ships_sunk=? WHERE match_id=? AND user_id=?")) {
                    match.setTimestamp(1, java.sql.Timestamp.from(ended));
                    match.setString(2, reason);
                    match.setObject(3, winnerSlot);
                    match.setInt(4, totalTurns);
                    match.setObject(5, matchId);
                    if (match.executeUpdate() == 1) for (Statistics stats : statistics) {
                        player.setString(1, stats.result());
                        player.setInt(2, stats.shots());
                        player.setInt(3, stats.hits());
                        player.setInt(4, stats.sunk());
                        player.setObject(5, matchId);
                        player.setObject(6, stats.userId());
                        player.addBatch();
                    }
                    player.executeBatch();
                    connection.commit();
                } catch (SQLException exception) { connection.rollback(); throw exception; }
            }
        });
    }

    public void awaitPendingWrites() throws InterruptedException, java.util.concurrent.ExecutionException,
            java.util.concurrent.TimeoutException {
        // Called on a client worker, never while holding the room/timer lock.
        writes.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    private void enqueue(SqlWrite operation) {
        try {
            writes.execute(() -> {
                try { operation.run(); }
                catch (SQLException exception) { LOG.severe("Match history write failed; SQL state=" + exception.getSQLState()); }
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            LOG.severe("Match history queue unavailable; record was not saved.");
        }
    }

    @Override public void close() {
        writes.shutdown();
        try { if (!writes.awaitTermination(30, TimeUnit.SECONDS)) writes.shutdownNow(); }
        catch (InterruptedException exception) { writes.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    @FunctionalInterface private interface SqlWrite { void run() throws SQLException; }
}
