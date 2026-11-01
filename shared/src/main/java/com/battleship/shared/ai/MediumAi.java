package com.battleship.shared.ai;

import com.battleship.shared.game.Coordinate;
import java.util.*;

/** Hunt on alternating cells; pursue hits, preferring continuations of a known line. */
public final class MediumAi implements AiStrategy {
    @Override public Coordinate chooseTarget(AiKnowledge knowledge, Random random) {
        Map<Coordinate, Integer> targets = new HashMap<>();
        for (Coordinate hit : knowledge.shots().keySet().stream().sorted(
                Comparator.comparingInt(Coordinate::row).thenComparingInt(Coordinate::column)).toList()) {
            if (!knowledge.hit(hit)) continue;
            for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Coordinate next = hit.offset(direction[0], direction[1]);
                if (knowledge.unshot(next)) {
                    int priority = knowledge.hit(hit.offset(-direction[0], -direction[1])) ? 10 : 1;
                    targets.merge(next, priority, Integer::sum);
                }
            }
        }
        List<Coordinate> available = knowledge.available();
        if (!targets.isEmpty()) {
            int best = Collections.max(targets.values());
            return AiStrategy.pick(available.stream().filter(c -> targets.getOrDefault(c, 0) == best).toList(), random);
        }
        List<Coordinate> hunt = available.stream().filter(c -> (c.row() + c.column()) % 2 == 0).toList();
        return AiStrategy.pick(hunt.isEmpty() ? available : hunt, random);
    }
}
