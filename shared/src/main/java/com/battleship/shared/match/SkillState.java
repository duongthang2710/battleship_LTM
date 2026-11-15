package com.battleship.shared.match;

import com.battleship.shared.game.SkillResult;
import com.battleship.shared.game.SonarReading;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.ShieldCell;
import com.battleship.shared.game.SkillType;
import java.util.List;

/** Per-recipient skill data; only the recipient's own sonar readings are included. */
public record SkillState(int ownEnergy, int opponentEnergy, int maxEnergy, List<SonarReading> ownScans,
                         SkillResult lastSkill, List<Coordinate> ownMines, List<ShieldCell> ownShields,
                         List<SkillType> ownUsedSkills) {
    public SkillState(int ownEnergy, int opponentEnergy, int maxEnergy, List<SonarReading> ownScans, SkillResult lastSkill) {
        this(ownEnergy, opponentEnergy, maxEnergy, ownScans, lastSkill, List.of(), List.of(), List.of());
    }
    public SkillState(int ownEnergy, int opponentEnergy, int maxEnergy, List<SonarReading> ownScans,
                      SkillResult lastSkill, List<Coordinate> ownMines, List<ShieldCell> ownShields) {
        this(ownEnergy, opponentEnergy, maxEnergy, ownScans, lastSkill, ownMines, ownShields, List.of());
    }
    public SkillState {
        ownScans = ownScans == null ? List.of() : List.copyOf(ownScans);
        ownMines = ownMines == null ? List.of() : List.copyOf(ownMines);
        ownShields = ownShields == null ? List.of() : List.copyOf(ownShields);
        ownUsedSkills = ownUsedSkills == null ? List.of() : List.copyOf(ownUsedSkills);
    }
}
