package com.battleship.shared.game;

public enum TurnRule {
    ALTERNATING("Alternate shots"),
    HIT_CONTINUES("Hit grants another shot");

    private final String label;
    TurnRule(String label) { this.label = label; }
    public String label() { return label; }
}
