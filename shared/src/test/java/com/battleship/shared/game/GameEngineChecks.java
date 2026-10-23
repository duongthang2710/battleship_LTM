package com.battleship.shared.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class GameEngineChecks {
    public static void main(String[] args) {
        boundariesAndAtomicPlacement();
        placementSelectionAndGrab();
        completeFleetAndRandomPlacement();
        attackLifecycle();
        System.out.println("Game rules checks passed: bounds, overlap, move/rotate, fleet, turns, hit/sunk/game over.");
    }

    private static void placementSelectionAndGrab() {
        GameEngine engine = new GameEngine("Player", new Random(4));
        check(engine.clickPlacement(new Coordinate(2, 1)).success(), "Click places selected ship");
        check(engine.state().placement().selectedType() == ShipType.BATTLESHIP, "Next unplaced ship selected");
        check(engine.clickPlacement(new Coordinate(2, 3)).success(), "Click grabs existing carrier");
        check(engine.state().placement().grabOffset().equals(new Coordinate(0, 2)), "Grab offset stored in grid cells");
        PlacementResult preview = engine.previewPlacement(new Coordinate(4, 6));
        check(preview.success() && preview.affectedCells().getFirst().equals(new Coordinate(4, 4)), "Preview uses grab anchor");
        check(engine.rotateSelected().success(), "Grabbed ship rotates for preview");
        check(engine.state().placement().grabOffset().equals(new Coordinate(2, 0)), "Rotation swaps grid offsets");
        check(engine.clickPlacement(new Coordinate(6, 4)).success(), "Drop moves rotated ship atomically");
        Ship carrier = engine.state().player().board().ship(ShipType.CARRIER);
        check(carrier.position().equals(new Coordinate(4, 4)) && carrier.orientation() == ShipOrientation.VERTICAL,
                "Dropped ship matches rotated preview");
        check(!engine.state().placement().moving(), "Drop clears moving state");
    }

    private static void boundariesAndAtomicPlacement() {
        GameEngine engine = new GameEngine("Player", new Random(1));
        check(engine.state().phase() == GamePhase.PLACEMENT, "Initial placement phase");
        check(!engine.ready().success(), "Incomplete fleet cannot start");
        rejectedAttack(engine, "player", new Coordinate(0, 0), AttackResultType.NOT_IN_BATTLE);
        for (ShipType type : ShipType.values()) {
            for (ShipOrientation orientation : ShipOrientation.values()) {
                check(engine.canPlace(type, new Coordinate(0, 0), orientation), "Origin is valid");
                Coordinate last = new Coordinate(Board.SIZE - 1 - (type.length() - 1) * orientation.deltaRow(),
                        Board.SIZE - 1 - (type.length() - 1) * orientation.deltaColumn());
                check(engine.canPlace(type, last, orientation), "Last fitting anchor is valid");
                check(!engine.canPlace(type, new Coordinate(-1, 0), orientation), "Negative row rejected");
                check(!engine.canPlace(type, new Coordinate(0, -1), orientation), "Negative column rejected");
                check(!engine.canPlace(type, last.offset(orientation.deltaRow(), orientation.deltaColumn()), orientation),
                        "Beyond final edge rejected");
            }
        }
        List<Coordinate> preview = engine.previewCells(ShipType.CARRIER, new Coordinate(9, 9), ShipOrientation.HORIZONTAL);
        check(preview.size() == 5 && preview.getLast().column() == 13, "Invalid preview keeps full footprint");
        check(engine.placeShip(ShipType.CARRIER, new Coordinate(0, 0), ShipOrientation.HORIZONTAL).success(), "Place carrier");
        GameState initial = engine.state();
        check(!engine.placeShip(ShipType.DESTROYER, new Coordinate(0, 2), ShipOrientation.VERTICAL).success(), "Overlap rejected");
        check(engine.state() == initial, "Invalid placement leaves state unchanged");
        check(engine.placeShip(ShipType.CARRIER, new Coordinate(0, 1), ShipOrientation.HORIZONTAL).success(), "Move ignores own old cells");
        check(engine.state().player().board().cell(new Coordinate(0, 0)).state() == CellState.EMPTY, "Old position cleared");
        check(initial.player().board().cell(new Coordinate(0, 0)).state() == CellState.SHIP, "Old snapshot immutable");
        check(engine.rotateShip(ShipType.CARRIER).success(), "Rotate into open space");
        check(engine.state().player().board().ship(ShipType.CARRIER).orientation() == ShipOrientation.VERTICAL, "Rotation stored");
        check(engine.placeShip(ShipType.CARRIER, new Coordinate(9, 0), ShipOrientation.HORIZONTAL).success(), "Place along final row");
        initial = engine.state();
        check(!engine.rotateShip(ShipType.CARRIER).success(), "Boundary rotation rejected");
        check(engine.state() == initial, "Failed rotation leaves original ship");
        check(engine.placeShip(ShipType.CARRIER, new Coordinate(0, 0), ShipOrientation.HORIZONTAL).success(), "Move carrier");
        check(engine.placeShip(ShipType.DESTROYER, new Coordinate(2, 0), ShipOrientation.HORIZONTAL).success(), "Place destroyer");
        initial = engine.state();
        check(!engine.rotateShip(ShipType.CARRIER).success(), "Overlap rotation rejected");
        check(!engine.placeShip(ShipType.CARRIER, new Coordinate(2, 0), ShipOrientation.HORIZONTAL).success(), "Overlap reposition rejected");
        check(engine.state() == initial, "Invalid reposition atomic");
        check(engine.removeShip(ShipType.DESTROYER).success(), "Remove ship");
        check(!engine.removeShip(ShipType.DESTROYER).success(), "Missing ship removal is false");
        engine.resetFleet();
        check(engine.state().player().board().ships().isEmpty(), "Reset clears fleet");
    }

    private static void completeFleetAndRandomPlacement() {
        GameEngine engine = new GameEngine("Player", new Random(2));
        int row = 0;
        for (ShipType type : ShipType.values()) {
            check(!engine.ready().success(), "Ready stays gated until all five ships");
            check(engine.placeShip(type, new Coordinate(row++, 0), ShipOrientation.HORIZONTAL).success(), "Place every type");
        }
        verifyFleet(engine.state().player().board());
        check(engine.ready().success(), "Ready starts battle");
        check(engine.state().phase() == GamePhase.BATTLE, "Battle phase stored");
        check(engine.state().turnNumber() == 1, "Ready starts first turn");
        verifyFleet(engine.state().enemy().board());
        check(!engine.ready().success(), "Cannot start twice");
        check(!engine.placeShip(ShipType.CARRIER, new Coordinate(5, 5), ShipOrientation.HORIZONTAL).success(), "Placement closed in battle");
        check(!engine.rotateShip(ShipType.CARRIER).success(), "Rotation closed in battle");
        check(!engine.removeShip(ShipType.CARRIER).success(), "Removal closed in battle");
        check(!engine.resetFleet().success(), "Reset closed in battle");
        check(!engine.randomPlacement().success(), "Random placement closed in battle");
        for (int seed = 0; seed < 100; seed++) {
            GameEngine random = new GameEngine("Random", new Random(seed));
            random.randomPlacement();
            verifyFleet(random.state().player().board());
            check(random.ready().success(), "Random fleet enables Ready");
            verifyFleet(random.state().enemy().board());
        }
    }

    private static void attackLifecycle() {
        GameEngine engine = new GameEngine("Player", new Random(3));
        engine.randomPlacement();
        check(engine.startGame().success(), "Start-game alias starts prepared fleet");
        Board enemyBefore = engine.state().enemy().board();
        List<Coordinate> enemyMisses = emptyCells(enemyBefore);
        List<Coordinate> playerMisses = emptyCells(engine.state().player().board());
        rejectedAttack(engine, "unknown", new Coordinate(0, 0), AttackResultType.NOT_YOUR_TURN);
        rejectedAttack(engine, "enemy", new Coordinate(0, 0), AttackResultType.NOT_YOUR_TURN);
        rejectedAttack(engine, "player", new Coordinate(-1, 0), AttackResultType.INVALID_COORDINATE);
        rejectedAttack(engine, "player", null, AttackResultType.INVALID_COORDINATE);
        Coordinate miss = enemyMisses.getFirst();
        check(engine.canAttack("player", miss), "Unattacked cell is actionable");
        check(engine.attack(miss).type() == AttackResultType.MISS, "Empty cell misses");
        check(engine.state().enemy().board().cell(miss).state() == CellState.MISS, "Miss marked");
        check(engine.state().currentPlayerId().equals("enemy"), "Turn switches after miss");
        check(engine.state().turnNumber() == 2, "Accepted attack advances turn counter");
        engine.attack("enemy", playerMisses.getFirst());
        rejectedAttack(engine, "player", miss, AttackResultType.ALREADY_ATTACKED);
        int opponentShot = 1;
        int destroyed = 0;
        for (Ship ship : enemyBefore.ships()) {
            for (int i = 0; i < ship.occupiedCells().size(); i++) {
                Coordinate coordinate = ship.occupiedCells().get(i);
                AttackResult result = engine.attack("player", coordinate);
                check(result.shipType() == ship.type(), "Hit identifies ship");
                check(result.attackerId().equals("player") && result.defenderId().equals("enemy"), "Attack ownership");
                boolean lastCell = i == ship.type().length() - 1;
                boolean lastShip = destroyed == ShipType.values().length - 1;
                AttackResultType expected = !lastCell ? AttackResultType.HIT : lastShip ? AttackResultType.GAME_OVER : AttackResultType.SUNK;
                check(result.accepted() && result.type() == expected, "Hit / sunk / final victory outcome");
                check(result.sunk() == lastCell && result.gameOver() == (lastCell && lastShip), "Result flags reflect fleet state");
                check(result.affectedCells().equals(lastCell ? ship.occupiedCells() : List.of(coordinate)),
                        "Result includes whole sunk ship footprint");
                if (lastCell) {
                    destroyed++;
                    Ship sunk = engine.state().enemy().board().ship(ship.type());
                    check(sunk.isSunk() && sunk.hitCells().size() == ship.type().length(), "Sunk state and hit cells");
                    check(sunk.occupiedCells().stream().allMatch(cell -> engine.state().enemy().board().cell(cell).state() == CellState.SUNK),
                            "Every cell of sunk ship marked SUNK");
                }
                if (engine.state().phase() == GamePhase.BATTLE) {
                    check(engine.state().currentPlayerId().equals("enemy"), "Turn switches after a hit too");
                    engine.attack("enemy", playerMisses.get(opponentShot++));
                }
            }
        }
        check(destroyed == 5 && engine.state().phase() == GamePhase.GAME_OVER, "Full fleet defeat ends battle");
        check(engine.state().winnerId().equals("player"), "Winner persisted");
        check(engine.state().currentPlayerId().equals("player"), "Final attack keeps winner as active player");
        check(engine.state().lastAttack().winnerId().equals("player"), "Result includes winner");
        check(enemyBefore.ships().stream().allMatch(ship -> ship.hitCells().isEmpty()), "Attacks do not mutate prior snapshots");
        expect(UnsupportedOperationException.class, () -> engine.state().enemy().board().ships().clear());
        rejectedAttack(engine, "player", enemyMisses.get(1), AttackResultType.GAME_FINISHED);
        engine.restart();
        check(engine.state().phase() == GamePhase.PLACEMENT && engine.state().turnNumber() == 0,
                "Restart returns to initial placement");
        check(engine.state().player().board().ships().isEmpty() && engine.state().enemy().board().ships().isEmpty()
                && engine.state().lastAttack() == null && engine.state().winnerId() == null, "Restart clears match boards and result");
    }

    private static void rejectedAttack(GameEngine engine, String attackerId, Coordinate coordinate, AttackResultType type) {
        GameState before = engine.state();
        check(!engine.canAttack(attackerId, coordinate), "Rejected attack is not actionable");
        AttackResult result = engine.attack(attackerId, coordinate);
        check(!result.accepted() && result.type() == type, "Typed rejection: " + type);
        check(engine.state() == before, "Rejected attack leaves boards, result and turn unchanged");
    }

    private static void verifyFleet(Board board) {
        check(board.fleet().isComplete() && board.ships().size() == 5, "Five unique ship types");
        Set<Coordinate> occupied = new HashSet<>();
        for (Ship ship : board.ships()) {
            check(ship.occupiedCells().size() == ship.type().length(), "Correct ship length");
            for (Coordinate coordinate : ship.occupiedCells()) {
                check(Board.inBounds(coordinate), "Generated coordinate in bounds");
                check(occupied.add(coordinate), "Generated fleet has no overlap");
                check(board.cell(coordinate).shipType() == ship.type(), "Cell identifies occupant");
            }
        }
        check(occupied.size() == 17, "Fleet occupies exactly 17 cells");
    }

    private static List<Coordinate> emptyCells(Board board) {
        List<Coordinate> cells = new ArrayList<>();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                Coordinate coordinate = new Coordinate(row, column);
                if (board.shipAt(coordinate) == null) cells.add(coordinate);
            }
        }
        return cells;
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName(), failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
