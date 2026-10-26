package com.battleship.shared.chat;

import java.util.UUID;

public record ChatContext(UUID contextId, String roomCode, ChatScope scope, boolean canSend) {}
