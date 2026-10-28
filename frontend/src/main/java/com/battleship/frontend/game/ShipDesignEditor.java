package com.battleship.frontend.game;

import com.battleship.shared.game.*;
import java.util.*;

/** Draft/brush state only; every placement rule is evaluated by the shared engine. */
public final class ShipDesignEditor {
    private final GameEngine engine;
    private ShipType type;
    private LinkedHashSet<Coordinate> cells;
    private Coordinate previous;
    public ShipDesignEditor(GameEngine engine) { this.engine = engine; }
    public boolean hasDraft() { return cells != null; }
    public List<Coordinate> cells() { return cells == null ? List.of() : List.copyOf(cells); }
    public ShipType type() { return type; }
    public void clear() { cells = null; type = null; previous = null; }
    public void beginStroke() {
        ShipType selected = engine.state().placement().selectedType();
        if (cells == null || type != selected) {
            type = selected; cells = new LinkedHashSet<>();
            Ship placed = engine.state().player().board().ship(type);
            if (placed != null) cells.addAll(placed.occupiedCells());
        }
        previous = null;
    }
    public boolean paint(Coordinate cell, boolean erase) {
        if (cells == null || !Board.inBounds(cell)) return false;
        boolean changed = false;
        if (previous != null) {
            int row = previous.row(), column = previous.column();
            while (row != cell.row() || column != cell.column()) {
                if (row != cell.row()) row += Integer.signum(cell.row() - row);
                else column += Integer.signum(cell.column() - column);
                changed |= edit(new Coordinate(row, column), erase);
            }
        } else changed = edit(cell, erase);
        previous = cell;
        return changed;
    }
    private boolean edit(Coordinate cell, boolean erase) {
        return erase ? cells.remove(cell) : cells.size() < type.length() && cells.add(cell);
    }
    public void endStroke() { previous = null; }
    public PlacementResult validation() { return engine.validateDesignedShip(type, cells()); }
    public PlacementResult commit() {
        var result = engine.placeDesignedShip(type, cells());
        if (result.success()) clear();
        return result;
    }
}
