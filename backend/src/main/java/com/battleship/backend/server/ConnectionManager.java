package com.battleship.backend.server;

import com.battleship.backend.room.RoomManager;
import com.battleship.backend.auth.AuthService;
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

    public ConnectionManager(AuthService auth, MatchRepository history) {
        this.auth = auth;
        this.history = history;
        rooms = new RoomManager(this, history);
    }

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
        ClientHandler client = clients.get(id);
        if (client != null) {
            rooms.disconnected(id);
            auth.logout(id, client.account());
            clients.remove(id, client);
            LOG.info("Client disconnected " + id + "; active=" + clients.size());
        }
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
