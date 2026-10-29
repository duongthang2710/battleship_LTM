package com.battleship.shared.match;

/** Relative server time avoids depending on either client's wall clock. */
public record TurnTimer(long matchRevision, int turnNumber, String currentPlayerId, long remainingMillis,
                        long startsInMillis) {
    public static final long START_COUNTDOWN_MILLIS = 3_000;
    public TurnTimer(long matchRevision, int turnNumber, String currentPlayerId, long remainingMillis) {
        this(matchRevision, turnNumber, currentPlayerId, remainingMillis, 0);
    }
}
