package com.battleship.shared.game;

public enum Side {
    WHITE, BLACK;
    public Side opponent() { return this == WHITE ? BLACK : WHITE; }
}
