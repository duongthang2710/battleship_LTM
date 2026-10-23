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
    private long revision = 1;

    public MatchService(List<RoomPlayer> players) {
        RoomPlayer white = players.stream().filter(player -> player.side() == Side.WHITE).findFirst().orElseThrow();
        RoomPlayer black = players.stream().filter(player -> player.side() == Side.BLACK).findFirst().orElseThrow();
        engine = new GameEngine(new Player(white.clientId(), white.playerName(), new Board()),
                new Player(black.clientId(), black.playerName(), new Board()));
    }

    public GamePhase phase() { return engine.state().phase(); }

    public void submit(String clientId, FleetSubmission request) {
        if (submitted(clientId)) throw new IllegalStateException("Fleet already submitted");
        if (request == null || request.ships() == null) throw new IllegalArgumentException("Missing fleet");
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
        boolean white = state.player().id().equals(clientId);
        Player own = white ? state.player() : state.enemy();
        Player enemy = white ? state.enemy() : state.player();
        AttackResult attack = state.lastAttack();
        if (attack != null && !attack.sunk()) attack = new AttackResult(attack.type(), attack.coordinate(), null,
                false, false, attack.attackerId(), attack.defenderId(), null, attack.affectedCells());
        return new MatchSnapshot(revision, state.phase(), own.id(), enemy.id(), enemy.name(),
                white ? Side.WHITE : Side.BLACK, BoardView.from(own.board(), false), BoardView.from(enemy.board(), true),
                own.fleet().isComplete(), enemy.fleet().isComplete(), state.currentPlayerId(), state.turnNumber(),
                state.winnerId(), attack);
    }

    private boolean submitted(String id) {
        GameState state = engine.state();
        return (state.player().id().equals(id) ? state.player() : state.enemy()).fleet().isComplete();
    }
}
