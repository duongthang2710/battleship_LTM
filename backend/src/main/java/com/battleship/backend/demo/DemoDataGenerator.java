package com.battleship.backend.demo;

import com.battleship.backend.server.TcpServer;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.auth.LoginRequest;
import com.battleship.shared.auth.RegisterRequest;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.ShipType;
import com.battleship.shared.leaderboard.LeaderboardEntry;
import com.battleship.shared.leaderboard.LeaderboardPage;
import com.battleship.shared.leaderboard.LeaderboardRequest;
import com.battleship.shared.leaderboard.LeaderboardSort;
import com.battleship.shared.match.AttackRequest;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.shared.match.ShipPlacement;
import com.battleship.shared.match.TurnTimer;
import com.battleship.shared.protocol.ConnectRequest;
import com.battleship.shared.protocol.ConnectResponse;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageCodec;
import com.battleship.shared.protocol.MessageType;
import com.battleship.shared.room.JoinRoomRequest;
import com.battleship.shared.room.RoomSnapshot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates reusable demo accounts and records complete PvP matches through the real TCP protocol.
 * No rows are inserted directly: authentication, rooms, fleet validation, turns and match history
 * all go through the same server code as the desktop client.
 */
public final class DemoDataGenerator {
    private static final String PASSWORD = "DemoBattle2026!";
    private static final List<String> PLAYERS = List.of(
            "MinhNguyen", "LanAnh", "QuangHuy", "ThuTrang", "DucLong", "NgocMai");

    // Each pair plays twice. The first leg follows seed order; the return leg adds realistic upsets.
    private static final Set<String> RETURN_LEG_FIRST_PLAYER_WINS = Set.of(
            "0-1", "0-2", "0-3", "1-2", "1-4", "1-5", "2-3", "2-4", "2-5", "3-4", "4-5");

    private DemoDataGenerator() {}

    public static void main(String[] args) throws Exception {
        int sets = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        if (sets < 1 || sets > 10) throw new IllegalArgumentException("Demo sets must be between 1 and 10.");
        quietServerLogs();

        String host = args.length > 1 && !args[1].isBlank() ? args[1] : null;
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 2209;
        boolean tls = args.length > 3 && Boolean.parseBoolean(args[3]);
        if (host == null) {
            try (TcpServer server = new TcpServer(0)) {
                Thread.ofVirtual().name("demo-data-server").start(() -> {
                    try { server.start(); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                });
                generate(new ConnectionTarget("localhost", server.port(), false), sets);
            }
        } else {
            generate(new ConnectionTarget(host, port, tls), sets);
        }
    }

    private static void generate(ConnectionTarget target, int sets) throws Exception {
        System.out.printf("Target: %s:%d (%s)%n", target.host(), target.port(), target.tls() ? "TLS" : "plain TCP");
        int total = sets * 30;
        int completed = 0;
        for (int set = 0; set < sets; set++) {
            for (int leg = 0; leg < 2; leg++) {
                for (int first = 0; first < PLAYERS.size(); first++) {
                    for (int second = first + 1; second < PLAYERS.size(); second++) {
                        boolean firstWins = leg == 0
                                || RETURN_LEG_FIRST_PLAYER_WINS.contains(first + "-" + second);
                        String winner = PLAYERS.get(firstWins ? first : second);
                        playMatch(target, PLAYERS.get(first), PLAYERS.get(second), winner,
                                2209L + set * 10_000L + leg * 1_000L + first * 100L + second);
                        completed++;
                        System.out.printf("[%02d/%02d] %-12s vs %-12s -> %s%n",
                                completed, total, PLAYERS.get(first), PLAYERS.get(second), winner);
                    }
                }
            }
        }
        printLeaderboard(target);
    }

    private static void playMatch(ConnectionTarget target, String firstName, String secondName, String winnerName, long seed)
            throws Exception {
        try (DemoPeer first = new DemoPeer(target, firstName); DemoPeer second = new DemoPeer(target, secondName)) {
            first.send(MessageType.CREATE_ROOM, null);
            String roomCode = first.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class).roomCode();
            second.send(MessageType.JOIN_ROOM, new JoinRoomRequest(roomCode));
            second.read(MessageType.ROOM_JOINED);
            first.read(MessageType.PLAYER_JOINED);

            first.send(MessageType.READY, null);
            first.read(MessageType.ROOM_UPDATED);
            second.read(MessageType.ROOM_UPDATED);
            second.send(MessageType.READY, null);
            first.read(MessageType.ROOM_UPDATED);
            second.read(MessageType.ROOM_UPDATED);
            first.read(MessageType.START_PLACEMENT);
            second.read(MessageType.START_PLACEMENT);

            FleetSubmission fleet = fleet();
            first.send(MessageType.SUBMIT_FLEET, fleet);
            first.read(MessageType.FLEET_ACCEPTED);
            second.read(MessageType.FLEET_ACCEPTED);
            second.send(MessageType.SUBMIT_FLEET, fleet);
            MatchSnapshot firstView = first.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
            second.read(MessageType.START_BATTLE);
            awaitBattleStart(first, second);

            DemoPeer winner = firstName.equals(winnerName) ? first : second;
            List<Coordinate> winnerShots = winnerShots(fleet, seed);
            List<Coordinate> loserShots = loserShots(fleet, seed + 1);
            int winnerIndex = 0;
            int loserIndex = 0;
            while (firstView.phase() == GamePhase.BATTLE) {
                DemoPeer actor = firstView.currentPlayerId().equals(first.id) ? first : second;
                Coordinate shot;
                if (actor == winner) {
                    shot = winnerShots.get(winnerIndex++);
                } else {
                    shot = loserShots.get(loserIndex++);
                }
                actor.send(MessageType.ATTACK, new AttackRequest(shot));
                firstView = first.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                second.read(MessageType.MATCH_UPDATED);
            }
            if (!winner.id.equals(firstView.winnerId()))
                throw new IllegalStateException("Unexpected winner in generated match.");

            first.send(MessageType.LEAVE_ROOM, null);
            first.read(MessageType.ROOM_LEFT);
            second.send(MessageType.LEAVE_ROOM, null);
            second.read(MessageType.ROOM_LEFT);
            first.logout();
            second.logout();
        }
    }

