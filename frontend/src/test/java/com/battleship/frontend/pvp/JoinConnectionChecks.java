package com.battleship.frontend.pvp;

import com.battleship.frontend.menu.MainMenu;
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
                        client.open(MainMenu.Action.JOIN_ROOM);
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
            System.out.println("PASS: rejected Join preserves login; dropped Join reports TCP loss; login navigation retains the reason without automatic reconnect.");
        } finally {
            restore("battleship.port", oldPort); restore("battleship.host", oldHost);
        }
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
