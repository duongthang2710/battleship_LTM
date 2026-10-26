package com.battleship.backend.server;

import com.battleship.shared.game.PlayerIdentity;
import java.util.UUID;
import com.battleship.shared.auth.AccountProfile;

public final class ClientSession {
    private final String id = UUID.randomUUID().toString();
    private PlayerIdentity player;
    private volatile AccountProfile account;

    public AccountProfile account() { return account; }
    public void authenticate(AccountProfile value) {
        account = value;
        player = new PlayerIdentity(id, value == null ? "Guest" : value.displayName());
    }

    public String id() { return id; }
    public PlayerIdentity player() { return player; }
    public void connect(String playerName) { player = new PlayerIdentity(id, playerName); }
}
