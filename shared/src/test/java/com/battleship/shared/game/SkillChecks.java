package com.battleship.shared.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Skill battle rules: energy, the four skill geometries, turn passing, rejection atomicity and victory. */
public final class SkillChecks {
    private static final GameRules SKILLS = new GameRules(GameMode.CLASSIC, 5, TurnRule.ALTERNATING, CombatMode.SKILLS);

    public static void main(String[] args) {
        classicRejectsSkills();
        energyAndCluster();
        hitContinuesStillPassesTurn();
        torpedo();
        sonar();
        barrage();
        rejectionsAndVictory();
        System.out.println("Skill checks passed: energy +1/cap 8, cluster, barrage, torpedo, sonar, turn pass, rejections, victory, restart.");
    }

    private static void classicRejectsSkills() {
        GameEngine engine = battle(GameRules.DEFAULT, 1);
        for (int i = 0; i < 6; i++) { miss(engine, "a"); miss(engine, "b"); }
        check(engine.energy("a") == 0, "Classic combat never charges energy");
        check(engine.useSkill("a", SkillType.SONAR, new Coordinate(5, 5), null).type() == AttackResultType.INVALID_SKILL,
                "Classic combat rejects every skill");
    }

    private static void energyAndCluster() {
        GameEngine engine = battle(SKILLS, 2);
        check(engine.energy("a") == 0, "Energy starts at zero");
        int turn = engine.state().turnNumber();
        SkillResult early = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(0, 0), null);
        check(early.type() == AttackResultType.NOT_ENOUGH_ENERGY && !early.accepted(), "Skill needs enough energy");
        check(engine.state().turnNumber() == turn && engine.state().currentPlayerId().equals("a")
                && engine.state().enemy().board().attackedCells().isEmpty(), "Rejected skill keeps state unchanged");
        for (int i = 0; i < 3; i++) { miss(engine, "a"); miss(engine, "b"); }
        check(engine.energy("a") == 3 && engine.energy("b") == 3, "Each accepted normal shot adds one energy");

