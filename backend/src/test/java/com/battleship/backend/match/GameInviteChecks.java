package com.battleship.backend.match;

import com.battleship.backend.database.Database;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.auth.*;
import com.battleship.shared.friends.*;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.*;
import static com.battleship.backend.match.TcpMatchChecks.Peer;

/** Real TCP and PostgreSQL in a fresh isolated database. Real 60s expiry is also checked by desktop clients. */
public final class GameInviteChecks {
    static final Map<Peer, Deque<Message>> events = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        String name = System.getenv("DB_NAME");
        require(name != null && name.matches("battleship_invite_test_[a-z0-9_]+"), "Use an isolated invite test database");
        var database = Database.fromEnvironment(); database.migrate();
        try (var c = database.open(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM users")) {
            require(r.next() && r.getLong(1) == 0, "Fresh test database required");
        }
        try (TcpServer server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.port(), "A"); Peer b = new Peer(server.port(), "B"); Peer c = new Peer(server.port(), "C")) {
                rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(b.accountId), "friend");
                friends(a, b); friends(a, c);
                require(request(c, MessageType.LOGOUT, null, MessageType.LOGOUT_OK).type() == MessageType.LOGOUT_OK, "C logs out");
                rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(c.accountId), "offline");
                require(request(c, MessageType.SEND_GAME_INVITE, new SendGameInvite(a.accountId), MessageType.AUTH_FAILED).type() == MessageType.AUTH_FAILED, "Unauthenticated invites denied");
                request(c, MessageType.LOGIN, new LoginRequest(c.username, "TcpCheckPassword2026!"), MessageType.AUTH_SUCCESS);

                GameInvite first = send(a, b);
                var created = receive(a, m -> m.type() == MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                require(first.roomCode().equals(created.roomCode()) && created.players().size() == 1, "Invite auto-creates exactly one waiting room");
                require(first.remainingMillis() > 59000 && first.remainingMillis() <= 60000, "Server starts a 60-second invitation");
                rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(b.accountId), "already invited");
                rejected(c, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(first.inviteId()), "another player");
                require(answer(b, first, false).status() == GameInviteStatus.DECLINED, "Recipient declines");
                update(a, first.inviteId(), GameInviteStatus.DECLINED);
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(first.inviteId()), "already been processed");

                GameInvite busy = send(a, b);
                require(!create(b).equals(busy.roomCode()), "Recipient creates a different room");
                update(a, busy.inviteId(), GameInviteStatus.INVALIDATED);
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(busy.inviteId()), "already been processed");
                leave(b); drain(a, b, c);
                GameInvite cancelled = send(a, b);
                require(cancelled.roomCode().equals(created.roomCode()), "Existing waiting room is reused");
                leave(a); update(b, cancelled.inviteId(), GameInviteStatus.INVALIDATED);
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(cancelled.inviteId()), "already been processed");

                GameInvite full = send(a, b);
                join(c, full.roomCode());
                update(b, full.inviteId(), GameInviteStatus.INVALIDATED);
                rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(b.accountId), "full");
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(full.inviteId()), "already been processed");
                leave(c); drain(a, b, c);

                // Two different rooms invite B; accepting one invalidates B's other invitations.
                friends(b, c);
                GameInvite fromA = send(a, b); GameInvite fromC = send(c, b);
                require(answer(b, fromA, true).status() == GameInviteStatus.ACCEPTED, "B accepts A's invitation");
                update(a, fromA.inviteId(), GameInviteStatus.ACCEPTED);
                update(c, fromC.inviteId(), GameInviteStatus.INVALIDATED);
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(fromC.inviteId()), "already been processed");
                var host = receive(a, m -> m.type() == MessageType.PLAYER_JOINED).payloadAs(RoomSnapshot.class);
                var guest = receive(b, m -> m.type() == MessageType.ROOM_JOINED).payloadAs(RoomSnapshot.class);
                require(host.equals(guest) && host.roomCode().equals(fromA.roomCode()) && host.players().size() == 2
                        && host.players().getFirst().clientId().equals(a.id), "Both clients receive the same room, Player 1 preserved");
                rejected(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(fromA.inviteId()), "already been processed");
                rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(b.accountId), "same room");
                leave(c); drain(a, b, c);
                battle(a, b, c);
                leave(a); leave(b); drain(a, b, c);

                // Two recipients race for a single room seat; RoomManager serializes acceptance and invalidation.
                GameInvite toB = send(a, b), toC = send(a, c);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var gate = new CyclicBarrier(2);
                    var rb = executor.submit(() -> { gate.await(); return request(b, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(toB.inviteId()), MessageType.GAME_INVITE_UPDATED); });
                    var rc = executor.submit(() -> { gate.await(); return request(c, MessageType.ACCEPT_GAME_INVITE, new GameInviteResponse(toC.inviteId()), MessageType.GAME_INVITE_UPDATED); });
                    Message mb = rb.get(10, TimeUnit.SECONDS), mc = rc.get(10, TimeUnit.SECONDS);
                    boolean bWon = accepted(mb), cWon = accepted(mc);
                    require(bWon != cWon, "Exactly one concurrent recipient gets the last room seat");
                    Peer winner = bWon ? b : c, loser = bWon ? c : b;
                    var snapshot = request(a, MessageType.READY, null, MessageType.ROOM_UPDATED).payloadAs(RoomSnapshot.class);
                    require(snapshot.players().size() == 2 && snapshot.players().stream().anyMatch(p -> p.clientId().equals(winner.id)), "Room capacity remains two after the race");
                    create(loser); leave(loser); leave(winner); leave(a);
                }
                drain(a, b, c);
                String oldCode = create(a); join(b, oldCode);
                require(receive(a, m -> m.type() == MessageType.PLAYER_JOINED).payloadAs(RoomSnapshot.class).players().size() == 2, "Original create/code-join notifications still work");
                leave(b); leave(a);
                System.out.println("PASS game invites TCP/PostgreSQL: friendship/auth/offline, auto-create/reuse, decline, wrong recipient, busy/full/left rooms, replay, concurrent last seat, normal room codes and full PvP match.");
            }
        }
    }

    private static boolean accepted(Message message) {
        return message.type() == MessageType.GAME_INVITE_UPDATED && message.payloadAs(GameInvite.class).status() == GameInviteStatus.ACCEPTED;
    }
    private static void battle(Peer a, Peer b, Peer c) throws Exception {
        request(a, MessageType.READY, null, MessageType.ROOM_UPDATED);
        request(b, MessageType.READY, null, MessageType.ROOM_UPDATED);
        receive(a, m -> m.type() == MessageType.START_PLACEMENT); receive(b, m -> m.type() == MessageType.START_PLACEMENT);
        rejected(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(c.accountId), "started");
        request(a, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.FLEET_ACCEPTED);
        request(b, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.START_BATTLE);
        var state = receive(a, m -> m.type() == MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
        require(state.currentPlayerId().equals(a.id) && state.enemyBoard().ships().isEmpty(), "Invited match keeps first turn and private fleets");
        var cells = TcpMatchChecks.fleet().ships().stream().flatMap(p -> p.ship().occupiedCells().stream()).toList();
        int ai = 0, bi = 0;
        while (state.phase() == GamePhase.BATTLE) {
            Peer actor = state.currentPlayerId().equals(a.id) ? a : b;
            Peer opponent = actor == a ? b : a;
            var updated = request(actor, MessageType.ATTACK, new AttackRequest(cells.get(actor == a ? ai++ : bi++)), MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            var other = receive(opponent, m -> m.type() == MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            require(updated.revision() == other.revision(), "Both clients agree on every invited-match shot");
            state = updated;
        }
        require(state.winnerId().equals(a.id), "Invited match finishes with the correct winner");
    }

    private static void friends(Peer a, Peer b) throws Exception {
        request(a, MessageType.FRIENDS_REQUEST, new FriendsRequest(FriendsAction.SEND, b.accountId, null, 0), MessageType.FRIENDS_RESULT);
        request(b, MessageType.FRIENDS_REQUEST, new FriendsRequest(FriendsAction.ACCEPT, a.accountId, null, 0), MessageType.FRIENDS_RESULT);
    }
    private static GameInvite send(Peer a, Peer b) throws Exception {
        var response = request(a, MessageType.SEND_GAME_INVITE, new SendGameInvite(b.accountId), MessageType.GAME_INVITE_UPDATED);
        require(response.type() == MessageType.GAME_INVITE_UPDATED, "Invitation succeeds: " + response);
        var invite = response.payloadAs(GameInvite.class);
        require(invite.status() == GameInviteStatus.PENDING && invite.inviter().id().equals(a.accountId)
                && invite.recipient().id().equals(b.accountId), "Server account identities in invitation");
        update(b, invite.inviteId(), GameInviteStatus.PENDING);
        return invite;
    }
    private static GameInvite answer(Peer peer, GameInvite invite, boolean accept) throws Exception {
        var response = request(peer, accept ? MessageType.ACCEPT_GAME_INVITE : MessageType.DECLINE_GAME_INVITE,
                new GameInviteResponse(invite.inviteId()), MessageType.GAME_INVITE_UPDATED);
        require(response.type() == MessageType.GAME_INVITE_UPDATED, "Invitation response succeeds: " + response);
        return response.payloadAs(GameInvite.class);
    }
    private static GameInvite update(Peer peer, UUID id, GameInviteStatus status) throws Exception {
        return receive(peer, m -> m.type() == MessageType.GAME_INVITE_UPDATED
                && m.payloadAs(GameInvite.class).inviteId().equals(id) && m.payloadAs(GameInvite.class).status() == status).payloadAs(GameInvite.class);
    }
    static Message request(Peer peer, MessageType type, Object payload, MessageType response) throws Exception {
        Message message = Message.request(type, payload); MessageCodec.write(peer.output, message);
        return receive(peer, m -> m.requestId().equals(message.requestId()) && (m.type() == response
                || m.type() == MessageType.CHAT_ERROR || m.type() == MessageType.GAME_INVITE_ERROR || m.type() == MessageType.ERROR || m.type() == MessageType.AUTH_FAILED));
    }
    private static void rejected(Peer peer, MessageType type, Object payload, String reason) throws Exception {
        Message result = request(peer, type, payload, MessageType.GAME_INVITE_ERROR);
        require(result.type() == MessageType.GAME_INVITE_ERROR && result.payloadAs(RoomError.class).message().contains(reason), "Rejected " + type + ": " + result);
    }
    static Message receive(Peer peer, Predicate<Message> wanted) throws Exception {
        var queue = events.computeIfAbsent(peer, ignored -> new ArrayDeque<>());
        for (var it = queue.iterator(); it.hasNext();) { Message m = it.next(); if (wanted.test(m)) { it.remove(); return m; } }
        for (;;) {
            Message message = MessageCodec.read(peer.input); require(message != null, "TCP stayed connected");
            if (wanted.test(message)) return message;
            if (message.type() != MessageType.TURN_TIMER && message.type() != MessageType.FRIENDS_CHANGED) queue.addLast(message);
        }
    }
    static String create(Peer peer) throws Exception {
        return request(peer, MessageType.CREATE_ROOM, null, MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class).roomCode();
    }
    static void join(Peer peer, String code) throws Exception {
        var result = request(peer, MessageType.JOIN_ROOM, new JoinRoomRequest(code), MessageType.ROOM_JOINED);
        require(result.type() == MessageType.ROOM_JOINED && result.payloadAs(RoomSnapshot.class).roomCode().equals(code), "Code-based join preserved");
    }
    static void leave(Peer peer) throws Exception { require(request(peer, MessageType.LEAVE_ROOM, null, MessageType.ROOM_LEFT).type() == MessageType.ROOM_LEFT, "Room leave succeeds"); }
    private static void drain(Peer... peers) throws Exception {
        for (Peer peer : peers) { request(peer, MessageType.PING, null, MessageType.PONG); events.get(peer).clear(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
