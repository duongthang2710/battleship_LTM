package com.battleship.frontend.pvp;

import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.game.PlayerIdentity;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import com.jme3.asset.DesktopAssetManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real TCP transport with a scripted server; does not claim to test PostgreSQL or macOS. */
public final class JoinConnectionChecks {
    public static void main(String[] args) throws Exception {
        var assets = new DesktopAssetManager(true);
        String oldPort = System.getProperty("battleship.port"), oldHost = System.getProperty("battleship.host");
        try {
            System.setProperty("battleship.host", "127.0.0.1");
            for (boolean drop : new boolean[] {false, true}) {
                try (var listener = new ServerSocket(0); var worker = Executors.newSingleThreadExecutor()) {
                    System.setProperty("battleship.port", Integer.toString(listener.getLocalPort()));
                    Future<?> server = worker.submit(() -> {
                        try (Socket socket = listener.accept()) {
                            socket.setSoTimeout(5_000);
                            var input = new DataInputStream(socket.getInputStream());
                            var output = new DataOutputStream(socket.getOutputStream());
                            Message request;
                            while ((request = MessageCodec.read(input)) != null) {
                                switch (request.type()) {
                                    case CONNECT -> MessageCodec.write(output, Message.reply(MessageType.CONNECT_OK, request.requestId(),
                                            new ConnectResponse(new PlayerIdentity("join-client", "Admiral"))));
                                    case LOGIN -> MessageCodec.write(output, Message.reply(MessageType.AUTH_SUCCESS, request.requestId(),
                                            new AccountProfile(UUID.randomUUID(), "join_check", "Admiral")));
                                    case JOIN_ROOM -> {
                                        if (drop) return;
                                        MessageCodec.write(output, Message.reply(MessageType.ERROR, request.requestId(), new RoomError("ROOM_NOT_FOUND", "Room not found.")));
                                    }
                                    case PING -> MessageCodec.write(output, Message.reply(MessageType.PONG, request.requestId(), null));
                                    case DISCONNECT -> { return; }
                                    default -> throw new AssertionError("Unexpected test request: " + request.type());
                                }
                            }
                        } catch (IOException exception) { throw new UncheckedIOException(exception); }
                    });
                    var panel = new PvpRoomPanel(assets);
                    try (var client = new PvpController(panel, "Admiral", room -> { }, () -> { }, () -> { }, match -> { }, error -> { })) {
                        client.openAccount(); await(client, client::connected);
                        client.login("join_check", "TransportCheckPassword!"); await(client, () -> client.account() != null);
                        client.open();
                        client.handle(PvpRoomPanel.Action.SHOW_JOIN);
                        for (char letter : "ABCDEF".toCharArray()) panel.inputCharacter(letter);
                        client.handle(PvpRoomPanel.Action.JOIN_ROOM);
                        if (drop) {
                            await(client, () -> client.account() == null);
                            require(!client.connected() && client.status().contains("Server closed the connection"), "Lost TCP clears authentication with a reason");
                            String reason = client.status();
                            client.openAccount(false);
                            for (int i = 0; i < 30; i++) { client.update(); Thread.sleep(5); }
                            require(client.status().equals(reason) && !client.connected(), "Returning to login preserves the reason and does not reconnect silently");
                            listener.setSoTimeout(200);
                            try (var unexpected = listener.accept()) { throw new AssertionError("Unexpected automatic reconnect"); }
                            catch (SocketTimeoutException expected) { }
                        } else {
                            await(client, () -> !client.pending());
                            require(client.connected() && client.account() != null && client.status().equals("Room not found."), "Rejected Join keeps authentication and the TCP session");
                        }
                    }
                    server.get(5, TimeUnit.SECONDS);
                }
            }
            checkReconnect(assets);
            checkCreateConfiguration(assets);
            System.out.println("PASS: rejected Join preserves login; dropped Join reports TCP loss; login navigation retains the reason without automatic reconnect.");
        } finally {
            restore("battleship.port", oldPort); restore("battleship.host", oldHost);
        }
    }
    private static void checkCreateConfiguration(DesktopAssetManager assets) throws Exception {
        var creates = new java.util.concurrent.atomic.AtomicInteger();
        try (var listener = new ServerSocket(0); var worker = Executors.newSingleThreadExecutor()) {
            System.setProperty("battleship.port", Integer.toString(listener.getLocalPort()));
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    var input = new DataInputStream(socket.getInputStream()); var output = new DataOutputStream(socket.getOutputStream());
                    Message request;
                    while ((request = MessageCodec.read(input)) != null) switch (request.type()) {
                        case CONNECT -> MessageCodec.write(output, Message.reply(MessageType.CONNECT_OK, request.requestId(),
                                new ConnectResponse(new PlayerIdentity("creator", "Admiral"))));
                        case LOGIN -> MessageCodec.write(output, Message.reply(MessageType.AUTH_SUCCESS, request.requestId(),
                                new AccountProfile(UUID.randomUUID(), "creator", "Admiral")));
                        case CREATE_ROOM -> {
                            creates.incrementAndGet();
                            var rules = request.payloadAs(CreateRoomRequest.class).rules();
                            MessageCodec.write(output, Message.reply(MessageType.ROOM_CREATED, request.requestId(),
                                    new RoomSnapshot("ABC234", List.of(new RoomPlayer("creator", "Admiral", false)), RoomPhase.WAITING, rules)));
                        }
                        case PING -> MessageCodec.write(output, Message.reply(MessageType.PONG, request.requestId(), null));
                        case DISCONNECT -> { return; }
                        default -> throw new AssertionError("Unexpected configuration request: " + request.type());
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            var panel = new PvpRoomPanel(assets);
            try (var client = new PvpController(panel, "Admiral", r -> { }, () -> { }, () -> { }, s -> { }, e -> { })) {
                client.openAccount(); await(client, client::connected);
                client.login("creator", "TestPassword!"); await(client, () -> client.account() != null); client.open();
                client.handle(PvpRoomPanel.Action.SHOW_CREATE);
                client.handle(PvpRoomPanel.Action.TOGGLE_MODE);
                client.handle(PvpRoomPanel.Action.FEWER_SHIPS); client.handle(PvpRoomPanel.Action.FEWER_SHIPS);
                client.handle(PvpRoomPanel.Action.TOGGLE_TURN_RULE);
                var rules = panel.selectedRules();
                require(panel.configuring() && !client.pending() && creates.get() == 0, "Editing settings never sends CREATE_ROOM");
                client.handle(PvpRoomPanel.Action.BACK);
                require(!panel.configuring() && panel.enabled(PvpRoomPanel.Action.SHOW_CREATE) && client.room() == null, "Back returns to PvP without creating a room");
                client.handle(PvpRoomPanel.Action.SHOW_CREATE); client.handle(PvpRoomPanel.Action.CREATE_ROOM);
                await(client, () -> client.room() != null);
                require(creates.get() == 1 && client.room().rules().equals(rules) && rules.shipCount() == 3
                        && rules.mode() == com.battleship.shared.game.GameMode.DESIGNER
                        && rules.turnRule() == com.battleship.shared.game.TurnRule.HIT_CONTINUES, "Confirmation sends selected rules once and consumes authoritative snapshot");
            }
            server.get(5, TimeUnit.SECONDS);
        }
        System.out.println("PASS client TCP adapter (scripted server): configure/back/confirm sends Designer + 3 ships + hit-again only on confirmation.");
    }
    private static void checkReconnect(DesktopAssetManager assets) throws Exception {
        var drop = new CountDownLatch(1);
        try (var listener = new ServerSocket(0); var worker = Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(5_000); System.setProperty("battleship.port", Integer.toString(listener.getLocalPort()));
            Future<?> server = worker.submit(() -> {
                try {
                    for (int session = 1; session <= 2; session++) try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(5_000);
                        var input = new DataInputStream(socket.getInputStream()); var output = new DataOutputStream(socket.getOutputStream());
                        Message connect = MessageCodec.read(input);
                        if (connect.type() != MessageType.CONNECT) throw new AssertionError("Expected connect handshake");
                        MessageCodec.write(output, Message.reply(MessageType.CONNECT_OK, connect.requestId(), new ConnectResponse(new PlayerIdentity("reconnect-" + session, "Admiral"))));
                        Message login = MessageCodec.read(input);
                        if (login.type() != MessageType.LOGIN) throw new AssertionError("Fresh session requires sign-in");
                        MessageCodec.write(output, Message.reply(MessageType.AUTH_SUCCESS, login.requestId(), new AccountProfile(UUID.randomUUID(), "reconnect_check", "Admiral")));
                        if (session == 1) { if (!drop.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test never dropped connection"); }
                        else {
                            Message request;
                            while ((request = MessageCodec.read(input)) != null) {
                                if (request.type() == MessageType.DISCONNECT) break;
                                if (request.type() == MessageType.PING) MessageCodec.write(output, Message.reply(MessageType.PONG, request.requestId(), null));
                            }
                        }
                    }
                } catch (IOException error) { throw new UncheckedIOException(error); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
            });
            try (var client = new PvpController(new PvpRoomPanel(assets), "Admiral", room -> { }, () -> { }, () -> { }, match -> { }, error -> { })) {
                client.openAccount(); await(client, client::connected);
                client.login("reconnect_check", "TestPassword!"); await(client, () -> client.account() != null);
                drop.countDown(); await(client, () -> client.connectionProblem() != null);
                require(client.account() == null && !client.connected(), "Lost TCP requires notice and clears authentication");
                client.reconnect(); client.reconnect(); await(client, () -> client.connected() && !client.connecting());
                require(client.connectionProblem() == null && client.account() == null && client.clientId().equals("reconnect-2"), "Reconnect creates one fresh unauthenticated session and clears notice");
                client.login("reconnect_check", "TestPassword!"); await(client, () -> client.account() != null);
            }
            server.get(5, TimeUnit.SECONDS);
        }
        System.out.println("PASS: real TCP drop, connection notice state, explicit reconnect, duplicate reconnect guard and fresh sign-in.");
    }

    private static void await(PvpController client, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            client.update();
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out: " + client.status());
            Thread.sleep(5);
        }
    }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
