package com.battleship.backend.match;

import com.battleship.backend.database.Database;
import com.battleship.backend.server.TcpServer;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import com.google.gson.JsonParser;
import java.io.*;
import java.util.*;
import static com.battleship.backend.match.RoomRulesChecks.*;

/** Real PostgreSQL, authenticated TCP sessions and RoomManager; no scripted server. */
public final class RoomSettingsTcpChecks {
    public static void main(String[] args) throws Exception {
        var accounts = new ArrayList<UUID>();
        try {
            try (var server = new TcpServer(0)) {
                Thread.ofVirtual().start(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
                try (var a = new TcpMatchChecks.Peer(server.port(), "Room rules A"); var b = new TcpMatchChecks.Peer(server.port(), "Room rules B")) {
                    accounts.add(a.accountId); accounts.add(b.accountId);
                    for (String invalid : List.of("{\"shipCount\":2}", "{\"shipCount\":6}", "{\"shipCount\":3.5}",
                            "{\"shipCount\":\"3\"}", "{\"mode\":\"UNKNOWN\"}", "{\"turnRule\":\"UNKNOWN\"}")) {
                        a.send(MessageType.CREATE_ROOM, JsonParser.parseString(invalid));
                        require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("INVALID_ROOM_SETTINGS"), "Server rejects malformed rules before room creation");
                    }
                    for (GameMode mode : GameMode.values()) for (int count = 3; count <= 5; count++) for (TurnRule turn : TurnRule.values()) {
                        var rules = new GameRules(mode, count, turn);
                        a.send(MessageType.CREATE_ROOM, new CreateRoomRequest(rules));
                        var created = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                        require(created.rules().equals(rules), "Creator receives authoritative rules");
                        b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(created.roomCode()));
                        require(b.read(MessageType.ROOM_JOINED).payloadAs(RoomSnapshot.class).rules().equals(rules), "Joiner inherits rules");
                        a.read(MessageType.PLAYER_JOINED);
                        a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                        b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                        require(a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class).rules().equals(rules), "Placement retains room rules");
                        b.read(MessageType.START_PLACEMENT);
                        play(a, b, rules);
                        if (count == 3 && turn == TurnRule.HIT_CONTINUES) {
                            a.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            b.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            var next = a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class); b.read(MessageType.START_PLACEMENT);
                            require(next.rules().equals(rules) && next.roomCode().equals(created.roomCode()), "Rematch preserves settings and room");
                            play(a, b, rules);
                        }
                        a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.ROOM_LEFT); b.read(MessageType.REMATCH_UPDATED); b.read(MessageType.PLAYER_LEFT);
                        b.send(MessageType.LEAVE_ROOM, null); b.read(MessageType.ROOM_LEFT);
                    }
                    System.out.println("PASS real TCP: 12 configurations, invalid settings, create/join/Ready, private 3-5 ship fleets, both turn rules, full battles and two configured rematches.");
                }
            }
        } finally {
            if (!accounts.isEmpty()) try (var connection = Database.fromEnvironment().open()) {
                connection.setAutoCommit(false);
                for (var id : accounts) {
                    try (var query = connection.prepareStatement("DELETE FROM matches WHERE id IN (SELECT match_id FROM match_players WHERE user_id=?)")) { query.setObject(1, id); query.executeUpdate(); }
                    try (var query = connection.prepareStatement("DELETE FROM users WHERE id=? AND username LIKE 'tcp_%'")) { query.setObject(1, id); query.executeUpdate(); }
                }
                connection.commit();
            }
        }
    }

    private static void play(TcpMatchChecks.Peer a, TcpMatchChecks.Peer b, GameRules rules) throws Exception {
        var fleet = fleet(rules);
        a.send(MessageType.SUBMIT_FLEET, new FleetSubmission(fleet.ships().subList(0, rules.shipCount() - 1))); a.read(MessageType.ERROR);
        a.send(MessageType.SUBMIT_FLEET, fleet);
        var waiting = a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class); b.read(MessageType.FLEET_ACCEPTED);
        require(waiting.fleetSubmitted() && waiting.phase() == GamePhase.PLACEMENT && waiting.ownBoard().shots().isEmpty(), "New configured fleet accepted privately with empty shot history");
        b.send(MessageType.SUBMIT_FLEET, fleet);
        var state = a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class); b.read(MessageType.START_BATTLE);
        require(state.currentPlayerId().equals(a.id) && state.timer().remainingMillis() > 59_000 && state.enemyBoard().ships().isEmpty(), "Player 1 / fresh 60s / hidden fleet");
        a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9)));
        state = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class); b.read(MessageType.MATCH_UPDATED);
        require(state.currentPlayerId().equals(b.id), "Miss passes turn on both clients");
        b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
        List<Coordinate> cells = fleet.ships().stream().flatMap(s -> s.ship().occupiedCells().stream()).toList();
        int ai = 0, bi = 0; String current = a.id;
        while (state.phase() != GamePhase.GAME_OVER) {
            var actor = current.equals(a.id) ? a : b;
            actor.send(MessageType.ATTACK, new AttackRequest(cells.get(actor == a ? ai++ : bi++)));
            state = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            var other = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            require(state.revision() == other.revision() && state.currentPlayerId().equals(other.currentPlayerId()), "Both TCP peers agree on revision and current shooter");
            if (state.phase() == GamePhase.BATTLE) {
                String expected = rules.turnRule() == TurnRule.HIT_CONTINUES ? actor.id : actor == a ? b.id : a.id;
                require(state.currentPlayerId().equals(expected), "Authoritative hit/sunk rule");
            }
            current = state.currentPlayerId();
        }
        require(state.winnerId().equals(a.id) && state.endReason() == MatchEndReason.FLEET_DESTROYED, "Configured fleet victory");
        a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 8))); a.read(MessageType.ERROR);
    }
}
