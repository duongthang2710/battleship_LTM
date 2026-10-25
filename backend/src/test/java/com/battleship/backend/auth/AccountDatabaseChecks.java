package com.battleship.backend.auth;

import com.battleship.backend.database.Database;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.auth.*;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

/** Real PostgreSQL and real TCP, opt-in with -PdatabaseChecks. Leaves uniquely named QA records. */
public final class AccountDatabaseChecks {
    private static final String PASSWORD = "DatabaseCheckPassword2026!";
    public static void run() throws Exception {
        Database database = Database.fromEnvironment();
        database.migrate(); database.migrate();
        String name = "db_" + UUID.randomUUID().toString().substring(0, 8);
        UUID accountId;
        try (TcpServer server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException exception) { throw new UncheckedIOException(exception); } });
            try (Peer a = new Peer(server.port()); Peer b = new Peer(server.port())) {
                a.send(MessageType.CREATE_ROOM, Map.of("userId", UUID.randomUUID(), "username", name));
                require(a.read(MessageType.AUTH_FAILED).payloadAs(AuthError.class).code().equals("AUTH_REQUIRED"), "Anonymous forged identity cannot create room");
                b.send(MessageType.JOIN_ROOM, new JoinRoomRequest("AAAAAA")); b.read(MessageType.AUTH_FAILED);
                a.send(MessageType.REGISTER, new RegisterRequest("x", PASSWORD, PASSWORD)); a.read(MessageType.AUTH_FAILED);
                a.send(MessageType.REGISTER, new RegisterRequest(name, "short", "short")); a.read(MessageType.AUTH_FAILED);
                a.send(MessageType.REGISTER, new RegisterRequest(name, PASSWORD, PASSWORD + "x")); a.read(MessageType.AUTH_FAILED);
                a.send(MessageType.REGISTER, new RegisterRequest(name, PASSWORD, PASSWORD));
                AccountProfile profile = a.read(MessageType.REGISTER_OK).payloadAs(AccountProfile.class);
                accountId = profile.id();
                a.send(MessageType.CREATE_ROOM, null); a.read(MessageType.AUTH_FAILED);
                b.send(MessageType.REGISTER, new RegisterRequest(name.toUpperCase(Locale.ROOT), PASSWORD, PASSWORD)); b.read(MessageType.AUTH_FAILED);
                a.send(MessageType.LOGIN, new LoginRequest(name, "IncorrectPassword2026!")); a.read(MessageType.AUTH_FAILED);
                a.send(MessageType.LOGIN, new LoginRequest("' OR 1=1 --", PASSWORD)); a.read(MessageType.AUTH_FAILED);
                a.send(MessageType.LOGIN, new LoginRequest(name.toUpperCase(Locale.ROOT), PASSWORD));
                Message authenticated = a.read(MessageType.AUTH_SUCCESS);
                require(!authenticated.payload().has("password") && !authenticated.payload().has("passwordHash"), "No credentials in response");
                b.send(MessageType.LOGIN, new LoginRequest(name, PASSWORD)); b.read(MessageType.AUTH_FAILED);
                a.send(MessageType.CREATE_ROOM, Map.of("username", "Impersonated", "userId", UUID.randomUUID()));
                RoomSnapshot room = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                require(room.players().getFirst().playerName().equals(name), "Room name comes from authenticated DB account");
                a.send(MessageType.LOGOUT, null); a.read(MessageType.LOGOUT_OK);
                a.send(MessageType.READY, null); a.read(MessageType.AUTH_FAILED);
                b.send(MessageType.LOGIN, new LoginRequest(name, PASSWORD)); b.read(MessageType.AUTH_SUCCESS);
                b.send(MessageType.LOGOUT, null); b.read(MessageType.LOGOUT_OK);
                b.send(MessageType.REGISTER, new RegisterRequest(name + "_b", PASSWORD, PASSWORD)); b.read(MessageType.REGISTER_OK);
                b.send(MessageType.LOGIN, new LoginRequest(name + "_b", PASSWORD)); b.read(MessageType.AUTH_SUCCESS);
                a.send(MessageType.LOGIN, new LoginRequest(name, PASSWORD)); a.read(MessageType.AUTH_SUCCESS);
                a.send(MessageType.CREATE_ROOM, null); room = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(room.roomCode())); b.read(MessageType.ROOM_JOINED); a.read(MessageType.PLAYER_JOINED);
                a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                a.read(MessageType.START_PLACEMENT); b.read(MessageType.START_PLACEMENT);
                List<ShipPlacement> fleet = java.util.stream.IntStream.range(0, ShipType.values().length)
                        .mapToObj(i -> new ShipPlacement(ShipType.values()[i], new Coordinate(i, 0), ShipOrientation.HORIZONTAL)).toList();
                a.send(MessageType.SUBMIT_FLEET, new FleetSubmission(fleet)); a.read(MessageType.FLEET_ACCEPTED); b.read(MessageType.FLEET_ACCEPTED);
                b.send(MessageType.SUBMIT_FLEET, new FleetSubmission(fleet)); a.read(MessageType.START_BATTLE); b.read(MessageType.START_BATTLE);
                List<Coordinate> cells = fleet.stream().flatMap(ship -> ship.ship().occupiedCells().stream()).toList();
                for (int i = 0; i < cells.size(); i++) {
                    a.send(MessageType.ATTACK, new AttackRequest(cells.get(i))); a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
                    if (i < cells.size() - 1) {
                        b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9 - i / 10, i % 10)));
                        // Use two empty rows so every miss is a fresh cell.
                        a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
                    }
                }
            }
        }
        database.migrate();
        try (var connection = database.open(); var query = connection.prepareStatement(
                "SELECT password_hash FROM users WHERE id=?")) {
            query.setObject(1, accountId);
            try (var row = query.executeQuery()) {
                require(row.next() && !row.getString(1).contains(PASSWORD) && new PasswordHasher().verify(PASSWORD, row.getString(1)), "Hash persisted and survives repeat migration");
            }
        }
        try (var connection = database.open(); var query = connection.prepareStatement(
                "SELECT m.status, m.end_reason, p.result, p.shots, p.hits, p.ships_sunk FROM matches m JOIN match_players p ON p.match_id=m.id WHERE p.user_id=?")) {
            query.setObject(1, accountId);
            try (var row = query.executeQuery()) {
                require(row.next() && row.getString(1).equals("FINISHED") && row.getString(2).equals("FLEET_DESTROYED")
                        && row.getString(3).equals("VICTORY") && row.getInt(4) == 17 && row.getInt(5) == 17 && row.getInt(6) == 5,
                        "Server records exact winner and statistics in PostgreSQL");
                require(!row.next(), "One match recorded once");
            }
        }
        System.out.println("PASS PostgreSQL + TCP: repeated migration, validation/duplicate/SQL input, salted hash, login/logout/single-session, identity, completed match + persisted stats.");
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        Peer(int port) throws Exception {
            socket = new Socket("localhost", port); socket.setSoTimeout(10_000);
            input = new DataInputStream(socket.getInputStream()); output = new DataOutputStream(socket.getOutputStream());
            send(MessageType.CONNECT, new ConnectRequest("Untrusted guest")); read(MessageType.CONNECT_OK);
        }
        void send(MessageType type, Object payload) throws IOException { MessageCodec.write(output, Message.request(type, payload)); }
        Message read(MessageType type) throws IOException {
            Message message;
            do { message = MessageCodec.read(input); } while (message != null && message.type() == MessageType.TURN_TIMER);
            require(message != null && message.type() == type, "Expected " + type + ", got " + (message == null ? "EOF" : message.type()));
            return message;
        }
        @Override public void close() throws IOException { socket.close(); }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
