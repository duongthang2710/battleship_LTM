package com.battleship.frontend.game;

import com.battleship.shared.game.Board;
import com.battleship.shared.game.Coordinate;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Optional;

/** Converts through the existing board transform, including rotation and presentation scale. */
public final class BoardCoordinates {
    private final Node board;

    public BoardCoordinates(Node board) { this.board = board; }

    public Vector3f localCenter(Coordinate coordinate, float height) {
        // Rank 1 is local -X; file A is local +Z, matching the printed coordinates.
        return new Vector3f(coordinate.row() - 4.5f, height, 4.5f - coordinate.column());
    }

    public Vector3f worldCenter(Coordinate coordinate, float height) {
        return board.localToWorld(localCenter(coordinate, height), new Vector3f());
    }

    public Optional<Coordinate> cellAtWorld(Vector3f world) {
        Vector3f local = board.worldToLocal(world, new Vector3f());
        int row = (int) Math.floor(local.x + Board.SIZE / 2f);
        int column = (int) Math.floor(Board.SIZE / 2f - local.z);
        Coordinate cell = new Coordinate(row, column);
        return Board.inBounds(cell) ? Optional.of(cell) : Optional.empty();
    }
}
