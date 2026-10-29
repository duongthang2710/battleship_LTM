package com.battleship.shared.room;

import com.battleship.shared.game.GameMode;
import com.battleship.shared.game.GameRules;
import com.battleship.shared.game.TurnRule;

public record CreateRoomRequest(GameMode mode, Integer shipCount, TurnRule turnRule) {
    public CreateRoomRequest(GameMode mode) { this(mode, 5, TurnRule.ALTERNATING); }
    public CreateRoomRequest(GameRules rules) { this(rules.mode(), rules.shipCount(), rules.turnRule()); }
    public GameRules rules() {
        return new GameRules(mode == null ? GameMode.CLASSIC : mode, shipCount == null ? 5 : shipCount,
                turnRule == null ? TurnRule.ALTERNATING : turnRule);
    }
}
