package com.battleship.backend.room;

import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.chat.*;
import java.time.Instant;
import java.util.UUID;

/** No history is replayed to joiners; only the context and live messages are sent. */
final class RoomChat {
    final UUID id = UUID.randomUUID();
    final ChatScope scope;
    boolean writable;
    private long sequence;

    RoomChat(ChatScope scope, boolean writable) { this.scope = scope; this.writable = writable; }
    ChatContext view(String roomCode) { return new ChatContext(id, roomCode, scope, writable); }
    ChatMessage message(AccountProfile sender, String text) {
        return new ChatMessage(id, ++sequence, sender, text, Instant.now().toString());
    }
}
