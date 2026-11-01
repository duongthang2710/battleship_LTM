package com.battleship.frontend.ai;

import com.battleship.frontend.ui.NavalHudSkin;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.shared.ai.AiDifficulty;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import java.util.Optional;

/** Small setup card reusing the existing naval palette, fonts and controls. */
public final class AiSetupPanel {
    public enum Action { EASY, MEDIUM, HARD, START, BACK }
    private final Node root = new Node("AI setup"), layout = new Node("AI setup card");
    private final AssetManager assets;
    private AiDifficulty difficulty = AiDifficulty.MEDIUM;
    private float scale = 1, left, bottom;
    private boolean visible;

    public AiSetupPanel(AssetManager assets) {
        this.assets = assets; root.attachChild(layout); rebuild(); resize(1440, 900); hide();
    }
    public Node root() { return root; }
    public AiDifficulty difficulty() { return difficulty; }
    public boolean visible() { return visible; }
    public void show() { visible = true; root.setCullHint(CullHint.Never); }
    public void hide() { visible = false; root.setCullHint(CullHint.Always); }
    public void select(AiDifficulty difficulty) { this.difficulty = difficulty; rebuild(); }
    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        left = (width - 540 * scale) / 2; bottom = (height - 430 * scale) / 2;
        layout.setLocalScale(scale); layout.setLocalTranslation(left, bottom, 0);
    }
    public Vector2f buttonCenter(Action action) {
        float x = action == Action.BACK ? 143 : action == Action.START ? 397 : 270;
        float y = switch (action) { case EASY -> 302; case MEDIUM -> 230; case HARD -> 158; default -> 50; };
        return new Vector2f(left + x * scale, bottom + y * scale);
    }
    public Optional<Action> actionAt(Vector2f cursor) {
        if (!visible) return Optional.empty();
        float x = (cursor.x-left)/scale, y = (cursor.y-bottom)/scale;
        for (Action action : Action.values()) {
            Vector2f center = buttonCenter(action);
            float cx = (center.x-left)/scale, cy = (center.y-bottom)/scale;
            float halfWidth = action == Action.BACK || action == Action.START ? 111 : 238;
            float halfHeight = action == Action.BACK || action == Action.START ? 20 : 30;
            if (Math.abs(x-cx)<=halfWidth && Math.abs(y-cy)<=halfHeight) return Optional.of(action);
        }
        return Optional.empty();
    }
    private void rebuild() {
        layout.detachAllChildren();
        var draw = new NavalHudSkin(assets, layout, 60);
        draw.panel(0,0,540,430,0x031020);
        draw.text("AI BATTLE",30,20,365,500,46,0xF4EAD1);
        draw.text("CHOOSE DIFFICULTY",13,20,342,500,24,0x91CEFF);
        for (AiDifficulty level : AiDifficulty.values()) {
            float y = 272 - level.ordinal()*72;
            boolean selected = difficulty == level;
            draw.panel(32,y,476,60,selected?0x12557C:0x082D46);
            draw.text(level.label().toUpperCase(),22,32,y,476,60,selected?0xDEB663:0xF4EAD1);
        }
        draw.text("Classic fleet / 5 ships / 60 seconds per turn",12,20,86,500,24,0x91CEFF);
        draw.panel(32,30,222,40,0x082D46); draw.text("MAIN MENU",14,32,30,222,40,0xF4EAD1);
        draw.panel(286,30,222,40,0x12557C); draw.text("START",16,286,30,222,40,0xDEB663);
    }
}
