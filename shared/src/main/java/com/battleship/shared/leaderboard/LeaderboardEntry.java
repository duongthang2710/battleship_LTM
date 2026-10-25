package com.battleship.shared.leaderboard;

public record LeaderboardEntry(long rank, String displayName, String username, long matches,
                               long wins, long losses, double winRate, Long currentWinStreak) {}
