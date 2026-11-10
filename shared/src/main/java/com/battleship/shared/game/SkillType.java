package com.battleship.shared.game;

public enum SkillType {
    CLUSTER(3, "Cluster shot"),
    BARRAGE(4, "Missile barrage"),
    TORPEDO(5, "Torpedo"),
    SONAR(3, "Sonar");

    /** Every accepted normal shot adds one point; skill shots add none. */
    public static final int MAX_ENERGY = 8;
    public static final int BARRAGE_SIZE = 4;
    public static final int BARRAGE_MISSILES = 4;

    private final int cost;
    private final String label;
    SkillType(int cost, String label) { this.cost = cost; this.label = label; }
    public int cost() { return cost; }
    public String label() { return label; }
}
