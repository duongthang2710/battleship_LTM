package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.ShipPlacement;
import com.battleship.shared.room.RoomPlayer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class MatchTimerChecks {
    static void run() {
        AtomicLong time = new AtomicLong();
        MatchService match = match(time);
        require(match.timer().remainingMillis() == 60_000, "First turn starts at 60 seconds");
        time.set(TimeUnit.SECONDS.toNanos(20));
        require(match.attack("first", new Coordinate(-1, 0)).type() == AttackResultType.INVALID_COORDINATE,
                "Invalid coordinate rejected");
        require(match.timer().remainingMillis() == 40_000 && match.snapshot("first").revision() == 3,
                "Rejected shot keeps deadline and revision");
        require(match.attack("first", new Coordinate(9, 9)).accepted(), "Valid shot accepted");
        require(match.timer().remainingMillis() == 60_000 && match.timer().currentPlayerId().equals("second"),
                "Valid shot changes player and resets 60 seconds");
        require(match.attack("second", new Coordinate(8, 8)).accepted(), "Opponent shoots");
        time.addAndGet(TimeUnit.SECONDS.toNanos(10));
        long revision = match.snapshot("first").revision();
        require(match.attack("first", new Coordinate(9, 9)).type() == AttackResultType.ALREADY_ATTACKED,
                "Repeated cell rejected");
        require(match.timer().remainingMillis() == 50_000 && match.snapshot("first").revision() == revision,
                "Repeated shot does not reset clock or turn");

        time.set(0);
        match = match(time);
        time.set(TimeUnit.SECONDS.toNanos(60) - 1);
        require(match.attack("first", new Coordinate(9, 9)).accepted(), "One nanosecond before deadline is valid");
        time.set(TimeUnit.SECONDS.toNanos(60));
        require(!match.expireTurn() && match.phase() == GamePhase.BATTLE, "Old deadline cannot expire new turn");
        time.set(TimeUnit.SECONDS.toNanos(120) - 1);
        require(match.expireTurn(), "Exact new deadline expires once");
        var ended = match.snapshot("first");
        require(ended.timedOut() && ended.winnerId().equals("first") && ended.lastAttack() == null,
                "Current player loses; no prior attack is replayed");
        require(!match.expireTurn() && match.snapshot("first").revision() == ended.revision(),
                "Repeated expiry cannot change finished match");
        require(!match.attack("second", new Coordinate(7, 7)).accepted()
                && match.snapshot("first").ownBoard().shots().isEmpty(), "Finished match rejects further attacks");

        time.set(0);
        match = match(time);
        time.set(TimeUnit.SECONDS.toNanos(60));
        require(match.attack("first", new Coordinate(0, 0)).type() == AttackResultType.GAME_FINISHED,
                "A shot processed exactly at expiry loses to timeout");
        require(match.snapshot("second").winnerId().equals("second")
                && match.snapshot("second").ownBoard().shots().isEmpty(), "Late shot cannot damage defender");
        AtomicLong disconnectTime = new AtomicLong();
        MatchService disconnected = match(disconnectTime);
        require(disconnected.forfeit("second") && disconnected.snapshot("first").winnerId().equals("first")
                && disconnected.endReason() == com.battleship.shared.match.MatchEndReason.PLAYER_LEFT, "Disconnect ends a live match");
        long disconnectRevision = disconnected.snapshot("first").revision();
        disconnectTime.set(TimeUnit.SECONDS.toNanos(120));
        require(!disconnected.expireTurn() && !disconnected.forfeit("first")
                && disconnected.snapshot("first").revision() == disconnectRevision, "Stale timer/disconnect cannot overwrite forfeit");
        AtomicLong boundary = new AtomicLong();
        MatchService deadlineFirst = match(boundary);
        boundary.set(TimeUnit.SECONDS.toNanos(60));
        require(deadlineFirst.forfeit("second") && deadlineFirst.snapshot("first").winnerId().equals("second")
                && deadlineFirst.endReason() == com.battleship.shared.match.MatchEndReason.TURN_TIMEOUT,
                "Expired turn takes precedence over a concurrent opponent departure");
        System.out.println("PASS turn timer: 60 seconds, valid reset, invalid/repeat unchanged, deadline boundary, stale expiry, timeout and post-game guard.");
    }

    private static MatchService match(AtomicLong time) {
        MatchService match = new MatchService(List.of(new RoomPlayer("first", "First", true, Side.WHITE),
                new RoomPlayer("second", "Second", true, Side.BLACK)), time::get);
        require(!match.expireTurn() && match.timer().remainingMillis() == 0, "Placement has no clock");
        ShipType[] types = ShipType.values();
        FleetSubmission fleet = new FleetSubmission(java.util.stream.IntStream.range(0, types.length)
                .mapToObj(i -> new ShipPlacement(types[i], new Coordinate(i, 0), ShipOrientation.HORIZONTAL)).toList());
        match.submit("first", fleet);
        match.submit("second", fleet);
        return match;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
