package com.battleship.shared.ai;

import com.battleship.shared.game.*;
import com.battleship.shared.match.BoardView;
import java.util.*;

/** Only public shot outcomes and the identities of sunk ships. No hidden board reference. */
public record AiKnowledge(Map<Coordinate, CellState> shots, Set<ShipType> sunkShips) {
    public AiKnowledge {
        shots = Map.copyOf(shots);
        sunkShips = Set.copyOf(sunkShips);
    }

    public static AiKnowledge from(BoardView publicView) {
        Map<Coordinate, CellState> shots = new HashMap<>();
        Set<ShipType> sunk = EnumSet.noneOf(ShipType.class);
        for (Cell cell : publicView.shots()) {
            shots.put(cell.coordinate(), cell.state());
            if (cell.state() == CellState.SUNK && cell.shipType() != null) sunk.add(cell.shipType());
        }
        return new AiKnowledge(shots, sunk);
    }

    public boolean unshot(Coordinate cell) { return Board.inBounds(cell) && !shots.containsKey(cell); }
    public boolean hit(Coordinate cell) { return shots.get(cell) == CellState.HIT; }
    public boolean blocked(Coordinate cell) {
        return shots.get(cell) == CellState.MISS || shots.get(cell) == CellState.SUNK;
    }
    public List<Coordinate> available() {
        List<Coordinate> cells = new ArrayList<>();
        for (int row = 0; row < Board.SIZE; row++) for (int col = 0; col < Board.SIZE; col++) {
            Coordinate cell = new Coordinate(row, col);
            if (unshot(cell)) cells.add(cell);
        }
        return List.copyOf(cells);
    }
}
