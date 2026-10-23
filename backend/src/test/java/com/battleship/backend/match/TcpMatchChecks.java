package com.battleship.backend.match;

import com.battleship.backend.server.TcpServer;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.net.Socket;
import java.util.List;

/** One real TCP match checks authority, privacy, first turn and ordered notifications. */
public final class TcpMatchChecks {
    public static void main(String[] args) throws Exception {
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
                Peer white = room.players().get(0).side() == Side.WHITE ? a : b;
                Peer black = white == a ? b : a;
                a.send(MessageType.SUBMIT_FLEET, new FleetSubmission(List.of()));
                a.read(MessageType.ERROR);
                a.send(MessageType.SUBMIT_FLEET, fleet());
                MatchSnapshot aWaiting = a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
                MatchSnapshot bWaiting = b.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
                require(aWaiting.fleetSubmitted() && !bWaiting.fleetSubmitted() && bWaiting.enemyBoard().ships().isEmpty(), "Fleet acknowledgement is private");
                a.send(MessageType.SUBMIT_FLEET, fleet()); a.read(MessageType.ERROR);
                b.send(MessageType.SUBMIT_FLEET, fleet());
                MatchSnapshot sa = a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
                MatchSnapshot sb = b.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
                require(sa.revision() == sb.revision() && sa.enemyBoard().ships().isEmpty(), "Same revision; hidden enemy fleet");
                require(sa.currentPlayerId().equals(white.id), "White starts regardless of room owner");
                black.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); black.read(MessageType.ERROR);
                white.send(MessageType.ATTACK, new AttackRequest(new Coordinate(-1, 0))); white.read(MessageType.ERROR);
                white.send(MessageType.ATTACK, new AttackRequest(new Coordinate(0, 0)));
                sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                require(sa.lastAttack().type() == AttackResultType.HIT && sa.revision() == sb.revision(), "Server resolves hit for both peers");
                require(sa.currentPlayerId().equals(black.id), "Turn switches on hit");
                Peer actor = black;
                int whiteTarget = 1, blackTarget = 0;
                List<Coordinate> cells = fleet().ships().stream().flatMap(p -> p.ship().occupiedCells().stream()).toList();
                while (sa.phase() != GamePhase.GAME_OVER) {
                    Coordinate target = cells.get(actor == white ? whiteTarget++ : blackTarget++);
                    actor.send(MessageType.ATTACK, new AttackRequest(target));
                    sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    require(sa.revision() == sb.revision() && java.util.Objects.equals(sa.winnerId(), sb.winnerId()), "Peers agree after every shot");
                    actor = actor == white ? black : white;
                }
                require(sa.winnerId().equals(white.id), "White wins complete alternating match");
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.ERROR);
                b.close();
                require(a.read(MessageType.PLAYER_LEFT).payloadAs(RoomSnapshot.class).players().size() == 1, "Disconnect clears match");
                System.out.println("PASS TCP match: random sides, white first, fleet validation/privacy, authoritative attacks/game over, disconnect. Join notification: " + joinedMs + " ms (localhost).");
            }
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
            Message message = MessageCodec.read(input);
            require(message != null && message.type() == type, "Expected " + type + ", received " + message);
            return message;
        }
        @Override public void close() throws IOException { socket.close(); }
    }

    private static void require(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
