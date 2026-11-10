package com.battleship.shared.game;

import java.util.List;

/**
 * Outcome of one skill. {@code type} summarizes the whole volley (GAME_OVER, SUNK, HIT, MISS, SCANNED for sonar)
 * or holds the rejection. {@code impacts} lists each shot in firing order.
 */
public record SkillResult(AttackResultType type, SkillType skill, String attackerId, String defenderId,
                          Coordinate origin, SkillDirection direction, List<Coordinate> area,
                          List<AttackResult> impacts, Integer sonarCount, boolean gameOver, String winnerId) {
    public SkillResult {
        area = area == null ? List.of() : List.copyOf(area);
        impacts = impacts == null ? List.of() : List.copyOf(impacts);
    }

    public boolean accepted() {
        return type == AttackResultType.HIT || type == AttackResultType.MISS || type == AttackResultType.SUNK
                || type == AttackResultType.GAME_OVER || type == AttackResultType.SCANNED;
    }

    static SkillResult rejected(AttackResultType type, SkillType skill, String attackerId, String defenderId,
                                Coordinate origin, SkillDirection direction, boolean gameOver, String winnerId) {
        return new SkillResult(type, skill, attackerId, defenderId, origin, direction, List.of(), List.of(), null,
                gameOver, winnerId);
    }
}
