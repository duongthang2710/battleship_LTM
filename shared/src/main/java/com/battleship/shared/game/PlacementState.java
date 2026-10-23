package com.battleship.shared.game;


public record PlacementState(ShipType selectedType, ShipOrientation orientation, boolean moving,
                             Coordinate grabOffset) {
    public static PlacementState initial() {
        return new PlacementState(ShipType.CARRIER, ShipOrientation.HORIZONTAL, false, new Coordinate(0, 0));
    }
}
