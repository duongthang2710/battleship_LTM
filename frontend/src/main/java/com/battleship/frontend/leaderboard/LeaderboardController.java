package com.battleship.frontend.leaderboard;

import com.battleship.frontend.pvp.PvpController;
import com.battleship.shared.leaderboard.*;
import com.battleship.shared.protocol.Message;
import java.util.concurrent.TimeUnit;

/** Render-thread coordinator. The existing TCP adapter delivers every callback here. */
public final class LeaderboardController {
    private final PvpController network;
    private final LeaderboardPanel panel;
    private final Runnable mainMenu;
    private LeaderboardSort sort = LeaderboardSort.WINS;
    private int page;
    private String requestId;
    private long sentAt;
    private boolean active;

    public LeaderboardController(PvpController network, LeaderboardPanel panel, Runnable mainMenu) {
        this.network = network; this.panel = panel; this.mainMenu = mainMenu;
        network.setLeaderboardListener(this::onMessage);
    }
    public void show() { active = true; sort = LeaderboardSort.WINS; page = 0; panel.show(); load(); }
    public void hide() { active = false; requestId = null; panel.hide(); }
    public void update() {
        if (active && requestId != null && System.nanoTime() - sentAt > TimeUnit.SECONDS.toNanos(20)) {
            requestId = null; panel.setError("Server did not respond. Refresh to retry.");
        }
    }
    public void handle(LeaderboardPanel.Action action) {
        if (!active || !panel.enabled(action)) return;
        switch (action) {
            case BACK -> { hide(); mainMenu.run(); }
            case WINS, WIN_RATE, MATCHES -> {
                sort = LeaderboardSort.valueOf(action.name()); page = 0; load();
            }
            case PREVIOUS -> { page--; load(); }
            case NEXT -> { page++; load(); }
            case REFRESH -> load();
        }
    }
    private void load() {
        panel.setLoading(sort, page);
        requestId = network.requestLeaderboard(new LeaderboardRequest(sort, page));
        sentAt = System.nanoTime();
        if (requestId == null) panel.setError("Connect and sign in to view rankings.");
    }
    private void onMessage(Message message) {
        if (!active || !message.requestId().equals(requestId)) return;
        requestId = null;
        switch (message.type()) {
            case LEADERBOARD_RESULT -> {
                LeaderboardPage result = message.payloadAs(LeaderboardPage.class);
                if (result.sort() != sort || result.page() != page) { panel.setError("Rankings changed. Refresh to retry."); return; }
                panel.setPage(result);
            }
            case LEADERBOARD_ERROR -> panel.setError(message.payloadAs(LeaderboardError.class).message());
            default -> panel.setError("Sign in to view rankings.");
        }
    }
}
