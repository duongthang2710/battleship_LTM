package com.battleship.shared.chat;

import com.battleship.shared.auth.AccountProfile;
import java.util.UUID;

public record ChatMessage(UUID contextId, long sequence, AccountProfile sender, String text, String sentAt) {}
