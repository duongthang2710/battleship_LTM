package com.battleship.shared.game;

public record Coordinate(int row, int column) {
    public Coordinate offset(int rows, int columns) {
        return new Coordinate(row + rows, column + columns);
    }
}
