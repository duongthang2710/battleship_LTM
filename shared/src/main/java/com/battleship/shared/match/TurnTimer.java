package com.battleship.shared.match;

/** Relative server time avoids depending on either client's wall clock. */
public record TurnTimer(long matchRevision, int turnNumber, String currentPlayerId, long remainingMillis,
                        long startsInMillis, java.util.Map<String, Integer> energy) {
    public TurnTimer {
        energy = energy == null ? java.util.Map.of() : java.util.Map.copyOf(energy);
    }
    public TurnTimer(long revision, int turn, String player, long remaining, long startsIn) {
        this(revision, turn, player, remaining, startsIn, java.util.Map.of());
    }
    public static final long START_COUNTDOWN_MILLIS = 3_000;
    public TurnTimer(long matchRevision, int turnNumber, String currentPlayerId, long remainingMillis) {
        this(matchRevision, turnNumber, currentPlayerId, remainingMillis, 0);
    }
}