        turn = engine.state().turnNumber();
        SkillResult cluster = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(0, 0), null);
        check(cluster.accepted() && cluster.type() == AttackResultType.HIT, "Cluster hits the corner fleet");
        check(new HashSet<>(cluster.impacts().stream().map(AttackResult::coordinate).toList())
                .equals(new HashSet<>(List.of(new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(0, 1)))),
                "Corner cluster keeps only in-bounds plus cells");
        check(engine.energy("a") == 0 && engine.state().currentPlayerId().equals("b")
                && engine.state().turnNumber() == turn + 1, "Cluster spends 3 energy, passes turn, counts one turn");
        check(engine.lastSkill() == cluster && engine.state().lastAttack().equals(cluster.impacts().getLast()),
                "Last skill and last impact are exposed");

        miss(engine, "b");
        check(engine.energy("a") == 0 && engine.lastSkill() == null, "Skill shots do not charge energy; normal shot clears last skill");
        miss(engine, "a");
        miss(engine, "b");
        SkillResult second = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(0, 1), null);
        check(second.type() == AttackResultType.NOT_ENOUGH_ENERGY, "Energy is spent");
        for (int i = 0; i < 2; i++) { miss(engine, "a"); miss(engine, "b"); }
        second = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(0, 1), null);
        check(second.impacts().size() == 2, "Cluster skips cells already attacked");

        GameEngine capped = battle(SKILLS, 3);
        for (int i = 0; i < 12; i++) { miss(capped, "a"); miss(capped, "b"); }
        check(capped.energy("a") == SkillType.MAX_ENERGY, "Energy is capped at 8");
        capped.restart();
        check(capped.energy("a") == 0 && capped.scans("a").isEmpty() && capped.lastSkill() == null, "Restart clears skill state");
    }

    private static void hitContinuesStillPassesTurn() {
        GameEngine engine = battle(new GameRules(GameMode.CLASSIC, 5, TurnRule.HIT_CONTINUES, CombatMode.SKILLS), 4);
        for (int i = 0; i < 3; i++) { miss(engine, "a"); miss(engine, "b"); }
        SkillResult hit = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(1, 1), null);
        check(hit.type() == AttackResultType.HIT && engine.state().currentPlayerId().equals("b"),
                "A hitting skill still ends the turn under Hit grants another shot");
    }

    private static void torpedo() {
        GameEngine engine = battle(SKILLS, 5);
        for (int i = 0; i < 5; i++) { miss(engine, "a"); miss(engine, "b"); }
        SkillResult noDirection = engine.useSkill("a", SkillType.TORPEDO, new Coordinate(2, 5), null);
        check(noDirection.type() == AttackResultType.INVALID_SKILL, "Torpedo requires a direction");
        SkillResult west = engine.useSkill("a", SkillType.TORPEDO, new Coordinate(2, 5), SkillDirection.WEST);
        List<Coordinate> cells = west.impacts().stream().map(AttackResult::coordinate).toList();
        check(cells.getFirst().equals(new Coordinate(2, 9)) && cells.getLast().equals(new Coordinate(2, 2)) && cells.size() == 8,
                "Westward torpedo enters at the east edge and stops on the cruiser");
        check(west.impacts().subList(0, 7).stream().allMatch(i -> i.type() == AttackResultType.MISS)
                && west.impacts().getLast().type() == AttackResultType.HIT && engine.energy("a") == 0, "Water path is revealed as misses");
        check(SkillTargeting.area(SkillType.TORPEDO, new Coordinate(2, 5), SkillDirection.SOUTH).getFirst().equals(new Coordinate(0, 5)),
                "Southward torpedo enters from the north edge");

        GameEngine through = battle(SKILLS, 6);
        for (int i = 0; i < 5; i++) { miss(through, "a"); miss(through, "b"); }
        check(through.attack("a", new Coordinate(4, 0)).accepted(), "Destroyer bow hit by a normal shot");
        miss(through, "b");
        SkillResult east = through.useSkill("a", SkillType.TORPEDO, new Coordinate(4, 8), SkillDirection.EAST);
        check(east.impacts().size() == 1 && east.impacts().getFirst().coordinate().equals(new Coordinate(4, 1))
                && east.type() == AttackResultType.SUNK, "Torpedo passes over attacked cells and sinks the next ship cell");
    }

    private static void sonar() {
        GameEngine engine = battle(SKILLS, 7);
        for (int i = 0; i < 3; i++) { miss(engine, "a"); miss(engine, "b"); }
        int shotsBefore = engine.state().enemy().board().attackedCells().size();
        SkillResult scan = engine.useSkill("a", SkillType.SONAR, new Coordinate(0, 0), null);
        check(scan.type() == AttackResultType.SCANNED && scan.sonarCount() == 9 && scan.area().size() == 9,
                "Corner sonar clamps to 3x3 and counts unhit ship cells");
        check(engine.state().enemy().board().attackedCells().size() == shotsBefore && scan.impacts().isEmpty(),
                "Sonar does not mark any cell as attacked");
        check(engine.scans("a").size() == 1 && engine.scans("b").isEmpty() && engine.state().currentPlayerId().equals("b"),
                "Sonar reading belongs to the scanner and ends the turn");
    }

    private static void barrage() {
        GameEngine engine = battle(SKILLS, 8);
        for (int i = 0; i < 4; i++) { miss(engine, "a"); miss(engine, "b"); }
        SkillResult barrage = engine.useSkill("a", SkillType.BARRAGE, new Coordinate(9, 9), null);
        var area = SkillTargeting.area(SkillType.BARRAGE, new Coordinate(9, 9), null);
        check(area.size() == 16 && area.getFirst().equals(new Coordinate(6, 6)), "Barrage area clamps inside the board");
        var shots = barrage.impacts().stream().map(AttackResult::coordinate).toList();
        check(shots.size() == 4 && new HashSet<>(shots).size() == 4 && area.containsAll(shots), "Four distinct missiles inside the area");

        Board board = new Board();
        for (Coordinate cell : area.subList(0, 14)) board = board.withAttack(cell);
        check(SkillTargeting.shots(SkillType.BARRAGE, new Coordinate(6, 6), null, board, new Random(1)).size() == 2,
                "Barrage fires at the remaining open cells only");
        check(SkillTargeting.shots(SkillType.BARRAGE, new Coordinate(6, 6), null, board, new Random(9))
                .equals(SkillTargeting.shots(SkillType.BARRAGE, new Coordinate(6, 6), null, board, new Random(9))),
                "Barrage is deterministic for a seeded random");
    }

    private static void rejectionsAndVictory() {
        GameEngine engine = battle(new GameRules(GameMode.CLASSIC, 3, TurnRule.ALTERNATING, CombatMode.SKILLS), 9);
        for (int i = 0; i < 3; i++) { miss(engine, "a"); miss(engine, "b"); }
        check(engine.useSkill("b", SkillType.SONAR, new Coordinate(5, 5), null).type() == AttackResultType.NOT_YOUR_TURN,
                "Skill only on own turn");
        check(engine.useSkill("a", SkillType.CLUSTER, new Coordinate(10, 0), null).type() == AttackResultType.INVALID_COORDINATE,
                "Out-of-bounds origin rejected");
        // Leave only the carrier bow (0,0): sink everything else with normal shots.
        List<Coordinate> targets = new ArrayList<>();
        for (Ship ship : engine.state().enemy().board().ships()) targets.addAll(ship.occupiedCells());
        targets.remove(new Coordinate(0, 0));
        for (Coordinate cell : targets) {
            check(engine.attack("a", cell).accepted(), "Setup shot");
            miss(engine, "b");
        }
        SkillResult finish = engine.useSkill("a", SkillType.CLUSTER, new Coordinate(0, 0), null);
        check(finish.type() == AttackResultType.GAME_OVER && finish.gameOver() && "a".equals(finish.winnerId())
                && engine.state().phase() == GamePhase.GAME_OVER, "A skill can finish the fleet and win");
        check(engine.useSkill("b", SkillType.SONAR, new Coordinate(5, 5), null).type() == AttackResultType.GAME_FINISHED,
                "No skills after game over");
    }

    private static final List<Coordinate> WATER = water();

    private static List<Coordinate> water() {
        List<Coordinate> cells = new ArrayList<>();
        for (int row = 5; row < Board.SIZE; row++) for (int column = 0; column < Board.SIZE; column++) cells.add(new Coordinate(row, column));
        return List.copyOf(cells);
    }

    private static void miss(GameEngine engine, String actor) {
        Board defender = actor.equals("a") ? engine.state().enemy().board() : engine.state().player().board();
        Coordinate cell = WATER.stream().filter(c -> !defender.wasAttacked(c)).findFirst().orElseThrow();
        check(engine.attack(actor, cell).type() == AttackResultType.MISS, "Water shot misses");
    }

    /** Both fleets: ship i on row i starting at column 0, horizontal. */
    private static GameEngine battle(GameRules rules, long seed) {
        GameEngine engine = new GameEngine(new Player("a", "A", new Board()), new Player("b", "B", new Board()), new Random(seed));
        engine.setRules(rules);
        List<Ship> fleet = new ArrayList<>();
        for (int i = 0; i < rules.shipCount(); i++)
            fleet.add(new Ship(rules.shipTypes().get(i), new Coordinate(i, 0), ShipOrientation.HORIZONTAL));
        engine.submitFleet("a", fleet);
        engine.submitFleet("b", fleet);
        check(engine.state().phase() == GamePhase.BATTLE, "Both fleets start battle");
        return engine;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
