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
        com.battleship.backend.auth.AccountDatabaseChecks.run();
        playerOneStartsWithEitherColor();
        disconnectionRounds();
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
                a.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.ERROR);
                b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); b.read(MessageType.ERROR);
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(-1, 0))); a.read(MessageType.ERROR);
                Message firstShot = Message.request(MessageType.ATTACK, new AttackRequest(new Coordinate(0, 0)));
                MessageCodec.write(a.output, firstShot);
                sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                require(sa.lastAttack().type() == AttackResultType.HIT && sa.revision() == sb.revision(), "Server resolves hit for both peers");
                checkBoardViews(sa, sb);
                require(sa.currentPlayerId().equals(b.id), "Turn switches on hit");
                MessageCodec.write(a.output, firstShot);
                a.read(MessageType.ERROR);
                b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9)));
                sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                require(sa.lastAttack().type() == AttackResultType.MISS && sa.revision() == 5,
                        "Duplicate frame does not apply another shot; next valid shot is a miss");
                checkBoardViews(sa, sb);
                MessageCodec.write(a.output, firstShot);
                require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("ALREADY_ATTACKED"),
                        "Same shot remains rejected after the turn returns");
                Peer actor = a;
                int firstTarget = 1, secondTarget = 0;
                List<Coordinate> cells = fleet().ships().stream().flatMap(p -> p.ship().occupiedCells().stream()).toList();
                while (sa.phase() != GamePhase.GAME_OVER) {
                    Coordinate target = cells.get(actor == a ? firstTarget++ : secondTarget++);
                    actor.send(MessageType.ATTACK, new AttackRequest(target));
                    sa = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    sb = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    require(sa.revision() == sb.revision() && java.util.Objects.equals(sa.winnerId(), sb.winnerId()), "Peers agree after every shot");
                    checkBoardViews(sa, sb);
                    actor = actor == a ? b : a;
                }
                require(sa.winnerId().equals(a.id), "Player 1 wins complete alternating match");
                a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.ERROR);
                rematchRounds(a, b, code);
                System.out.println("PASS TCP match: placement/privacy, turns/game over, three consecutive games, rematch wait/decline/reset, menu cancellation. Join notification: " + joinedMs + " ms (localhost).");
            }
        }
    }

    private static void disconnectionRounds() throws Exception {
        var database = com.battleship.backend.database.Database.fromEnvironment();
        var accountIds = new java.util.ArrayList<java.util.UUID>();
        try (TcpServer server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            for (String scenario : List.of("leave", "disconnect", "broken-frame", "waiting")) {
                try (Peer a = new Peer(server.port(), "Survivor"); Peer b = new Peer(server.port(), "Leaving")) {
                    a.send(MessageType.CREATE_ROOM, null);
                    String code = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class).roomCode();
                    b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(code)); b.read(MessageType.ROOM_JOINED); a.read(MessageType.PLAYER_JOINED);
                    if (scenario.equals("waiting")) {
                        b.close();
                        require(a.read(MessageType.PLAYER_LEFT).payloadAs(RoomSnapshot.class).players().size() == 1,
                                "Disconnect in waiting updates membership without declaring a winner");
                        a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.ROOM_LEFT);
                        continue;
                    }
                    accountIds.add(a.accountId);
                    a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    a.read(MessageType.START_PLACEMENT); b.read(MessageType.START_PLACEMENT);
                    a.send(MessageType.SUBMIT_FLEET, fleet()); a.read(MessageType.FLEET_ACCEPTED); b.read(MessageType.FLEET_ACCEPTED);
                    b.send(MessageType.SUBMIT_FLEET, fleet()); a.read(MessageType.START_BATTLE); b.read(MessageType.START_BATTLE);
                    if (scenario.equals("leave")) {
                        b.send(MessageType.LEAVE_ROOM, null);
                        require(b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class).winnerId().equals(a.id), "Leaving player receives final defeat before room ack");
                        b.read(MessageType.ROOM_LEFT);
                    } else if (scenario.equals("broken-frame")) {
                        b.output.writeInt(100_000); b.output.flush();
                    } else b.close();
                    MatchSnapshot result = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    require(result.phase() == GamePhase.GAME_OVER && result.winnerId().equals(a.id)
                            && result.endReason() == com.battleship.shared.match.MatchEndReason.PLAYER_LEFT
                            && result.timer().remainingMillis() == 0 && !result.timedOut()
                            && result.enemyBoard().ships().isEmpty(), "Peer departure produces one private final victory: " + scenario);
                    require(!a.read(MessageType.REMATCH_UPDATED).payloadAs(RematchState.class).available(), "Rematch is unavailable after departure");
                    a.read(MessageType.PLAYER_LEFT);
                    a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9)));
                    require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("GAME_FINISHED"), "No shot after forfeit");
                    a.send(MessageType.PING, null); a.read(MessageType.PONG);
                    a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.ROOM_LEFT);
                }
            }
        }
        try (var connection = database.open(); var query = connection.prepareStatement(
                "SELECT m.status, m.end_reason, m.winner_slot, m.total_turn, p.result FROM matches m JOIN match_players p ON p.match_id=m.id WHERE p.user_id=?")) {
            for (var id : accountIds) {
                query.setObject(1, id);
                try (var rows = query.executeQuery()) {
                    require(rows.next() && rows.getString(1).equals("FINISHED") && rows.getString(2).equals("PLAYER_LEFT")
                            && rows.getInt(3) == 1 && rows.getInt(4) == 0 && rows.getString(5).equals("VICTORY"), "Departure persists winner/reason/turns");
                    require(!rows.next(), "One departure cannot save two results");
                }
            }
        }
        System.out.println("PASS TCP departures: leave, socket close, invalid frame, waiting-room cleanup, private final victory, late-shot guard and database.");
    }

    private static void checkBoardViews(MatchSnapshot a, MatchSnapshot b) {
        Board aOwn = a.ownBoard().board(), aEnemy = a.enemyBoard().board();
        Board bOwn = b.ownBoard().board(), bEnemy = b.enemyBoard().board();
        require(aEnemy.attackedCells().equals(bOwn.attackedCells()) && bEnemy.attackedCells().equals(aOwn.attackedCells()),
                "Both clients agree on shot history of each board");
        for (Coordinate shot : aEnemy.attackedCells())
            require(aEnemy.cell(shot).state() == bOwn.cell(shot).state(), "Attacker and defender agree on hit/miss/sunk");
        for (Coordinate shot : bEnemy.attackedCells())
            require(bEnemy.cell(shot).state() == aOwn.cell(shot).state(), "Defender and attacker agree on board state");
        require(aEnemy.ships().stream().allMatch(Ship::isSunk) && bEnemy.ships().stream().allMatch(Ship::isSunk),
                "Only sunk enemy ships are revealed");
    }

    private static void rematchRounds(Peer a, Peer b, String code) throws IOException {
        a.send(MessageType.REMATCH_REQUEST, null);
        RematchState waiting = a.read(MessageType.REMATCH_UPDATED).payloadAs(RematchState.class);
        b.read(MessageType.REMATCH_UPDATED);
        require(waiting.acceptedPlayerIds().equals(List.of(a.id)), "One consent stays at finished match");
        a.send(MessageType.PING, null); a.read(MessageType.PONG);
        b.send(MessageType.REMATCH_DECLINE, null);
        RematchState declined = a.read(MessageType.REMATCH_UPDATED).payloadAs(RematchState.class);
        b.read(MessageType.REMATCH_UPDATED);
        require(declined.declined() && declined.acceptedPlayerIds().isEmpty(), "Decline clears both consents");
        b.send(MessageType.PING, null); b.read(MessageType.PONG);
        for (int round = 0; round < 2; round++) {
            a.send(MessageType.REMATCH_REQUEST, null);
            a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
            b.send(MessageType.REMATCH_REQUEST, null);
            a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
            require(a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class).roomCode().equals(code)
                    && b.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class).players().size() == 2,
                    "Both agree: same room starts placement again");
            a.send(MessageType.SUBMIT_FLEET, fleet());
            MatchSnapshot fresh = a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
            MatchSnapshot other = b.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class);
            require(fresh.phase() == GamePhase.PLACEMENT && fresh.winnerId() == null && fresh.lastAttack() == null
                    && fresh.endReason() == null && fresh.turnNumber() == 0 && fresh.timer().remainingMillis() == 0 && !fresh.timedOut()
                    && fresh.ownBoard().shots().isEmpty() && other.ownBoard().ships().isEmpty()
                    && other.ownBoard().shots().isEmpty() && other.enemyBoard().ships().isEmpty(),
                    "Fresh placement contains no old fleet, shots, winner or deadline");
            b.send(MessageType.SUBMIT_FLEET, fleet());
            fresh = a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
            b.read(MessageType.START_BATTLE);
            require(fresh.turnNumber() == 1 && fresh.timer().remainingMillis() > 59_000, "New battle starts a fresh 60-second turn");
            List<Coordinate> cells = fleet().ships().stream().flatMap(ship -> ship.ship().occupiedCells().stream()).toList();
            for (int shot = 0; shot < cells.size() * 2 - 1; shot++) {
                Peer attacker = shot % 2 == 0 ? a : b;
                attacker.send(MessageType.ATTACK, new AttackRequest(cells.get(shot / 2)));
                fresh = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                MatchSnapshot peer = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                checkBoardViews(fresh, peer);
            }
            require(fresh.phase() == GamePhase.GAME_OVER && fresh.winnerId().equals(a.id), "Consecutive round ends correctly");
        }
        a.send(MessageType.REMATCH_REQUEST, null);
        a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
        b.send(MessageType.LEAVE_ROOM, null); b.read(MessageType.ROOM_LEFT);
        RematchState cancelled = a.read(MessageType.REMATCH_UPDATED).payloadAs(RematchState.class);
        require(!cancelled.available() && cancelled.acceptedPlayerIds().isEmpty(), "Menu leave cancels pending rematch");
        require(a.read(MessageType.PLAYER_LEFT).payloadAs(RoomSnapshot.class).players().size() == 1, "Survivor retains the room");
        a.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.ERROR);
        a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.ROOM_LEFT);
        a.send(MessageType.PING, null); a.read(MessageType.PONG);
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

    static FleetSubmission fleet() {
        ShipType[] types = ShipType.values();
        return new FleetSubmission(java.util.stream.IntStream.range(0, types.length)
                .mapToObj(i -> new ShipPlacement(types[i], new Coordinate(i, 0), ShipOrientation.HORIZONTAL)).toList());
    }

    static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream input;
        final DataOutputStream output;
        final String id;
        final java.util.UUID accountId;
        final String username;
        Peer(int port, String name) throws Exception { this(port, name, null); }
        Peer(int port, String name, String existingUsername) throws Exception {
            socket = new Socket("localhost", port); socket.setTcpNoDelay(true); socket.setSoTimeout(5_000);
            input = new DataInputStream(socket.getInputStream()); output = new DataOutputStream(socket.getOutputStream());
            send(MessageType.CONNECT, new ConnectRequest(name));
            id = read(MessageType.CONNECT_OK).payloadAs(ConnectResponse.class).player().clientId();
            username = existingUsername == null ? "tcp_" + java.util.UUID.randomUUID().toString().substring(0, 12).replace("-", "") : existingUsername;
            String password = "TcpCheckPassword2026!";
            if (existingUsername == null) {
                send(MessageType.REGISTER, new com.battleship.shared.auth.RegisterRequest(username, password, password));
                read(MessageType.REGISTER_OK);
            }
            send(MessageType.LOGIN, new com.battleship.shared.auth.LoginRequest(username, password));
            accountId = read(MessageType.AUTH_SUCCESS).payloadAs(com.battleship.shared.auth.AccountProfile.class).id();
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
