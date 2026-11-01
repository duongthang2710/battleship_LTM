package com.battleship.shared.ai;

public enum AiDifficulty {
    EASY("Easy"), MEDIUM("Medium"), HARD("Hard");
    private final String label;
    AiDifficulty(String label) { this.label = label; }
    public String label() { return label; }
}
