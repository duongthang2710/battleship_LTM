package com.battleship.shared.leaderboard;

import java.util.List;

public record LeaderboardPage(LeaderboardSort sort, int page, int pageSize, long totalPlayers,
                              int minWinRateMatches, List<LeaderboardEntry> entries) {
    public LeaderboardPage { entries = List.copyOf(entries); }
}
