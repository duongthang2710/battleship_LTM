package com.battleship.shared.game;

public enum SkillType {
    SONAR(3, "Sonar"),
    MINE(4, "Naval mine"),
    SHIELD(4, "Aegis shield"),
    AIRSTRIKE(8, "Airstrike");

    /** Public resource limit shared by rules, server snapshots and HUD. */
    public static final int MAX_ENERGY = 10;
    public boolean targetsOwnBoard() { return this == MINE || this == SHIELD; }
    public boolean endsTurn() { return this == AIRSTRIKE; }

    private final int cost;
    private final String label;
    SkillType(int cost, String label) { this.cost = cost; this.label = label; }
    public int cost() { return cost; }
    public String label() { return label; }
}
