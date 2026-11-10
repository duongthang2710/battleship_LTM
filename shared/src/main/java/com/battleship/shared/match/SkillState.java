package com.battleship.shared.match;

import com.battleship.shared.game.SkillResult;
import com.battleship.shared.game.SonarReading;
import java.util.List;

/** Per-recipient skill data; only the recipient's own sonar readings are included. */
public record SkillState(int ownEnergy, int opponentEnergy, int maxEnergy, List<SonarReading> ownScans,
                         SkillResult lastSkill) {
    public SkillState { ownScans = ownScans == null ? List.of() : List.copyOf(ownScans); }
}
