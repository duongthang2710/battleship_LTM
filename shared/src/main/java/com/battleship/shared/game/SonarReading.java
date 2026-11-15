package com.battleship.shared.game;

import java.util.List;

/** The scanner learns only the total occupied-cell count for the whole area. */
public record SonarReading(List<Coordinate> area, int count) {
    public SonarReading {
        area = List.copyOf(area);
    }
}
