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
    final Map<String, RoomPlayer> players = new LinkedHashMap<>();
    RoomPhase phase = RoomPhase.WAITING;
    MatchService match;
    java.util.UUID historyId;
    java.util.Map<String, java.util.UUID> accountIds = java.util.Map.of();
    boolean historyFinished;
    final Set<String> rematchRequests = new HashSet<>();
    boolean rematchDeclined;

    Room(String code) { this.code = code; }

    RoomSnapshot snapshot() { return new RoomSnapshot(code, players.values().stream().toList(), phase); }

    RematchState rematchState() {
        return new RematchState(code, rematchRequests.stream().toList(), rematchDeclined,
                players.size() == 2 && match != null && match.phase() == com.battleship.shared.game.GamePhase.GAME_OVER);
    }
}
