package com.battleship.frontend.ui;

/** Logical pixels shared by the banner, minimap and downward-opening chat. */
public final class BattleSidebarLayout {
    public static final float LEFT = 36;
    public static final float CHAT_WIDTH = 255;
    public static final float CENTER_X = LEFT + CHAT_WIDTH / 2;
    public static final float BANNER_HEIGHT = 265;
    public static final float MAP_TOP = 280;
    public static final float MAP_SIZE = 200;
    public static final float GAP = 12;
    private BattleSidebarLayout() { }
    public static float flagWidth(float width) { return Math.min(180, width * .125f); }
    public static float flagLeft(float width) { return CENTER_X - flagWidth(width) / 2; }
    public static float mapLeft() { return CENTER_X - MAP_SIZE / 2; }
    public static float mapBottom(float height) { return height - MAP_TOP - MAP_SIZE; }
    public static float chatTop(float height) { return mapBottom(height) - GAP; }
}