    private static FleetSubmission fleet() {
        ShipType[] types = ShipType.values();
        return new FleetSubmission(java.util.stream.IntStream.range(0, types.length)
                .mapToObj(row -> new ShipPlacement(types[row], new Coordinate(row, 0), ShipOrientation.HORIZONTAL))
                .toList());
    }

    private static List<Coordinate> winnerShots(FleetSubmission fleet, long seed) {
        Random random = new Random(seed);
        List<Coordinate> hits = occupiedCells(fleet);
        List<Coordinate> misses = missCells(fleet);
        Collections.shuffle(hits, random);
        Collections.shuffle(misses, random);
        List<Coordinate> shots = new ArrayList<>();
        int miss = 0;
        for (int hit = 0; hit < hits.size(); hit++) {
            if (hit % 4 == 0) shots.add(misses.get(miss++));
            shots.add(hits.get(hit));
        }
        return shots;
    }

    private static List<Coordinate> loserShots(FleetSubmission fleet, long seed) {
        Random random = new Random(seed);
        List<Coordinate> hits = occupiedCells(fleet);
        List<Coordinate> misses = missCells(fleet);
        Collections.shuffle(hits, random);
        Collections.shuffle(misses, random);
        List<Coordinate> shots = new ArrayList<>();
        int hit = 0;
        int miss = 0;
        while (shots.size() < 40) {
            if (shots.size() % 2 == 0 && hit < 10) shots.add(hits.get(hit++));
            else shots.add(misses.get(miss++));
        }
        return shots;
    }

    private static List<Coordinate> occupiedCells(FleetSubmission fleet) {
        return new ArrayList<>(fleet.ships().stream()
                .flatMap(placement -> placement.ship().occupiedCells().stream()).toList());
    }

    private static List<Coordinate> missCells(FleetSubmission fleet) {
        Set<Coordinate> occupied = new HashSet<>(occupiedCells(fleet));
        List<Coordinate> misses = new ArrayList<>();
        for (int row = 0; row < 10; row++) for (int column = 0; column < 10; column++) {
            Coordinate coordinate = new Coordinate(row, column);
            if (!occupied.contains(coordinate)) misses.add(coordinate);
        }
        return misses;
    }

