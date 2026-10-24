package com.battleship.shared.room;

import java.util.List;

public record RematchState(String roomCode, List<String> acceptedPlayerIds, boolean declined, boolean available) {
    public RematchState { acceptedPlayerIds = List.copyOf(acceptedPlayerIds); }
}
