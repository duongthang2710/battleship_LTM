package com.battleship.shared.game;

public enum GameMode {
    CLASSIC("Classic fleet"), DESIGNER("Fleet designer");
    private final String label;
    GameMode(String label) { this.label = label; }
    public String label() { return label; }
}
