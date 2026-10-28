package com.battleship.shared.game;

import java.util.List;

public record PlacementResult(boolean success, Failure reason, List<Coordinate> affectedCells) {
    public enum Failure {
        NONE, INVALID_ARGUMENT, OUT_OF_BOUNDS, OVERLAP, PHASE_CLOSED, SHIP_NOT_FOUND, FLEET_INCOMPLETE,
        WRONG_SIZE, DUPLICATE_CELLS, DISCONNECTED, MODE_MISMATCH
    }

    public PlacementResult {
        affectedCells = List.copyOf(affectedCells);
    }

    public static PlacementResult accepted(List<Coordinate> cells) {
        return new PlacementResult(true, Failure.NONE, cells);
    }

    public static PlacementResult rejected(Failure reason, List<Coordinate> cells) {
        return new PlacementResult(false, reason, cells);
    }
}
