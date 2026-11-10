package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.MatchEndReason;
import com.battleship.shared.match.SkillRequest;
import com.battleship.shared.room.RoomOptions;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static com.battleship.backend.match.DesignerMatchChecks.PLAYERS;
import static com.battleship.backend.match.RoomRulesChecks.require;

/** Authoritative Skill battle: countdown/deadline guards, timer reset, private sonar, hidden sinking, victory. */
public final class SkillMatchChecks {
    private static final GameRules SKILLS = new GameRules(GameMode.CLASSIC, 5, TurnRule.ALTERNATING, CombatMode.SKILLS);

    public static void main(String[] args) {
        AtomicLong time = new AtomicLong();
        var classic = start(GameRules.DEFAULT, RoomOptions.DEFAULT, time, 1);
        time.set(TimeUnit.SECONDS.toNanos(3));
        require(classic.useSkill("a", new SkillRequest(SkillType.SONAR, new Coordinate(5, 5), null)).type() == AttackResultType.INVALID_SKILL
                && classic.snapshot("a").skills() == null, "Classic room rejects skills and sends no skill state");

        time.set(0);
        var match = start(SKILLS, RoomOptions.DEFAULT, time, 2);
        require(match.useSkill("a", new SkillRequest(SkillType.SONAR, new Coordinate(5, 5), null)).type() == AttackResultType.NOT_IN_BATTLE,
                "No skill during the 3-2-1 countdown");
        time.set(TimeUnit.SECONDS.toNanos(3));
        for (int i = 0; i < 3; i++) { miss(match, "a"); miss(match, "b"); }
        time.addAndGet(TimeUnit.SECONDS.toNanos(20));
        long revision = match.snapshot("a").revision();
        require(!match.useSkill("a", new SkillRequest(SkillType.TORPEDO, new Coordinate(0, 0), SkillDirection.EAST)).accepted()
                && match.timer().remainingMillis() == 40_000 && match.snapshot("a").revision() == revision,
                "Rejected skill (not enough energy) keeps deadline and revision");
        require(match.useSkill("a", null).type() == AttackResultType.INVALID_SKILL, "Missing skill payload rejected");

        var sonar = match.useSkill("a", new SkillRequest(SkillType.SONAR, new Coordinate(0, 1), null));
        require(sonar.accepted() && match.timer().remainingMillis() == 60_000 && match.timer().currentPlayerId().equals("b")
                && match.snapshot("a").revision() == revision + 1, "Accepted skill passes the turn and opens a new 60s window");
        var own = match.snapshot("a").skills();
        var other = match.snapshot("b").skills();
        require(own.lastSkill().sonarCount() == 6 && own.ownScans().size() == 1 && own.ownEnergy() == 0 && own.opponentEnergy() == 3,
                "Scanner sees the count, its reading and both energy bars");
        require(other.lastSkill().sonarCount() == null && other.ownScans().isEmpty() && other.lastSkill().area().size() == 9
                && other.ownEnergy() == 3 && other.opponentEnergy() == 0, "Opponent sees the scanned area but not the count");
        require(match.totalTurns() == match.snapshot("a").turnNumber(), "A skill counts as one turn in history");

        time.addAndGet(TimeUnit.SECONDS.toNanos(60));
        require(match.useSkill("b", new SkillRequest(SkillType.CLUSTER, new Coordinate(8, 0), null)).type() == AttackResultType.GAME_FINISHED
                && match.endReason() == MatchEndReason.TURN_TIMEOUT && match.snapshot("a").ownBoard().shots().size() == 3,
                "Skill processed at the deadline loses to timeout and cannot damage the fleet");

        time.set(0);
        var hidden = start(SKILLS, new RoomOptions(true), time, 3);
        time.set(TimeUnit.SECONDS.toNanos(3));
        for (int i = 0; i < 3; i++) { miss(hidden, "a"); miss(hidden, "b"); }
        var cluster = hidden.useSkill("a", new SkillRequest(SkillType.CLUSTER, new Coordinate(8, 0), null));
        require(cluster.type() == AttackResultType.SUNK, "Cluster sinks the destroyer server-side");
        for (String viewer : List.of("a", "b")) {
            var snapshot = hidden.snapshot(viewer);
            var skill = snapshot.skills().lastSkill();
            require(skill.type() == AttackResultType.HIT && skill.impacts().stream().noneMatch(i -> i.sunk() || i.shipType() != null
                    || i.type() == AttackResultType.SUNK), "Hidden sinking also masks skill impacts");
            if (viewer.equals("a")) RoomRulesChecks.checkHiddenSnapshot(snapshot);
        }

        time.set(0);
        var barrage = start(SKILLS, RoomOptions.DEFAULT, time, 42);
        time.set(TimeUnit.SECONDS.toNanos(3));
        for (int i = 0; i < 4; i++) { miss(barrage, "a"); miss(barrage, "b"); }
        require(barrage.useSkill("a", new SkillRequest(SkillType.BARRAGE, new Coordinate(0, 0), null)).impacts().size() == 4,
                "Barrage fires four missiles");

        time.set(0);
        var three = new GameRules(GameMode.CLASSIC, 3, TurnRule.ALTERNATING, CombatMode.SKILLS);
        var finale = start(three, RoomOptions.DEFAULT, time, 5);
        time.set(TimeUnit.SECONDS.toNanos(3));
        for (int i = 0; i < 3; i++) { miss(finale, "a"); miss(finale, "b"); }
        var fleetCells = RoomRulesChecks.fleet(three).ships().stream()
                .flatMap(s -> s.ship().occupiedCells().stream()).filter(c -> !c.equals(new Coordinate(0, 0))).toList();
        for (Coordinate cell : fleetCells) { require(finale.attack("a", cell).accepted(), "Setup shot"); miss(finale, "b"); }
        var win = finale.useSkill("a", new SkillRequest(SkillType.CLUSTER, new Coordinate(0, 0), null));
        require(win.type() == AttackResultType.GAME_OVER && finale.endReason() == MatchEndReason.FLEET_DESTROYED
                && "a".equals(finale.snapshot("b").winnerId()), "Winning skill ends the match as fleet destroyed");
        System.out.println("PASS skill match: countdown/deadline guards, rejected skill keeps clock, accepted skill resets 60s, private sonar, hidden sinking, barrage, skill victory.");
    }

    private static MatchService start(GameRules rules, RoomOptions options, AtomicLong time, long seed) {
        var match = new MatchService(PLAYERS, rules, options, time::get, new Random(seed));
        var fleet = RoomRulesChecks.fleet(rules);
        match.submit("a", fleet);
        match.submit("b", fleet);
        return match;
    }

    /** Fleets occupy columns 0-4 of even rows; columns 5-9 are always water. */
    private static void miss(MatchService match, String actor) {
        var board = match.snapshot(actor).enemyBoard().board();
        for (int row = 0; row < Board.SIZE; row++) for (int column = 5; column < Board.SIZE; column++) {
            Coordinate cell = new Coordinate(row, column);
            if (!board.wasAttacked(cell)) {
                require(match.attack(actor, cell).type() == AttackResultType.MISS, "Water shot misses");
                return;
            }
        }
        throw new AssertionError("No water left");
    }
}
