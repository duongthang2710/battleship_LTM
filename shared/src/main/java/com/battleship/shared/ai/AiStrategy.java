package com.battleship.shared.ai;

import com.battleship.shared.game.Coordinate;
import java.util.*;

public interface AiStrategy {
    Coordinate chooseTarget(AiKnowledge knowledge, Random random);

    static AiStrategy forDifficulty(AiDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new EasyAi();
            case MEDIUM -> new MediumAi();
            case HARD -> new HardAi();
        };
    }

    static Coordinate pick(List<Coordinate> cells, Random random) {
        if (cells.isEmpty()) throw new IllegalStateException("No unshot cells remain");
        return cells.get(random.nextInt(cells.size()));
    }
}
