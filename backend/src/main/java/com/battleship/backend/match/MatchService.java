package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.BoardView;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.shared.match.TurnTimer;
import com.battleship.shared.match.MatchEndReason;
import com.battleship.shared.room.RoomPlayer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owned by one room. All calls are serialized by RoomManager. */
public final class MatchService {
    private final GameEngine engine;
    private final Side firstSide;
    private final LongSupplier nanoTime;
    private static final long TURN_NANOS = TimeUnit.SECONDS.toNanos(60);
    private long revision = 1;
    private long turnDeadline;
    private long lastClockSecond = 60;
    private MatchEndReason endReason;

    public MatchService(List<RoomPlayer> players) {
        this(players, System::nanoTime);
    }

    MatchService(List<RoomPlayer> players, LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
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
        if (phase() == GamePhase.BATTLE) startTurn();
    }

    public AttackResult attack(String clientId, Coordinate cell) {
        // RoomManager holds the same lock for incoming attacks and scheduled expiry.
        expireTurn();
        AttackResult result = engine.attack(clientId, cell);
        if (result.accepted()) {
            revision++;
            if (phase() == GamePhase.BATTLE) startTurn();
            else if (phase() == GamePhase.GAME_OVER) endReason = MatchEndReason.FLEET_DESTROYED;
        }
        return result;
    }

    private void startTurn() {
        turnDeadline = nanoTime.getAsLong() + TURN_NANOS;
        lastClockSecond = 60;
    }

    public boolean expireTurn() {
        if (phase() != GamePhase.BATTLE || nanoTime.getAsLong() - turnDeadline < 0) return false;
        engine.timeoutCurrentTurn();
        endReason = MatchEndReason.TURN_TIMEOUT;
        revision++;
        return true;
    }

    public boolean forfeit(String clientId) {
        if (expireTurn()) return true;
        if (!engine.forfeit(clientId)) return false;
        endReason = MatchEndReason.PLAYER_LEFT;
        revision++;
        return true;
    }

    public MatchEndReason endReason() { return endReason; }
    public int totalTurns() {
        return engine.state().player().board().attackedCells().size() + engine.state().enemy().board().attackedCells().size();
    }
    public Integer winnerSlot() {
        String winner = engine.state().winnerId();
        return winner == null ? null : winner.equals(engine.state().player().id()) ? 1 : 2;
    }

    public TurnTimer timer() {
        long remaining = phase() == GamePhase.BATTLE ? Math.max(0, turnDeadline - nanoTime.getAsLong()) : 0;
        return new TurnTimer(revision, engine.state().turnNumber(), engine.state().currentPlayerId(),
                (remaining + 999_999) / 1_000_000);
    }

    public TurnTimer clockUpdate() {
        if (phase() != GamePhase.BATTLE) return null;
        TurnTimer timer = timer();
        long seconds = (timer.remainingMillis() + 999) / 1000;
        if (seconds == lastClockSecond) return null;
        lastClockSecond = seconds;
        return timer;
    }

    public List<MatchRepository.Statistics> statistics(java.util.Map<String, java.util.UUID> accounts) {
        GameState state = engine.state();
        return List.of(state.player(), state.enemy()).stream().map(player -> {
            Player target = player.id().equals(state.player().id()) ? state.enemy() : state.player();
            String result = player.id().equals(state.winnerId()) ? "VICTORY" : "DEFEAT";
            return MatchRepository.Statistics.from(accounts.get(player.id()), result, target.board());
        }).toList();
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
                state.winnerId(), attack, timer(), endReason == MatchEndReason.TURN_TIMEOUT, endReason);
    }

}
