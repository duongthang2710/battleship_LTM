package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
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
import com.jme3.scene.shape.Quad;

public final class BattleHud {
    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private final Node root = new Node("Battle HUD");
    private final Node layout = new Node("HUD layout");
    private final Node turnClock = new Node("Turn countdown");
    private final Materials materials;
    private final BitmapFont display;
    private final BitmapFont body;
    private String playerName;
    private BitmapText playerText;
    private BitmapText connectionText;
    private BitmapText opponentText;
    private BitmapText playerSideText;
    private BitmapText opponentSideText;
    private com.battleship.shared.game.Side side;
    private BitmapText statusTitleText;
    private BitmapText statusDetailText;
    private BitmapText controlsText;
    private String connectionStatus = "Connecting";
    private String opponentName = "Awaiting captain";
    private String statusTitle = "Awaiting the next tide";
    private String statusDetail = "Visual preview - no match in progress";
    private String controlHints = "DRAG TO ORBIT  /  SCROLL TO ZOOM  /  R TO RESET";
    private float uiScale = 1;
    private float logicalWidth;
    private float logicalHeight;
    private BitmapText clockPlayer;
    private BitmapText clockTime;
    private String clockOwner = "";
    private int clockSeconds = -1;

    public BattleHud(AssetManager assets, String playerName) {
        this.playerName = playerName;
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        root.attachChild(layout);
    }

    public Node root() { return root; }

    public void resize(int width, int height) {
        float scale = UiTypography.scale(width, height);
        float w = width / scale, h = height / scale;
        uiScale = scale;
        logicalWidth = w;
        logicalHeight = h;
        layout.detachAllChildren();
        layout.setLocalScale(scale);
        frame(18, 18, w - 36, h - 36);
        ornatePanel(36, h - 130, 350, 94);
        text(display, "Battleship", 43, 57, h - 52, IVORY);
        text(body, "F A N T A S Y   N A V A L   S T R A T E G Y", 10, 60, h - 106, GOLD);
        ornatePanel(w - 427, h - 119, 391, 83);
        playerSideText = text(body, "RADIANT", 10, w - 406, h - 55, 0x91CEFF);
        playerText = text(body, playerName, 17, w - 406, h - 79, IVORY);
        line(w - 240, h - 100, w - 240, h - 55, GOLD);
        opponentSideText = text(body, "ABYSSAL", 10, w - 221, h - 55, 0xC1A3EC);
        setPlayerSide(side);
        opponentText = text(body, opponentName, 16, w - 221, h - 79, IVORY);
        ornatePanel(36, 43, 294, 85);
        text(body, "TURN STATUS", 10, 55, 109, GOLD);
        statusTitleText = text(display, statusTitle, 26, 54, 91, IVORY);
        statusDetailText = text(body, statusDetail, 10, 56, 62, 0xB6BDCF);
        ornatePanel(w - 374, 43, 338, 85);
        controlsText = text(body, controlHints, 10, w - 355, 116, IVORY);
        connectionText = text(body, connectionStatus, 11, w - 355, 65, GOLD);
        UiTypography.fit(playerText, 155); UiTypography.fit(opponentText, 165);
        UiTypography.fit(statusDetailText, 254);
        buildClock(w, h);
    }

    public boolean contains(Vector2f screen) {
        if (logicalWidth == 0) return false;
        float x = screen.x / uiScale, y = screen.y / uiScale;
        return inside(x, y, 36, logicalHeight - 130, 350, 94)
                || inside(x, y, logicalWidth - 427, logicalHeight - 119, 391, 83)
                || inside(x, y, 36, 43, 294, 85)
                || (turnClock.getCullHint() != com.jme3.scene.Spatial.CullHint.Always
                    && inside(x, y, logicalWidth / 2 - 132, logicalHeight - 116, 264, 80))
                || inside(x, y, logicalWidth - 374, 43, 338, 85);
    }

    private void buildClock(float width, float height) {
        turnClock.detachAllChildren();
        turnClock.setLocalTranslation(width / 2 - 132, height - 116, 4);
        layout.attachChild(turnClock);
        float[][] rectangles = {{0, 0, 264, 80}, {10, 0, 244, 1}, {10, 79, 244, 1},
                {0, 10, 1, 60}, {263, 10, 1, 60}};
        for (int i = 0; i < rectangles.length; i++) {
            float[] rect = rectangles[i];
            Geometry quad = new Geometry("Turn clock panel", new Quad(rect[2], rect[3]));
            Material material = materials.flat(i == 0 ? new ColorRGBA(0.003f, 0.007f, 0.021f, 0.90f) : Materials.rgb(GOLD));
            material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            quad.setMaterial(material);
            quad.setQueueBucket(Bucket.Gui);
            quad.setLocalTranslation(rect[0], rect[1], i == 0 ? 0 : 1);
            turnClock.attachChild(quad);
        }
        clockPlayer = new BitmapText(body);
        clockPlayer.setSize(UiTypography.bodySize(11));
        clockPlayer.setColor(Materials.rgb(GOLD));
        turnClock.attachChild(clockPlayer);
        clockTime = new BitmapText(body);
        clockTime.setSize(36);
        turnClock.attachChild(clockTime);
        clockSeconds = -1;
        hideTurnClock();
    }

