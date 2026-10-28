package com.battleship.shared.room;

import com.battleship.shared.game.GameMode;

public record CreateRoomRequest(GameMode mode) {
    public CreateRoomRequest { if (mode == null) mode = GameMode.CLASSIC; }
}
