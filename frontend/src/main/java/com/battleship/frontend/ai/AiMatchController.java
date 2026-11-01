package com.battleship.frontend.ai;

import com.battleship.shared.ai.*;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;

/** Local match authority. Called only on the app thread; no scheduled callbacks can outlive it. */
public final class AiMatchController {
    private final GameEngine engine;
    private final AiDifficulty difficulty;
    private final AiStrategy strategy;
    private final Random random;
    private final LongSupplier nanoTime;
    private final String playerName;
    private long turnStarts, deadline, botDue;
    private MatchEndReason endReason;
    private GameState cachedRaw, cachedView;

    public AiMatchController(GameEngine engine, AiDifficulty difficulty, String playerName) {
        this(engine, difficulty, playerName, new Random(), System::nanoTime);
    }
    public AiMatchController(GameEngine engine, AiDifficulty difficulty, String playerName, Random random, LongSupplier nanoTime) {
        this.engine = engine; this.difficulty = difficulty; this.playerName = playerName;
        this.random = random; this.nanoTime = nanoTime; strategy = AiStrategy.forDifficulty(difficulty);
    }
    public AiDifficulty difficulty() { return difficulty; }
    public MatchEndReason endReason() { return endReason; }
    public PlacementResult ready() {
        PlacementResult result = engine.ready();
        if (result.success()) startTurn(TurnTimer.START_COUNTDOWN_MILLIS);
        return result;
    }
    public void reset() { endReason = null; turnStarts = deadline = botDue = 0; }
    private void startTurn(long delayMillis) {
        turnStarts = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        deadline = turnStarts + TimeUnit.SECONDS.toNanos(60);
        botDue = turnStarts + TimeUnit.MILLISECONDS.toNanos(650 + random.nextInt(351));
    }
    public long remainingStartMillis() { return remaining(turnStarts); }
    public long remainingTurnMillis() { return Math.min(60_000, remaining(deadline)); }
    private long remaining(long end) {
        if (engine.state().phase() != GamePhase.BATTLE) return 0;
        return Math.max(0, (end - nanoTime.getAsLong() + 999_999) / 1_000_000);
    }
    public boolean expireTurn() {
        if (engine.state().phase() != GamePhase.BATTLE || nanoTime.getAsLong() - deadline < 0) return false;
        engine.timeoutCurrentTurn(); endReason = MatchEndReason.TURN_TIMEOUT; return true;
    }
    public boolean playerCanAttack(Coordinate cell) {
        return engine.state().phase() == GamePhase.BATTLE && nanoTime.getAsLong() - turnStarts >= 0
                && nanoTime.getAsLong() - deadline < 0 && engine.canAttack(engine.state().player().id(), cell);
    }
    public AttackResult attackPlayer(Coordinate cell) {
        expireTurn();
        if (!playerCanAttack(cell)) return null;
        return attack(engine.state().player().id(), cell);
    }
    public AttackResult updateBot(boolean presentationReady) {
        expireTurn();
        GameState state = engine.state();
        if (!presentationReady || state.phase() != GamePhase.BATTLE || !state.currentPlayerId().equals(state.enemy().id())
                || nanoTime.getAsLong() - botDue < 0) return null;
        AiKnowledge knowledge = AiKnowledge.from(BoardView.from(state.player().board(), true));
        return attack(state.enemy().id(), strategy.chooseTarget(knowledge, random));
    }
    private AttackResult attack(String attacker, Coordinate cell) {
        AttackResult result = engine.attack(attacker, cell);
        if (result.accepted()) {
            if (engine.state().phase() == GamePhase.GAME_OVER) endReason = MatchEndReason.FLEET_DESTROYED;
            else startTurn(0);
        }
        return publicResult(result);
    }
    private static AttackResult publicResult(AttackResult result) {
        if (result == null || result.sunk()) return result;
        return new AttackResult(result.type(), result.coordinate(), null, false, result.gameOver(),
                result.attackerId(), result.defenderId(), result.winnerId(), result.affectedCells());
    }
    /** Renderer sees only sunk enemy ships, even if the camera changes sides. */
    public GameState state() {
        GameState raw = engine.state();
        if (raw == cachedRaw) return cachedView;
        Player own = new Player(raw.player().id(), playerName, raw.player().board());
        Player enemy = new Player(raw.enemy().id(), "AI " + difficulty.label(), BoardView.from(raw.enemy().board(), true).board());
        cachedRaw = raw;
        cachedView = new GameState(raw.phase(), own, enemy, raw.currentPlayerId(), raw.winnerId(), publicResult(raw.lastAttack()),
                raw.placement(), raw.ready(), raw.turnNumber());
        return cachedView;
    }
}
