package com.battleship.shared.ai;

import com.battleship.shared.game.*;
import java.util.*;

/** Placement-density heuristic, not an exact posterior over complete fleets.
 * Bounded to at most 1,000 straight candidates on a 10x10 board; no hidden fleet input.
 */
public final class HardAi implements AiStrategy {
    public Map<Coordinate, Integer> scores(AiKnowledge knowledge) {
        record Candidate(List<Coordinate> cells, int hits) {}
        List<Candidate> candidates = new ArrayList<>();
        int mostHits = 0;
        for (ShipType type : ShipType.values()) {
            if (knowledge.sunkShips().contains(type)) continue;
            for (int row = 0; row < Board.SIZE; row++) for (int col = 0; col < Board.SIZE; col++) {
                for (int[] direction : new int[][]{{1,0},{0,1}}) {
                    List<Coordinate> cells = new ArrayList<>();
                    for (int i = 0; i < type.length(); i++) cells.add(new Coordinate(row + direction[0]*i, col + direction[1]*i));
                    if (cells.stream().anyMatch(c -> !Board.inBounds(c) || knowledge.blocked(c))) continue;
                    if (cells.stream().noneMatch(knowledge::unshot)) continue;
                    int hits = (int) cells.stream().filter(knowledge::hit).count();
                    mostHits = Math.max(mostHits, hits);
                    candidates.add(new Candidate(cells, hits));
                }
            }
        }
        Map<Coordinate, Integer> scores = new HashMap<>();
        for (Candidate candidate : candidates) {
            // Finish the strongest supported line; other hits remain available after it sinks/misses.
            if (mostHits > 0 && candidate.hits() != mostHits) continue;
            for (Coordinate cell : candidate.cells()) if (knowledge.unshot(cell)) scores.merge(cell, 1, Integer::sum);
        }
        return Map.copyOf(scores);
    }

    @Override public Coordinate chooseTarget(AiKnowledge knowledge, Random random) {
        Map<Coordinate, Integer> scores = scores(knowledge);
        List<Coordinate> available = knowledge.available();
        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return AiStrategy.pick(available.stream().filter(c -> scores.getOrDefault(c, 0) == best).toList(), random);
    }
}
