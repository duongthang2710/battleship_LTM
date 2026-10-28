package com.battleship.frontend.chat;

import com.battleship.frontend.pvp.PvpController;
import com.battleship.shared.chat.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.RoomError;
import java.util.concurrent.TimeUnit;

/** Live-only chat; context changes discard drafts, pending replies and the previous round's messages. */
public final class ChatController {
    private final PvpController network;
    private final ChatPanel panel;
    private ChatContext context;
    private String requestId;
    private long sentAt, lastSequence;

    public ChatController(PvpController network, ChatPanel panel) {
        this.network = network; this.panel = panel; network.setChatListener(this::onMessage);
    }
    public ChatContext context() { return context; }
    public void handle(ChatPanel.Action action) {
        switch (action) {
            case TOGGLE -> panel.toggle();
            case LATEST -> panel.latest();
            case SEND -> send();
        }
    }
    public void send() {
        if (requestId != null || context == null || !context.canSend()) return;
        try {
            ChatText.validate(panel.draft());
            requestId = network.requestChat(new ChatSend(context.contextId(), panel.draft()));
            if (requestId == null) panel.error("Connection unavailable. Message was not sent.");
            else { sentAt = System.nanoTime(); panel.setBusy(true); }
        } catch (IllegalArgumentException exception) { panel.error(exception.getMessage()); }
    }
    public void update() {
        if (network.account() == null || network.room() == null) { clear(); return; }
        if (requestId != null && System.nanoTime() - sentAt > TimeUnit.SECONDS.toNanos(20)) {
            requestId = null; panel.error("No confirmation received. Delivery is unknown; check the conversation before retrying.");
        }
    }
    private void clear() {
        if (context == null && requestId == null) return;
        context = null; requestId = null; lastSequence = 0; panel.clear();
    }
    private void onMessage(Message message) {
        if (message.type() == MessageType.ROOM_LEFT) { clear(); return; }
        if (message.type() == MessageType.CHAT_CONTEXT) {
            ChatContext next = message.payloadAs(ChatContext.class);
            if (network.room() == null || !network.room().roomCode().equals(next.roomCode())) return;
            if (context == null || !context.contextId().equals(next.contextId())) { requestId = null; lastSequence = 0; }
            context = next; if (!next.canSend()) requestId = null;
            panel.setContext(next); return;
        }
        boolean response = message.requestId().equals(requestId);
        if (response) requestId = null;
        if (message.type() == MessageType.CHAT_MESSAGE) {
            var delivered = message.payloadAs(ChatMessage.class);
            if (context == null || !context.contextId().equals(delivered.contextId()) || delivered.sequence() <= lastSequence) return;
            lastSequence = delivered.sequence(); panel.append(delivered);
            if (response) panel.sent();
        } else if (response) panel.error(message.type() == MessageType.CHAT_ERROR
                ? message.payloadAs(RoomError.class).message() : "Sign in before using room chat.");
    }
}
