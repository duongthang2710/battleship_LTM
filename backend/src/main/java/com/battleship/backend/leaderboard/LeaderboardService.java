package com.battleship.backend.leaderboard;

import com.battleship.backend.match.MatchRepository;
import com.battleship.shared.leaderboard.*;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class LeaderboardService {
    private final LeaderboardRepository repository;
    private final MatchRepository history;
    public LeaderboardService(LeaderboardRepository repository, MatchRepository history) {
        this.repository = repository;
        this.history = history;
    }
    public LeaderboardPage page(LeaderboardRequest request)
            throws SQLException, InterruptedException, ExecutionException, TimeoutException {
        if (request == null || request.sort() == null || request.page() < 0 || request.page() > 1_000_000)
            throw new IllegalArgumentException("Invalid leaderboard sort or page.");
        history.awaitPendingWrites();
        return repository.page(request);
    }
}
