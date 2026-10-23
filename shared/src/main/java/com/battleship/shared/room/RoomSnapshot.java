package com.battleship.shared.room;

import java.util.List;

public record RoomSnapshot(String roomCode, List<RoomPlayer> players, RoomPhase phase) {
    public RoomSnapshot {
        players = List.copyOf(players);
    }
}
