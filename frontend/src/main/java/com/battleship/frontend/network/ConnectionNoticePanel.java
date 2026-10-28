package com.battleship.frontend.network;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.font.Rectangle;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import java.util.Optional;

/** Connection notification only; transport remains in ConnectionManager. */
public final class ConnectionNoticePanel {
    public enum Action { RECONNECT, SETTINGS, DISMISS }
    private static final float WIDTH = 620, HEIGHT = 270;
    private final Node root = new Node("Connection notification"), card = new Node("Connection card");
    private final Materials materials;
    private final BitmapFont body, display;
    private boolean visible, busy;
    private String detail = "";
    private float left, bottom, scale = 1;

    public ConnectionNoticePanel(AssetManager assets) {
        materials = new Materials(assets); body = UiTypography.body(assets); display = UiTypography.display(assets);
        root.attachChild(card); resize(1440, 900); root.setCullHint(Spatial.CullHint.Always);
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public boolean busy() { return busy; }
    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height); left = (width - WIDTH * scale) / 2; bottom = (height - HEIGHT * scale) / 2;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public void sync(String problem, boolean connecting) {
        boolean show = problem != null;
        String next = show ? problem : "";
        if (show == visible && next.equals(detail) && busy == connecting) return;
        visible = show; detail = next; busy = connecting;
        root.setCullHint(show ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (show) rebuild();
    }
    public Optional<Action> actionAt(Vector2f point) {
        if (!visible) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        if (x >= 28 && x <= 592 && y >= 14 && y <= 42) return Optional.of(Action.DISMISS);
        if (busy) return Optional.empty();
        if (y >= 54 && y <= 94) {
            if (x >= 28 && x <= 292) return Optional.of(Action.RECONNECT);
            if (x >= 312 && x <= 592) return Optional.of(Action.SETTINGS);
        }
        return Optional.empty();
    }
    public Vector2f buttonCenter(Action action) {
        return new Vector2f(left + (action == Action.RECONNECT ? 160 : action == Action.SETTINGS ? 452 : 310) * scale,
                bottom + (action == Action.DISMISS ? 28 : 74) * scale);
    }
    private void rebuild() {
        card.detachAllChildren();
        rect(0, 0, WIDTH, HEIGHT, new ColorRGBA(.003f, .009f, .025f, .99f));
        frame(0, 0, WIDTH, HEIGHT, 0xDEB663); frame(7, 7, WIDTH - 14, HEIGHT - 14, 0x655638);
        text(display, busy ? "RECONNECTING" : "CONNECTION LOST", 35, 28, 240, 0xF4EAD1);
        text(body, busy ? "Contacting the server..." : "Reconnect to continue, then sign in again.", 15, 30, 191, 0x91CEFF);
        BitmapText reason = text(body, detail, 14, 30, 160, 0xB9C7DB);
        reason.setBox(new Rectangle(0, 0, WIDTH - 60, 56)); reason.setLineWrapMode(LineWrapMode.Word);
        button(28, 54, 264, busy ? "CONNECTING..." : "RECONNECT"); button(312, 54, 280, "CONNECTION SETTINGS");
        UiTypography.centerIn(text(body, "BACK TO SIGN IN  /  ESC", 13, 28, 14, 0xB9C7DB), 28, 14, WIDTH - 56, 28);
    }
    private void button(float x, float y, float width, String label) {
        rect(x, y, width, 40, new ColorRGBA(.025f, .055f, .095f, 1)); frame(x, y, width, 40, busy ? 0x334050 : 0xDEB663);
        UiTypography.centerIn(text(body, label, 15, x, y, busy ? 0x69788D : 0xF4EAD1), x, y, width, 40);
    }
    private void frame(float x, float y, float width, float height, int color) {
        ColorRGBA c = Materials.rgb(color); rect(x, y, width, .8f, c); rect(x, y + height, width, .8f, c);
        rect(x, y, .8f, height, c); rect(x + width, y, .8f, height, c);
    }
    private void rect(float x, float y, float width, float height, ColorRGBA color) {
        Geometry geometry = new Geometry("Connection notice detail", new Quad(width, height)); var material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha); geometry.setMaterial(material);
        geometry.setLocalTranslation(x, y, 300 + card.getQuantity() * .001f); card.attachChild(geometry);
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        var label = new BitmapText(font); label.setText(UiTypography.normalize(value)); label.setSize(size);
        label.setColor(Materials.rgb(color)); label.setLocalTranslation(x, y, 305); card.attachChild(label); return label;
    }
}
