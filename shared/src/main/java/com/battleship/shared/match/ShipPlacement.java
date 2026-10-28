package com.battleship.shared.match;

import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Ship;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.ShipType;

public record ShipPlacement(ShipType type, Coordinate position, ShipOrientation orientation, java.util.List<Coordinate> cells) {
    public ShipPlacement { if (cells != null) cells = java.util.List.copyOf(cells); }
    public ShipPlacement(ShipType type, Coordinate position, ShipOrientation orientation) { this(type, position, orientation, null); }
    public static ShipPlacement from(Ship ship) {
        return new ShipPlacement(ship.type(), ship.position(), ship.orientation(), ship.custom() ? ship.occupiedCells() : null);
    }
    public Ship ship() {
        Ship ship = cells == null ? new Ship(type, position, orientation) : new Ship(type, cells);
        if (cells != null && !ship.position().equals(position)) throw new IllegalArgumentException("Footprint anchor does not match position.");
        return ship;
    }
}
