package com.battleship.shared.game;

public record Cell(Coordinate coordinate, CellState state, ShipType shipType) {}
