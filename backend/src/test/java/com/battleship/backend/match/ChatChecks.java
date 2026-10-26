package com.battleship.backend.match;

import com.battleship.backend.database.Database;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.auth.*;
import com.battleship.shared.chat.*;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.util.*;
import static com.battleship.backend.match.TcpMatchChecks.Peer;
import static com.battleship.backend.match.GameInviteChecks.*;

/** Real framed TCP against PostgreSQL accounts; no fixtures in the application database. */
public final class ChatChecks {
    public static void main(String[] args) throws Exception {
        String name = System.getenv("DB_NAME");
        require(name != null && name.matches("battleship_chat_test_[a-z0-9_]+"), "Use an isolated chat test database");
        var database = Database.fromEnvironment(); database.migrate();
        try (var c = database.open(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM users")) {
            require(r.next() && r.getLong(1) == 0, "Fresh database required");
        }
        try (var server = new TcpServer(0)) {
            Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            try (Peer a = new Peer(server.port(), "A"); Peer b = new Peer(server.port(), "B"); Peer outsider = new Peer(server.port(), "C")) {
                String code = create(a); ChatContext waiting = context(a, ChatScope.WAITING_ROOM, true);
                send(a, waiting, "before-join");
                rejected(outsider, waiting, "outside", "NOT_IN_ROOM");
                request(outsider, MessageType.LOGOUT, null, MessageType.LOGOUT_OK);
                require(request(outsider, MessageType.CHAT_SEND, new ChatSend(waiting.contextId(), "guest"), MessageType.AUTH_FAILED).type() == MessageType.AUTH_FAILED, "Authentication required");
                request(outsider, MessageType.LOGIN, new LoginRequest(outsider.username, "TcpCheckPassword2026!"), MessageType.AUTH_SUCCESS);
                create(outsider); ChatContext otherRoom = context(outsider, ChatScope.WAITING_ROOM, true);
                rejected(outsider, waiting, "cross-room", "CHAT_CONTEXT_CHANGED");
                join(b, code); ChatContext joined = context(b, ChatScope.WAITING_ROOM, true);
                require(joined.contextId().equals(waiting.contextId()), "Joiner receives room context"); noMessages(b);

                String special = "Hello <script> & 'quotes' [brackets] \\#ff0000# -- Xin chào";
                Message forged = Message.request(MessageType.CHAT_SEND, new ChatSend(waiting.contextId(), special));
                forged.payload().addProperty("username", outsider.username); forged.payload().addProperty("roomCode", otherRoom.roomCode());
                MessageCodec.write(a.output, forged);
                ChatMessage own = receive(a, m -> m.type() == MessageType.CHAT_MESSAGE && m.requestId().equals(forged.requestId())).payloadAs(ChatMessage.class);
                ChatMessage peer = receive(b, m -> m.type() == MessageType.CHAT_MESSAGE).payloadAs(ChatMessage.class);
                require(own.equals(peer) && own.sender().id().equals(a.accountId) && own.text().equals(special)
                        && own.sentAt() != null, "Both peers see identical authoritative sender, text and timestamp; forged room ignored");
                rejected(a, waiting, "", "INVALID_CHAT"); rejected(a, waiting, "   ", "INVALID_CHAT");
                rejected(a, waiting, "\u00a0\u00a0", "INVALID_CHAT"); rejected(a, waiting, "x".repeat(301), "INVALID_CHAT");
                rejected(a, waiting, "hello\nworld", "INVALID_CHAT"); rejected(a, waiting, "\u202eevil", "INVALID_CHAT");
                // UTF-8 cannot carry an unpaired surrogate directly; test an escaped JSON code unit on the wire.
                String invalidId = UUID.randomUUID().toString();
                String invalidJson = "{\"type\":\"CHAT_SEND\",\"requestId\":\"" + invalidId
                        + "\",\"payload\":{\"contextId\":\"" + waiting.contextId() + "\",\"text\":\"" + "\\u" + "d800\"}}";
                byte[] invalidBytes = invalidJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                a.output.writeInt(invalidBytes.length); a.output.write(invalidBytes); a.output.flush();
                require(receive(a, m -> m.requestId().equals(invalidId)).type() == MessageType.CHAT_ERROR, "Escaped unpaired surrogate rejected");
                var boundary = send(a, waiting, "x".repeat(300));
                require(boundary.text().length() == 300, "300-character boundary accepted");
                receive(b, m -> m.type() == MessageType.CHAT_MESSAGE);
                for (int i = 0; i < 5; i++) {
                    send(b, joined, "burst " + i); receive(a, m -> m.type() == MessageType.CHAT_MESSAGE);
                }
                rejected(b, joined, "spam", "CHAT_RATE_LIMIT");
                Thread.sleep(5100);
                send(b, joined, "after cooldown"); receive(a, m -> m.type() == MessageType.CHAT_MESSAGE);
                leave(b); request(b, MessageType.PING, null, MessageType.PONG);
                send(a, waiting, "after peer left"); noMessages(b);
                rejected(b, waiting, "left", "NOT_IN_ROOM");
                create(b); ChatContext bOther = context(b, ChatScope.WAITING_ROOM, true);
                require(!bOther.contextId().equals(waiting.contextId()), "Different room has different context");
                rejected(b, waiting, "late packet", "CHAT_CONTEXT_CHANGED"); leave(b);
                join(b, code); context(b, ChatScope.WAITING_ROOM, true); noMessages(b);
                MatchSnapshot state = startBattle(a, b, waiting.contextId());
                ChatContext match = context(a, ChatScope.MATCH, true); ChatContext matchB = context(b, ChatScope.MATCH, true);
                require(match.contextId().equals(matchB.contextId()), "Both players share new match context");
                send(a, match, "Battle chat without shooting"); receive(b, m -> m.type() == MessageType.CHAT_MESSAGE);
                TurnTimer tick = receive(a, m -> m.type() == MessageType.TURN_TIMER).payloadAs(TurnTimer.class);
                require(tick.matchRevision() == state.revision() && tick.turnNumber() == 1 && tick.currentPlayerId().equals(a.id)
                        && tick.remainingMillis() < state.timer().remainingMillis(), "Chat does not switch turn, increment match revision or reset deadline");
                var cells = TcpMatchChecks.fleet().ships().stream().flatMap(s -> s.ship().occupiedCells().stream()).toList();
                int ai = 0, bi = 0;
                while (state.phase() == GamePhase.BATTLE) {
                    Peer actor = state.currentPlayerId().equals(a.id) ? a : b, opponent = actor == a ? b : a;
                    state = request(actor, MessageType.ATTACK, new AttackRequest(cells.get(actor == a ? ai++ : bi++)), MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    var other = receive(opponent, m -> m.type() == MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                    require(other.revision() == state.revision(), "PvP attacks remain synchronized");
                }
                context(a, ChatScope.MATCH, false); context(b, ChatScope.MATCH, false);
                rejected(a, match, "after game over", "CHAT_READ_ONLY");
                request(a, MessageType.REMATCH_REQUEST, null, MessageType.REMATCH_UPDATED);
                request(b, MessageType.REMATCH_REQUEST, null, MessageType.REMATCH_UPDATED);
                var newRound = placementContext(a); placementContext(b);
                require(!newRound.contextId().equals(match.contextId()), "Rematch always creates a new chat context");
                rejected(a, match, "old-round packet", "CHAT_CONTEXT_CHANGED");
                request(a, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.FLEET_ACCEPTED);
                request(b, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.START_BATTLE);
                receive(a, m -> m.type() == MessageType.START_BATTLE);
                var round2 = context(a, ChatScope.MATCH, true); context(b, ChatScope.MATCH, true);
                send(a, round2, "only round two");
                require(receive(b, m -> m.type() == MessageType.CHAT_MESSAGE).payloadAs(ChatMessage.class).contextId().equals(round2.contextId()), "New match chat stays in its own context");
                noMessages(outsider);
                leave(a); leave(b); leave(outsider);
                System.out.println("PASS chat TCP/PostgreSQL: live-only waiting chat, authenticated sender, room privacy, length/blank/control validation, spam/cooldown, leave/rejoin isolation, Battle timer and turn unchanged, full match, read-only end and new rematch context.");
            }
        }
    }
    private static MatchSnapshot startBattle(Peer a, Peer b, UUID old) throws Exception {
        request(a, MessageType.READY, null, MessageType.ROOM_UPDATED); request(b, MessageType.READY, null, MessageType.ROOM_UPDATED);
        var placement = placementContext(a); placementContext(b);
        require(!placement.contextId().equals(old), "Waiting chat is not mixed into match chat");
        rejected(a, placement, "during placement", "CHAT_READ_ONLY");
        request(a, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.FLEET_ACCEPTED);
        request(b, MessageType.SUBMIT_FLEET, TcpMatchChecks.fleet(), MessageType.START_BATTLE);
        return receive(a, m -> m.type() == MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
    }
    private static ChatContext placementContext(Peer peer) throws Exception {
        receive(peer, m -> m.type() == MessageType.START_PLACEMENT);
        return context(peer, ChatScope.MATCH, false);
    }
    private static ChatContext context(Peer peer, ChatScope scope, boolean writable) throws Exception {
        return receive(peer, m -> m.type() == MessageType.CHAT_CONTEXT && m.payloadAs(ChatContext.class).scope() == scope
                && m.payloadAs(ChatContext.class).canSend() == writable).payloadAs(ChatContext.class);
    }
    private static ChatMessage send(Peer peer, ChatContext context, String text) throws Exception {
        Message result = request(peer, MessageType.CHAT_SEND, new ChatSend(context.contextId(), text), MessageType.CHAT_MESSAGE);
        require(result.type() == MessageType.CHAT_MESSAGE, "Message sent: " + result);
        return result.payloadAs(ChatMessage.class);
    }
    private static void rejected(Peer peer, ChatContext context, String text, String code) throws Exception {
        Message result = request(peer, MessageType.CHAT_SEND, new ChatSend(context.contextId(), text), MessageType.CHAT_ERROR);
        require(result.type() == MessageType.CHAT_ERROR && result.payloadAs(RoomError.class).code().equals(code), "Chat rejected: " + result);
    }
    private static void noMessages(Peer peer) throws Exception {
        request(peer, MessageType.PING, null, MessageType.PONG);
        require(events.get(peer).stream().noneMatch(m -> m.type() == MessageType.CHAT_MESSAGE), "No old, foreign or post-leave messages delivered");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
