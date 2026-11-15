package com.battleship.shared.game;

import java.util.List;

public record AttackResult(AttackResultType type, Coordinate coordinate, ShipType shipType,
                           boolean sunk, boolean gameOver, String attackerId, String defenderId,
                           String winnerId, List<Coordinate> affectedCells, DefenseKind defense,
                           List<AttackResult> counterattacks) {
    public AttackResult(AttackResultType type, Coordinate coordinate, ShipType shipType, boolean sunk,
                        boolean gameOver, String attackerId, String defenderId, String winnerId,
                        List<Coordinate> affectedCells) {
        this(type, coordinate, shipType, sunk, gameOver, attackerId, defenderId, winnerId, affectedCells, null, List.of());
    }
    public AttackResult {
        affectedCells = List.copyOf(affectedCells);
        counterattacks = counterattacks == null ? List.of() : List.copyOf(counterattacks);
    }

    public boolean accepted() {
        return type == AttackResultType.HIT || type == AttackResultType.MISS
                || type == AttackResultType.SUNK || type == AttackResultType.GAME_OVER || type == AttackResultType.BLOCKED;
    }
}
