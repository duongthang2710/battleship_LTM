package com.battleship.shared.match;

import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Ship;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.ShipType;

public record ShipPlacement(ShipType type, Coordinate position, ShipOrientation orientation) {
    public static ShipPlacement from(Ship ship) {
        return new ShipPlacement(ship.type(), ship.position(), ship.orientation());
    }
    public Ship ship() { return new Ship(type, position, orientation); }
}
