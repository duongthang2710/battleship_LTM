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

    public ConnectionManager(AuthService auth, MatchRepository history, LeaderboardRepository leaderboardRepository) {
        this.auth = auth;
        this.history = history;
        leaderboard = new LeaderboardService(leaderboardRepository, history);
        rooms = new RoomManager(this, history);
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
        clients.computeIfPresent(id, (clientId, client) -> {
            rooms.disconnected(clientId);
            auth.logout(clientId, client.account());
            LOG.info("Client disconnected " + clientId);
            return null;
        });
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
        history.close();
    }
}
