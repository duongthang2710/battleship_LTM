package com.battleship.backend.match;

import com.battleship.backend.server.TcpServer;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.JsonParser;

/** One real TCP match checks authority, privacy, first turn and ordered notifications. */
public final class TcpMatchChecks {
    public static void main(String[] args) throws Exception {
        MatchTimerChecks.run();
        playerOneStartsWithEitherColor();
        try (TcpServer server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.port(), "A"); Peer b = new Peer(server.port(), "B")) {
                a.send(MessageType.CREATE_ROOM, null);
                String code = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class).roomCode();
                long joinedAt = System.nanoTime();
                b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(code));
                b.read(MessageType.ROOM_JOINED);
                require(a.read(MessageType.PLAYER_JOINED).payloadAs(RoomSnapshot.class).players().size() == 2, "Creator sees peer without polling");
                long joinedMs = (System.nanoTime() - joinedAt) / 1_000_000;
                a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                RoomSnapshot room = a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class);
                b.read(MessageType.START_PLACEMENT);
                require(room.players().get(0).side() != room.players().get(1).side(), "Opposite sides assigned");
                rejectFleet(a, b, new FleetSubmission(List.of()), "Exactly five ships");
                List<ShipPlacement> invalid = new ArrayList<>(fleet().ships());
                invalid.set(4, invalid.get(0));
                rejectFleet(a, b, new FleetSubmission(invalid), "Each ship type");
                invalid = new ArrayList<>(fleet().ships());
                invalid.set(0, new ShipPlacement(ShipType.CARRIER, new Coordinate(9, 9), ShipOrientation.HORIZONTAL));
                rejectFleet(a, b, new FleetSubmission(invalid), "outside the board");
                invalid.set(0, new ShipPlacement(ShipType.CARRIER, new Coordinate(0, 0), ShipOrientation.VERTICAL));
                rejectFleet(a, b, new FleetSubmission(invalid), "overlaps another ship");
                invalid.set(0, null);
                rejectFleet(a, b, new FleetSubmission(invalid), "Missing or unknown ship");
                rejectFleet(a, b, JsonParser.parseString("{\"ships\":[{\"type\":\"UNKNOWN\",\"orientation\":\"HORIZONTAL\",\"position\":{\"row\":0,\"column\":0}}]}"),
                        "Missing or unknown ship");
                rejectFleet(a, b, JsonParser.parseString("{\"ships\":\"invalid\"}"), "Malformed match data");
                a.send(MessageType.SUBMIT_FLEET, fleet());
                MatchSnapshot aWaiting = a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
                MatchSnapshot bWaiting = b.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
                require(aWaiting.fleetSubmitted() && !bWaiting.fleetSubmitted() && bWaiting.enemyBoard().ships().isEmpty(), "Fleet acknowledgement is private");
                require(aWaiting.phase() == GamePhase.PLACEMENT && bWaiting.phase() == GamePhase.PLACEMENT
                        && aWaiting.ownBoard().ships().size() == 5 && aWaiting.enemyBoard().ships().isEmpty()
                        && bWaiting.ownBoard().ships().isEmpty() && bWaiting.enemyBoard().shots().isEmpty(),
                        "Only one valid fleet must leave both in placement without leaking positions");
                rejectFleet(a, b, fleet(), "Fleet already submitted");
                rejectFleet(b, a, new FleetSubmission(invalid), "Missing or unknown ship");
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.ERROR);
                b.send(MessageType.SUBMIT_FLEET, fleet());
                MatchSnapshot sa = a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
                MatchSnapshot sb = b.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
                require(sa.revision() == 3 && sa.revision() == sb.revision() && sa.enemyBoard().ships().isEmpty()
                        && sb.enemyBoard().ships().isEmpty(), "Only accepted fleets change revision; enemy fleets stay hidden");
                require(sa.currentPlayerId().equals(a.id), "Player 1 starts regardless of color or submission order");
                b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); b.read(MessageType.ERROR);
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(-1, 0))); a.read(MessageType.ERROR);
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(0, 0)));
                sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                require(sa.lastAttack().type() == AttackResultType.HIT && sa.revision() == sb.revision(), "Server resolves hit for both peers");
                require(sa.currentPlayerId().equals(b.id), "Turn switches on hit");
                Peer actor = b;
                int firstTarget = 1, secondTarget = 0;
                List<Coordinate> cells = fleet().ships().stream().flatMap(p -> p.ship().occupiedCells().stream()).toList();
                while (sa.phase() != GamePhase.GAME_OVER) {
                    Coordinate target = cells.get(actor == a ? firstTarget++ : secondTarget++);
                    actor.send(MessageType.ATTACK, new AttackRequest(target));
                    sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    require(sa.revision() == sb.revision() && java.util.Objects.equals(sa.winnerId(), sb.winnerId()), "Peers agree after every shot");
                    actor = actor == a ? b : a;
                }
                require(sa.winnerId().equals(a.id), "Player 1 wins complete alternating match");
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.ERROR);
                b.close();
                require(a.read(MessageType.PLAYER_LEFT).payloadAs(RoomSnapshot.class).players().size() == 1, "Disconnect clears match");
                System.out.println("PASS TCP match: Player 1 first, fleet rejection reasons/retry, waiting, privacy, authoritative attacks/game over, disconnect. Join notification: " + joinedMs + " ms (localhost).");
            }
        }
    }

    private static void rejectFleet(Peer actor, Peer other, Object payload, String reason) throws IOException {
        Message request = Message.request(MessageType.SUBMIT_FLEET, payload);
        MessageCodec.write(actor.output, request);
        Message response = actor.read(MessageType.ERROR);
        require(response.requestId().equals(request.requestId()) && response.payloadAs(RoomError.class).message().contains(reason),
                "Fleet error includes reason: " + reason);
        // An invalid submission must not broadcast acceptance or start the other client.
        other.send(MessageType.PING, null);
        other.read(MessageType.PONG);
    }

    private static void playerOneStartsWithEitherColor() {
        for (Side side : Side.values()) {
            MatchService match = new MatchService(List.of(new RoomPlayer("first", "First", true, side),
                    new RoomPlayer("second", "Second", true, side.opponent())));
            match.submit("second", fleet());
            require(match.phase() == GamePhase.PLACEMENT, "Player 2 Ready first still waits");
            match.submit("first", fleet());
            require(match.snapshot("first").currentPlayerId().equals("first")
                    && match.snapshot("first").side() == side && match.snapshot("second").side() == side.opponent(),
                    "Player 1 opens with either color, even if Player 2 submitted first");
        }
    }

    private static FleetSubmission fleet() {
        ShipType[] types = ShipType.values();
        return new FleetSubmission(java.util.stream.IntStream.range(0, types.length)
                .mapToObj(i -> new ShipPlacement(types[i], new Coordinate(i, 0), ShipOrientation.HORIZONTAL)).toList());
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream input;
        final DataOutputStream output;
        final String id;
        Peer(int port, String name) throws Exception {
            socket = new Socket("localhost", port); socket.setTcpNoDelay(true); socket.setSoTimeout(5_000);
            input = new DataInputStream(socket.getInputStream()); output = new DataOutputStream(socket.getOutputStream());
            send(MessageType.CONNECT, new ConnectRequest(name));
            id = read(MessageType.CONNECT_OK).payloadAs(ConnectResponse.class).player().clientId();
        }
        void send(MessageType type, Object payload) throws IOException { MessageCodec.write(output, Message.request(type, payload)); }
        Message read(MessageType type) throws IOException {
            Message message;
            do { message = MessageCodec.read(input); }
            while (message != null && message.type() == MessageType.TURN_TIMER);
            require(message != null && message.type() == type, "Expected " + type + ", received " + message);
            return message;
        }
        @Override public void close() throws IOException { socket.close(); }
    }

    private static void require(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
