package com.battleship.shared.game;

public enum ShipType {
    CARRIER(5, "Carrier"),
    BATTLESHIP(4, "Battleship"),
    CRUISER(3, "Cruiser"),
    SUBMARINE(3, "Submarine"),
    DESTROYER(2, "Destroyer");

    private final int length;
    private final String displayName;

    ShipType(int length, String displayName) {
        this.length = length;
        this.displayName = displayName;
    }

    public int length() { return length; }
    public String displayName() { return displayName; }
}
