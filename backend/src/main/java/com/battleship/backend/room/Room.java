package com.battleship.backend.room;

import com.battleship.backend.match.MatchService;
import com.battleship.shared.room.RoomPhase;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

final class Room {
    final String code;
    final Map<String, RoomPlayer> players = new LinkedHashMap<>();
    RoomPhase phase = RoomPhase.WAITING;
    MatchService match;

    Room(String code) { this.code = code; }

    RoomSnapshot snapshot() { return new RoomSnapshot(code, players.values().stream().toList(), phase); }
}
