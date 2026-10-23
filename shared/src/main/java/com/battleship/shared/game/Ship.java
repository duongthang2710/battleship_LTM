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
        return new Ship(type, position, orientation, hits);
    }
}
