package com.battleship.frontend.ui;

/** Logical pixels shared by the commander banner and minimap. */
public final class BattleSidebarLayout {
    public static final float LEFT = 36;
    public static final float SIDEBAR_WIDTH = 255;
    public static final float CENTER_X = LEFT + SIDEBAR_WIDTH / 2;
    public static final float BANNER_HEIGHT = 288;
    public static final float MAP_TOP = 308;
    public static final float MAP_SIZE = 200;
    private BattleSidebarLayout() { }
    public static float flagWidth(float width) { return Math.min(192, width * .14f); }
    public static float flagLeft(float width) { return CENTER_X - flagWidth(width) / 2; }
    public static float mapLeft() { return CENTER_X - MAP_SIZE / 2; }
    public static float mapBottom(float height) { return height - MAP_TOP - MAP_SIZE; }
}
