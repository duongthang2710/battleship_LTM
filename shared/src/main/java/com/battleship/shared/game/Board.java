package com.battleship.shared.game;

import com.battleship.shared.game.PlacementResult.Failure;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public final class Board {
    public static final int SIZE = 10;

    private final Fleet fleet;
    private final Set<Coordinate> attacks;
    private final Map<Coordinate, Cell> observations;

    public Board() { this(new Fleet(), Set.of()); }

    private Board(Fleet fleet, Set<Coordinate> attacks) {
        this(fleet, attacks, Map.of());
    }

    private Board(Fleet fleet, Set<Coordinate> attacks, Map<Coordinate, Cell> observations) {
        this.fleet = fleet;
        this.attacks = Set.copyOf(attacks);
        this.observations = Map.copyOf(observations);
    }

    public static Board view(List<Ship> visibleShips, List<Cell> shots) {
        Fleet fleet = new Fleet();
        for (Ship ship : visibleShips) {
            for (Cell shot : shots)
                if (ship.occupiedCells().contains(shot.coordinate())) ship = ship.withHit(shot.coordinate());
            fleet = fleet.withShip(ship);
        }
        Map<Coordinate, Cell> observations = new HashMap<>();
        for (Cell shot : shots) observations.put(shot.coordinate(), shot);
        return new Board(fleet, observations.keySet(), observations);
    }

    public Fleet fleet() { return fleet; }
    public List<Ship> ships() { return fleet.ships(); }
    public Ship ship(ShipType type) { return fleet.ship(type); }
    public Set<Coordinate> attackedCells() { return attacks; }
    public boolean wasAttacked(Coordinate coordinate) { return attacks.contains(coordinate); }

    public static boolean inBounds(Coordinate coordinate) {
        return coordinate != null && coordinate.row() >= 0 && coordinate.row() < SIZE
                && coordinate.column() >= 0 && coordinate.column() < SIZE;
    }

    public Ship shipAt(Coordinate coordinate) {
        return ships().stream().filter(ship -> ship.occupiedCells().contains(coordinate)).findFirst().orElse(null);
    }

    public Cell cell(Coordinate coordinate) {
        requireBounds(coordinate);
        if (observations.containsKey(coordinate)) return observations.get(coordinate);
        Ship ship = shipAt(coordinate);
        if (ship == null) return new Cell(coordinate, wasAttacked(coordinate) ? CellState.MISS : CellState.EMPTY, null);
        CellState state = ship.isSunk() ? CellState.SUNK
                : ship.hitCells().contains(coordinate) ? CellState.HIT : CellState.SHIP;
        return new Cell(coordinate, state, ship.type());
    }

    public PlacementResult validatePlacement(ShipType type, Coordinate position, ShipOrientation orientation) {
        if (type == null || position == null || orientation == null)
            return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        return validatePlacement(type, new Ship(type, position, orientation).occupiedCells());
    }

    public PlacementResult validatePlacement(ShipType type, List<Coordinate> cells) {
        var shape = ShipShapes.validate(type, cells);
        if (shape != PlacementResult.Failure.NONE) return PlacementResult.rejected(shape, cells == null || cells.stream().anyMatch(Objects::isNull) ? List.of() : cells);
        if (!attacks.isEmpty()) return PlacementResult.rejected(Failure.PHASE_CLOSED, cells);
        if (!cells.stream().allMatch(Board::inBounds)) return PlacementResult.rejected(Failure.OUT_OF_BOUNDS, cells);
        if (cells.stream().anyMatch(cell -> ships().stream()
                .filter(ship -> ship.type() != type).anyMatch(ship -> ship.occupiedCells().contains(cell))))
            return PlacementResult.rejected(Failure.OVERLAP, cells);
        return PlacementResult.accepted(cells);
    }

    public boolean canPlace(ShipType type, Coordinate position, ShipOrientation orientation) {
        return validatePlacement(type, position, orientation).success();
    }

    public Board withPlacement(ShipType type, Coordinate position, ShipOrientation orientation) {
        if (!canPlace(type, position, orientation)) throw new IllegalArgumentException("Invalid ship placement");
        return new Board(fleet.withShip(new Ship(type, position, orientation)), attacks);
    }

    public Board withShip(Ship ship) {
        if (!validatePlacement(ship.type(), ship.occupiedCells()).success()) throw new IllegalArgumentException("Invalid ship placement");
        return new Board(fleet.withShip(ship), attacks);
    }

    public Board withoutShip(ShipType type) {
        if (!attacks.isEmpty()) throw new IllegalStateException("Cannot edit an attacked board");
        return new Board(fleet.withoutShip(Objects.requireNonNull(type)), attacks);
    }

    public Board withAttack(Coordinate coordinate) {
        requireBounds(coordinate);
        if (wasAttacked(coordinate)) throw new IllegalArgumentException("Cell has already been attacked");
        Set<Coordinate> shots = new HashSet<>(attacks);
        shots.add(coordinate);
        Ship target = shipAt(coordinate);
        return new Board(target == null ? fleet : fleet.withShip(target.withHit(coordinate)), shots);
    }

    private static void requireBounds(Coordinate coordinate) {
        if (!inBounds(coordinate)) throw new IllegalArgumentException("Coordinate is outside the 10x10 board");
    }
}
