package com.battleship.shared.match;

import com.battleship.shared.game.AttackResult;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.Side;

/** {@code skills} is null in Classic combat. */
public record MatchSnapshot(long revision, GamePhase phase, String playerId, String opponentId,
                            String opponentName, Side side, BoardView ownBoard, BoardView enemyBoard,
                            boolean fleetSubmitted, boolean opponentSubmitted, String currentPlayerId,
                            int turnNumber, String winnerId, AttackResult lastAttack,
                            TurnTimer timer, boolean timedOut, MatchEndReason endReason, SkillState skills) {
    public MatchSnapshot(long revision, GamePhase phase, String playerId, String opponentId,
                         String opponentName, Side side, BoardView ownBoard, BoardView enemyBoard,
                         boolean fleetSubmitted, boolean opponentSubmitted, String currentPlayerId,
                         int turnNumber, String winnerId, AttackResult lastAttack,
                         TurnTimer timer, boolean timedOut, MatchEndReason endReason) {
        this(revision, phase, playerId, opponentId, opponentName, side, ownBoard, enemyBoard, fleetSubmitted,
                opponentSubmitted, currentPlayerId, turnNumber, winnerId, lastAttack, timer, timedOut, endReason, null);
    }
}
