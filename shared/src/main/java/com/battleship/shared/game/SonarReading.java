package com.battleship.shared.game;

import java.util.List;

/** Number of not-yet-hit ship cells inside the scanned area when the scan happened. */
public record SonarReading(List<Coordinate> area, int count) {
    public SonarReading { area = List.copyOf(area); }
}
