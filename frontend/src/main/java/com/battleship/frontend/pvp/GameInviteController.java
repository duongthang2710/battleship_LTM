package com.battleship.frontend.pvp;

import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class GameInviteController {
    private record Received(GameInvite invite, long receivedAt) {}
    private final PvpController network;
    private final GameInvitePanel panel;
    private final Map<UUID, Received> pending = new LinkedHashMap<>();
    private Received current;
    private String requestId;
    private long sentAt;

    public GameInviteController(PvpController network, GameInvitePanel panel) {
        this.network = network; this.panel = panel; network.setGameInviteListener(this::onMessage);
    }
    public GameInvite current() { return current == null ? null : current.invite; }
    public boolean busy() { return requestId != null; }
    public void send(UUID friend) {
        if (busy()) return;
        submit(MessageType.SEND_GAME_INVITE, new SendGameInvite(friend));
    }
    public void handle(GameInvitePanel.Action action) {
        if (busy()) return;
        if (action == GameInvitePanel.Action.DISMISS) {
            if (current != null) pending.remove(current.invite.inviteId());
            current = null; panel.hide(); next(); return;
        }
        if (current == null || current.invite.status() != GameInviteStatus.PENDING || remaining() == 0) return;
        submit(action == GameInvitePanel.Action.ACCEPT ? MessageType.ACCEPT_GAME_INVITE : MessageType.DECLINE_GAME_INVITE,
                new GameInviteResponse(current.invite.inviteId()));
    }
    private void submit(MessageType type, Object payload) {
        if (network.pending()) { panel.error("Finish the current room request before answering or sending an invitation."); return; }
        requestId = network.requestGameInvite(type, payload); sentAt = System.nanoTime();
        if (requestId == null) panel.error("Connect and sign in to invite friends.");
        else if (current == null) panel.waiting();
        else render();
    }
    public void update() {
        if (network.account() == null) { current = null; requestId = null; pending.clear(); panel.hide(); return; }
        if (busy() && System.nanoTime() - sentAt > TimeUnit.SECONDS.toNanos(20)) {
            requestId = null; panel.error("Server did not respond. Reopen Friends or wait for an invitation update.");
        }
        if (current != null && panel.visible()) panel.updateCountdown(remaining());
    }
    private long remaining() {
        if (current == null) return 0;
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current.receivedAt);
        return Math.max(0, current.invite.remainingMillis() - elapsed);
    }
    private void onMessage(Message message) {
        boolean response = message.requestId().equals(requestId);
        if (response) requestId = null;
        if (message.type() != MessageType.GAME_INVITE_UPDATED) {
            if (response) panel.error(message.type() == MessageType.GAME_INVITE_ERROR
                    ? message.payloadAs(RoomError.class).message() : "Sign in to invite friends.");
            return;
        }
        var invite = message.payloadAs(GameInvite.class);
        if (network.account() == null || !invite.inviter().id().equals(network.account().id()) && !invite.recipient().id().equals(network.account().id())) return;
        Received received = new Received(invite, System.nanoTime());
        if (invite.status() == GameInviteStatus.PENDING) {
            pending.put(invite.inviteId(), received);
            if (current == null || current.invite.status() != GameInviteStatus.PENDING || response
                    || invite.recipient().id().equals(network.account().id()) && !current.invite.recipient().id().equals(network.account().id())) current = received;
        } else {
            pending.remove(invite.inviteId());
            if (current == null || current.invite.inviteId().equals(invite.inviteId()) || response
                    || invite.recipient().id().equals(network.account().id()) && !current.invite.recipient().id().equals(network.account().id())) current = received;
            if (invite.status() == GameInviteStatus.ACCEPTED) network.showInvitedRoom();
        }
        render();
    }
    private void next() { if (!pending.isEmpty()) { current = pending.values().iterator().next(); render(); } }
    private void render() {
        if (current != null) panel.show(current.invite, network.account() != null && current.invite.recipient().id().equals(network.account().id()), busy(), remaining());
    }
}
