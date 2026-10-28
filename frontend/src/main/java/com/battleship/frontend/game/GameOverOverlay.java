package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.frontend.ui.UiShapes;
import com.battleship.shared.game.GameState;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;

public final class GameOverOverlay {
    private static final float WIDTH = 430;
    private static final float HEIGHT = 220;
    private static final float BUTTON_X = 32;
    private static final float MENU_BUTTON_X = 234;
    private static final float BUTTON_Y = 28;
    private static final float BUTTON_WIDTH = 164;
    private static final float BUTTON_HEIGHT = 36;
    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private final Node root = new Node("Game over overlay");
    private final Node layout = new Node("Game over card");
    private final Materials materials;
    private final BitmapFont display;
    private final BitmapFont body;
    private BitmapText title;
    private BitmapText winner;
    private BitmapText resultReason;
    private BitmapText restartLabel;
    private BitmapText rematchStatus;
    private BitmapText declineLabel;
    private boolean playEnabled = true;
    private boolean declineEnabled;
    private float scale = 1;
    private float left;
    private float bottom;
    private boolean visible;

    public GameOverOverlay(AssetManager assets) {
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        root.attachChild(layout);
        build();
        resize(1440, 900);
        hide();
    }

    public Node root() { return root; }

    public void setNetworkMode(boolean value) {
        restartLabel.setText("PLAY AGAIN");
        UiTypography.centerIn(restartLabel, BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        setRematchStatus("", true, false);
        setEndReason(null);
    }

    public void setEndReason(com.battleship.shared.match.MatchEndReason reason) {
        if (resultReason == null) return;
        resultReason.setText(reason == null ? "WINNER" : switch (reason) {
            case FLEET_DESTROYED -> "FLEET DESTROYED";
            case TURN_TIMEOUT -> "TURN TIME EXPIRED";
            case PLAYER_LEFT -> "PLAYER LEFT THE MATCH";
        });
        center(resultReason, 131);
    }

    public void setRematchStatus(String status, boolean canPlay, boolean canDecline) {
        playEnabled = canPlay;
        declineEnabled = canDecline;
        restartLabel.setColor(Materials.rgb(canPlay ? IVORY : 0x8793A9));
        if (rematchStatus == null) return;
        rematchStatus.setText(status.length() > 48 ? status.substring(0, 45) + "..." : status);
        center(rematchStatus, 86);
        declineLabel.setCullHint(canDecline ? CullHint.Inherit : CullHint.Always);
    }

    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        left = (width - WIDTH * scale) / 2;
        bottom = (height - HEIGHT * scale) / 2;
        layout.setLocalScale(scale);
        layout.setLocalTranslation(left, bottom, 0);
    }

    public void show(GameState state) {
        boolean victory = state.player().id().equals(state.winnerId());
        title.setText(victory ? "VICTORY" : "DEFEAT");
        title.setColor(Materials.rgb(victory ? GOLD : 0xC1A3EC));
        winner.setText(victory ? state.player().name() : state.enemy().name());
        center(title, HEIGHT - 42);
        center(winner, 108);
        visible = true;
        root.setCullHint(CullHint.Never);
    }

    public void hide() {
        visible = false;
        root.setCullHint(CullHint.Always);
    }

    public boolean visible() { return visible; }

    public boolean contains(Vector2f screen) {
        return visible && inside(screen, 0, 0, WIDTH, HEIGHT);
    }

    public boolean restartAt(Vector2f screen) {
        return visible && playEnabled && inside(screen, BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    public boolean declineAt(Vector2f screen) {
        return visible && declineEnabled && inside(screen, BUTTON_X, 72, 80, 22);
    }

    public boolean mainMenuAt(Vector2f screen) {
        return visible && inside(screen, MENU_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private boolean inside(Vector2f screen, float x, float y, float width, float height) {
        float localX = (screen.x - left) / scale;
        float localY = (screen.y - bottom) / scale;
        return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
    }

    private void build() {
        panel(0, 0, WIDTH, HEIGHT, new ColorRGBA(0.003f, 0.007f, 0.021f, 0.96f), 40);
        frame(0, 0, WIDTH, HEIGHT);
        line(27, HEIGHT - 24, WIDTH / 2 - 12, HEIGHT - 24, 0x617D94);
        line(WIDTH / 2 + 12, HEIGHT - 24, WIDTH - 27, HEIGHT - 24, 0x617D94);
        Geometry crest = panel(WIDTH / 2, HEIGHT - 29, 7, 7, Materials.rgb(GOLD), 41);
        crest.rotate(0, 0, FastMath.QUARTER_PI);
        title = text(display, "VICTORY", 46, HEIGHT - 42, GOLD);
        resultReason = text(body, "WINNER", 10, 131, GOLD);
        winner = text(display, "", 25, 108, IVORY);
        panel(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                new ColorRGBA(0.025f, 0.055f, 0.095f, 1), 40).setMesh(UiShapes.chamfered(BUTTON_WIDTH, BUTTON_HEIGHT, 10));
        frame(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        restartLabel = text(body, "Restart", 13, BUTTON_Y + 24, IVORY);
        setNetworkMode(false);
        panel(MENU_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                new ColorRGBA(0.025f, 0.055f, 0.095f, 1), 40).setMesh(UiShapes.chamfered(BUTTON_WIDTH, BUTTON_HEIGHT, 10));
        frame(MENU_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        BitmapText menu = text(body, "MAIN MENU", 13, BUTTON_Y + 24, IVORY);
        UiTypography.centerIn(menu, MENU_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        rematchStatus = text(body, "", 10, 86, IVORY);
        declineLabel = text(body, "DECLINE", 10, 86, GOLD);
        UiTypography.centerIn(declineLabel, BUTTON_X, 72, 80, 22);
        declineLabel.setCullHint(CullHint.Always);
    }

    private void frame(float x, float y, float width, float height) {
        line(x + 10, y, x + width - 10, y, GOLD);
        line(x + 10, y + height, x + width - 10, y + height, GOLD);
        line(x, y + 10, x, y + height - 10, GOLD);
        line(x + width, y + 10, x + width, y + height - 10, GOLD);
        for (int sx : new int[] {-1, 1}) {
            for (int sy : new int[] {-1, 1}) {
                float px = sx < 0 ? x : x + width;
                float py = sy < 0 ? y : y + height;
                line(px, py - sy * 10, px - sx * 10, py, GOLD);
            }
        }
    }

    private void line(float x, float y, float endX, float endY, int color) {
        float dx = endX - x, dy = endY - y;
        Geometry geometry = panel(x, y, FastMath.sqrt(dx * dx + dy * dy), 0.8f, Materials.rgb(color), 41);
        geometry.rotate(0, 0, FastMath.atan2(dy, dx));
    }

    private BitmapText text(BitmapFont font, String value, float size, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(UiTypography.normalize(value));
        label.setSize(font == body ? UiTypography.bodySize(size) : size);
        label.setColor(Materials.rgb(color));
        center(label, y);
        layout.attachChild(label);
        return label;
    }

    private void center(BitmapText label, float y) {
        UiTypography.fit(label, WIDTH - 64);
        label.setLocalTranslation((WIDTH - label.getLineWidth()) / 2, y, 42);
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color, float depth) {
        Geometry geometry = new Geometry("Game over ornament", new Quad(width, height));
        Material material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, depth);
        layout.attachChild(geometry);
        return geometry;
    }
}
