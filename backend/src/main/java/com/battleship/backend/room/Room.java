package com.battleship.backend.room;

import com.battleship.backend.match.MatchService;
import com.battleship.shared.room.RoomPhase;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.room.RematchState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

final class Room {
    final String code;
    final com.battleship.shared.game.GameRules rules;
    final Map<String, RoomPlayer> players = new LinkedHashMap<>();
    RoomPhase phase = RoomPhase.WAITING;
    MatchService match;
    RoomChat chat = new RoomChat(com.battleship.shared.chat.ChatScope.WAITING_ROOM, true);
    java.util.UUID historyId;
    java.util.Map<String, java.util.UUID> accountIds = java.util.Map.of();
    boolean historyFinished;
    final Set<String> rematchRequests = new HashSet<>();
    boolean rematchDeclined;

    Room(String code) { this(code, com.battleship.shared.game.GameMode.CLASSIC); }
    Room(String code, com.battleship.shared.game.GameMode mode) { this(code, new com.battleship.shared.game.GameRules(mode, 5, com.battleship.shared.game.TurnRule.ALTERNATING)); }
    Room(String code, com.battleship.shared.game.GameRules rules) { this.code = code; this.rules = rules; }

    RoomSnapshot snapshot() { return new RoomSnapshot(code, players.values().stream().toList(), phase, rules); }

    RematchState rematchState() {
        return new RematchState(code, rematchRequests.stream().toList(), rematchDeclined,
                players.size() == 2 && match != null && match.phase() == com.battleship.shared.game.GamePhase.GAME_OVER);
    }
}
