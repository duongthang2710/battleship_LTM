package com.battleship.shared.protocol;

import com.battleship.shared.game.PlayerIdentity;

public record ConnectResponse(PlayerIdentity player) {}
