package com.battleship.frontend.settings;

import java.util.List;

public record ClientPreferences(int width, int height, boolean fullscreen, float rotation, float zoom) {
    public record Resolution(int width, int height) {
        @Override public String toString() { return width + " x " + height; }
    }
    public static final List<Resolution> RESOLUTIONS = List.of(new Resolution(960, 600), new Resolution(1280, 720),
            new Resolution(1440, 900), new Resolution(1600, 900), new Resolution(1920, 1080));

    public ClientPreferences {
        if (!RESOLUTIONS.contains(new Resolution(width, height)) || !Float.isFinite(rotation) || !Float.isFinite(zoom)
                || rotation < .5f || rotation > 2 || zoom < .5f || zoom > 2)
            throw new IllegalArgumentException("Unsupported display size or sensitivity. Using defaults.");
    }
    public static ClientPreferences defaults() { return new ClientPreferences(1440, 900, false, 1, 1); }
}
