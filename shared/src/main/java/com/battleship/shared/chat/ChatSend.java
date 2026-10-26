package com.battleship.shared.chat;

import java.util.UUID;

public record ChatSend(UUID contextId, String text) {}
