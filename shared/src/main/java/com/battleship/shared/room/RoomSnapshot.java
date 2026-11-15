package com.battleship.shared.room;

import java.util.List;
import com.battleship.shared.game.CombatMode;
import com.battleship.shared.game.GameMode;
import com.battleship.shared.game.GameRules;
import com.battleship.shared.game.TurnRule;

public record RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameMode mode,
                           Integer shipCount, TurnRule turnRule, RoomOptions options, CombatMode combatMode) {
    public RoomSnapshot(String code, List<RoomPlayer> players, RoomPhase phase, GameMode mode, Integer count,
                        TurnRule rule, RoomOptions options) {
        this(code, players, phase, mode, count, rule, options, CombatMode.CLASSIC);
    }
    public RoomSnapshot(String code,List<RoomPlayer> players,RoomPhase phase,GameMode mode,Integer count,TurnRule rule) {
        this(code,players,phase,mode,count,rule,RoomOptions.DEFAULT);
    }
    public RoomSnapshot(String code,List<RoomPlayer> players,RoomPhase phase,GameRules rules,RoomOptions options) {
        this(code,players,phase,rules.mode(),rules.shipCount(),rules.turnRule(),options,rules.combatMode());
    }
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase) { this(roomCode, players, phase, GameRules.DEFAULT); }
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameMode mode) {
        this(roomCode, players, phase, new GameRules(mode, 5, TurnRule.ALTERNATING));
    }
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, GameRules rules) {
        this(roomCode, players, phase, rules, RoomOptions.DEFAULT);
    }
    public RoomSnapshot {
        if(options==null) options=RoomOptions.DEFAULT;
        if (mode == null) mode = GameMode.CLASSIC;
        if (shipCount == null) shipCount = 5;
        if (turnRule == null) turnRule = TurnRule.ALTERNATING;
        if (combatMode == null) combatMode = CombatMode.CLASSIC;
        new GameRules(mode, shipCount, turnRule, combatMode);
        players = List.copyOf(players);
    }
    public GameRules rules() { return new GameRules(mode, shipCount, turnRule, combatMode); }
}
