package com.battleship.shared.match;

import com.battleship.shared.game.Board;
import com.battleship.shared.game.Cell;
import com.battleship.shared.game.CellState;
import java.util.List;

/** Enemy views contain shots and sunk ships, never an unhit fleet. */
public record BoardView(List<ShipPlacement> ships, List<Cell> shots) {
    public static BoardView from(Board board, boolean hideFleet) {
        return new BoardView(board.ships().stream().filter(ship -> !hideFleet || ship.isSunk())
                .map(ShipPlacement::from).toList(), board.attackedCells().stream().map(board::cell)
                .map(cell -> new Cell(cell.coordinate(), cell.state(),
                        !hideFleet || cell.state() == CellState.SUNK ? cell.shipType() : null)).toList());
    }
    public Board board() { return Board.view(ships.stream().map(ShipPlacement::ship).toList(), shots); }
}