    private static void awaitBattleStart(DemoPeer... peers) throws IOException {
        for (DemoPeer peer : peers) {
            TurnTimer timer;
            do { timer = peer.read(MessageType.TURN_TIMER).payloadAs(TurnTimer.class); }
            while (timer.startsInMillis() > 0);
        }
    }

    private static void printLeaderboard(ConnectionTarget target) throws Exception {
        System.out.println("\nDemo leaderboard (Wins):");
        try (DemoPeer viewer = new DemoPeer(target, PLAYERS.getFirst())) {
            Set<String> demoUsernames = PLAYERS.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
            List<LeaderboardEntry> entries = new ArrayList<>();
            for (int pageNumber = 0; pageNumber < 100; pageNumber++) {
                viewer.send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(LeaderboardSort.WINS, pageNumber));
                LeaderboardPage page = viewer.read(MessageType.LEADERBOARD_RESULT).payloadAs(LeaderboardPage.class);
                page.entries().stream().filter(entry -> demoUsernames.contains(entry.username())).forEach(entries::add);
                if ((long) (pageNumber + 1) * page.pageSize() >= page.totalPlayers()) break;
            }
            entries.forEach(entry -> System.out.printf("#%-3d %-12s %2d matches  %2dW-%2dL  %5.1f%%%n",
                    entry.rank(), entry.displayName(), entry.matches(), entry.wins(), entry.losses(), entry.winRate()));
            viewer.logout();
        }
    }

    private static void quietServerLogs() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.WARNING);
        for (Handler handler : root.getHandlers()) handler.setLevel(Level.WARNING);
    }

    private record ConnectionTarget(String host, int port, boolean tls) {
        private ConnectionTarget {
            if (host == null || host.isBlank()) throw new IllegalArgumentException("Demo server host is required.");
            if (port < 1 || port > 65_535) throw new IllegalArgumentException("Invalid demo server port.");
        }
    }

    private static final class DemoPeer implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final String id;

        private DemoPeer(ConnectionTarget target, String displayName) throws Exception {
            socket = connect(target);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            send(MessageType.CONNECT, new ConnectRequest(displayName));
            id = read(MessageType.CONNECT_OK).payloadAs(ConnectResponse.class).player().clientId();

            send(MessageType.REGISTER, new RegisterRequest(displayName, PASSWORD, PASSWORD));
            Message registration = readAny();
            if (registration.type() != MessageType.REGISTER_OK && registration.type() != MessageType.AUTH_FAILED)
                throw new IllegalStateException("Unexpected registration response: " + registration.type());
            send(MessageType.LOGIN, new LoginRequest(displayName, PASSWORD));
            Message login = read(MessageType.AUTH_SUCCESS);
            AccountProfile profile = login.payloadAs(AccountProfile.class);
            if (!profile.username().equals(displayName.toLowerCase()))
                throw new IllegalStateException("Authenticated the wrong demo account.");
        }

        private static Socket connect(ConnectionTarget target) throws IOException {
            if (!target.tls()) return new Socket(target.host(), target.port());
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(target.host(), target.port());
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            return socket;
        }

        private void send(MessageType type, Object payload) throws IOException {
            MessageCodec.write(output, Message.request(type, payload));
        }

        private Message read(MessageType expected) throws IOException {
            Message message;
            do { message = readAny(); }
            while (background(message.type(), expected));
            if (message.type() != expected)
                throw new IllegalStateException("Expected " + expected + ", received " + message.type());
            return message;
        }

        private Message readAny() throws IOException {
            Message message = MessageCodec.read(input);
            if (message == null) throw new IOException("Server closed the demo connection.");
            return message;
        }

        private static boolean background(MessageType actual, MessageType expected) {
            return actual != expected && (actual == MessageType.TURN_TIMER
                    || actual == MessageType.CHAT_CONTEXT
                    || actual == MessageType.REMATCH_UPDATED
                    || actual == MessageType.PLAYER_LEFT
                    || actual == MessageType.FRIENDS_CHANGED);
        }

        private void logout() throws IOException {
            send(MessageType.LOGOUT, null);
            read(MessageType.LOGOUT_OK);
        }

        @Override public void close() throws IOException { socket.close(); }
    }
}
