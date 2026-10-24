package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.BoardView;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.shared.room.RoomPlayer;
import java.util.List;

/** Owned by one room. All calls are serialized by RoomManager. */
public final class MatchService {
    private final GameEngine engine;
    private final Side firstSide;
    private long revision = 1;

    public MatchService(List<RoomPlayer> players) {
        RoomPlayer first = players.get(0);
        RoomPlayer second = players.get(1);
        firstSide = first.side();
        engine = new GameEngine(new Player(first.clientId(), first.playerName(), new Board()),
                new Player(second.clientId(), second.playerName(), new Board()));
    }

    public GamePhase phase() { return engine.state().phase(); }

    public void submit(String clientId, FleetSubmission request) {
        if (request == null || request.ships() == null) throw new IllegalArgumentException("Missing fleet data.");
        for (var ship : request.ships()) {
            if (ship == null || ship.type() == null || ship.position() == null || ship.orientation() == null)
                throw new IllegalArgumentException("Missing or unknown ship data.");
        }
        engine.submitFleet(clientId, request.ships().stream().map(ship -> ship.ship()).toList());
        revision++;
    }

    public AttackResult attack(String clientId, Coordinate cell) {
        AttackResult result = engine.attack(clientId, cell);
        if (result.accepted()) revision++;
        return result;
    }

    public MatchSnapshot snapshot(String clientId) {
        GameState state = engine.state();
        boolean first = state.player().id().equals(clientId);
        Player own = first ? state.player() : state.enemy();
        Player enemy = first ? state.enemy() : state.player();
        AttackResult attack = state.lastAttack();
        if (attack != null && !attack.sunk()) attack = new AttackResult(attack.type(), attack.coordinate(), null,
                false, false, attack.attackerId(), attack.defenderId(), null, attack.affectedCells());
        return new MatchSnapshot(revision, state.phase(), own.id(), enemy.id(), enemy.name(),
                first ? firstSide : firstSide.opponent(), BoardView.from(own.board(), false), BoardView.from(enemy.board(), true),
                own.fleet().isComplete(), enemy.fleet().isComplete(), state.currentPlayerId(), state.turnNumber(),
                state.winnerId(), attack);
    }

}
