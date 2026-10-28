package com.battleship.shared.game;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public final class Ship {
    private final ShipType type;
    private final Coordinate position;
    private final ShipOrientation orientation;
    private final List<Coordinate> occupiedCells;
    private final Set<Coordinate> hitCells;
    private final boolean custom;

    public Ship(ShipType type, Coordinate position, ShipOrientation orientation) {
        this(type, position, orientation, Set.of());
    }

    private Ship(ShipType type, Coordinate position, ShipOrientation orientation, Set<Coordinate> hitCells) {
        this.type = Objects.requireNonNull(type);
        this.position = Objects.requireNonNull(position);
        this.orientation = Objects.requireNonNull(orientation);
        occupiedCells = IntStream.range(0, type.length())
                .mapToObj(i -> position.offset(i * orientation.deltaRow(), i * orientation.deltaColumn()))
                .toList();
        this.hitCells = Set.copyOf(hitCells);
        custom = false;
    }

    public Ship(ShipType type, List<Coordinate> cells) { this(type, cells, Set.of()); }
    private Ship(ShipType type, List<Coordinate> cells, Set<Coordinate> hits) {
        var failure = ShipShapes.validate(type, cells);
        if (failure != PlacementResult.Failure.NONE) throw new IllegalArgumentException("Invalid " + type + " footprint: " + failure);
        this.type = type;
        int row = cells.stream().mapToInt(Coordinate::row).min().orElseThrow();
        int column = cells.stream().mapToInt(Coordinate::column).min().orElseThrow();
        position = new Coordinate(row, column); orientation = ShipOrientation.HORIZONTAL;
        occupiedCells = List.copyOf(cells.stream().sorted(java.util.Comparator.comparingInt(Coordinate::row).thenComparingInt(Coordinate::column)).toList());
        hitCells = Set.copyOf(hits); custom = true;
    }
    public boolean custom() { return custom; }
    public List<Coordinate> shape() { return ShipShapes.normalize(occupiedCells); }
    public Ship translatedTo(Coordinate anchor) {
        return custom ? new Ship(type, occupiedCells.stream().map(c -> c.offset(anchor.row() - position.row(), anchor.column() - position.column())).toList())
                : new Ship(type, anchor, orientation);
    }
    public Ship rotated() {
        return custom ? new Ship(type, ShipShapes.rotate(shape()).stream().map(c -> c.offset(position.row(), position.column())).toList())
                : new Ship(type, position, orientation.rotate());
    }

    public ShipType type() { return type; }
    public Coordinate position() { return position; }
    public ShipOrientation orientation() { return orientation; }
    public List<Coordinate> occupiedCells() { return occupiedCells; }
    public Set<Coordinate> hitCells() { return hitCells; }
    public boolean isSunk() { return hitCells.size() == type.length(); }

    public Ship withHit(Coordinate coordinate) {
        if (!occupiedCells.contains(coordinate)) throw new IllegalArgumentException("Cell is outside this ship");
        Set<Coordinate> hits = new HashSet<>(hitCells);
        hits.add(coordinate);
        return custom ? new Ship(type, occupiedCells, hits) : new Ship(type, position, orientation, hits);
    }
}
