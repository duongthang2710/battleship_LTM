package com.battleship.shared.game;

import java.util.List;
import java.util.Objects;

/** Immutable room rules shared by placement validation and the authoritative match. */
public record GameRules(GameMode mode, int shipCount, TurnRule turnRule) {
    public static final GameRules DEFAULT = new GameRules(GameMode.CLASSIC, 5, TurnRule.ALTERNATING);

    public GameRules {
        Objects.requireNonNull(mode, "Choose Basic or Fleet designer.");
        Objects.requireNonNull(turnRule, "Choose a turn rule.");
        if (shipCount < 3 || shipCount > 5) throw new IllegalArgumentException("Choose between 3 and 5 ships.");
    }

    public List<ShipType> shipTypes() { return List.of(ShipType.values()).subList(0, shipCount); }
    public boolean allows(ShipType type) { return type != null && shipTypes().contains(type); }
}
