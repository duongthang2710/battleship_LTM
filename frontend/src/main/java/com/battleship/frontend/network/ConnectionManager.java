package com.battleship.frontend.network;

import com.battleship.shared.protocol.ConnectRequest;
import com.battleship.shared.protocol.ConnectResponse;
import com.battleship.shared.game.PlayerIdentity;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageType;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Delivers queued socket callbacks only when update() is called on the render thread. */
public final class ConnectionManager implements AutoCloseable {
    public record Status(boolean connected, boolean connecting, String detail, PlayerIdentity player) { }

    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
    private final Consumer<Message> messages;
    private final Consumer<Status> statuses;
    private final ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<>();
    private volatile Session current;

    public ConnectionManager(Consumer<Message> messages, Consumer<Status> statuses) {
        this.messages = messages;
        this.statuses = statuses;
    }

    public void connect(String host, int port, String playerName) {
        disconnect();
        Session session = new Session(playerName);
        current = session;
        statuses.accept(new Status(false, true, "Connecting to " + host + ":" + port, null));
        session.start(host, port);
    }

    public boolean connected() { return current != null && current.connected; }

    public boolean send(Message message) {
        Session session = current;
        return session != null && session.connected && session.send(message);
    }

    public void update() {
        Runnable event;
        while ((event = events.poll()) != null) event.run();
    }

    public void disconnect() {
        Session session = current;
        current = null;
        events.clear();
        if (session != null) session.close(true);
    }

    @Override public void close() { disconnect(); }

    private final class Session implements MessageHandler {
        private final Message connect;
        private final NetworkClient client = new NetworkClient(this);
        private final ExecutorService writer = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("tcp-writer").factory());
        private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("tcp-heartbeat").factory());
        private volatile boolean connected;

        private Session(String name) { connect = Message.request(MessageType.CONNECT, new ConnectRequest(name)); }

        private void start(String host, int port) {
            heartbeat.scheduleAtFixedRate(() -> {
                if (connected) send(Message.request(MessageType.PING, null));
            }, 5, 10, TimeUnit.SECONDS);
            Thread.ofVirtual().name("tcp-reader").start(() -> client.connectAndRead(host, port, connect));
        }

        private boolean send(Message message) {
            try {
                writer.execute(() -> {
                    try { client.send(message); }
                    catch (IOException exception) {
                        LOG.warning("TCP write failed for " + message.type() + ": " + exception.getClass().getSimpleName());
                        client.close();
                    }
                });
                return true;
            } catch (RejectedExecutionException exception) {
                return false;
            }
        }

        @Override public void onMessage(Message message) {
            if (current != this) return;
            if (message.type() == MessageType.CONNECT_OK) {
                if (connected || !message.requestId().equals(connect.requestId())) { client.close(); return; }
                PlayerIdentity player = message.payloadAs(ConnectResponse.class).player();
                if (player == null || player.clientId() == null) { client.close(); return; }
                connected = true;
                LOG.info("TCP CONNECT_OK player=" + player.playerName() + " clientId=" + player.clientId());
                publish(() -> statuses.accept(new Status(true, false, "Connected", player)));
            } else if (message.type() != MessageType.PONG && connected) {
                publish(() -> messages.accept(message));
            }
        }

        @Override public void onDisconnected(String reason) {
            connected = false;
            client.close();
            writer.shutdownNow();
            heartbeat.shutdownNow();
            publish(() -> {
                current = null;
                statuses.accept(new Status(false, false, "Disconnected: " + reason, null));
            });
        }

        private void publish(Runnable event) { events.add(() -> { if (current == this) event.run(); }); }

        private void close(boolean graceful) {
            if (graceful && connected) {
                // Bound graceful shutdown without waiting for socket writes on the render thread.
                try {
                    heartbeat.schedule(client::close, 500, TimeUnit.MILLISECONDS);
                    send(Message.request(MessageType.DISCONNECT, null));
                    writer.execute(client::close);
                    writer.shutdown();
                    heartbeat.shutdown();
                } catch (RejectedExecutionException exception) { client.close(); }
            } else {
                client.close();
                writer.shutdownNow();
                heartbeat.shutdownNow();
            }
            connected = false;
        }
    }
}
