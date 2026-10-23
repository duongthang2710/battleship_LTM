package com.battleship.shared.room;

public record RoomPlayer(String clientId, String playerName, boolean ready, com.battleship.shared.game.Side side) {
    public RoomPlayer(String clientId, String playerName, boolean ready) { this(clientId, playerName, ready, null); }
}
