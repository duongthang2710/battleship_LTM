package com.battleship.backend.server;

import com.battleship.backend.room.RoomManager;
import com.battleship.backend.auth.AuthService;
import com.battleship.backend.leaderboard.LeaderboardService;
import com.battleship.backend.leaderboard.LeaderboardRepository;
import com.battleship.backend.match.MatchRepository;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.protocol.Message;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ConnectionManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final RoomManager rooms;
    private final AuthService auth;
    private final MatchRepository history;
    private final LeaderboardService leaderboard;
    private final com.battleship.backend.friends.FriendsService friends;
    private final com.battleship.backend.friends.FriendsRepository friendsRepository;
    private final java.util.concurrent.ExecutorService presence = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("friend-presence").factory());

    public ConnectionManager(AuthService auth, MatchRepository history, LeaderboardRepository leaderboardRepository,
                             com.battleship.backend.friends.FriendsRepository friendsRepository) {
        this.auth = auth;
        this.friendsRepository = friendsRepository;
        friends = new com.battleship.backend.friends.FriendsService(friendsRepository, this::online);
        this.history = history;
        leaderboard = new LeaderboardService(leaderboardRepository, history);
        rooms = new RoomManager(this, history);
    }

    public com.battleship.backend.friends.FriendsService friends() { return friends; }
    private boolean online(java.util.UUID id) {
        return clients.values().stream().anyMatch(c -> { AccountProfile account = c.account(); return account != null && account.id().equals(id); });
    }
    public void notifyFriends(java.util.Collection<java.util.UUID> accounts, String message) {
        var event = Message.request(com.battleship.shared.protocol.MessageType.FRIENDS_CHANGED,
                new com.battleship.shared.friends.FriendsNotice(message));
        clients.values().forEach(client -> {
            AccountProfile account = client.account();
            if (account != null && accounts.contains(account.id())) client.send(event);
        });
    }
    public void presenceChanged(AccountProfile account) {
        if (account == null || presence.isShutdown()) return;
        try {
            presence.submit(() -> {
                try { notifyFriends(friendsRepository.friendIds(account.id()), "Friend online status changed."); }
                catch (java.sql.SQLException exception) { LOG.warning("Friend presence unavailable; SQL state=" + exception.getSQLState()); }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { /* Server is closing. */ }
    }

    public LeaderboardService leaderboard() { return leaderboard; }
    public AuthService auth() { return auth; }
    public AccountProfile account(String id) {
        ClientHandler client = clients.get(id);
        return client == null ? null : client.account();
    }

    public RoomManager rooms() { return rooms; }

    public void add(ClientHandler client) {
        clients.put(client.id(), client);
        LOG.info("Client accepted " + client.id() + "; active=" + clients.size());
    }

    public void remove(String id) {
        AccountProfile[] removed = new AccountProfile[1];
        clients.computeIfPresent(id, (clientId, client) -> {
            removed[0] = client.account();
            rooms.disconnected(clientId);
            auth.logout(clientId, client.account());
            LOG.info("Client disconnected " + clientId);
            return null;
        });
        presenceChanged(removed[0]);
    }

    public void send(String id, Message message) {
        ClientHandler client = clients.get(id);
        if (client != null) client.send(message);
    }

    @Override
    public void close() {
        rooms.close();
        clients.values().forEach(client -> {
            client.close();
            remove(client.id());
        });
        presence.shutdownNow();
        history.close();
    }
}
