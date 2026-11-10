package com.battleship.shared.game;

/** Independent of fleet shape: Classic is plain shots only; Skills adds the energy bar and four skills. */
public enum CombatMode {
    CLASSIC("Classic"), SKILLS("Skill battle");
    private final String label;
    CombatMode(String label) { this.label = label; }
    public String label() { return label; }
}
