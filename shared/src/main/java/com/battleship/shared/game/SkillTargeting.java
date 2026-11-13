package com.battleship.shared.game;

import java.util.ArrayList;
import java.util.List;

public final class SkillTargeting {
    private SkillTargeting() { }

    public static List<Coordinate> area(SkillType skill, Coordinate origin, SkillDirection direction) {
        if (skill == null || !Board.inBounds(origin)) return List.of();
        return switch (skill) {
            case MINE -> List.of(origin);
            case SHIELD -> List.of(origin, origin.offset(-1, 0), origin.offset(1, 0),
                    origin.offset(0, -1), origin.offset(0, 1)).stream().filter(Board::inBounds).toList();
            case SONAR -> {
                int row = Math.max(0, Math.min(7, origin.row() - 1));
                int column = Math.max(0, Math.min(7, origin.column() - 1));
                List<Coordinate> cells = new ArrayList<>();
                for (int r = row; r < row + 3; r++) for (int c = column; c < column + 3; c++)
                    cells.add(new Coordinate(r, c));
                yield List.copyOf(cells);
            }
            case AIRSTRIKE -> {
                if (direction == null) yield List.of();
                boolean horizontal = direction.rows() == 0;
                List<Coordinate> line = new ArrayList<>();
                for (int i = 0; i < Board.SIZE; i++)
                    line.add(new Coordinate(horizontal ? origin.row() : i, horizontal ? i : origin.column()));
                yield List.copyOf(line);
            }
        };
    }
}
