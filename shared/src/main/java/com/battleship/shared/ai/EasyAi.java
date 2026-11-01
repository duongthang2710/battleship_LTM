package com.battleship.shared.ai;

import com.battleship.shared.game.Coordinate;
import java.util.Random;

public final class EasyAi implements AiStrategy {
    @Override public Coordinate chooseTarget(AiKnowledge knowledge, Random random) {
        return AiStrategy.pick(knowledge.available(), random);
    }
}
