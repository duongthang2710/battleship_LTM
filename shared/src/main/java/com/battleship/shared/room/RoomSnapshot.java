package com.battleship.shared.room;

import java.util.List;
import com.battleship.shared.game.GameMode;
import com.battleship.shared.game.GameRules;
import com.battleship.shared.game.TurnRule;

public record RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameMode mode,
                           Integer shipCount, TurnRule turnRule) {
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase) { this(roomCode, players, phase, GameRules.DEFAULT); }
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameMode mode) {
        this(roomCode, players, phase, new GameRules(mode, 5, TurnRule.ALTERNATING));
    }
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameRules rules) {
        this(roomCode, players, phase, rules.mode(), rules.shipCount(), rules.turnRule());
    }
    public RoomSnapshot {
        if (mode == null) mode = GameMode.CLASSIC;
        if (shipCount == null) shipCount = 5;
        if (turnRule == null) turnRule = TurnRule.ALTERNATING;
        new GameRules(mode, shipCount, turnRule);
        players = List.copyOf(players);
    }
    public GameRules rules() { return new GameRules(mode, shipCount, turnRule); }
}
