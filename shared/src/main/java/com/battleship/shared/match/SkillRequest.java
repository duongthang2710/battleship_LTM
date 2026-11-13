package com.battleship.shared.match;

import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.SkillDirection;
import com.battleship.shared.game.SkillType;

/** The direction selects the Airstrike row or column. */
public record SkillRequest(SkillType skill, Coordinate target, SkillDirection direction) { }
