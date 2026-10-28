package com.battleship.shared.room;

import java.util.List;

public record RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase, com.battleship.shared.game.GameMode mode) {
    public RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase) { this(roomCode, players, phase, com.battleship.shared.game.GameMode.CLASSIC); }
    public RoomSnapshot {
        if (mode == null) mode = com.battleship.shared.game.GameMode.CLASSIC;
        players = List.copyOf(players);
    }
}
