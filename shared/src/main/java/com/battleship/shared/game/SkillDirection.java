package com.battleship.shared.game;

/** Cardinal axes used to select an Airstrike row or column. */
public enum SkillDirection {
    NORTH(-1, 0), EAST(0, 1), SOUTH(1, 0), WEST(0, -1);
    private final int rows, columns;
    SkillDirection(int rows, int columns) { this.rows = rows; this.columns = columns; }
    public int rows() { return rows; }
    public int columns() { return columns; }
    public SkillDirection next() { return values()[(ordinal() + 1) % values().length]; }
}
