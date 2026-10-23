package com.battleship.backend.server;

import com.battleship.shared.game.PlayerIdentity;
import java.util.UUID;

public final class ClientSession {
    private final String id = UUID.randomUUID().toString();
    private PlayerIdentity player;

    public String id() { return id; }
    public PlayerIdentity player() { return player; }
    public void connect(String playerName) { player = new PlayerIdentity(id, playerName); }
}
