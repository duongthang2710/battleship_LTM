package com.battleship.shared.match;

import com.battleship.shared.game.AttackResult;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.Side;

public record MatchSnapshot(long revision, GamePhase phase, String playerId, String opponentId,
                            String opponentName, Side side, BoardView ownBoard, BoardView enemyBoard,
                            boolean fleetSubmitted, boolean opponentSubmitted, String currentPlayerId,
                            int turnNumber, String winnerId, AttackResult lastAttack) { }
