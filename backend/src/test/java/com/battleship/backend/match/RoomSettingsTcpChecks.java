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
                            "{\"shipCount\":\"3\"}", "{\"mode\":\"UNKNOWN\"}", "{\"turnRule\":\"UNKNOWN\"}", "{\"options\":{\"hideSunkInfo\":\"yes\"}}", "{\"options\":{\"hideFleetReport\":1}}")) {
                        a.send(MessageType.CREATE_ROOM, JsonParser.parseString(invalid));
                        require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("INVALID_ROOM_SETTINGS"), "Server rejects malformed rules before room creation");
                    }
                    for (GameMode mode : GameMode.values()) for (int count = 3; count <= 5; count++)
                            for (TurnRule turn : TurnRule.values()) for (CombatMode combat : CombatMode.values()) {
                        var rules = new GameRules(mode, count, turn, combat);
                        var options=new com.battleship.shared.room.RoomOptions(count%2==1);
                        a.send(MessageType.CREATE_ROOM, new CreateRoomRequest(rules,options));
                        var created = a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                        require(created.rules().equals(rules)&&created.options().equals(options), "Creator receives authoritative rules");
                        b.send(MessageType.JOIN_ROOM, new JoinRoomRequest(created.roomCode()));
                        var joined=b.read(MessageType.ROOM_JOINED).payloadAs(RoomSnapshot.class);
                        require(joined.rules().equals(rules)&&joined.options().equals(options),"Joiner inherits all settings");
                        a.read(MessageType.PLAYER_JOINED);
                        a.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                        b.send(MessageType.READY, null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                        require(a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class).rules().equals(rules), "Placement retains room rules");
                        b.read(MessageType.START_PLACEMENT);
                        play(a, b, rules, options);
                        if (count == 3 && turn == TurnRule.HIT_CONTINUES) {
                            a.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            b.send(MessageType.REMATCH_REQUEST, null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            var next = a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class); b.read(MessageType.START_PLACEMENT);
                            require(next.rules().equals(rules) && next.options().equals(options) && next.roomCode().equals(created.roomCode()), "Rematch preserves settings and room");
                            play(a, b, rules, options);
                        }
                        a.send(MessageType.LEAVE_ROOM, null); a.read(MessageType.ROOM_LEFT); b.read(MessageType.REMATCH_UPDATED); b.read(MessageType.PLAYER_LEFT);
                        b.send(MessageType.LEAVE_ROOM, null); b.read(MessageType.ROOM_LEFT);
                    }
                    System.out.println("PASS real TCP: 24 configurations, four skills, invalid requests, private sonar, hidden sinking, full battles and four rematches with reset energy/scans.");
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

    private static void play(TcpMatchChecks.Peer a, TcpMatchChecks.Peer b, GameRules rules, RoomOptions options) throws Exception {
        var fleet = fleet(rules);
        a.send(MessageType.SUBMIT_FLEET, new FleetSubmission(fleet.ships().subList(0, rules.shipCount() - 1))); a.read(MessageType.ERROR);
        a.send(MessageType.SUBMIT_FLEET, fleet);
        var waiting = a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class); b.read(MessageType.FLEET_ACCEPTED);
        require(waiting.fleetSubmitted() && waiting.phase() == GamePhase.PLACEMENT && waiting.ownBoard().shots().isEmpty(), "New configured fleet accepted privately with empty shot history");
        b.send(MessageType.SUBMIT_FLEET, fleet);
        var state = a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class); b.read(MessageType.START_BATTLE);
        TcpMatchChecks.awaitStart(a,b);
        require(state.currentPlayerId().equals(a.id) && state.timer().remainingMillis() > 59_000 && state.enemyBoard().ships().isEmpty(), "Player 1 / fresh 60s / hidden fleet");
        if (rules.skills()) {
            require(state.skills().ownEnergy() == 0 && state.skills().ownScans().isEmpty()
                    && state.skills().lastSkill() == null, "Initial and rematch skill state starts empty");
            state = checkSkills(a, b, state, options);
        } else require(state.skills() == null, "Classic combat sends no skill state");
        a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9)));
        state = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class); b.read(MessageType.MATCH_UPDATED);
        require(state.currentPlayerId().equals(b.id), "Miss passes turn on both clients");
        b.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 9))); a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
        List<Coordinate> cells = fleet.ships().stream().flatMap(s -> s.ship().occupiedCells().stream()).toList();
        String current = a.id;
        while (state.phase() != GamePhase.GAME_OVER) {
            var actor = current.equals(a.id) ? a : b;
            Board target = actor == a ? state.enemyBoard().board() : state.ownBoard().board();
            actor.send(MessageType.ATTACK, new AttackRequest(cells.stream().filter(cell -> !target.wasAttacked(cell)).findFirst().orElseThrow()));
            state = a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            var other = b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            require(state.revision() == other.revision() && state.currentPlayerId().equals(other.currentPlayerId()), "Both TCP peers agree on revision and current shooter");
            if (state.phase() == GamePhase.BATTLE) {
                if (options.hideSunkInfo()) {
                    RoomRulesChecks.checkHiddenSnapshot(state);
                    RoomRulesChecks.checkHiddenSnapshot(other);
                }
                String expected = rules.turnRule() == TurnRule.HIT_CONTINUES ? actor.id : actor == a ? b.id : a.id;
                require(state.currentPlayerId().equals(expected), "Authoritative hit/sunk rule");
            }
            current = state.currentPlayerId();
        }
        if (options.hideSunkInfo()) require(state.enemyBoard().ships().size() == rules.shipCount(), "Finished TCP match reveals the full enemy fleet");
        require(state.winnerId().equals(a.id) && state.endReason() == MatchEndReason.FLEET_DESTROYED, "Configured fleet victory");
        a.send(MessageType.ATTACK, new AttackRequest(new Coordinate(9, 8))); a.read(MessageType.ERROR);
        if (rules.skills()) {
            a.send(MessageType.USE_SKILL, new SkillRequest(SkillType.SONAR, new Coordinate(0, 0), null));
            require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("GAME_FINISHED"), "Skills are locked after game over");
        }
    }

    private static MatchSnapshot checkSkills(TcpMatchChecks.Peer a, TcpMatchChecks.Peer b,
                                             MatchSnapshot state, RoomOptions options) throws Exception {
        a.send(MessageType.USE_SKILL,new SkillRequest(SkillType.AIRSTRIKE,new Coordinate(0,0),SkillDirection.EAST));
        require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("NOT_ENOUGH_ENERGY"),"Insufficient energy rejected");
        b.send(MessageType.USE_SKILL,new SkillRequest(SkillType.SONAR,new Coordinate(0,0),null));
        require(b.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("NOT_YOUR_TURN"),"Out-of-turn support rejected");
        for(SkillType skill:SkillType.values()) {
            while(state.skills().ownEnergy()<skill.cost()) {
                state=waterShot(a,b,state.enemyBoard().board(),a.id);
                state=waterShot(b,a,state.ownBoard().board(),a.id);
            }
            int oldEnergy=state.skills().ownEnergy();
            long remaining=state.timer().remainingMillis();
            Coordinate target=switch(skill) {
                case MINE -> new Coordinate(8,8);
                case SHIELD -> new Coordinate(2,1);
                case AIRSTRIKE -> new Coordinate(8,0);
                default -> new Coordinate(0,0);
            };
            a.send(MessageType.USE_SKILL,new SkillRequest(skill,target,skill==SkillType.AIRSTRIKE?SkillDirection.EAST:null));
            state=a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            var other=b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
            var result=state.skills().lastSkill();
            require(result.skill()==skill && result.accepted() && state.revision()==other.revision()
                    && state.skills().ownEnergy()==oldEnergy-skill.cost(),"Skill energy is spent exactly once");
            if(skill==SkillType.SONAR) require(state.skills().ownScans().getFirst().count()>0
                    && other.skills().ownScans().isEmpty() && other.skills().lastSkill().sonarCount()==null,"Only the scanner receives the area's count");
            require(state.skills().ownUsedSkills().contains(skill),"Server synchronizes the spent skill");
            if(!skill.endsTurn()) {
                a.send(MessageType.USE_SKILL,new SkillRequest(skill,target,null));
                require(a.read(MessageType.ERROR).payloadAs(RoomError.class).code().equals("SKILL_ALREADY_USED"),"Repeated skill rejected over TCP");
            }
            if(skill==SkillType.MINE) require(state.skills().ownMines().size()==1 && other.skills().ownMines().isEmpty()
                    && other.skills().lastSkill()==null,"Mine coordinates and deployment result are private");
            if(skill==SkillType.SHIELD) require(!state.skills().ownShields().isEmpty(),"Own shield segments are synchronized");
            if(!skill.endsTurn()) {
                require(state.currentPlayerId().equals(a.id) && state.timer().remainingMillis()<=remaining,"Support keeps turn and deadline");
                state=waterShot(a,b,state.enemyBoard().board(),a.id);
            } else {
                require(result.area().size()==10 && state.currentPlayerId().equals(b.id)
                        && state.timer().remainingMillis()>59_000,"Airstrike covers ten cells and ends turn");
                if(options.hideSunkInfo()) {
                    checkHiddenSnapshot(state); checkHiddenSnapshot(other);
                    require(result.impacts().stream().noneMatch(impact->impact.sunk()||impact.shipType()!=null),"Airstrike honors hidden sinking");
                }
            }
            state=waterShot(b,a,state.ownBoard().board(),a.id);
        }
        return state;
    }

    /** Returns player A's snapshot, regardless of which peer fired. */
    private static MatchSnapshot waterShot(TcpMatchChecks.Peer actor, TcpMatchChecks.Peer opponent, Board target,
                                           String viewerId) throws Exception {
        Coordinate water = java.util.stream.IntStream.range(0, Board.SIZE * 5)
                .mapToObj(index -> new Coordinate(index / 5, 5 + index % 5))
                .filter(cell -> !target.wasAttacked(cell)).findFirst().orElseThrow();
        actor.send(MessageType.ATTACK, new AttackRequest(water));
        var own = actor.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
        var other = opponent.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
        require(own.lastAttack().type() == AttackResultType.MISS, "Charging shot hits known water");
        return own.playerId().equals(viewerId) ? own : other;
    }
}
