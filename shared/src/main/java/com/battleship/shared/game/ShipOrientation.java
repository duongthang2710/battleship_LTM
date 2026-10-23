package com.battleship.shared.game;

public enum ShipOrientation {
    HORIZONTAL(0, 1), VERTICAL(1, 0);

    private final int deltaRow;
    private final int deltaColumn;

    ShipOrientation(int deltaRow, int deltaColumn) {
        this.deltaRow = deltaRow;
        this.deltaColumn = deltaColumn;
    }

    public int deltaRow() { return deltaRow; }
    public int deltaColumn() { return deltaColumn; }
    public ShipOrientation rotate() { return this == HORIZONTAL ? VERTICAL : HORIZONTAL; }
}
