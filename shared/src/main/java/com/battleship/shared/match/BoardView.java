package com.battleship.shared.match;

import com.battleship.shared.game.Board;
import com.battleship.shared.game.Cell;
import com.battleship.shared.game.CellState;
import java.util.List;

/** Board snapshot with configurable fleet and sinking visibility. */
public record BoardView(List<ShipPlacement> ships, List<Cell> shots) {
    public static BoardView from(Board board, boolean hideFleet) {
        return from(board, hideFleet, false);
    }

    public static BoardView from(Board board, boolean hideFleet, boolean hideSunkInfo) {
        var ships = board.ships().stream()
                .filter(ship -> !hideFleet || !hideSunkInfo && ship.isSunk())
                .map(ShipPlacement::from).toList();
        var shots = board.attackedCells().stream().map(board::cell)
                .map(cell -> new Cell(cell.coordinate(),
                        hideSunkInfo && cell.state() == CellState.SUNK ? CellState.HIT : cell.state(),
                        !hideFleet || !hideSunkInfo && cell.state() == CellState.SUNK ? cell.shipType() : null))
                .toList();
        return new BoardView(ships, shots);
    }
    public Board board() { return Board.view(ships.stream().map(ShipPlacement::ship).toList(), shots); }
}
