package com.battleship.shared.game;

import java.util.List;

public record AttackResult(AttackResultType type, Coordinate coordinate, ShipType shipType,
                           boolean sunk, boolean gameOver, String attackerId, String defenderId,
                           String winnerId, List<Coordinate> affectedCells) {
    public AttackResult {
        affectedCells = List.copyOf(affectedCells);
    }

    public boolean accepted() {
        return type == AttackResultType.HIT || type == AttackResultType.MISS
                || type == AttackResultType.SUNK || type == AttackResultType.GAME_OVER;
    }
}
