package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.battleship.backend.match.DesignerMatchChecks.PLAYERS;

/** Checks all 12 room configurations against the real authoritative match service. */
public final class RoomRulesChecks {
    public static void main(String[] args) {
        for (GameMode mode : GameMode.values()) for (int count = 3; count <= 5; count++) for (TurnRule turn : TurnRule.values()) {
            GameRules rules = new GameRules(mode, count, turn);
            GameEngine placement = new GameEngine("Placement", new Random(17)); placement.setRules(rules);
            placement.randomPlacement();
            require(placement.canReady() && placement.state().player().board().ships().size() == count, "Configured random fleet and Ready");
            if (count < 5) require(!placement.selectShip(ShipType.DESTROYER).success()
                    && !placement.placeShip(ShipType.DESTROYER, new Coordinate(9, 0), ShipOrientation.HORIZONTAL).success(), "Disabled types cannot be selected/placed");
            placement.restart(); require(placement.rules().equals(rules) && !placement.canReady(), "Restart preserves rules and clears fleet");
            var now = new AtomicLong(); var match = new MatchService(PLAYERS, rules, now::get);
            FleetSubmission fleet = fleet(rules);
            expectRejected(() -> match.submit("a", new FleetSubmission(fleet.ships().subList(0, rules.shipCount() - 1))));
            if (count < 5) {
                var wrong = new ArrayList<>(fleet.ships());
                wrong.set(0, new ShipPlacement(ShipType.DESTROYER, new Coordinate(9, 0), ShipOrientation.HORIZONTAL));
                expectRejected(() -> match.submit("a", new FleetSubmission(wrong)));
            }
            require(match.snapshot("a").ownBoard().ships().isEmpty(), "Rejected fleet is atomic");
            match.submit("a", fleet);
            require(match.snapshot("a").fleetSubmitted() && match.phase() == GamePhase.PLACEMENT
                    && match.snapshot("b").enemyBoard().ships().isEmpty(), "Configured fleet waits privately");
            match.submit("b", fleet);
            require(match.timer().remainingMillis() == 60_000 && match.snapshot("a").currentPlayerId().equals("a"), "Player 1 opens with 60s");
            now.addAndGet(10_000_000_000L);
            require(match.attack("a", new Coordinate(-1, 0)).type() == AttackResultType.INVALID_COORDINATE
                    && match.timer().remainingMillis() == 53_000, "Invalid shot keeps deadline");
            require(match.attack("a", new Coordinate(9, 9)).type() == AttackResultType.MISS
                    && match.snapshot("a").currentPlayerId().equals("b") && match.timer().remainingMillis() == 60_000, "Miss always passes turn/reset timer");
            match.attack("b", new Coordinate(9, 9)); now.addAndGet(2_000_000_000L);
            require(match.attack("a", new Coordinate(9, 9)).type() == AttackResultType.ALREADY_ATTACKED
                    && match.timer().remainingMillis() == 58_000, "Duplicate shot keeps deadline");
            List<Coordinate> cells = fleet.ships().stream().flatMap(s -> s.ship().occupiedCells().stream()).toList();
            int ai = 0, bi = 0; boolean sawSunk = false;
            while (match.phase() == GamePhase.BATTLE) {
                String actor = match.snapshot("a").currentPlayerId();
                Coordinate cell = cells.get(actor.equals("a") ? ai++ : bi++);
                AttackResult result = match.attack(actor, cell);
                require(result.accepted(), "Valid shot"); sawSunk |= result.sunk();
                if (match.phase() == GamePhase.BATTLE) {
                    require(match.snapshot("a").currentPlayerId().equals(turn == TurnRule.HIT_CONTINUES ? actor : actor.equals("a") ? "b" : "a"), "Hit/sunk applies configured turn rule");
                    require(match.timer().remainingMillis() == 60_000, "Every accepted shot starts a 60s shot window");
                }
            }
            require(sawSunk && match.snapshot("a").winnerId().equals("a") && match.endReason() == MatchEndReason.FLEET_DESTROYED, "Configured fleet can be entirely sunk");
            require(!match.attack("a", new Coordinate(9, 8)).accepted(), "Finished match locked");
            var timed = new MatchService(PLAYERS, rules, now::get); timed.submit("a", fleet); timed.submit("b", fleet);
            now.addAndGet(59_000_000_000L); timed.attack("a", cells.getFirst());
            now.addAndGet(59_999_999_999L); require(!timed.expireTurn(), "Previous deadline cannot expire new shot window");
            now.incrementAndGet();
            require(!timed.attack(timed.snapshot("a").currentPlayerId(), cells.get(1)).accepted()
                    && timed.endReason() == MatchEndReason.TURN_TIMEOUT, "Deadline wins the exact-boundary race");
        }
        for (int invalid : new int[] {0, 2, 6}) expectRejected(() -> new GameRules(GameMode.CLASSIC, invalid, TurnRule.ALTERNATING));
        System.out.println("PASS: 12 room rule combinations; counts/types/Ready, privacy, hit/miss/sunk turns, full wins, deadline resets/rejections/timeout races and restart.");
    }

    static FleetSubmission fleet(GameRules rules) {
        var ships = new ArrayList<ShipPlacement>();
        for (ShipType type : rules.shipTypes()) {
            Coordinate anchor = new Coordinate(type.ordinal() * 2, 0);
            if (rules.mode() == GameMode.DESIGNER) {
                var cells = ShipShapes.all(type.length()).stream().filter(shape -> shape.stream().mapToInt(Coordinate::row).max().orElse(0) <= 1).filter(shape -> shape.stream().anyMatch(c -> c.row() == 1)).findFirst().orElseThrow().stream().map(c -> c.offset(anchor.row(), anchor.column())).toList();
                // Use a compact row-fitting shape so independent ships cannot overlap.
                if (cells.stream().mapToInt(Coordinate::row).max().orElse(0) >= anchor.row() + 2)
                    cells = new Ship(type, anchor, ShipOrientation.HORIZONTAL).occupiedCells();
                ships.add(ShipPlacement.from(new Ship(type, cells)));
            } else ships.add(new ShipPlacement(type, anchor, ShipOrientation.HORIZONTAL));
        }
        return new FleetSubmission(ships);
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expectRejected(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid configuration/fleet accepted");
    }
}
