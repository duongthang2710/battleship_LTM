package com.battleship.shared.game;

import java.util.Objects;

public record Player(String id, String name, Board board) {
    public Player {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(board);
    }

    public Player withBoard(Board replacement) { return new Player(id, name, replacement); }
    public Fleet fleet() { return board.fleet(); }
}
