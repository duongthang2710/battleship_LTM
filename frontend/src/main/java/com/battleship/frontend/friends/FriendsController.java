package com.battleship.frontend.friends;

import com.battleship.frontend.pvp.PvpController;
import com.battleship.shared.friends.*;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageType;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/** All callbacks run on the render thread; the TCP adapter owns background I/O. */
public final class FriendsController {
    private final PvpController network;
    private final FriendsPanel panel;
    private final Runnable mainMenu;
    private final Consumer<Boolean> notification;
    private FriendsAction tab = FriendsAction.FRIENDS;
    private int page;
    private String query = "", requestId, notice = "";
    private long sentAt;
    private boolean active, dirty;

    public FriendsController(PvpController network, FriendsPanel panel, Runnable mainMenu, Consumer<Boolean> notification) {
        this.network = network; this.panel = panel; this.mainMenu = mainMenu; this.notification = notification;
        network.setFriendsListener(this::onMessage);
    }
    public void show() {
        active = true; dirty = false; tab = FriendsAction.FRIENDS; page = 0; notice = "";
        notification.accept(false); panel.show(); load();
    }
    public void hide() { active = false; requestId = null; dirty = false; panel.hide(); }
    public void update() {
        if (network.account() == null) { notification.accept(false); return; }
        if (!active) return;
        if (requestId != null && System.nanoTime() - sentAt > TimeUnit.SECONDS.toNanos(20)) {
            requestId = null; dirty = false; panel.setError("Server did not respond. Refresh to retry.");
        }
        if (dirty && requestId == null && !panel.confirming()) { dirty = false; load(); }
    }
    public void handle(FriendsPanel.Command command) {
        if (!active || !panel.enabled(command.action())) return;
        switch (command.action()) {
            case BACK -> { hide(); mainMenu.run(); }
            case FRIENDS, RECEIVED, SENT, SEARCH -> {
                tab = FriendsAction.valueOf(command.action().name()); page = 0; query = panel.query(); notice = ""; load();
            }
            case PREVIOUS -> { page--; load(); }
            case NEXT -> { page++; load(); }
            case REFRESH -> { notice = ""; load(); }
            case REMOVE -> panel.confirm(command.target());
            case CANCEL_REMOVE -> panel.cancelConfirmation();
            default -> {
                FriendsAction action = command.action() == FriendsPanel.Action.CONFIRM_REMOVE
                        ? FriendsAction.REMOVE : FriendsAction.valueOf(command.action().name());
                panel.setLoading(tab, page);
                send(new FriendsRequest(action, command.target(), null, 0));
            }
        }
    }
    private void load() { panel.setLoading(tab, page); send(new FriendsRequest(tab, null, query, page)); }
    private void send(FriendsRequest request) {
        requestId = network.requestFriends(request); sentAt = System.nanoTime();
        if (requestId == null) panel.setError("Connect and sign in to use Friends.");
    }
    private void onMessage(Message message) {
        if (message.type() == MessageType.FRIENDS_CHANGED) {
            if (active) dirty = true;
            else if (!message.payloadAs(FriendsNotice.class).message().equals("Friend online status changed.")) notification.accept(true);
            return;
        }
        if (!active || !message.requestId().equals(requestId)) return;
        requestId = null;
        if (message.type() == MessageType.FRIENDS_RESULT) {
            FriendsResult result = message.payloadAs(FriendsResult.class);
            if (result.action().listing()) {
                panel.setData(result);
                if (!notice.isBlank()) panel.notice(notice);
            } else { notice = result.message(); dirty = true; }
        } else {
            dirty = false;
            panel.setError(message.type() == MessageType.FRIENDS_ERROR ? message.payloadAs(FriendsNotice.class).message() : "Sign in to use Friends.");
        }
    }
}
