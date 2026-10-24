package com.battleship.shared.match;

/** Relative server time avoids depending on either client's wall clock. */
public record TurnTimer(long matchRevision, int turnNumber, String currentPlayerId, long remainingMillis) { }
