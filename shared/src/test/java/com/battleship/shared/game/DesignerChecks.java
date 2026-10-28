package com.battleship.shared.game;

import com.battleship.shared.match.ShipPlacement;
import com.battleship.shared.protocol.*;
import java.util.*;

public final class DesignerChecks {
    public static void main(String[] args) {
        int[] counts = {0,1,2,6,19,63};
        for (int size = 1; size <= 5; size++) {
            var generated = ShipShapes.all(size); Set<String> keys = new HashSet<>();
            for (var shape : generated) keys.add(ShipShapes.key(shape));
            require(generated.size() == counts[size] && keys.size() == counts[size], "Unique fixed shapes for size " + size);
            // Independent oracle: enumerate every subset of a 5x5 box, then retain connected shapes.
            Set<String> oracle = new HashSet<>(); subsets(0, size, new ArrayList<>(), oracle);
            require(keys.equals(oracle), "Catalog contains every connected shape, including rotations and mirrors: " + size);
            System.out.println("All shapes length " + size + ": " + keys.size());
        }
        int cases = 0, placements = 0;
        for (ShipType type : ShipType.values()) for (var shape : ShipShapes.all(type.length())) {
            cases++;
            var original = new Ship(type, shape); var rotated = original;
            for (int i = 0; i < 4; i++) rotated = rotated.rotated();
            require(rotated.occupiedCells().equals(original.occupiedCells()), "Four rotations restore every footprint");
            ShipPlacement dto = ShipPlacement.from(original);
            Ship roundTrip = Message.request(MessageType.SUBMIT_FLEET, dto).payloadAs(ShipPlacement.class).ship();
            require(roundTrip.custom() && roundTrip.occupiedCells().equals(shape), "JSON preserves exact footprint");
            for (int row = 0; row < Board.SIZE; row++) for (int column = 0; column < Board.SIZE; column++) {
                var moved = original.translatedTo(new Coordinate(row,column));
                var board = new Board(); boolean expected = moved.occupiedCells().stream().allMatch(Board::inBounds);
                require(board.validatePlacement(type, moved.occupiedCells()).success() == expected, "Bounds for every shape/anchor");
                if (expected) { placements++; require(board.withShip(moved).ship(type).occupiedCells().equals(moved.occupiedCells()), "Exact placed cells"); }
            }
            GameEngine editor = new GameEngine("Designer"); editor.setMode(GameMode.DESIGNER);
            var cells = original.translatedTo(new Coordinate(2,2)).occupiedCells();
            require(editor.placeDesignedShip(type, cells).success(), "Place every designed ship");
            editor.selectShip(type); require(editor.rotateSelected().success(), "Rotate every designed ship");
            Ship placed = editor.state().player().board().ship(type);
            Coordinate grabbed = placed.occupiedCells().getFirst(); editor.clickPlacement(grabbed);
            require(editor.previewPlacement(grabbed.offset(1,1)).success(), "Preview move preserves shape");
            require(editor.clickPlacement(grabbed.offset(1,1)).success(), "Drop every designed ship");
            require(editor.state().player().board().ship(type).shape().equals(placed.shape()), "Move does not turn custom ship into a line");
        }
        GameEngine engine = new GameEngine("Designer", new Random(12)); engine.setMode(GameMode.DESIGNER);
        require(engine.placeDesignedShip(ShipType.DESTROYER,List.of(new Coordinate(0,0),new Coordinate(1,1))).reason() == PlacementResult.Failure.DISCONNECTED, "Diagonal cells are not connected");
        require(engine.placeDesignedShip(ShipType.DESTROYER,List.of(new Coordinate(0,0),new Coordinate(0,0))).reason() == PlacementResult.Failure.DUPLICATE_CELLS, "Duplicate cells rejected");
        require(engine.placeDesignedShip(ShipType.CARRIER,List.of(new Coordinate(0,0))).reason() == PlacementResult.Failure.WRONG_SIZE, "Wrong size rejected");
        for (int i=0;i<100;i++) { engine.resetFleet(); require(engine.randomPlacement().success() && engine.state().canReady(), "Random custom fleet complete"); }
        require(engine.state().player().board().ships().stream().allMatch(Ship::custom), "Random designer ships retain custom footprints");
        engine.restart(); require(engine.mode() == GameMode.DESIGNER && engine.state().player().board().ships().isEmpty(), "Restart keeps mode and clears fleet");
        GameEngine classic = new GameEngine("Classic");
        require(classic.placeDesignedShip(ShipType.DESTROYER,List.of(new Coordinate(0,0),new Coordinate(0,1))).reason() == PlacementResult.Failure.MODE_MISMATCH, "Classic cannot draw");
        System.out.println("PASS designer rules: " + cases + " type/shape cases, " + placements + " valid anchors, exhaustive subset oracle, move/rotate/JSON/random and invalid inputs.");
    }
    private static void subsets(int next, int remaining, List<Coordinate> cells, Set<String> oracle) {
        if (remaining == 0) {
            // Connectivity oracle uses Manhattan adjacency, independent of the catalog growth process.
            Set<Coordinate> reached = new HashSet<>(); reached.add(cells.getFirst());
            for (int step=0; step<cells.size(); step++) for (Coordinate c:cells)
                if (reached.stream().anyMatch(r -> Math.abs(r.row()-c.row()) + Math.abs(r.column()-c.column()) == 1)) reached.add(c);
            if (reached.size() == cells.size()) oracle.add(ShipShapes.key(cells)); return;
        }
        for (int i=next;i<=25-remaining;i++) { cells.add(new Coordinate(i/5,i%5)); subsets(i+1,remaining-1,cells,oracle); cells.removeLast(); }
    }
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
