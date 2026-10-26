package com.battleship.backend.match;

import com.battleship.backend.auth.*;
import com.battleship.backend.database.Database;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.auth.*;
import com.battleship.shared.friends.*;
import com.battleship.shared.protocol.*;
import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static com.battleship.backend.match.TcpMatchChecks.Peer;

/** Real PostgreSQL and TCP; fixtures are restricted to an isolated test database. */
public final class FriendsChecks {
    private static final Map<Peer, Integer> notices = new ConcurrentHashMap<>();
    private static Database database;

    public static void main(String[] args) throws Exception {
        String name = System.getenv("DB_NAME");
        require(name != null && name.matches("battleship_friends_test_[a-z0-9_]+"), "Use an isolated friends test database");
        database = Database.fromEnvironment(); database.migrate(); database.migrate();
        try (var c = database.open(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM users")) {
            require(r.next() && r.getLong(1) == 0, "Fresh database required");
        }
        String aName, bName, cName;
        UUID aId, bId;
        try (TcpServer server = start(); Peer a = new Peer(server.port(), "A"); Peer b = new Peer(server.port(), "B"); Peer c = new Peer(server.port(), "C")) {
            aName = a.username; bName = b.username; cName = c.username; aId = a.accountId; bId = b.accountId;
            anonymous(server.port());
            require(list(a, FriendsAction.FRIENDS, "", 0).entries().isEmpty(), "Empty friends list");
            var exact = list(a, FriendsAction.SEARCH, b.username.toUpperCase(Locale.ROOT), 0);
            require(exact.total() == 1 && exact.entries().getFirst().userId().equals(b.accountId), "Case insensitive exact username search");
            String hash = new PasswordHasher().hash("FriendsFixturePassword2026!");
            for (int i = 0; i < 11; i++) new UserRepository(database).create("search_sailor_" + i, hash, "Sailor " + i);
            var first = list(a, FriendsAction.SEARCH, "search_sailor_", 0);
            var second = list(a, FriendsAction.SEARCH, "search_sailor_", 1);
            require(first.total() == 11 && first.entries().size() == 8 && second.entries().size() == 3, "Bounded search pagination");
            require(first.entries().stream().noneMatch(e -> second.entries().contains(e)), "No duplicate page entries");
            rejected(a, FriendsAction.SEND, a.accountId, "yourself");
            rejected(a, FriendsAction.SEND, UUID.randomUUID(), "no longer exists");
            change(a, FriendsAction.SEND, b.accountId);
            awaitNotice(b);
            var received = list(b, FriendsAction.RECEIVED, "", 0);
            require(received.total() == 1 && received.entries().getFirst().requestedAt() != null
                    && received.entries().getFirst().username().equals(a.username), "Recipient receives invitation and timestamp");
            rejected(a, FriendsAction.SEND, b.accountId, "already sent");
            rejected(b, FriendsAction.SEND, a.accountId, "Received");
            rejected(a, FriendsAction.ACCEPT, b.accountId, "recipient");
            rejected(b, FriendsAction.CANCEL, a.accountId, "sender");
            rejected(c, FriendsAction.ACCEPT, a.accountId, "no longer exists");
            change(b, FriendsAction.DECLINE, a.accountId);
            require(list(a, FriendsAction.SENT, "", 0).entries().getFirst().relationship().equals("DECLINED"), "Declined status persists");
            change(a, FriendsAction.SEND, b.accountId); change(a, FriendsAction.CANCEL, b.accountId);
            require(list(b, FriendsAction.RECEIVED, "", 0).entries().isEmpty()
                    && list(a, FriendsAction.SENT, "", 0).entries().getFirst().relationship().equals("CANCELLED"), "Cancel clears received and retains sender status");
            change(a, FriendsAction.SEND, b.accountId); change(b, FriendsAction.ACCEPT, a.accountId);
            require(friend(a, b.accountId).online() && friend(b, a.accountId).online(), "Symmetric friendship and online status");
            rejected(a, FriendsAction.SEND, b.accountId, "already friends");
            change(a, FriendsAction.REMOVE, b.accountId);
            require(list(a, FriendsAction.FRIENDS, "", 0).total() == 0 && list(b, FriendsAction.FRIENDS, "", 0).total() == 0, "Remove updates both sides");
            rejected(b, FriendsAction.REMOVE, a.accountId, "not on");
            // Two independent client handlers race on the same unordered pair.
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var gate = new CyclicBarrier(2);
                var ra = executor.submit(() -> { gate.await(); return request(a, new FriendsRequest(FriendsAction.SEND, b.accountId, null, 0)); });
                var rb = executor.submit(() -> { gate.await(); return request(b, new FriendsRequest(FriendsAction.SEND, a.accountId, null, 0)); });
                var ma = ra.get(15, TimeUnit.SECONDS); var mb = rb.get(15, TimeUnit.SECONDS);
                require((ma.type() == MessageType.FRIENDS_RESULT) != (mb.type() == MessageType.FRIENDS_RESULT), "Exactly one simultaneous crossed invitation succeeds");
            }
            var search = list(a, FriendsAction.SEARCH, b.username, 0).entries().getFirst();
            if (search.relationship().equals("INCOMING")) change(a, FriendsAction.ACCEPT, b.accountId);
            else change(b, FriendsAction.ACCEPT, a.accountId);
            change(a, FriendsAction.SEND, c.accountId);
            // Caller-supplied actor IDs cannot accept an invitation on someone else's behalf.
            var forged = Message.request(MessageType.FRIENDS_REQUEST, new FriendsRequest(FriendsAction.ACCEPT, c.accountId, null, 0));
            forged.payload().addProperty("actorId", c.accountId.toString());
            require(exchange(a, forged).type() == MessageType.FRIENDS_ERROR, "Authenticated connection identity wins over forged actor ID");
            var invalid = Message.request(MessageType.FRIENDS_REQUEST, new FriendsRequest(FriendsAction.SEARCH, null, "'; DROP TABLE users;--", 0));
            require(exchange(a, invalid).type() == MessageType.FRIENDS_ERROR, "Unsafe query rejected");
            require(request(a, new FriendsRequest(FriendsAction.FRIENDS, null, "", -1)).type() == MessageType.FRIENDS_ERROR, "Invalid page rejected");
            require(!Message.request(MessageType.FRIENDS_RESULT, list(a, FriendsAction.SEARCH, b.username, 0)).payload().toString().contains("password"), "No credentials in public DTO");
            logout(b); awaitNotice(a);
            require(!friend(a, b.accountId).online(), "Logout publishes offline status");
            login(b); awaitNotice(a);
            require(friend(a, b.accountId).online(), "Relogin publishes online status without losing friends");
            constraints(a.accountId);
            databaseFailure(a);
            System.out.println("PASS friends TCP/database: search/pages, invitations/notifications, accept/decline/cancel/remove, permissions, crossed concurrency, presence, constraints, database error recovery.");
        }
        // A fresh server instance uses the same persisted database and existing credentials.
        try (TcpServer server = start(); Peer a = new Peer(server.port(), "A", aName); Peer b = new Peer(server.port(), "B", bName); Peer c = new Peer(server.port(), "C", cName)) {
            require(a.accountId.equals(aId) && b.accountId.equals(bId), "Same accounts after server restart");
            require(friend(a, bId).online() && friend(b, aId).online(), "Friendship survives server restart");
            require(list(c, FriendsAction.RECEIVED, "", 0).entries().stream().anyMatch(e -> e.userId().equals(aId)), "Pending invitation survives restart");
            try (var connection = database.open(); var s = connection.createStatement(); var r = s.executeQuery("SELECT count(*), count(DISTINCT (user_low,user_high)) FROM friendships")) {
                require(r.next() && r.getLong(1) == 2 && r.getLong(2) == 2, "Exactly one persisted relationship per pair");
            }
            System.out.println("PASS persistence: logout/login, server restart, pending invitation and canonical pair SQL audit.");
        }
    }
    private static TcpServer start() throws Exception {
        var server = new TcpServer(0);
        Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
        return server;
    }
    private static Message next(Peer peer) throws IOException {
        var message = MessageCodec.read(peer.input);
        require(message != null, "Server closed connection");
        if (message.type() == MessageType.FRIENDS_CHANGED) notices.merge(peer, 1, Integer::sum);
        return message;
    }
    private static Message exchange(Peer peer, Message message) throws IOException {
        MessageCodec.write(peer.output, message);
        for (;;) {
            var reply = next(peer);
            if (reply.requestId().equals(message.requestId())) return reply;
            require(reply.type() == MessageType.FRIENDS_CHANGED || reply.type() == MessageType.TURN_TIMER, "Unexpected TCP event");
        }
    }
    private static Message request(Peer peer, FriendsRequest request) throws IOException { return exchange(peer, Message.request(MessageType.FRIENDS_REQUEST, request)); }
    private static FriendsResult list(Peer peer, FriendsAction action, String query, int page) throws IOException {
        var message = request(peer, new FriendsRequest(action, null, query, page));
        require(message.type() == MessageType.FRIENDS_RESULT, "List succeeded: " + message);
        return message.payloadAs(FriendsResult.class);
    }
    private static FriendEntry friend(Peer peer, UUID id) throws IOException {
        return list(peer, FriendsAction.FRIENDS, "", 0).entries().stream().filter(e -> e.userId().equals(id)).findFirst().orElseThrow();
    }
    private static void change(Peer peer, FriendsAction action, UUID target) throws IOException {
        require(request(peer, new FriendsRequest(action, target, null, 0)).type() == MessageType.FRIENDS_RESULT, "Mutation " + action + " succeeded");
    }
    private static void rejected(Peer peer, FriendsAction action, UUID target, String reason) throws IOException {
        var reply = request(peer, new FriendsRequest(action, target, null, 0));
        require(reply.type() == MessageType.FRIENDS_ERROR && reply.payloadAs(FriendsNotice.class).message().contains(reason), "Rejected " + action + ": " + reply);
    }
    private static void awaitNotice(Peer peer) throws IOException {
        if (notices.getOrDefault(peer, 0) > 0) { notices.put(peer, 0); return; }
        while (next(peer).type() != MessageType.FRIENDS_CHANGED) { }
        notices.put(peer, 0);
    }
    private static void logout(Peer peer) throws IOException { require(exchange(peer, Message.request(MessageType.LOGOUT, null)).type() == MessageType.LOGOUT_OK, "Logout"); }
    private static void login(Peer peer) throws IOException { require(exchange(peer, Message.request(MessageType.LOGIN, new LoginRequest(peer.username, "TcpCheckPassword2026!"))).type() == MessageType.AUTH_SUCCESS, "Login"); }
    private static void anonymous(int port) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
            MessageCodec.write(out, Message.request(MessageType.CONNECT, new ConnectRequest("Guest"))); MessageCodec.read(in);
            MessageCodec.write(out, Message.request(MessageType.FRIENDS_REQUEST, new FriendsRequest(FriendsAction.FRIENDS, null, "", 0)));
            require(MessageCodec.read(in).type() == MessageType.AUTH_FAILED, "Anonymous friends access denied");
        }
    }
    private static void constraints(UUID account) throws Exception {
        try (var c = database.open(); var s = c.createStatement()) {
            try { s.executeUpdate("INSERT INTO friendships SELECT * FROM friendships LIMIT 1"); throw new AssertionError("Duplicate pair accepted"); }
            catch (SQLException e) { require(e.getSQLState().equals("23505"), "Canonical pair unique constraint"); }
            try (var p = c.prepareStatement("INSERT INTO friendships(user_low,user_high,requester_id,status) VALUES(?,?,?,'PENDING')")) {
                for (int i = 1; i <= 3; i++) p.setObject(i, account);
                try { p.executeUpdate(); throw new AssertionError("Self friendship accepted"); }
                catch (SQLException e) { require(e.getSQLState().equals("23514"), "Database rejects self friendship"); }
            }
        }
    }
    private static void databaseFailure(Peer a) throws Exception {
        try (var c = database.open(); var s = c.createStatement()) {
            s.execute("ALTER TABLE friendships RENAME TO friendships_unavailable");
            try { require(request(a, new FriendsRequest(FriendsAction.FRIENDS, null, "", 0)).type() == MessageType.FRIENDS_ERROR, "Database failure is a safe error response"); }
            finally { s.execute("ALTER TABLE friendships_unavailable RENAME TO friendships"); }
        }
        list(a, FriendsAction.FRIENDS, "", 0);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
