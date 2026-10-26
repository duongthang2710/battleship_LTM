package com.battleship.backend.chat;

import com.battleship.shared.chat.ChatText;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Per-connection rate limits survive room changes. Called under RoomManager's lock. */
public final class ChatService {
    private static final long WINDOW = TimeUnit.SECONDS.toNanos(5);
    private final Map<String, Deque<Long>> sent = new HashMap<>();

    public String accept(String clientId, String text) {
        String normalized = ChatText.validate(text);
        long now = System.nanoTime();
        Deque<Long> recent = sent.computeIfAbsent(clientId, ignored -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.getFirst() >= WINDOW) recent.removeFirst();
        if (recent.size() >= 5) throw new IllegalStateException("Please wait: max 5 messages every 5 seconds.");
        recent.addLast(now);
        return normalized;
    }
    public void disconnected(String clientId) { sent.remove(clientId); }
}
