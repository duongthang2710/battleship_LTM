package com.battleship.backend.match;

import com.battleship.backend.database.Database;
import com.battleship.backend.leaderboard.*;
import com.battleship.backend.auth.PasswordHasher;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.leaderboard.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import com.battleship.shared.auth.AuthError;
import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Fixtures exist only in an explicitly named isolated test database, never the application DB. */
public final class LeaderboardChecks {
    private static Database database;
    private static long sequence;
    private static final Map<String, UUID> accounts = new LinkedHashMap<>();
    private static final String HASH = new PasswordHasher().hash("LeaderboardChecks2026!");

    public static void main(String[] args) throws Exception {
        String name = System.getenv("DB_NAME");
        require(name != null && name.matches("battleship_lb_test_[a-z0-9_]+"), "Use an isolated leaderboard test database");
        database = Database.fromEnvironment(); database.migrate();
        try (var c = database.open(); var s = c.createStatement(); var row = s.executeQuery("SELECT current_database(), (SELECT COUNT(*) FROM users)")) {
            require(row.next() && row.getString(1).equals(name) && row.getLong(2) == 0, "Database must be fresh and isolated");
        }
        var repository = new LeaderboardRepository(database);
        require(repository.page(new LeaderboardRequest(LeaderboardSort.WINS, 0)).entries().isEmpty(), "Empty history produces no invented entries");
        player("alpha", "LLLWWWWWWW"); player("beta", "WWWWWWWLLL");
        player("champion", "WWWWWWWWLL"); player("veteran", "WLWLWLWLWLWL"); player("rookie", "W");
        for (int i = 0; i < 7; i++) player("sailor_" + i, "W");
        UUID unknown = user("incomplete"); UUID other = user("other_incomplete");
        fixture(unknown, other, "BATTLE", "PVP", null, null, false, null);
        fixture(unknown, other, "ABORTED", "PVP", "ABORTED", "ABORTED", false, "PLAYER_LEFT");
        fixture(unknown, other, "FINISHED", "PVAI", "VICTORY", "DEFEAT", false, "FLEET_DESTROYED");
        fixture(unknown, other, "FINISHED", "PVP", "VICTORY", null, false, "TURN_TIMEOUT");
        fixture(unknown, other, "FINISHED", "PVP", "VICTORY", "VICTORY", false, "TURN_TIMEOUT");
        fixture(unknown, other, "FINISHED", "PVP", "VICTORY", "DEFEAT", true, "FLEET_DESTROYED");
        UUID ambiguous = user("ambiguous"), opponent = user("ambiguous_opponent");
        Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
        match(ambiguous, opponent, true, "TURN_TIMEOUT", sameTime);
        match(ambiguous, opponent, false, "FLEET_DESTROYED", sameTime);
        List<LeaderboardEntry> wins = all(repository, LeaderboardSort.WINS);
        require(wins.getFirst().username().equals("champion"), "Wins is the default descending order");
        require(index(wins, "alpha") < index(wins, "beta"), "Identical wins/rate/games tie by username");
        require(entry(wins, "alpha").matches() == 10 && entry(wins, "alpha").wins() == 7 && entry(wins, "alpha").losses() == 3
                && Math.abs(entry(wins, "alpha").winRate() - 70) < .00001 && entry(wins, "alpha").currentWinStreak() == 7, "Exact totals, rate and chronological streak");
        require(entry(wins, "beta").currentWinStreak() == 0 && entry(wins, "ambiguous").currentWinStreak() == null,
                "Loss resets streak; ambiguous tied history is not presented as a precise streak");
        require(wins.stream().noneMatch(e -> e.username().contains("incomplete")), "Unfinished, aborted, AI, missing or contradictory results are excluded");
        var rates = all(repository, LeaderboardSort.WIN_RATE);
        require(rates.getFirst().username().equals("champion") && rates.stream().allMatch(e -> e.matches() >= 10)
                && rates.stream().noneMatch(e -> e.username().equals("rookie")), "One perfect game cannot top win-rate rankings");
        var games = all(repository, LeaderboardSort.MATCHES);
        require(games.getFirst().matches() == 12, "Sort by total completed matches");
        require(repository.page(new LeaderboardRequest(LeaderboardSort.WINS, 0)).equals(repository.page(new LeaderboardRequest(LeaderboardSort.WINS, 0))), "Stable ordering across unchanged reads");
        auditAlpha();
        try (var history = new MatchRepository(database)) {
            var service = new LeaderboardService(repository, history);
            UUID id = UUID.randomUUID(); Instant now = Instant.now();
            history.start(id, now, List.of(new MatchRepository.Participant(accounts.get("alpha"), com.battleship.shared.game.Side.WHITE, 1),
                    new MatchRepository.Participant(accounts.get("champion"), com.battleship.shared.game.Side.BLACK, 2)));
            history.finish(id, now, "TURN_TIMEOUT", 1, 0, List.of(new MatchRepository.Statistics(accounts.get("alpha"), "VICTORY", 0, 0, 0),
                    new MatchRepository.Statistics(accounts.get("champion"), "DEFEAT", 0, 0, 0)));
            var refreshed = service.page(new LeaderboardRequest(LeaderboardSort.WINS, 0));
            require(entry(refreshed.entries(), "alpha").wins() == 8 && entry(refreshed.entries(), "alpha").matches() == 11,
                    "Immediate read waits for queued result commit; timeout counts exactly once");
            history.finish(id, now, "TURN_TIMEOUT", 1, 0, List.of());
            require(entry(service.page(new LeaderboardRequest(LeaderboardSort.WINS, 0)).entries(), "alpha").wins() == 8, "Repeated finish does not double-count a match");
        }
        tcp();
        System.out.println("PASS leaderboard PostgreSQL/TCP: empty, pagination, three sorts/ties, min 10 games, streak/ambiguity, excluded records, SQL audit, new committed result, authentication/error paths.");
    }

