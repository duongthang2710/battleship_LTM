package com.battleship.shared.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure targeting geometry shared by the authoritative engine and the client preview. */
public final class SkillTargeting {
    private SkillTargeting() { }

    /** Cells the skill covers before considering earlier shots; a torpedo path is in travel order. */
    public static List<Coordinate> area(SkillType skill, Coordinate origin, SkillDirection direction) {
        if (skill == null || !Board.inBounds(origin)) return List.of();
        return switch (skill) {
            case CLUSTER -> List.of(origin, origin.offset(-1, 0), origin.offset(1, 0), origin.offset(0, -1), origin.offset(0, 1))
                    .stream().filter(Board::inBounds).toList();
            case BARRAGE -> square(clamp(origin.row(), SkillType.BARRAGE_SIZE), clamp(origin.column(), SkillType.BARRAGE_SIZE), SkillType.BARRAGE_SIZE);
            case SONAR -> square(clamp(origin.row() - 1, 3), clamp(origin.column() - 1, 3), 3);
            case TORPEDO -> direction == null ? List.of() : torpedoPath(origin, direction);
        };
    }

    /** Cells actually fired at against the defender's real board (empty for sonar or when nothing is left). */
    public static List<Coordinate> shots(SkillType skill, Coordinate origin, SkillDirection direction, Board defender, Random random) {
        List<Coordinate> area = area(skill, origin, direction);
        return switch (skill) {
            case CLUSTER -> area.stream().filter(cell -> !defender.wasAttacked(cell)).toList();
            case BARRAGE -> {
                List<Coordinate> open = new ArrayList<>(area.stream().filter(cell -> !defender.wasAttacked(cell)).toList());
                Collections.shuffle(open, random);
                yield List.copyOf(open.subList(0, Math.min(SkillType.BARRAGE_MISSILES, open.size())));
            }
            case TORPEDO -> {
                List<Coordinate> path = new ArrayList<>();
                for (Coordinate cell : area) {
                    if (defender.wasAttacked(cell)) continue;
                    path.add(cell);
                    if (defender.shipAt(cell) != null) break;
                }
                yield List.copyOf(path);
            }
            case SONAR -> List.of();
        };
    }

    public static int sonarCount(List<Coordinate> area, Board defender) {
        return (int) area.stream().filter(cell -> defender.shipAt(cell) != null && !defender.wasAttacked(cell)).count();
    }

    private static List<Coordinate> torpedoPath(Coordinate origin, SkillDirection direction) {
        int row = direction.rows() == 0 ? origin.row() : direction.rows() > 0 ? 0 : Board.SIZE - 1;
        int column = direction.columns() == 0 ? origin.column() : direction.columns() > 0 ? 0 : Board.SIZE - 1;
        List<Coordinate> path = new ArrayList<>(Board.SIZE);
        for (Coordinate cell = new Coordinate(row, column); Board.inBounds(cell); cell = cell.offset(direction.rows(), direction.columns()))
            path.add(cell);
        return List.copyOf(path);
    }

    private static int clamp(int start, int size) { return Math.max(0, Math.min(Board.SIZE - size, start)); }

    private static List<Coordinate> square(int row, int column, int size) {
        List<Coordinate> cells = new ArrayList<>(size * size);
        for (int r = row; r < row + size; r++) for (int c = column; c < column + size; c++) cells.add(new Coordinate(r, c));
        return List.copyOf(cells);
    }
}
