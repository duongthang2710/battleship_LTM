package com.battleship.shared.room;

import com.battleship.shared.game.CombatMode;
import com.battleship.shared.game.GameMode;
import com.battleship.shared.game.GameRules;
import com.battleship.shared.game.TurnRule;

public record CreateRoomRequest(GameMode mode, Integer shipCount, TurnRule turnRule, RoomOptions options, CombatMode combatMode) {
    public CreateRoomRequest(GameMode mode, Integer shipCount, TurnRule turnRule, RoomOptions options) { this(mode, shipCount, turnRule, options, CombatMode.CLASSIC); }
    public CreateRoomRequest(GameMode mode,Integer shipCount,TurnRule turnRule) { this(mode,shipCount,turnRule,RoomOptions.DEFAULT); }
    public CreateRoomRequest(GameRules rules,RoomOptions options) { this(rules.mode(),rules.shipCount(),rules.turnRule(),options,rules.combatMode()); }
    public CreateRoomRequest(GameMode mode) { this(mode, 5, TurnRule.ALTERNATING); }
    public CreateRoomRequest(GameRules rules) { this(rules, RoomOptions.DEFAULT); }
    public GameRules rules() {
        return new GameRules(mode == null ? GameMode.CLASSIC : mode, shipCount == null ? 5 : shipCount,
                turnRule == null ? TurnRule.ALTERNATING : turnRule, combatMode);
    }
}