    public void hideTurnClock() { turnClock.setCullHint(com.jme3.scene.Spatial.CullHint.Always); }

    public void setTurnClock(String player, long remainingMillis) {
        if (clockTime == null) return;
        turnClock.setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
        int seconds = (int) Math.min(60, (Math.max(0, remainingMillis) + 999) / 1000);
        if (seconds == clockSeconds && player.equals(clockOwner)) return;
        clockOwner = player;
        clockSeconds = seconds;
        String name = player.length() > 24 ? player.substring(0, 23) + "." : player;
        clockPlayer.setText("TURN: " + name);
        UiTypography.fit(clockPlayer, 244);
        clockPlayer.setLocalTranslation((264 - clockPlayer.getLineWidth()) / 2, 64, 2);
        clockTime.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
        clockTime.setColor(Materials.rgb(seconds <= 10 ? 0xF5A38E : IVORY));
        clockTime.setLocalTranslation((264 - clockTime.getLineWidth()) / 2, 45, 2);
    }

    private boolean inside(float x, float y, float left, float bottom, float width, float height) {
        return x >= left && x <= left + width && y >= bottom && y <= bottom + height;
    }

    public void setConnectionStatus(String status) {
        connectionStatus = status;
        if (connectionText != null) connectionText.setText(status);
    }

    public void setPlayerSide(com.battleship.shared.game.Side side) {
        this.side = side;
        if (playerSideText == null) return;
        playerSideText.setText(side == null ? "RADIANT" : side + " / YOU");
        opponentSideText.setText(side == null ? "ABYSSAL" : side.opponent() + " / OPPONENT");
        playerSideText.setColor(Materials.rgb(side == com.battleship.shared.game.Side.BLACK ? 0xC1A3EC : 0x91CEFF));
        opponentSideText.setColor(Materials.rgb(side == com.battleship.shared.game.Side.BLACK ? 0x91CEFF : 0xC1A3EC));
    }

    public void setPlayerName(String name) {
        playerName = name;
        if (playerText != null) { playerText.setText(name); UiTypography.fit(playerText, 155); }
    }

    public void setOpponentName(String name) {
        opponentName = name;
        if (opponentText != null) { opponentText.setText(name); UiTypography.fit(opponentText, 165); }
    }

    public void setGameStatus(String title, String detail) {
        statusTitle = title;
        statusDetail = detail;
        if (statusTitleText != null) statusTitleText.setText(title);
        if (statusDetailText != null) { statusDetailText.setText(detail); UiTypography.fit(statusDetailText, 254); }
    }

    public void setControlHints(String hints) {
        controlHints = hints;
        if (controlsText != null) controlsText.setText(hints);
    }

    private void ornatePanel(float x, float y, float width, float height) {
        panel(x, y, width, height, new ColorRGBA(0.003f, 0.007f, 0.021f, 0.86f));
        frame(x, y, width, height);
    }

    private void frame(float x, float y, float width, float height) {
        line(x + 12, y, x + width - 12, y, GOLD);
        line(x + 12, y + height, x + width - 12, y + height, GOLD);
        line(x, y + 12, x, y + height - 12, GOLD);
        line(x + width, y + 12, x + width, y + height - 12, GOLD);
        for (int sx : new int[] {-1, 1}) {
            for (int sy : new int[] {-1, 1}) {
                float px = sx < 0 ? x : x + width, py = sy < 0 ? y : y + height;
                float dx = -sx, dy = -sy;
                line(px, py + dy * 12, px + dx * 12, py, GOLD);
                line(px + dx * 4, py + dy * 22, px + dx * 22, py + dy * 4, GOLD);
                diamond(px + dx * 11, py + dy * 11, 2.8f, GOLD);
            }
        }
    }

    private void line(float x, float y, float endX, float endY, int color) {
        float dx = endX - x, dy = endY - y;
        Geometry geometry = panel(x, y, FastMath.sqrt(dx * dx + dy * dy), 1, Materials.rgb(color));
        geometry.rotate(0, 0, FastMath.atan2(dy, dx));
        geometry.setLocalTranslation(x, y, 1);
    }

    private void diamond(float x, float y, float size, int color) {
        Geometry geometry = panel(x, y - size, size * 1.414f, size * 1.414f, Materials.rgb(color));
        geometry.rotate(0, 0, FastMath.QUARTER_PI);
        geometry.setLocalTranslation(x, y - size, 1);
    }

    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText text = new BitmapText(font);
        text.setText(UiTypography.normalize(value));
        text.setSize(font == body ? UiTypography.bodySize(size) : size);
        text.setColor(Materials.rgb(color));
        text.setLocalTranslation(x, y, 2);
        layout.attachChild(text);
        return text;
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color) {
        Geometry geometry = new Geometry("Ornamental HUD", new Quad(width, height));
        Material material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, 0);
        layout.attachChild(geometry);
        return geometry;
    }
}
