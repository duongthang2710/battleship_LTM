package com.battleship.shared.match;

import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.SkillDirection;
import com.battleship.shared.game.SkillType;

/** {@code direction} is only used by the torpedo. */
public record SkillRequest(SkillType skill, Coordinate target, SkillDirection direction) { }
