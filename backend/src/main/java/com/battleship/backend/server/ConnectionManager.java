package com.battleship.backend.server;

import com.battleship.backend.room.RoomManager;
import com.battleship.shared.protocol.Message;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ConnectionManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final RoomManager rooms = new RoomManager(this);

    public RoomManager rooms() { return rooms; }

    public void add(ClientHandler client) {
        clients.put(client.id(), client);
        LOG.info("Client accepted " + client.id() + "; active=" + clients.size());
    }

    public void remove(String id) {
        if (clients.remove(id) != null) {
            rooms.disconnected(id);
            LOG.info("Client disconnected " + id + "; active=" + clients.size());
        }
    }

    public void send(String id, Message message) {
        ClientHandler client = clients.get(id);
        if (client != null) client.send(message);
    }

    @Override
    public void close() {
        clients.values().forEach(ClientHandler::close);
        clients.clear();
    }
}