    private static void tcp() throws Exception {
        try (TcpServer server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Socket s = new Socket("localhost", server.port())) {
                s.setSoTimeout(5000);
                var in = new DataInputStream(s.getInputStream()); var out = new DataOutputStream(s.getOutputStream());
                MessageCodec.write(out, Message.request(MessageType.CONNECT, new ConnectRequest("Guest"))); MessageCodec.read(in);
                MessageCodec.write(out, Message.request(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(LeaderboardSort.WINS, 0)));
                require(MessageCodec.read(in).payloadAs(AuthError.class).code().equals("AUTH_REQUIRED"), "Anonymous leaderboard requests are rejected");
            }
            try (var a = new TcpMatchChecks.Peer(server.port(), "A"); var b = new TcpMatchChecks.Peer(server.port(), "B")) {
                a.send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(null, -1)); a.read(MessageType.LEADERBOARD_ERROR);
                a.send(MessageType.LEADERBOARD_REQUEST, com.google.gson.JsonParser.parseString("{\"sort\":\"WINS; DROP TABLE users\",\"page\":0}"));
                a.read(MessageType.LEADERBOARD_ERROR);
                a.send(MessageType.LEADERBOARD_REQUEST, com.google.gson.JsonParser.parseString("{\"sort\":\"WINS\",\"page\":0,\"pageSize\":1000000,\"wins\":9999}"));
                var page = a.read(MessageType.LEADERBOARD_RESULT).payloadAs(LeaderboardPage.class);
                require(page.entries().size() == 10 && page.pageSize() == 10, "Client cannot inflate result limit or claim wins");
                a.send(MessageType.CREATE_ROOM, null); String code = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class).roomCode();
                b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(code)); b.read(MessageType.ROOM_JOINED); a.read(MessageType.PLAYER_JOINED);
                a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                a.read(MessageType.START_PLACEMENT); b.read(MessageType.START_PLACEMENT);
                a.send(MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet()); a.read(MessageType.FLEET_ACCEPTED); b.read(MessageType.FLEET_ACCEPTED);
                b.send(MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet()); a.read(MessageType.START_BATTLE); b.read(MessageType.START_BATTLE);
                a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.MATCH_UPDATED); a.read(MessageType.ROOM_LEFT);
                b.read(MessageType.MATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED); b.read(MessageType.PLAYER_LEFT);
                boolean found = false;
                for (int i = 0; i < 10; i++) {
                    b.send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(LeaderboardSort.WINS, i));
                    page = b.read(MessageType.LEADERBOARD_RESULT).payloadAs(LeaderboardPage.class);
                    for (var e : page.entries()) if (e.username().equals(b.username)) { require(e.matches() == 1 && e.wins() == 1, "Real match appears immediately"); found = true; }
                    if ((long) (i + 1) * page.pageSize() >= page.totalPlayers()) break;
                }
                require(found, "Completed real TCP match is visible in rankings");
                try (var c = database.open(); var statement = c.createStatement()) {
                    statement.execute("ALTER TABLE match_players RENAME TO leaderboard_test_unavailable");
                    try {
                        b.send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(LeaderboardSort.WINS, 0));
                        var error = b.read(MessageType.LEADERBOARD_ERROR).payloadAs(LeaderboardError.class);
                        require(!error.message().contains("SQL") && !error.message().contains("match_players"), "Database error is safe and actionable");
                    } finally { statement.execute("ALTER TABLE leaderboard_test_unavailable RENAME TO match_players"); }
                }
            }
        }
    }
    private static List<LeaderboardEntry> all(LeaderboardRepository repository, LeaderboardSort sort) throws Exception {
        var result = new ArrayList<LeaderboardEntry>(); long expected = -1;
        for (int i = 0; i < 10; i++) {
            var page = repository.page(new LeaderboardRequest(sort, i)); expected = page.totalPlayers();
            require(page.entries().size() <= 10, "Bounded page"); result.addAll(page.entries());
            if (result.size() >= expected) break;
        }
        require(result.size() == expected && result.stream().map(LeaderboardEntry::username).distinct().count() == expected, "Pages cover every player exactly once");
        for (int i = 0; i < result.size(); i++) require(result.get(i).rank() == i + 1, "Global ranks continue across pages");
        return result;
    }
    private static UUID user(String name) throws Exception {
        UUID id = UUID.randomUUID(); accounts.put(name, id);
        try (var c = database.open(); var s = c.prepareStatement("INSERT INTO users(id,username,display_name,password_hash) VALUES (?,?,?,?)")) {
            s.setObject(1,id); s.setString(2,name); s.setString(3,name); s.setString(4,HASH); s.executeUpdate();
        }
        return id;
    }
    private static void player(String name, String outcomes) throws Exception {
        UUID id = user(name), opponent = user(name + "_opponent");
        for (char outcome : outcomes.toCharArray()) match(id, opponent, outcome == 'W', "FLEET_DESTROYED", null);
    }
    private static void match(UUID first, UUID second, boolean win, String reason, Instant instant) throws Exception {
        fixture(first, second, "FINISHED", "PVP", win ? "VICTORY" : "DEFEAT", win ? "DEFEAT" : "VICTORY", false, reason, instant);
    }
    private static void fixture(UUID a, UUID b, String status, String mode, String ar, String br, boolean missing, String reason) throws Exception {
        fixture(a,b,status,mode,ar,br,missing,reason,null);
    }
    private static void fixture(UUID a, UUID b, String status, String mode, String ar, String br, boolean missing, String reason, Instant instant) throws Exception {
        Instant end = instant == null ? Instant.parse("2026-01-01T00:00:00Z").plusSeconds(++sequence) : instant;
        UUID id = UUID.randomUUID();
        try (var c = database.open(); var m = c.prepareStatement("INSERT INTO matches(id,started_at,ended_at,status,end_reason,mode,winner_slot) VALUES (?,?,?,?,?,?,?)");
             var p = c.prepareStatement("INSERT INTO match_players(match_id,user_id,player_slot,side,result) VALUES (?,?,?,?,?)")) {
            m.setObject(1,id); m.setTimestamp(2,Timestamp.from(end.minusSeconds(1))); m.setTimestamp(3,status.equals("BATTLE") ? null : Timestamp.from(end));
            m.setString(4,status); m.setString(5,reason); m.setString(6,mode); m.setObject(7,"VICTORY".equals(ar) ? Integer.valueOf(1) : "VICTORY".equals(br) ? Integer.valueOf(2) : null); m.executeUpdate();
            for (int slot=1; slot<=(missing ? 1 : 2); slot++) {
                p.setObject(1,id); p.setObject(2,slot==1 ? a : b); p.setInt(3,slot); p.setString(4,slot==1 ? "WHITE" : "BLACK"); p.setString(5,slot==1 ? ar : br); p.executeUpdate();
            }
        }
    }
    private static void auditAlpha() throws Exception {
        try (var c = database.open(); var s = c.prepareStatement("SELECT COUNT(*), COUNT(*) FILTER (WHERE p.result='VICTORY') FROM match_players p JOIN matches m ON m.id=p.match_id WHERE p.user_id=? AND m.status='FINISHED' AND m.mode='PVP'")) {
            s.setObject(1,accounts.get("alpha")); try (var r=s.executeQuery()) { require(r.next() && r.getLong(1)==10 && r.getLong(2)==7, "Counts agree with a direct PostgreSQL audit"); }
        }
    }
    private static LeaderboardEntry entry(List<LeaderboardEntry> list, String name) { return list.stream().filter(e -> e.username().equals(name)).findFirst().orElseThrow(); }
    private static int index(List<LeaderboardEntry> list, String name) { return list.indexOf(entry(list,name)); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
