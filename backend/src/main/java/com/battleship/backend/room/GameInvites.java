package com.battleship.backend.room;

import com.battleship.backend.server.ConnectionManager;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Session-only invitations. Every call holds RoomManager's lock, including expiry. */
final class GameInvites {
    private static final long LIFETIME = TimeUnit.SECONDS.toNanos(60);
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final ConnectionManager connections;

    GameInvites(ConnectionManager connections) { this.connections = connections; }

    void checkCapacity(Room room, String receiver) {
        if (pending.values().stream().anyMatch(p -> p.room == room && p.receiver.equals(receiver)))
            throw new IllegalArgumentException("You already invited this friend. Wait for their response.");
        // Bound notification load without reserving a seat; other code-based joins remain possible.
        if (pending.values().stream().filter(p -> p.room == room).count() >= 8
                || pending.values().stream().filter(p -> p.receiver.equals(receiver)).count() >= 8)
            throw new IllegalArgumentException("Too many pending invitations. Wait for a response or expiry.");
    }

    Pending add(Room room, String sender, String receiver, AccountProfile inviter, AccountProfile recipient) {
        checkCapacity(room, receiver);
        Pending invite = new Pending(UUID.randomUUID(), room, sender, receiver, inviter, recipient, System.nanoTime() + LIFETIME);
        pending.put(invite.id, invite);
        return invite;
    }

    Pending forRecipient(UUID id, String receiver, UUID accountId) {
        Pending invite = pending.get(id);
        if (invite == null) throw new IllegalArgumentException("Invitation expired, was cancelled or has already been processed.");
        if (!invite.receiver.equals(receiver) || !invite.recipient.id().equals(accountId))
            throw new IllegalArgumentException("This invitation belongs to another player.");
        return invite;
    }

    void expire() {
        long now = System.nanoTime();
        for (Pending invite : List.copyOf(pending.values())) if (now - invite.deadline >= 0)
            finish(invite, GameInviteStatus.EXPIRED, null, null, "PvP invitation expired after 60 seconds.");
    }

    void invalidateRoom(Room room, String reason) {
        for (Pending invite : List.copyOf(pending.values())) if (invite.room == room)
            finish(invite, GameInviteStatus.INVALIDATED, null, null, reason);
    }

    void invalidateClient(String clientId, String reason) {
        for (Pending invite : List.copyOf(pending.values())) if (invite.sender.equals(clientId) || invite.receiver.equals(clientId))
            finish(invite, GameInviteStatus.INVALIDATED, null, null, reason);
    }

    void finish(Pending invite, GameInviteStatus status, String actor, String requestId, String reason) {
        if (pending.remove(invite.id) != null) publish(invite, status, actor, requestId, reason);
    }

    void publish(Pending invite, GameInviteStatus status, String actor, String requestId, String reason) {
        long remaining = status == GameInviteStatus.PENDING ? Math.max(0, (invite.deadline - System.nanoTime() + 999_999) / 1_000_000) : 0;
        GameInvite view = new GameInvite(invite.id, invite.room.code, invite.inviter, invite.recipient, status, remaining, reason);
        for (String id : List.of(invite.sender, invite.receiver)) connections.send(id, Message.reply(MessageType.GAME_INVITE_UPDATED,
                id.equals(actor) ? requestId : UUID.randomUUID().toString(), view));
    }

    record Pending(UUID id, Room room, String sender, String receiver, AccountProfile inviter, AccountProfile recipient, long deadline) {}
}
